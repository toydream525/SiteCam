package com.sitecam.app.feature.annotation

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import com.sitecam.app.core.database.AppDatabase
import com.sitecam.app.core.database.entity.AnnotationEntity
import com.sitecam.app.core.database.entity.MediaItemEntity
import com.sitecam.app.core.database.entity.ProjectEntity
import com.sitecam.app.core.di.AppContainer
import com.sitecam.app.core.media.ExifPreserver
import com.sitecam.app.core.media.MediaDeletionCoordinator
import com.sitecam.app.core.media.MediaStoreManager
import com.sitecam.app.core.preferences.AppSettingsDataStore
import com.sitecam.app.feature.annotation.model.AnnotationElement
import io.mockk.*
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(ExperimentalCoroutinesApi::class)
class TextEditingViewModelIntegrationTest {
    @Test fun textEditSurvivesKeyboardResizeUndoRedoTransformAndSaveReopen() = runBlocking {
        Dispatchers.setMain(Dispatchers.Unconfined)
        val context = RuntimeEnvironment.getApplication()
        val directory = File(context.cacheDir, "text-edit-pipeline").apply { mkdirs() }
        val provider = PhotoAnnotationViewModelIntegrationTest.ImageProvider(directory)
        provider.attachInfo(context, android.content.pm.ProviderInfo().apply { authority = "media"; exported = true; grantUriPermissions = true })
        ShadowContentResolver.registerProviderInternal("media", provider)
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        val settings = AppSettingsDataStore(context)
        val container = mockk<AppContainer>()
        every { container.appContext } returns context
        every { container.database } returns database
        every { container.settingsDataStore } returns settings
        every { container.mediaStoreManager } returns MediaStoreManager(context)
        mockkObject(ExifPreserver)
        every { ExifPreserver.writeExifAttributesToUri(context, any(), any(), any()) } answers {
            ExifPreserver.writeExifAttributes(provider.files.getValue(secondArg<Uri>().toString()), thirdArg(), arg(3))
        }
        val models = mutableListOf<PhotoAnnotationViewModel>()
        suspend fun ready(model: PhotoAnnotationViewModel) = withTimeout(15_000) { model.uiState.first { !it.isSaving && it.previewBitmap != null } }
        try {
            settings.setSaveToSystemGallery(true)
            val source = File(directory, "original.jpg")
            val bitmap = Bitmap.createBitmap(600, 400, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(android.graphics.Color.BLUE)
            source.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }; bitmap.recycle()
            val original = source.readBytes()
            val project = database.projectDao().insertProject(ProjectEntity(name = "文字编辑"))
            val id = database.mediaItemDao().insertMediaItem(MediaItemEntity(projectId = project, contentUri = provider.add(source).toString(),
                fileName = "original.jpg", width = 600, height = 400, captureTimestamp = 1700000000000L))
            val legacyBase = File(directory, "legacy-cropped-product.jpg")
            val legacyBitmap = Bitmap.createBitmap(300, 200, Bitmap.Config.ARGB_8888)
            legacyBitmap.eraseColor(android.graphics.Color.GREEN)
            legacyBase.outputStream().use { legacyBitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }; legacyBitmap.recycle()
            val legacyUri = provider.add(legacyBase)
            database.issueDao().replaceAnnotation(AnnotationEntity(mediaId = id, annotatedContentUri = legacyUri.toString(), vectorDataJson = ""))
            val model = PhotoAnnotationViewModel(container, id).also(models::add)
            ready(model)
            assertEquals(300, model.uiState.value.imageWidth)
            assertEquals(200, model.uiState.value.imageHeight)
            model.viewportChanged(600f, 400f)
            val text = AnnotationElement.TextNote(Offset(180f, 180f), "完整原文0123456789", Color.Yellow, 10f)
            model.addElement(text)
            val dialogTarget = model.textTargets(600f, 400f).single()
            // A continuous drag is represented by a latest-wins preview worker,
            // not by cancelling a delayed job before it can render.
            model.previewText(dialogTarget, dialogTarget.moved(Offset(8f, 6f)), 600f, 400f)
            withTimeout(15_000) { model.uiState.first { it.textDragPreview != null } }
            model.clearTextPreview()
            assertNull(model.uiState.value.textDragPreview)
            // New text confirmation must commit the text and close the dialog.
            model.openTextEditor(null, Offset(240f, 300f), 600f, 400f)
            model.textDraftChanged("新增文字")
            model.confirmTextEditor()
            assertNull(model.uiState.value.textEditor)
            assertEquals(2, model.textTargets(600f, 400f).size)
            model.undo(); ready(model)
            assertEquals(1, model.textTargets(600f, 400f).size)
            // The keyboard resizes the viewport while the edit dialog retains its original target.
            model.openTextEditor(dialogTarget, text.position, 600f, 400f)
            model.textDraftChanged("取消的改动")
            model.dismissTextEditor()
            assertEquals(text.text, model.textTargets(600f, 400f).single().note.text)
            model.openTextEditor(dialogTarget, text.position, 600f, 400f)
            model.textDraftChanged("修改后的完整原文")
            model.viewportChanged(400f, 240f); ready(model)
            assertEquals("修改后的完整原文", model.uiState.value.textEditor!!.text)
            model.confirmTextEditor(); ready(model)
            assertNull(model.uiState.value.textEditor)
            assertEquals("修改后的完整原文", model.textTargets(400f, 240f).single().note.text)
            model.undo(); ready(model)
            assertEquals(text.text, model.textTargets(400f, 240f).single().note.text)
            model.redo(); ready(model)
            assertEquals("修改后的完整原文", model.textTargets(400f, 240f).single().note.text)
            model.applyTransform("rotate", 400f, 240f); ready(model)
            val target = model.textTargets(400f, 240f).single()
            model.updateText(target, target.moved(Offset(12f, 8f))); ready(model)
            val expected = model.textTargets(400f, 240f).single().note
            val completed = async(start = CoroutineStart.UNDISPATCHED) { model.saveCompleted.first() }
            val failed = async(start = CoroutineStart.UNDISPATCHED) { model.saveFailed.first() }
            model.saveAnnotatedImage(context, 400f, 240f)
            withTimeout(15_000) { kotlinx.coroutines.selects.select<Unit> {
                completed.onAwait { }; failed.onAwait { error(it) }
            } }; failed.cancel()
            val reopened = PhotoAnnotationViewModel(container, id).also(models::add)
            ready(reopened); reopened.viewportChanged(400f, 240f)
            val restored = reopened.textTargets(400f, 240f).single().note
            assertEquals(expected, restored)
            val firstAnnotation = database.issueDao().getAnnotationByMediaId(id)!!
            val baseUri = EditRecipe.baseUri(firstAnnotation.vectorDataJson)!!
            assertNotEquals(legacyUri.toString(), baseUri)
            assertTrue(baseUri.contains("annotation_bases"))
            assertTrue(context.contentResolver.openInputStream(Uri.parse(baseUri))!!.use { it.read() > 0 })
            // The copied base replaces the user-visible legacy product.
            assertFalse(legacyBase.exists())
            assertArrayEquals(original, source.readBytes())
            val nextTarget = reopened.textTargets(400f, 240f).single()
            reopened.updateText(nextTarget, nextTarget.note.copy(text = "连续保存")); ready(reopened)
            val savedAgain = async(start = CoroutineStart.UNDISPATCHED) { reopened.saveCompleted.first() }
            reopened.saveAnnotatedImage(context, 400f, 240f)
            withTimeout(15_000) { savedAgain.await() }
            val secondBase = EditRecipe.baseUri(database.issueDao().getAnnotationByMediaId(id)!!.vectorDataJson)!!
            assertEquals(baseUri, secondBase)
            assertTrue(context.contentResolver.openInputStream(Uri.parse(secondBase))!!.use { it.read() > 0 })
            assertFalse(legacyBase.exists())
            val deleted = MediaDeletionCoordinator(database, MediaStoreManager(context)).delete(database.mediaItemDao().getMediaItemById(id)!!)
            assertTrue(deleted.success)
            assertNull(database.mediaItemDao().getMediaItemById(id))
            assertFalse(runCatching { context.contentResolver.openInputStream(Uri.parse(secondBase))?.use { true } ?: false }.getOrDefault(false))
        } finally {
            models.forEach { it.viewModelScope.cancel() }
            unmockkObject(ExifPreserver); database.close(); directory.deleteRecursively(); Dispatchers.resetMain()
        }
    }
}
