package com.sitecam.app.core.media

import android.database.sqlite.SQLiteDiskIOException
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.room.Room
import com.sitecam.app.core.database.AppDatabase
import com.sitecam.app.core.database.dao.MediaItemDao
import com.sitecam.app.core.database.entity.MediaItemEntity
import com.sitecam.app.core.database.entity.ProjectEntity
import com.sitecam.app.core.watermark.model.WatermarkData
import com.sitecam.app.core.watermark.model.WatermarkSnapshotCodec
import kotlinx.coroutines.runBlocking
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PendingVideoStoreTest {
    @Test fun failedDatabaseInsertSurvivesReopenAndPublishesOneVisibleRetryTarget() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        // This JVM suite omits packaged Android resources. Keep FileProvider URI generation as
        // a storage boundary; the manifest, Room failure, reopening and file ownership stay real.
        mockkStatic(FileProvider::class)
        every { FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", any()) } answers {
            Uri.Builder().scheme("content").authority("${context.packageName}.fileprovider")
                .appendPath("private_sitecam_media")
                .appendPath(thirdArg<java.io.File>().relativeTo(context.filesDir).path).build()
        }
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        val realDao = database.mediaItemDao()
        var failInsert = true
        val dao = object : MediaItemDao by realDao {
            override suspend fun insertMediaItem(item: MediaItemEntity): Long {
                if (failInsert) throw SQLiteDiskIOException("injected insert failure")
                return realDao.insertMediaItem(item)
            }
        }
        val store = PendingVideoStore(context, dao)
        val project = database.projectDao().insertProject(ProjectEntity(name = "录像恢复回归"))
        val snapshot = WatermarkSnapshotCodec.encode(WatermarkData(projectName = "原工程", captureTimestamp = 1700000000000L,
            captureTimeZoneId = "Asia/Shanghai", saveToSystemGallery = true))
        val record = store.prepare(MediaItemEntity(projectId = project, mediaType = "VIDEO", contentUri = "",
            fileName = "现场录像.mp4", captureTimestamp = 1700000000000L, latitude = 31.25, longitude = 121.5,
            altitude = -4.25, locationStatus = "FRESH", watermarkSnapshotJson = snapshot))
        // Bytes exercise file ownership; codec validity is tested separately by device finalization.
        val bytes = byteArrayOf(1, 7, 9, 12, 24, 36)
        record.file.writeBytes(bytes)
        val metadata = VideoMetadata(1280, 720, 5000, 90)
        try {
            try { store.publish(record, metadata); fail("The injected Room failure must propagate") }
            catch (_: SQLiteDiskIOException) { }
            assertTrue(record.manifest.exists())
            assertArrayEquals(bytes, record.file.readBytes())
            assertTrue(realDao.getMediaForAvailabilitySync().isEmpty())

            failInsert = false
            val reopened = PendingVideoStore(context, dao)
            val pending = reopened.pending().single { it.file == record.file }
            val published = reopened.publish(pending, metadata)
            assertEquals(project, published.projectId)
            assertEquals("FAILED_VIDEO_WATERMARK_RETRY", published.processingStatus)
            assertEquals(snapshot, published.watermarkSnapshotJson)
            assertEquals(-4.25, published.altitude!!, 0.0)
            assertEquals(1280, published.width)
            assertEquals(5000L, published.duration)
            assertFalse(published.isUnavailable)
            assertFalse(record.manifest.exists())
            assertArrayEquals(bytes, record.file.readBytes())
            assertEquals(published.id, reopened.publish(pending, metadata).id)
            assertEquals(1, realDao.getMediaForAvailabilitySync().size)
        } finally {
            store.discard(record)
            database.close()
            unmockkStatic(FileProvider::class)
        }
    }
}
