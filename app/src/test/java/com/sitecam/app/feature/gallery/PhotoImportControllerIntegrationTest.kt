package com.sitecam.app.feature.gallery

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import androidx.room.Room
import com.sitecam.app.core.database.AppDatabase
import com.sitecam.app.core.database.entity.ProjectEntity
import com.sitecam.app.core.di.AppContainer
import com.sitecam.app.core.media.MediaStoreManager
import com.sitecam.app.core.media.PhotoQualityProfile
import com.sitecam.app.core.media.SavedMediaResult
import com.sitecam.app.core.preferences.AppSettingsDataStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PhotoImportControllerIntegrationTest {
    @get:Rule val temp = TemporaryFolder()

    private class Fixture(val context: Context, val directory: File, hasOriginalExifTime: Boolean = true) : AutoCloseable {
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        val settings = AppSettingsDataStore(context)
        val mediaStore = mockk<MediaStoreManager>()
        val container = mockk<AppContainer>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val messages = mutableListOf<String>()
        val controller = PhotoImportController(container, scope) { messages += it }
        val projectId: Long
        val source = File(directory, "selected-source.jpg")
        val sourceUri: Uri
        val originalBytes: ByteArray

        init {
            every { container.appContext } returns context
            every { container.database } returns database
            every { container.settingsDataStore } returns settings
            every { container.mediaStoreManager } returns mediaStore
            val bitmap = Bitmap.createBitmap(160, 120, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.rgb(20, 80, 140))
            source.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it)) }
            bitmap.recycle()
            ExifInterface(source).apply {
                if (hasOriginalExifTime) setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, "2026:01:02 03:04:05")
                setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
                saveAttributes()
            }
            originalBytes = source.readBytes()
            sourceUri = Uri.fromFile(source)
            projectId = runBlocking {
                AppDatabase.ensureDefaultData(database)
                val template = database.watermarkDao().getDefaultTemplate()!!
                val inserted = database.projectDao().insertProject(ProjectEntity(name = "导入回归工程", watermarkTemplateId = template.id))
                settings.setPhotoQualityProfile(PhotoQualityProfile.SMALL)
                settings.setSaveToSystemGallery(false)
                inserted
            }
        }

        fun startAndAwaitPreview() {
            controller.begin(sourceUri, projectId)
            runBlocking { withTimeout(15_000) { controller.state.first { it != null && !it.loading && it.preview != null } } }
        }

        fun assertSourceUnchanged() {
            assertTrue(source.exists())
            assertTrue(originalBytes.contentEquals(source.readBytes()))
        }

        override fun close() {
            controller.dismiss()
            scope.cancel()
            database.close()
            directory.deleteRecursively()
        }
    }

    @Test
    fun dismissingImportPreviewDoesNotInsertMediaOrChangeSelectedPhoto() = runBlocking {
        val context = RuntimeEnvironment.getApplication() as Context
        val fixture = Fixture(context, temp.newFolder("import_cancel"))
        try {
            val templateBefore = fixture.database.watermarkDao().getTemplateById(1L)
            fixture.startAndAwaitPreview()
            fixture.controller.dismiss()

            assertNull(fixture.controller.state.value)
            assertTrue(fixture.database.mediaItemDao().getAllMediaItems().first().isEmpty())
            fixture.assertSourceUnchanged()
            assertEquals(templateBefore, fixture.database.watermarkDao().getTemplateById(1L))
            coVerify(exactly = 0) { fixture.mediaStore.savePhotoToMediaStore(any(), any(), any(), any(), any(), any(), any()) }
        } finally {
            fixture.close()
        }
    }

    @Test
    fun duplicateSaveTapsCommitOneNewPhotoAndLeaveSelectedPhotoUntouched() = runBlocking {
        val context = RuntimeEnvironment.getApplication() as Context
        val fixture = Fixture(context, temp.newFolder("import_repeat"))
        val savingStarted = CompletableDeferred<Unit>()
        val allowSaveToFinish = CompletableDeferred<SavedMediaResult>()
        val outputUri = Uri.parse("content://import-test/saved-photo")
        coEvery {
            fixture.mediaStore.savePhotoToMediaStore(any(), any(), any(), any(), any(), any(), any())
        } coAnswers {
            savingStarted.complete(Unit)
            allowSaveToFinish.await()
        }
        try {
            fixture.settings.setSelectedProjectId(9876L)
            val templateBefore = fixture.database.watermarkDao().getTemplateById(1L)
            fixture.startAndAwaitPreview()
            fixture.controller.update { it.copy(addressText = "手动填写位置") }
            fixture.controller.timeChanged("2026-02-03 04:05:06")
            withTimeout(15_000) { fixture.controller.state.first { it != null && !it.loading && it.preview != null && it.timeText == "2026-02-03 04:05:06" } }
            val expectedTimestamp = fixture.controller.state.value!!.data.captureTimestamp
            fixture.controller.save()
            withTimeout(15_000) { savingStarted.await() }
            assertTrue(fixture.controller.state.value?.saving == true)
            fixture.controller.save()
            allowSaveToFinish.complete(SavedMediaResult(outputUri, "imported.jpg", "private/imported.jpg", 160, 120))

            withTimeout(15_000) { fixture.controller.state.first { it == null } }
            val saved = fixture.database.mediaItemDao().getAllMediaItems().first()
            assertEquals(1, saved.size)
            assertEquals(fixture.projectId, saved.single().projectId)
            assertEquals(outputUri.toString(), saved.single().contentUri)
            assertNotEquals(fixture.sourceUri.toString(), saved.single().contentUri)
            assertEquals(expectedTimestamp, saved.single().captureTimestamp)
            assertEquals("手动填写位置", saved.single().addressText)
            assertEquals("UNAVAILABLE", saved.single().locationStatus)
            fixture.assertSourceUnchanged()
            assertEquals(9876L, fixture.settings.selectedProjectId.first())
            assertEquals(templateBefore, fixture.database.watermarkDao().getTemplateById(1L))
            coVerify(exactly = 1) { fixture.mediaStore.savePhotoToMediaStore(any(), any(), any(), any(), any(), any(), any()) }
        } finally {
            allowSaveToFinish.cancel()
            fixture.close()
        }
    }

    @Test
    fun mediaStoreFailureKeepsDraftAndDoesNotCreateMediaRow() = runBlocking {
        val context = RuntimeEnvironment.getApplication() as Context
        val fixture = Fixture(context, temp.newFolder("import_failure"))
        coEvery {
            fixture.mediaStore.savePhotoToMediaStore(any(), any(), any(), any(), any(), any(), any())
        } throws IllegalStateException("import save rejected")
        try {
            fixture.startAndAwaitPreview()
            fixture.controller.save()

            withTimeout(15_000) { fixture.controller.state.first { it != null && !it.saving && !it.loading } }
            assertTrue(fixture.database.mediaItemDao().getAllMediaItems().first().isEmpty())
            assertTrue(fixture.messages.any { it.contains("import save rejected") })
            fixture.assertSourceUnchanged()
            coVerify(exactly = 1) { fixture.mediaStore.savePhotoToMediaStore(any(), any(), any(), any(), any(), any(), any()) }
        } finally {
            fixture.close()
        }
    }

    @Test
    fun missingExifTimeIsLabeledAsEditableImportTime() = runBlocking {
        val context = RuntimeEnvironment.getApplication() as Context
        val fixture = Fixture(context, temp.newFolder("import_missing_time"), hasOriginalExifTime = false)
        try {
            fixture.startAndAwaitPreview()
            val state = fixture.controller.state.value!!
            assertNull(state.metadata.captureTimestamp)
            assertEquals("导入时间", state.data.fieldLabels[com.sitecam.app.core.watermark.model.BuiltInWatermarkFieldKeys.DATE_TIME])
            assertTrue(state.timeText.isNotBlank())
            fixture.controller.timeChanged("2026-03-04 05:06:07")
            assertEquals("2026-03-04 05:06:07", fixture.controller.state.value!!.timeText)
            assertFalse(fixture.controller.state.value!!.timeError)
            assertTrue(fixture.controller.state.value!!.data.captureTimestamp > 0L)
            fixture.assertSourceUnchanged()
        } finally {
            fixture.close()
        }
    }

    @Test
    fun projectLockedAfterMediaStoreWriteDeletesUncommittedOutput() = runBlocking {
        val context = RuntimeEnvironment.getApplication() as Context
        val fixture = Fixture(context, temp.newFolder("import_lock_during_save"))
        val savingStarted = CompletableDeferred<Unit>()
        val allowSaveToFinish = CompletableDeferred<SavedMediaResult>()
        val outputUri = Uri.parse("content://import-test/locked-output")
        coEvery {
            fixture.mediaStore.savePhotoToMediaStore(any(), any(), any(), any(), any(), any(), any())
        } coAnswers {
            savingStarted.complete(Unit)
            allowSaveToFinish.await()
        }
        coEvery { fixture.mediaStore.deleteMediaUri(outputUri) } returns true
        try {
            fixture.startAndAwaitPreview()
            fixture.controller.save()
            withTimeout(15_000) { savingStarted.await() }
            val project = fixture.database.projectDao().getProjectById(fixture.projectId)!!
            fixture.database.projectDao().updateProject(project.copy(isCaptureLocked = true))
            allowSaveToFinish.complete(SavedMediaResult(outputUri, "locked.jpg", "private/locked.jpg", 160, 120))

            withTimeout(15_000) { fixture.controller.state.first { it != null && !it.saving && !it.loading } }
            assertTrue(fixture.database.mediaItemDao().getAllMediaItems().first().isEmpty())
            assertTrue(fixture.messages.any { it.contains("锁定") })
            fixture.assertSourceUnchanged()
            coVerify(exactly = 1) { fixture.mediaStore.deleteMediaUri(outputUri) }
        } finally {
            allowSaveToFinish.cancel()
            fixture.close()
        }
    }

    @Test
    fun lockedAndArchivedProjectsAreRejectedBeforePhotoDecodeOrSave() = runBlocking {
        val context = RuntimeEnvironment.getApplication() as Context
        val fixture = Fixture(context, temp.newFolder("import_restricted"))
        try {
            for ((locked, archived) in listOf(true to false, false to true)) {
                val original = fixture.database.projectDao().getProjectById(fixture.projectId)!!
                fixture.database.projectDao().updateProject(original.copy(isCaptureLocked = locked, isArchived = archived))
                val oldMessageCount = fixture.messages.size
                fixture.controller.begin(fixture.sourceUri, fixture.projectId)
                withTimeout(15_000) { while (fixture.messages.size <= oldMessageCount) delay(5) }
                assertNull(fixture.controller.state.value)
                assertTrue(fixture.database.mediaItemDao().getAllMediaItems().first().isEmpty())
            }
            assertFalse(fixture.messages.isEmpty())
            fixture.assertSourceUnchanged()
            coVerify(exactly = 0) { fixture.mediaStore.savePhotoToMediaStore(any(), any(), any(), any(), any(), any(), any()) }
        } finally {
            fixture.close()
        }
    }
}
