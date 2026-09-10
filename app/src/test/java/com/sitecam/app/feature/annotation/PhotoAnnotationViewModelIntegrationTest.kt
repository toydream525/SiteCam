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
import com.sitecam.app.core.media.PhotoQualityProfile
import com.sitecam.app.core.preferences.AppSettingsDataStore
import io.mockk.every
import io.mockk.mockk
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
        private var next = 0
        override fun onCreate() = true
        fun add(file: File): Uri = Uri.parse("content://media/annotation-test/${++next}").also { files[it.toString()] = file }
        override fun insert(uri: Uri, values: ContentValues?): Uri = add(File(directory, "product_${next + 1}.jpg"))
        override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor = ParcelFileDescriptor.open(files.getValue(uri.toString()), ParcelFileDescriptor.parseMode(mode))
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
                filePath = source.absolutePath, width = 2400, height = 1600, captureTimestamp = 1700000000000L))
            val model = PhotoAnnotationViewModel(container, mediaId).also(models::add)
            // This fails on the regression: bounds-only decode returns null and preview never loads.
            val opened = withTimeout(15_000) { model.uiState.first { !it.isSaving && it.previewBitmap != null } }
            assertTrue(opened.imageWidth > opened.imageHeight)
            assertRed(opened.previewBitmap!!, .25f, .25f)
            model.viewportChanged(600f, 400f)
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
            Dispatchers.resetMain()
        }
    }

    private fun assertRed(bitmap: Bitmap, x: Float, y: Float) {
        val pixel = bitmap.getPixel((bitmap.width * x).toInt(), (bitmap.height * y).toInt())
        assertTrue("Expected red source region to survive preview/export transforms: $pixel", Color.red(pixel) > 200 && Color.green(pixel) < 40 && Color.blue(pixel) < 40)
    }
}
