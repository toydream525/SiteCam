package com.sitecam.app.feature.annotation

import android.content.ContentProvider
import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.ParcelFileDescriptor
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
import com.sitecam.app.feature.annotation.model.AnnotationTool
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowContentResolver
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(ExperimentalCoroutinesApi::class)
class AnnotationLoadGuardIntegrationTest {
    @get:Rule val temp = TemporaryFolder()
    private class Provider : ContentProvider() {
        val files = mutableMapOf<String, File>()
        val reads = AtomicInteger()
        var oomAtRead = Int.MAX_VALUE
        override fun onCreate() = true
        override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
            if (reads.incrementAndGet() == oomAtRead) throw OutOfMemoryError("injected bounds-read OOM")
            return ParcelFileDescriptor.open(files.getValue(uri.toString()), ParcelFileDescriptor.MODE_READ_ONLY)
        }
        override fun getType(uri: Uri) = "image/jpeg"
        override fun insert(uri: Uri, values: ContentValues?) = null
        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
        override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?) = null
    }

    private suspend fun scenario(malformed: Boolean, missingBase: Boolean = false) {
        Dispatchers.setMain(Dispatchers.Unconfined)
        val context = RuntimeEnvironment.getApplication()
        val directory = temp.newFolder()
        val provider = Provider()
        provider.attachInfo(context, android.content.pm.ProviderInfo().apply { authority = "annotation-load-guard"; exported = true; grantUriPermissions = true })
        ShadowContentResolver.registerProviderInternal("annotation-load-guard", provider)
        val source = File(directory, "source.jpg")
        val product = File(directory, "product.jpg")
        fun image(file: File, color: Int) {
            val bitmap = Bitmap.createBitmap(320, 240, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(color)
            file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it)) }; bitmap.recycle()
        }
        image(source, Color.BLUE); image(product, Color.RED)
        val sourceUri = Uri.parse("content://annotation-load-guard/source")
        val productUri = Uri.parse("content://annotation-load-guard/product")
        provider.files[sourceUri.toString()] = source; provider.files[productUri.toString()] = product
        val sourceBytes = source.readBytes(); val productBytes = product.readBytes()
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        val store = mockk<MediaStoreManager>(relaxed = true)
        val container = mockk<AppContainer>()
        every { container.appContext } returns context
        every { container.database } returns database
        every { container.settingsDataStore } returns AppSettingsDataStore(context)
        every { container.mediaStoreManager } returns store
        var model: PhotoAnnotationViewModel? = null
        try {
            val projectId = database.projectDao().insertProject(ProjectEntity(name = "加载边界"))
            val mediaId = database.mediaItemDao().insertMediaItem(MediaItemEntity(projectId = projectId,
                contentUri = sourceUri.toString(), fileName = source.name, width = 320, height = 240, captureTimestamp = 1L))
            val hasAnnotation = malformed || missingBase
            if (hasAnnotation) database.issueDao().insertAnnotation(AnnotationEntity(mediaId = mediaId,
                annotatedContentUri = productUri.toString(), vectorDataJson = if (malformed) "{broken recipe" else
                    EditRecipe.encode(listOf(EditStep.Draw(listOf(AnnotationElement.TextNote(Offset(70f, 100f), "旧矢量", ComposeColor.Red, 7f)), 320f, 240f)), "content://annotation-load-guard/lost-base")))
            else provider.oomAtRead = 2 // First read checks access; the second is imageDimensions' real provider-backed read.
            val row = database.issueDao().getAnnotationByMediaId(mediaId)
            val item = database.mediaItemDao().getMediaItemById(mediaId)
            val vm = PhotoAnnotationViewModel(container, mediaId).also { model = it }
            val state = withTimeout(15_000) { vm.uiState.first { it.loadError != null && !it.isSaving && (!hasAnnotation || it.previewBitmap != null) } }
            assertNotNull(state.loadError)
            // Subscribe after the asynchronous load finished: failure must remain observable.
            assertEquals(state.loadError, vm.uiState.first().loadError)
            if (hasAnnotation) {
                assertTrue(state.loadError!!.contains(if (malformed) "记录已损坏" else "底图已丢失"))
                val pixel = state.previewBitmap!!.getPixel(160, 120)
                assertTrue(Color.red(pixel) > 200 && Color.blue(pixel) < 50)
            } else {
                assertEquals(2, provider.reads.get())
                assertNull(state.previewBitmap)
            }
            val note = AnnotationElement.TextNote(Offset(70f, 100f), "禁止写入", ComposeColor.Yellow, 7f)
            assertFalse(vm.addElement(note))
            vm.applyTransform("rotate", 320f, 240f)
            vm.selectTool(AnnotationTool.TEXT); vm.selectColor(ComposeColor.Green); vm.selectStrokeWidth(20f)
            vm.openTextEditor(null, note.position, 320f, 240f)
            vm.undo(); vm.redo(); vm.clearAll(); vm.viewportChanged(640f, 480f)
            vm.saveAnnotatedImage(context, 320f, 240f)
            assertEquals(state.elements, vm.uiState.value.elements)
            assertEquals(state.selectedTool, vm.uiState.value.selectedTool)
            assertEquals(state.selectedColor, vm.uiState.value.selectedColor)
            assertEquals(state.selectedStrokeWidth, vm.uiState.value.selectedStrokeWidth)
            assertNull(vm.uiState.value.textEditor)
            assertFalse(vm.uiState.value.canUndo)
            assertEquals(state.loadError, vm.uiState.value.loadError)
            assertEquals(item, database.mediaItemDao().getMediaItemById(mediaId))
            assertEquals(row, database.issueDao().getAnnotationByMediaId(mediaId))
            assertArrayEquals(sourceBytes, source.readBytes()); assertArrayEquals(productBytes, product.readBytes())
            coVerify(exactly = 0) { store.savePhotoToMediaStore(any(), any(), any(), any(), any(), any(), any()) }
            coVerify(exactly = 0) { store.deleteMediaUri(any()) }
        } finally { model?.viewModelScope?.cancel(); database.close(); Dispatchers.resetMain() }
    }
    @Test fun malformedRecipeKeepsReadableProductAndPersistentReadOnlyFailure() = runBlocking { scenario(true) }
    @Test fun missingBaseKeepsReadableProductAndDistinctPersistentFailure() = runBlocking { scenario(false, missingBase = true) }
    @Test fun providerBoundsReadOutOfMemoryBecomesPersistentReadOnlyFailure() = runBlocking { scenario(false) }
}
