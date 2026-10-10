package com.sitecam.app.feature.annotation

import android.content.ContentProvider
import android.content.ContentValues
import android.database.MatrixCursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import com.sitecam.app.core.database.AppDatabase
import com.sitecam.app.core.database.entity.MediaItemEntity
import com.sitecam.app.core.database.entity.ProjectEntity
import com.sitecam.app.core.di.AppContainer
import com.sitecam.app.core.media.MediaStoreManager
import com.sitecam.app.core.media.ExifPreserver
import com.sitecam.app.core.media.PhotoQualityProfile
import com.sitecam.app.core.preferences.AppSettingsDataStore
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowContentResolver
import java.io.File

/** Exercises the actual ViewModel decoder, renderer, save pipeline and persisted recipe. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(ExperimentalCoroutinesApi::class)
class PhotoAnnotationViewModelIntegrationTest {
    class ImageProvider(private val directory: File) : ContentProvider() {
        val files = mutableMapOf<String, File>()
        var failNextRead = false
        private var next = 0
        override fun onCreate() = true
        fun add(file: File): Uri = Uri.parse("content://media/annotation-test/${++next}").also { files[it.toString()] = file }
        override fun insert(uri: Uri, values: ContentValues?): Uri = add(File(directory, "product_${next + 1}.jpg"))
        override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
            if (mode == "r" && failNextRead) { failNextRead = false; throw java.io.FileNotFoundException("暂时无法读取图片") }
            return ParcelFileDescriptor.open(files.getValue(uri.toString()), ParcelFileDescriptor.parseMode(mode))
        }
        override fun getType(uri: Uri) = "image/jpeg"
        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 1
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = if (files.remove(uri.toString())?.delete() == true) 1 else 0
        override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?) =
            MatrixCursor(projection ?: arrayOf(MediaStore.MediaColumns.DISPLAY_NAME)).apply {
                val file = files[uri.toString()] ?: return@apply
                addRow(columnNames.map { if (it == MediaStore.MediaColumns.DISPLAY_NAME) file.name else null }.toTypedArray())
            }
    }

    @Test fun opensRealContentUriTransformsSavesAndReopensWithoutChangingOriginal() = runBlocking {
        Dispatchers.setMain(Dispatchers.Unconfined)
        val context = RuntimeEnvironment.getApplication()
        val directory = File(context.cacheDir, "annotation_vm_pipeline").apply { mkdirs() }
        val provider = ImageProvider(directory)
        provider.attachInfo(context, android.content.pm.ProviderInfo().apply { authority = "media"; exported = true; grantUriPermissions = true })
        ShadowContentResolver.registerProviderInternal("media", provider)
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        val settings = AppSettingsDataStore(context)
        val container = mockk<AppContainer>()
        every { container.appContext } returns context
        every { container.database } returns database
        every { container.settingsDataStore } returns settings
        every { container.mediaStoreManager } returns MediaStoreManager(context)
        val models = mutableListOf<PhotoAnnotationViewModel>()
        // Robolectric's Linux shadow does not implement Os.dup used by ExifInterface(FileDescriptor).
        // Route that descriptor boundary to the same provider-backed file, preserving the real EXIF writer.
        mockkObject(ExifPreserver)
        every { ExifPreserver.writeExifAttributesToUri(context, any(), any(), any()) } answers {
            ExifPreserver.writeExifAttributes(provider.files.getValue(secondArg<Uri>().toString()), thirdArg(), arg(3))
        }
        try {
            settings.setPhotoQualityProfile(PhotoQualityProfile.SMALL)
            settings.setSaveToSystemGallery(true)
            val source = File(directory, "original.jpg")
            val bitmap = Bitmap.createBitmap(2400, 1600, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.BLUE)
            Canvas(bitmap).drawRect(0f, 0f, 1200f, 800f, Paint().apply { color = Color.RED })
            source.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it)) }
            bitmap.recycle()
            val originalBytes = source.readBytes()
            val originalUri = provider.add(source)
            val projectId = database.projectDao().insertProject(ProjectEntity(name = "编辑流程回归"))
            val mediaId = database.mediaItemDao().insertMediaItem(MediaItemEntity(
                projectId = projectId, contentUri = originalUri.toString(), fileName = "original.jpg",
                filePath = source.absolutePath, width = 2400, height = 1600, captureTimestamp = 1700000000000L,
                altitude = -4.25,
                watermarkSnapshotJson = com.sitecam.app.core.watermark.model.WatermarkSnapshotCodec.encode(
                    com.sitecam.app.core.watermark.model.WatermarkData(captureTimestamp = 1700000000000L, captureTimeZoneId = "Asia/Shanghai"))))
            val model = PhotoAnnotationViewModel(container, mediaId).also(models::add)
            // This fails on the regression: bounds-only decode returns null and preview never loads.
            val opened = withTimeout(15_000) { model.uiState.first { !it.isSaving && it.previewBitmap != null } }
            assertTrue(opened.imageWidth > opened.imageHeight)
            assertRed(opened.previewBitmap!!, .25f, .25f)
            model.viewportChanged(600f, 400f)
            val arrow = com.sitecam.app.feature.annotation.model.AnnotationElement.Arrow(
                androidx.compose.ui.geometry.Offset(200f, 200f), androidx.compose.ui.geometry.Offset(300f, 200f),
                androidx.compose.ui.graphics.Color.White, 10f)
            model.addElement(arrow)
            val beforeFailure = model.uiState.value
            val previewFailed = async(start = CoroutineStart.UNDISPATCHED) { model.saveFailed.first() }
            provider.failNextRead = true
            model.applyTransform("rotate", 600f, 400f)
            withTimeout(15_000) { previewFailed.await() }
            val unchanged = withTimeout(15_000) { model.uiState.first { !it.isSaving } }
            assertSame(beforeFailure.previewBitmap, unchanged.previewBitmap)
            assertEquals(listOf(arrow), unchanged.elements)
            assertEquals(beforeFailure.canUndo, unchanged.canUndo)
            assertEquals(beforeFailure.canRedo, unchanged.canRedo)
            // Undo must remove the original arrow, not an invisible failed rotation.
            model.undo()
            val undone = withTimeout(15_000) { model.uiState.first { !it.isSaving } }
            assertTrue(undone.elements.isEmpty())
            assertTrue(undone.imageWidth > undone.imageHeight)
            model.applyTransform("horizontal", 600f, 400f)
            val flipped = withTimeout(15_000) { model.uiState.first { !it.isSaving } }
            assertRed(flipped.previewBitmap!!, .75f, .25f)
            model.applyTransform("rotate", 600f, 400f)
            val rotated = withTimeout(15_000) { model.uiState.first { !it.isSaving } }
            assertTrue(rotated.imageHeight > rotated.imageWidth)
            assertRed(rotated.previewBitmap!!, .75f, .75f)

            val saved = async(start = CoroutineStart.UNDISPATCHED) { model.saveCompleted.first() }
            val failed = async(start = CoroutineStart.UNDISPATCHED) { model.saveFailed.first() }
            model.saveAnnotatedImage(context, 600f, 400f)
            val result = withTimeout(15_000) {
                kotlinx.coroutines.selects.select<AnnotationSaveResult> {
                    saved.onAwait { it }
                    failed.onAwait { error("Actual ViewModel save failed: $it") }
                }
            }
            failed.cancel()
            assertNotEquals(originalUri, result.uri)
            val product = context.contentResolver.openInputStream(result.uri)!!.use { BitmapFactory.decodeStream(it) }!!
            assertEquals(853, product.width)
            assertEquals(1280, product.height)
            assertRed(product, .75f, .75f)
            product.recycle()
            val exif = androidx.exifinterface.media.ExifInterface(provider.files.getValue(result.uri.toString()))
            assertEquals(-4.25, exif.getAltitude(Double.NaN), 0.02)
            assertEquals("2023:11:15 06:13:20", exif.getAttribute(androidx.exifinterface.media.ExifInterface.TAG_DATETIME_ORIGINAL))
            val annotation = database.issueDao().getAnnotationByMediaId(mediaId)!!
            assertEquals(result.uri.toString(), annotation.annotatedContentUri)
            assertEquals(2, EditRecipe.decode(annotation.vectorDataJson).size)
            assertEquals(originalUri.toString(), database.mediaItemDao().getMediaItemById(mediaId)!!.contentUri)
            assertArrayEquals(originalBytes, source.readBytes())

            val reopened = PhotoAnnotationViewModel(container, mediaId).also(models::add)
            val restored = withTimeout(15_000) { reopened.uiState.first { !it.isSaving && it.previewBitmap != null } }
            assertTrue(restored.imageHeight > restored.imageWidth)
            assertRed(restored.previewBitmap!!, .75f, .75f)
        } finally {
            models.forEach { it.viewModelScope.cancel() }
            database.close()
            directory.deleteRecursively()
            unmockkObject(ExifPreserver)
            Dispatchers.resetMain()
        }
    }

    @Test fun onePassMosaicProducesTheSameOpaqueBlocksOverBlackAndWhiteSourcePixels() = runBlocking {
        Dispatchers.setMain(Dispatchers.Unconfined)
        val context = RuntimeEnvironment.getApplication()
        val directory = File(context.cacheDir, "annotation_opaque_mask").apply { mkdirs() }
        val provider = ImageProvider(directory)
        provider.attachInfo(context, android.content.pm.ProviderInfo().apply { authority = "media"; exported = true; grantUriPermissions = true })
        ShadowContentResolver.registerProviderInternal("media", provider)
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        val settings = AppSettingsDataStore(context)
        val container = mockk<AppContainer>()
        every { container.appContext } returns context
        every { container.database } returns database
        every { container.settingsDataStore } returns settings
        every { container.mediaStoreManager } returns MediaStoreManager(context)
        var model: PhotoAnnotationViewModel? = null
        try {
            settings.setPhotoQualityProfile(PhotoQualityProfile.ORIGINAL)
            settings.setSaveToSystemGallery(true)
            val source = File(directory, "black_white.png")
            val bitmap = Bitmap.createBitmap(240, 160, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.BLACK)
            Canvas(bitmap).drawRect(120f, 0f, 240f, 160f, Paint().apply { color = Color.WHITE })
            source.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            bitmap.recycle()
            val uri = provider.add(source)
            val project = database.projectDao().insertProject(ProjectEntity(name = "遮盖结果回归"))
            val media = database.mediaItemDao().insertMediaItem(MediaItemEntity(projectId = project,
                contentUri = uri.toString(), fileName = source.name, width = 240, height = 160, captureTimestamp = 1700000000000L))
            val editor = PhotoAnnotationViewModel(container, media).also { model = it }
            withTimeout(15_000) { editor.uiState.first { !it.isSaving && it.previewBitmap != null } }
            editor.viewportChanged(240f, 160f)
            editor.addElement(com.sitecam.app.feature.annotation.model.AnnotationElement.Mosaic(
                listOf(androidx.compose.ui.geometry.Offset(120f, 80f)), strokeWidth = 36f))
            val saved = async(start = CoroutineStart.UNDISPATCHED) { editor.saveCompleted.first() }
            editor.saveAnnotatedImage(context, 240f, 160f)
            val result = withTimeout(15_000) { saved.await() }
            val product = context.contentResolver.openInputStream(result.uri)!!.use { BitmapFactory.decodeStream(it) }!!
            // Matching-parity block interiors straddle the black/white boundary.
            for ((left, right, expected) in listOf(Triple(102, 126, 24), Triple(114, 138, 72))) {
                val onBlack = Color.red(product.getPixel(left, 78))
                val onWhite = Color.red(product.getPixel(right, 78))
                assertEquals(expected.toDouble(), onBlack.toDouble(), 3.0)
                assertEquals(expected.toDouble(), onWhite.toDouble(), 3.0)
                assertEquals(onBlack.toDouble(), onWhite.toDouble(), 3.0)
            }
            product.recycle()
        } finally {
            model?.viewModelScope?.cancel()
            database.close()
            directory.deleteRecursively()
            Dispatchers.resetMain()
        }
    }

    private fun assertRed(bitmap: Bitmap, x: Float, y: Float) {
        val pixel = bitmap.getPixel((bitmap.width * x).toInt(), (bitmap.height * y).toInt())
        assertTrue("Expected red source region to survive preview/export transforms: $pixel", Color.red(pixel) > 200 && Color.green(pixel) < 40 && Color.blue(pixel) < 40)
    }
}
