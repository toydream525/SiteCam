package com.sitecam.app.core.media

import android.content.IntentSender
import android.net.Uri
import com.sitecam.app.core.database.AppDatabase
import com.sitecam.app.core.database.entity.MediaItemEntity
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * A system delete request for media this app does not own.  It is returned
 * before anything is removed from the index, so the same item ids can be
 * deleted again once the user approves the request.
 */
data class PendingDeleteConsent(val intentSender: IntentSender, val itemIds: List<Long>)

data class MediaDeletionResult(
    val success: Boolean,
    val mediaWasMissing: Boolean = false,
    val message: String? = null,
    val pendingConsent: PendingDeleteConsent? = null
)

data class ProjectDeletionResult(
    val projectDeleted: Boolean,
    val deletedMediaCount: Int = 0,
    val failures: List<String> = emptyList(),
    val pendingConsent: PendingDeleteConsent? = null
)

/**
 * Best-effort cross-store deletion that never leaves Room pointing at a file
 * already removed from MediaStore.  Every physical delete happens before its
 * index row, so a delete the system has not authorised yet changes no state.
 */
class MediaDeletionCoordinator(
    private val database: AppDatabase,
    private val deleteMedia: suspend (Uri) -> MediaDeletionOutcome
) {
    /** The application path resolves each refusal into the consent it needs. */
    constructor(database: AppDatabase, mediaStoreManager: MediaStoreManager) :
        this(database, mediaStoreManager::deleteMediaOutcome)

    suspend fun delete(item: MediaItemEntity): MediaDeletionResult = MediaOperationCoordinator.withExclusive { deleteExclusive(item) }

    /** The same lock covers membership, every media deletion and the final project commit. */
    suspend fun deleteProject(
        projectId: Long,
        onProgress: (MediaItemEntity) -> Unit = {},
        onDeleted: suspend () -> Unit = {}
    ): ProjectDeletionResult = MediaOperationCoordinator.withExclusive {
        val project = database.projectDao().getProjectById(projectId)
            ?: return@withExclusive ProjectDeletionResult(false)
        val items = database.mediaItemDao().getAllMediaItemsForProject(projectId)
        var removed = 0
        val failures = mutableListOf<String>()
        for (item in items) {
            // Cancellation stops before the next item, never halfway through one item's compensation.
            currentCoroutineContext().ensureActive()
            onProgress(item)
            val result = deleteExclusive(item)
            if (result.pendingConsent != null) {
                // No further item was touched, so the project and its index stay consistent.
                return@withExclusive ProjectDeletionResult(false, removed, failures, result.pendingConsent)
            }
            if (result.success) removed++ else failures += result.message ?: item.fileName
        }
        currentCoroutineContext().ensureActive()
        if (failures.isNotEmpty()) return@withExclusive ProjectDeletionResult(false, removed, failures)
        withContext(NonCancellable) {
            database.projectDao().deleteProject(project)
            onDeleted()
        }
        ProjectDeletionResult(true, removed)
    }

    private suspend fun deletePhysical(uri: String): MediaDeletionOutcome = try {
        deleteMedia(Uri.parse(uri))
    } catch (_: Exception) {
        MediaDeletionOutcome.Failed
    }

    private suspend fun deleteExclusive(requestedItem: MediaItemEntity): MediaDeletionResult {
        val issueDao = database.issueDao()
        val mediaDao = database.mediaItemDao()
        val item = mediaDao.getMediaItemById(requestedItem.id) ?: return MediaDeletionResult(true, mediaWasMissing = true)
        if (item.projectId != requestedItem.projectId) {
            return MediaDeletionResult(false, message = "资料已移入其他工程，请刷新后重试")
        }
        val annotation = issueDao.getAnnotationByMediaId(item.id)
        currentCoroutineContext().ensureActive()
        // Once the first index is removed, navigation cancellation must not interrupt
        // the physical deletes or the compensating database writes.
        return withContext(NonCancellable) {
            // Every physical delete happens before its index row, so a delete that
            // still needs the user's approval leaves the whole index untouched.
            if (annotation != null) {
                when (val annotationDelete = deletePhysical(annotation.annotatedContentUri)) {
                    is MediaDeletionOutcome.ConsentRequired -> return@withContext MediaDeletionResult(
                        false,
                        pendingConsent = PendingDeleteConsent(annotationDelete.intentSender, listOf(item.id))
                    )
                    MediaDeletionOutcome.Failed -> return@withContext MediaDeletionResult(false, message = "标注成品删除失败，已保留媒体与索引")
                    else -> Unit
                }
                try {
                    issueDao.deleteAnnotation(annotation)
                } catch (error: Exception) {
                    // The annotation product is already gone, so its pointer must not
                    // be restored; the media row is untouched and stays consistent.
                    return@withContext MediaDeletionResult(false, message = "标注索引删除失败: ${error.message ?: "未知错误"}")
                }
            }

            val mediaDelete = deletePhysical(item.contentUri)
            if (mediaDelete is MediaDeletionOutcome.ConsentRequired) {
                // The media row is still present, so the removed annotation pointer
                // refers to a media row that is not going away yet.
                return@withContext MediaDeletionResult(
                    false,
                    message = if (restoreAnnotationPointer(annotation)) null else "设备媒体需要授权，标注索引恢复失败",
                    pendingConsent = PendingDeleteConsent(mediaDelete.intentSender, listOf(item.id))
                )
            }
            if (mediaDelete == MediaDeletionOutcome.Failed) {
                return@withContext MediaDeletionResult(
                    false,
                    message = if (restoreAnnotationPointer(annotation)) "设备媒体删除失败，已恢复工程索引" else "设备媒体删除失败，索引恢复失败，请立即检查"
                )
            }

            try {
                // The media row is the only remaining pointer; its issues and
                // annotations cascade with the row.
                mediaDao.deleteMediaItem(item)
            } catch (error: Exception) {
                // The physical product is already gone, so no pointer may be restored.
                return@withContext MediaDeletionResult(false, message = "媒体索引删除失败: ${error.message ?: "未知错误"}")
            }

            MediaDeletionResult(
                success = true,
                mediaWasMissing = mediaDelete == MediaDeletionOutcome.AlreadyMissing
            )
        }
    }

    /**
     * Only valid while the media row still exists: the annotation product was
     * deleted before this point, so the restored pointer must never survive a
     * successful media deletion.
     */
    private suspend fun restoreAnnotationPointer(annotation: com.sitecam.app.core.database.entity.AnnotationEntity?): Boolean {
        if (annotation == null) return true
        return runCatching { database.issueDao().insertAnnotation(annotation) }.isSuccess
    }
}
