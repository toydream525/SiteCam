package com.sitecam.app.feature.gallery

import androidx.lifecycle.viewModelScope
import com.sitecam.app.core.database.AppDatabase
import com.sitecam.app.core.database.dao.IssueDao
import com.sitecam.app.core.database.dao.MediaItemDao
import com.sitecam.app.core.database.entity.IssueEntity
import com.sitecam.app.core.database.entity.MediaItemEntity
import com.sitecam.app.core.di.AppContainer
import com.sitecam.app.core.media.MediaDeletionCoordinator
import com.sitecam.app.core.media.MediaDeletionResult
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PhotoDetailDeletionTest {
    @Test fun repeatedDeleteTapsPerformOneDeletionAndEmitOneCloseEvent() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val item = MediaItemEntity(id = 7, projectId = 3, mediaType = "PHOTO", contentUri = "content://test/7", fileName = "7.jpg", captureTimestamp = 1L)
        val media = MutableStateFlow<MediaItemEntity?>(item)
        val container = mockk<AppContainer>()
        val database = mockk<AppDatabase>()
        val mediaDao = mockk<MediaItemDao>()
        val issueDao = mockk<IssueDao>()
        val deletion = mockk<MediaDeletionCoordinator>()
        every { container.database } returns database
        every { container.mediaDeletionCoordinator } returns deletion
        every { database.mediaItemDao() } returns mediaDao
        every { database.issueDao() } returns issueDao
        every { mediaDao.getMediaItemByIdFlow(7) } returns media
        every { issueDao.getIssueByMediaIdFlow(7) } returns MutableStateFlow<IssueEntity?>(null)
        coEvery { issueDao.getAnnotationByMediaId(7) } returns null
        coEvery { deletion.delete(item) } coAnswers {
            media.value = null
            delay(100)
            MediaDeletionResult(success = true)
        }
        val model = PhotoDetailViewModel(container, 7)
        val events = mutableListOf<PhotoDetailEvent>()
        val subscription = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.mediaItem.collect {} }
        val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.events.collect { events += it } }
        try {
            runCurrent()
            model.delete()
            model.delete()
            assertTrue(model.isDeleting.value)
            advanceUntilIdle()
            coVerify(exactly = 1) { deletion.delete(item) }
            assertEquals(listOf(PhotoDetailEvent.Deleted), events)
            assertFalse(model.isDeleting.value)
        } finally {
            subscription.cancelAndJoin()
            collector.cancelAndJoin()
            model.viewModelScope.coroutineContext[Job]!!.cancelAndJoin()
            runCurrent()
            Dispatchers.resetMain()
        }
    }
}
