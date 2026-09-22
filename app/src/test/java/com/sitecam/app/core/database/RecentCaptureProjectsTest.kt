package com.sitecam.app.core.database

import androidx.room.Room
import com.sitecam.app.core.database.entity.MediaItemEntity
import com.sitecam.app.core.database.entity.ProjectEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RecentCaptureProjectsTest {
    @Test fun sortsByLatestPhotoOrVideoIgnoringProjectEditsAndExcludingEmptyAndArchived() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
        try {
            val dao = db.projectDao()
            val old = dao.insertProject(ProjectEntity(name = "Recently edited", updatedAt = 9000))
            val newest = dao.insertProject(ProjectEntity(name = "Latest capture", updatedAt = 1, isCaptureLocked = true))
            val archived = dao.insertProject(ProjectEntity(name = "Archived", isArchived = true))
            dao.insertProject(ProjectEntity(name = "Empty", updatedAt = 10000))
            suspend fun capture(project: Long, time: Long, type: String = "PHOTO") {
                db.mediaItemDao().insertMediaItem(MediaItemEntity(projectId = project,
                    contentUri = "content://test/$time", fileName = "$time", captureTimestamp = time, mediaType = type))
            }
            capture(old, 100)
            capture(newest, 50)
            capture(newest, 200, "VIDEO")
            capture(archived, 300)
            assertEquals(listOf(newest, old), dao.getProjectsByLatestCapture().map { it.id })
            capture(old, 400)
            assertEquals(listOf(old, newest), dao.getProjectsByLatestCapture().map { it.id })
        } finally {
            db.close()
        }
    }
}
