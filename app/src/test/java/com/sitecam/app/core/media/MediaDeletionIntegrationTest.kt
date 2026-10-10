package com.sitecam.app.core.media

import android.net.Uri
import androidx.room.Room
import com.sitecam.app.core.database.AppDatabase
import com.sitecam.app.core.database.entity.AnnotationEntity
import com.sitecam.app.core.database.entity.IssueEntity
import com.sitecam.app.core.database.entity.MediaItemEntity
import com.sitecam.app.core.database.entity.ProjectEntity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MediaDeletionIntegrationTest {
    private class Fixture {
        val context = RuntimeEnvironment.getApplication()
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        val directory = File(context.cacheDir, "delete-test-${UUID.randomUUID()}").apply { mkdirs() }
        private var sequence = 0
        suspend fun project(name: String) = database.projectDao().insertProject(ProjectEntity(name = name))
        suspend fun media(project: Long, unavailable: Boolean = false): MediaItemEntity {
            val file = File(directory, "photo-${++sequence}.jpg").apply { writeText("original-$sequence") }
            val item = MediaItemEntity(projectId = project, contentUri = Uri.fromFile(file).toString(),
                fileName = file.name, captureTimestamp = sequence.toLong(), isUnavailable = unavailable)
            return item.copy(id = database.mediaItemDao().insertMediaItem(item))
        }
        suspend fun annotate(item: MediaItemEntity): AnnotationEntity {
            val file = File(directory, "annotation-${item.id}.jpg").apply { writeText("annotation") }
            val value = AnnotationEntity(mediaId = item.id, annotatedContentUri = Uri.fromFile(file).toString(), vectorDataJson = "[]")
            database.issueDao().insertAnnotation(value)
            return database.issueDao().getAnnotationByMediaId(item.id)!!
        }
        suspend fun issue(item: MediaItemEntity) {
            database.issueDao().saveIssueAndMarkMedia(IssueEntity(projectId = item.projectId, mediaId = item.id, title = "现场问题"))
        }
        suspend fun deletePhysical(uri: Uri): MediaDeletionOutcome = withContext(Dispatchers.IO) {
            val file = File(requireNotNull(uri.path))
            if (!file.exists()) MediaDeletionOutcome.AlreadyMissing
            else if (file.delete()) MediaDeletionOutcome.Deleted else MediaDeletionOutcome.Failed
        }
        fun exists(uri: String) = File(requireNotNull(Uri.parse(uri).path)).exists()
        fun close() { database.close(); directory.deleteRecursively() }
    }

    @Test fun cancellationAfterEitherIndexCommitStillFinishesPhysicalDeletion(): Unit = runBlocking {
        // Separate runs suspend after the annotation commit and after the media commit.
        for (withAnnotation in listOf(true, false)) {
            val fixture = Fixture()
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            var deletion: Job? = null
            try {
                val item = fixture.media(fixture.project("取消删除"))
                fixture.issue(item)
                val annotation = if (withAnnotation) fixture.annotate(item) else null
                val gateUri = annotation?.annotatedContentUri ?: item.contentUri
                val coordinator = MediaDeletionCoordinator(fixture.database) { uri ->
                    if (uri.toString() == gateUri) { entered.complete(Unit); release.await() }
                    fixture.deletePhysical(uri)
                }
                val task = launch(Dispatchers.IO) { coordinator.delete(item) }
                deletion = task
                withTimeout(10_000) { entered.await() }
                // Every physical delete now precedes its index write, so a
                // cancellation here still has the whole index untouched.
                assertNotNull(fixture.database.mediaItemDao().getMediaItemById(item.id))
                if (withAnnotation) assertNotNull(fixture.database.issueDao().getAnnotationByMediaId(item.id))
                task.cancel()
                release.complete(Unit)
                withTimeout(10_000) { task.join() }
                assertFalse(fixture.exists(item.contentUri))
                if (annotation != null) assertFalse(fixture.exists(annotation.annotatedContentUri))
                assertNull(fixture.database.mediaItemDao().getMediaItemById(item.id))
                assertNull(fixture.database.issueDao().getIssueByMediaId(item.id))
                assertNull(fixture.database.issueDao().getAnnotationByMediaId(item.id))
            } finally {
                release.complete(Unit); deletion?.cancelAndJoin(); fixture.close()
            }
        }
    }

    @Test fun cancellationWhilePhysicalDeleteFailsRestoresMediaAndIssueTogether(): Unit = runBlocking {
        val fixture = Fixture()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var deletion: Job? = null
        try {
            val item = fixture.media(fixture.project("恢复索引"))
            fixture.issue(item)
            val originalMedia = fixture.database.mediaItemDao().getMediaItemById(item.id)
            val originalIssue = fixture.database.issueDao().getIssueByMediaId(item.id)
            val coordinator = MediaDeletionCoordinator(fixture.database) {
                entered.complete(Unit); release.await(); MediaDeletionOutcome.Failed
            }
            val task = launch(Dispatchers.IO) { coordinator.delete(item) }
            deletion = task
            withTimeout(10_000) { entered.await() }
            assertNotNull(fixture.database.mediaItemDao().getMediaItemById(item.id))
            task.cancel()
            release.complete(Unit)
            withTimeout(10_000) { task.join() }
            assertTrue(fixture.exists(item.contentUri))
            assertEquals(originalMedia, fixture.database.mediaItemDao().getMediaItemById(item.id))
            assertEquals(originalIssue, fixture.database.issueDao().getIssueByMediaId(item.id))
        } finally {
            release.complete(Unit); deletion?.cancelAndJoin(); fixture.close()
        }
    }

    @Test fun projectDeletionIncludesHiddenMediaAndTheirIndependentAnnotations(): Unit = runBlocking {
        val fixture = Fixture()
        try {
            val project = fixture.project("系统相册原件已删除")
            val item = fixture.media(project, unavailable = true)
            val annotation = fixture.annotate(item)
            fixture.issue(item)
            fixture.deletePhysical(Uri.parse(item.contentUri))
            assertTrue(fixture.database.mediaItemDao().getMediaItemsByProject(project).first().isEmpty())
            val result = MediaDeletionCoordinator(fixture.database, fixture::deletePhysical).deleteProject(project)
            assertTrue(result.projectDeleted)
            assertEquals(1, result.deletedMediaCount)
            assertFalse(fixture.exists(annotation.annotatedContentUri))
            assertNull(fixture.database.projectDao().getProjectById(project))
            assertNull(fixture.database.mediaItemDao().getMediaItemById(item.id))
            assertNull(fixture.database.issueDao().getAnnotationByMediaId(item.id))
        } finally { fixture.close() }
    }

    @Test fun queuedMovesCannotEnterOrLeaveAProjectDuringItsDeletion(): Unit = runBlocking {
        val fixture = Fixture()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var deletion: Deferred<ProjectDeletionResult>? = null
        val moves = mutableListOf<Deferred<Result<Unit>>>()
        try {
            val a = fixture.project("删除工程 A")
            val b = fixture.project("保留工程 B")
            val first = fixture.media(a)
            val outgoing = fixture.media(a)
            val incoming = fixture.media(b)
            val coordinator = MediaDeletionCoordinator(fixture.database) { uri ->
                if (uri.toString() == first.contentUri) { entered.complete(Unit); release.await() }
                fixture.deletePhysical(uri)
            }
            val task = async(Dispatchers.IO) { coordinator.deleteProject(a) }
            deletion = task
            withTimeout(10_000) { entered.await() }
            moves += async(start = CoroutineStart.UNDISPATCHED) {
                runCatching { MediaOperationCoordinator.moveMedia(fixture.database, mapOf(outgoing.id to a), b) }
            }
            moves += async(start = CoroutineStart.UNDISPATCHED) {
                runCatching { MediaOperationCoordinator.moveMedia(fixture.database, mapOf(incoming.id to b), a) }
            }
            assertTrue(moves.none { it.isCompleted })
            release.complete(Unit)
            assertTrue(withTimeout(10_000) { task.await() }.projectDeleted)
            for (move in moves) assertTrue(withTimeout(10_000) { move.await() }.isFailure)
            assertEquals(b, fixture.database.mediaItemDao().getMediaItemById(incoming.id)!!.projectId)
            assertTrue(fixture.exists(incoming.contentUri))
            assertNull(fixture.database.mediaItemDao().getMediaItemById(outgoing.id))
        } finally {
            release.complete(Unit); deletion?.cancelAndJoin(); moves.forEach { it.cancelAndJoin() }; fixture.close()
        }
    }

    @Test fun moveCommittedBeforeDeletionIsPreservedAndStaleDeleteIsRejected(): Unit = runBlocking {
        val fixture = Fixture()
        try {
            val a = fixture.project("旧工程")
            val b = fixture.project("移入工程")
            val stale = fixture.media(a)
            fixture.issue(stale)
            MediaOperationCoordinator.moveMedia(fixture.database, mapOf(stale.id to a), b)
            val coordinator = MediaDeletionCoordinator(fixture.database, fixture::deletePhysical)
            assertFalse(coordinator.delete(stale).success)
            assertTrue(coordinator.deleteProject(a).projectDeleted)
            assertTrue(fixture.exists(stale.contentUri))
            assertEquals(b, fixture.database.mediaItemDao().getMediaItemById(stale.id)!!.projectId)
            assertEquals(b, fixture.database.issueDao().getIssueByMediaId(stale.id)!!.projectId)
        } finally { fixture.close() }
    }
}
