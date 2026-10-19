package com.sitecam.app.feature.annotation

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.MatrixCursor
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import com.sitecam.app.core.database.AppDatabase
import com.sitecam.app.core.database.entity.AnnotationEntity
import com.sitecam.app.core.database.entity.MediaItemEntity
import com.sitecam.app.core.database.entity.ProjectEntity
import com.sitecam.app.core.di.AppContainer
import com.sitecam.app.core.media.MediaStoreManager
import com.sitecam.app.core.preferences.AppSettingsDataStore
import com.sitecam.app.feature.annotation.model.AnnotationElement
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.io.File
import java.io.FileNotFoundException
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.rules.TemporaryFolder
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowContentResolver

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(ExperimentalCoroutinesApi::class)
class AnnotationFailureAndPreviewIntegrationTest {
    @get:Rule val temp = TemporaryFolder()

    private class ReadGate(val skippedReads: Int = 0) {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        private val skipped = AtomicInteger(0)

        fun intercept(): Boolean {
            if (skipped.getAndIncrement() < skippedReads) return false
            entered.countDown()
            check(release.await(20, TimeUnit.SECONDS)) { "test did not release blocked image read" }
            return true
        }
    }

    private class ImageProvider : ContentProvider() {
        private val files = mutableMapOf<String, File>()
        private val gates = ConcurrentLinkedQueue<Pair<String, ReadGate>>()
        private var next = 0

        override fun onCreate() = true
        fun add(file: File): Uri = Uri.parse("content://$AUTHORITY/image/${++next}").also { files[it.toString()] = file }
        fun missing(name: String): Uri = Uri.parse("content://$AUTHORITY/missing/$name")
        fun gateNextRead(uri: Uri, skippedReads: Int = 0) = ReadGate(skippedReads).also { gates.add(uri.toString() to it) }
        fun releaseGates() { gates.forEach { it.second.release.countDown() } }

        override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
            if (mode.contains('r')) {
                val gate = gates.firstOrNull { it.first == uri.toString() }?.second
                if (gate != null && gate.intercept()) gates.remove(uri.toString() to gate)
            }
            val file = files[uri.toString()] ?: throw FileNotFoundException(uri.toString())
            return ParcelFileDescriptor.open(file, ParcelFileDescriptor.parseMode(mode))
        }

        override fun getType(uri: Uri) = "image/jpeg"
        override fun insert(uri: Uri, values: ContentValues?) = null
        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
        override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?) =
            MatrixCursor(projection ?: arrayOf(MediaStore.MediaColumns.DISPLAY_NAME)).apply {
                val file = files[uri.toString()] ?: return@apply
                addRow(columnNames.map { if (it == MediaStore.MediaColumns.DISPLAY_NAME) file.name else null }.toTypedArray())
            }
    }

    private class Fixture(val context: Context, val directory: File) : AutoCloseable {
        val provider = ImageProvider()
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        val settings = AppSettingsDataStore(context)
        val mediaStore = mockk<MediaStoreManager>(relaxed = true)
        val container = mockk<AppContainer>()
        val source = File(directory, "source.jpg")
        val originalBytes: ByteArray
        val sourceUri: Uri
        val mediaId: Long
        var model: PhotoAnnotationViewModel

        init {
            provider.attachInfo(context, android.content.pm.ProviderInfo().apply {
                authority = AUTHORITY
                exported = true
                grantUriPermissions = true
            })
            ShadowContentResolver.registerProviderInternal(AUTHORITY, provider)
            every { container.appContext } returns context
            every { container.database } returns database
            every { container.settingsDataStore } returns settings
            every { container.mediaStoreManager } returns mediaStore

            val bitmap = Bitmap.createBitmap(400, 300, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.WHITE)
            source.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it)) }
            bitmap.recycle()
            originalBytes = source.readBytes()
            sourceUri = provider.add(source)
            mediaId = runBlocking {
                val projectId = database.projectDao().insertProject(ProjectEntity(name = "标注异常回归"))
                database.mediaItemDao().insertMediaItem(MediaItemEntity(
                    id = NEXT_MEDIA_ID.incrementAndGet().toLong(),
                    projectId = projectId,
                    contentUri = sourceUri.toString(),
                    filePath = source.absolutePath,
                    fileName = source.name,
                    width = 400,
                    height = 300,
                    captureTimestamp = 1_700_000_000_000L
                ))
            }
            model = PhotoAnnotationViewModel(container, mediaId)
        }

        suspend fun awaitLoaded() = withTimeout(15_000) {
            model.uiState.first { it.mediaItem?.id == mediaId }
        }

        suspend fun reloadModel(): AnnotationUiState {
            model.viewModelScope.cancel()
            model = PhotoAnnotationViewModel(container, mediaId)
            return awaitLoaded()
        }

        suspend fun installTextRecipe() {
            awaitLoaded()
            withTimeout(15_000) { model.uiState.first { it.previewBitmap != null && !it.isSaving } }
            val note = AnnotationElement.TextNote(Offset(60f, 130f), "moving", ComposeColor.Red, 7f)
            database.issueDao().insertAnnotation(AnnotationEntity(
                mediaId = mediaId,
                annotatedContentUri = sourceUri.toString(),
                vectorDataJson = EditRecipe.encode(listOf(EditStep.Draw(listOf(note), 400f, 300f)), sourceUri.toString())
            ))
            // A fresh ViewModel reads the persisted recipe.
            reloadModel()
            withTimeout(15_000) { model.uiState.first { it.mediaItem?.id == mediaId && it.previewBitmap != null && !it.isSaving } }
        }

        override fun close() {
            provider.releaseGates()
            model.viewModelScope.cancel()
            database.close()
            directory.deleteRecursively()
        }
    }

    @Test
    fun missingLegacyProductAndPrivateBaseRemainObservableAndCannotBeOverwritten() = runBlocking {
        Dispatchers.setMain(Dispatchers.Unconfined)
        val context = RuntimeEnvironment.getApplication() as Context
        val fixture = Fixture(context, temp.newFolder("missing_annotation_sources"))
        try {
            val baseUri = fixture.provider.missing("private-base")
            val productUri = fixture.provider.missing("old-product")
            val note = AnnotationElement.TextNote(Offset(40f, 80f), "保留", ComposeColor.Red, 7f)
            val annotation = AnnotationEntity(
                mediaId = fixture.mediaId,
                annotatedContentUri = productUri.toString(),
                vectorDataJson = EditRecipe.encode(listOf(EditStep.Draw(listOf(note), 400f, 300f)), baseUri.toString())
            )
            fixture.database.issueDao().insertAnnotation(annotation)
            val savedRow = fixture.database.issueDao().getAnnotationByMediaId(fixture.mediaId)
            val original = fixture.source.readBytes()
            val loaded = fixture.reloadModel()

            assertNotNull("the project media row remains observable", loaded.mediaItem)
            assertNotNull("load failure is retained in observable state", loaded.loadError)
            assertEquals(productUri.toString(), savedRow?.annotatedContentUri)
            assertArrayEquals(original, fixture.source.readBytes())

            val failure = async(start = CoroutineStart.UNDISPATCHED) { fixture.model.saveFailed.first() }
            fixture.model.saveAnnotatedImage(context, 400f, 300f)
            assertTrue(withTimeout(15_000) { failure.await() }.contains("底图"))
            assertEquals(savedRow, fixture.database.issueDao().getAnnotationByMediaId(fixture.mediaId))
            assertArrayEquals(original, fixture.source.readBytes())
            coVerify(exactly = 0) { fixture.mediaStore.savePhotoToMediaStore(any(), any(), any(), any(), any(), any(), any()) }
        } finally {
            fixture.close()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun malformedRecipeWithMissingOldProductLoadsAndRefusesReplacement() = runBlocking {
        Dispatchers.setMain(Dispatchers.Unconfined)
        val context = RuntimeEnvironment.getApplication() as Context
        val fixture = Fixture(context, temp.newFolder("malformed_annotation_recipe"))
        try {
            val productUri = fixture.provider.missing("malformed-old-product")
            val annotation = AnnotationEntity(
                mediaId = fixture.mediaId,
                annotatedContentUri = productUri.toString(),
                vectorDataJson = "{broken recipe"
            )
            fixture.database.issueDao().insertAnnotation(annotation)
            val savedRow = fixture.database.issueDao().getAnnotationByMediaId(fixture.mediaId)
            val original = fixture.source.readBytes()
            val loaded = fixture.reloadModel()

            assertNotNull(loaded.mediaItem)
            assertNotNull(loaded.loadError)
            val failure = async(start = CoroutineStart.UNDISPATCHED) { fixture.model.saveFailed.first() }
            fixture.model.saveAnnotatedImage(context, 400f, 300f)
            assertTrue(withTimeout(15_000) { failure.await() }.contains("底图"))
            assertEquals(savedRow, fixture.database.issueDao().getAnnotationByMediaId(fixture.mediaId))
            assertArrayEquals(original, fixture.source.readBytes())
            coVerify(exactly = 0) { fixture.mediaStore.savePhotoToMediaStore(any(), any(), any(), any(), any(), any(), any()) }
        } finally {
            fixture.close()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun lateStateObserverSeesLoadErrorWithoutSavingOrCollectingFailureEvents() = runBlocking {
        Dispatchers.setMain(Dispatchers.Unconfined)
        val context = RuntimeEnvironment.getApplication() as Context
        val fixture = Fixture(context, temp.newFolder("late_load_error_observer"))
        try {
            val annotation = AnnotationEntity(
                mediaId = fixture.mediaId,
                annotatedContentUri = fixture.provider.missing("legacy-product").toString(),
                vectorDataJson = ""
            )
            fixture.database.issueDao().insertAnnotation(annotation)
            val savedRow = fixture.database.issueDao().getAnnotationByMediaId(fixture.mediaId)
            fixture.reloadModel()

            // Subscribe only after loading completed, without collecting saveFailed
            // or pressing save. State must still describe the failed load.
            val observed = withTimeout(5_000) { fixture.model.uiState.first { it.loadError != null } }
            assertTrue(observed.loadError!!.contains("无法读取"))
            assertNull(observed.previewBitmap)
            assertEquals(savedRow, fixture.database.issueDao().getAnnotationByMediaId(fixture.mediaId))
            assertArrayEquals(fixture.originalBytes, fixture.source.readBytes())
            coVerify(exactly = 0) { fixture.mediaStore.savePhotoToMediaStore(any(), any(), any(), any(), any(), any(), any()) }
        } finally {
            fixture.close()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun continuousTextMovesPublishAnIntermediateFrameWhileTheFirstRenderIsSlow() = runBlocking {
        Dispatchers.setMain(Dispatchers.Unconfined)
        val context = RuntimeEnvironment.getApplication() as Context
        val fixture = Fixture(context, temp.newFolder("slow_text_preview"))
        try {
            fixture.installTextRecipe()
            val target = fixture.model.textTargets(400f, 300f).single()
            val firstRender = fixture.provider.gateNextRead(fixture.sourceUri)
            fixture.model.previewText(target, target.note.copy(position = Offset(100f, 130f)), 400f, 300f)
            assertTrue("first render entered the controlled image read", firstRender.entered.await(15, TimeUnit.SECONDS))

            // Queue a later Move while the first frame is still rendering. The second gate skips
            // the first render's decode read and blocks the next render's bounds read.
            val laterRender = fixture.provider.gateNextRead(fixture.sourceUri, skippedReads = 1)
            fixture.model.previewText(target, target.note.copy(position = Offset(240f, 130f)), 400f, 300f)
            firstRender.release.countDown()
            assertTrue("later Move reached its render while still being held", laterRender.entered.await(15, TimeUnit.SECONDS))

            val firstFrame = fixture.model.uiState.value.textDragPreview
            assertNotNull("the completed in-flight frame should publish before the later frame", firstFrame)
            assertTrue("the visible bubble is at the first Move's position",
                Color.red(firstFrame!!.getPixel(104, 104)) < 80 && Color.green(firstFrame.getPixel(104, 104)) < 80)
            laterRender.release.countDown()
        } finally {
            fixture.close()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun clearingTextPreviewDuringBlockedRenderPreventsLateWriteback() = runBlocking {
        Dispatchers.setMain(Dispatchers.Unconfined)
        val context = RuntimeEnvironment.getApplication() as Context
        val fixture = Fixture(context, temp.newFolder("clear_slow_text_preview"))
        try {
            fixture.installTextRecipe()
            val target = fixture.model.textTargets(400f, 300f).single()
            val first = fixture.provider.gateNextRead(fixture.sourceUri)
            fixture.model.previewText(target, target.note.copy(position = Offset(120f, 130f)), 400f, 300f)
            assertTrue(first.entered.await(15, TimeUnit.SECONDS))
            fixture.model.clearTextPreview()

            // Skip the in-flight render's decode read; this gate catches the next queued frame,
            // proving the first render has fully completed before asserting its result was ignored.
            val next = fixture.provider.gateNextRead(fixture.sourceUri, skippedReads = 1)
            first.release.countDown()
            fixture.model.previewText(target, target.note.copy(position = Offset(220f, 130f)), 400f, 300f)
            assertTrue(next.entered.await(15, TimeUnit.SECONDS))
            assertNull("the cleared in-flight frame must not repopulate preview", fixture.model.uiState.value.textDragPreview)
            fixture.model.clearTextPreview()
            next.release.countDown()
        } finally {
            fixture.close()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun cancelledViewModelDoesNotPublishLateTextRender() = runBlocking {
        Dispatchers.setMain(Dispatchers.Unconfined)
        val context = RuntimeEnvironment.getApplication() as Context
        val fixture = Fixture(context, temp.newFolder("cancel_slow_text_preview"))
        try {
            fixture.installTextRecipe()
            val target = fixture.model.textTargets(400f, 300f).single()
            val gate = fixture.provider.gateNextRead(fixture.sourceUri)
            fixture.model.previewText(target, target.note.copy(position = Offset(140f, 130f)), 400f, 300f)
            assertTrue(gate.entered.await(15, TimeUnit.SECONDS))
            val scopeJob = fixture.model.viewModelScope.coroutineContext[kotlinx.coroutines.Job]!!
            scopeJob.cancel()
            gate.release.countDown()
            withTimeout(15_000) { scopeJob.join() }
            assertNull(fixture.model.uiState.value.textDragPreview)
        } finally {
            fixture.close()
            Dispatchers.resetMain()
        }
    }

    companion object {
        private const val AUTHORITY = "com.sitecam.annotation.failure.preview.test"
        private val NEXT_MEDIA_ID = AtomicInteger(1_000_000)
    }
}
