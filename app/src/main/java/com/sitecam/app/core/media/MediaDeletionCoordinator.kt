package com.sitecam.app.core.media

import com.sitecam.app.core.database.AppDatabase
import com.sitecam.app.core.database.entity.MediaItemEntity

data class MediaDeletionResult(
    val success: Boolean,
    val mediaWasMissing: Boolean = false,
    val message: String? = null
)

/**
 * An annotation pointer can only be restored while its physical product is
 * still available.  Once deletion succeeded (or the product was already
 * missing), restoring the Room row would create an index to a dead URI.
 */
internal fun canRestoreAnnotationPointerSafely(
    annotationWasRemoved: Boolean,
    physicalDeleteOutcome: MediaDeleteOutcome?
): Boolean = annotationWasRemoved && physicalDeleteOutcome == null

/**
 * Best-effort cross-store deletion that never leaves Room pointing at a file
 * already removed from MediaStore.  Room rows are removed before their
 * physical files; if a physical delete fails the media row is restored.
 */
class MediaDeletionCoordinator(
    private val database: AppDatabase,
    private val mediaStoreManager: MediaStoreManager
) {
    suspend fun delete(item: MediaItemEntity): MediaDeletionResult = MediaOperationCoordinator.withExclusive { deleteExclusive(item) }

    private suspend fun deleteExclusive(requestedItem: MediaItemEntity): MediaDeletionResult {
        val issueDao = database.issueDao()
        val mediaDao = database.mediaItemDao()
        val item = mediaDao.getMediaItemById(requestedItem.id) ?: return MediaDeletionResult(true, mediaWasMissing = true)
        val annotation = issueDao.getAnnotationByMediaId(item.id)
        val issue = issueDao.getIssueByMediaId(item.id)
        var annotationRemoved = false
        var annotationDeleteOutcome: MediaDeleteOutcome? = null

        if (annotation != null) {
            try {
                // Remove the Room pointer before touching the annotation file.
                issueDao.deleteAnnotation(annotation)
                annotationRemoved = true
            } catch (error: Exception) {
                return MediaDeletionResult(false, message = "标注索引删除失败: ${error.message ?: "未知错误"}")
            }

            annotationDeleteOutcome = mediaStoreManager.deleteMediaUriDetailed(
                android.net.Uri.parse(annotation.annotatedContentUri)
            )
            if (annotationDeleteOutcome == MediaDeleteOutcome.FAILED) {
                runCatching { issueDao.insertAnnotation(annotation) }
                return MediaDeletionResult(false, message = "标注成品删除失败，已保留媒体与索引")
            }
        }

        try {
            // Remove the media pointer before deleting the physical row.
            mediaDao.deleteMediaItem(item)
        } catch (error: Exception) {
            // If the annotation product was already deleted (or was already
            // missing), restoring its pointer would create an orphan index.
            // The media row remains, so keep that row consistent and report
            // that the annotation index was intentionally not restored.
            if (canRestoreAnnotationPointerSafely(annotationRemoved, annotationDeleteOutcome) && annotation != null) {
                runCatching { issueDao.insertAnnotation(annotation) }
            }
            val message = if (annotation != null && annotationDeleteOutcome != null) {
                "媒体索引删除失败，媒体仍保留；标注成品已处理，标注索引未恢复"
            } else {
                "媒体索引删除失败: ${error.message ?: "未知错误"}"
            }
            return MediaDeletionResult(false, message = message)
        }

        val mediaDelete = mediaStoreManager.deleteMediaUriDetailed(
            android.net.Uri.parse(item.contentUri)
        )
        if (mediaDelete == MediaDeleteOutcome.FAILED) {
            // Physical media is still present, so restoring the media index is
            // valid.  Recreate the issue index too because media FK cascade may
            // have removed it.
            val restored = runCatching { mediaDao.insertMediaItem(item) }.isSuccess
            if (restored && issue != null) runCatching { issueDao.insertIssue(issue) }
            return MediaDeletionResult(
                false,
                message = if (restored) "设备媒体删除失败，已恢复工程索引" else "设备媒体删除失败，索引恢复失败，请立即检查"
            )
        }

        return MediaDeletionResult(
            success = true,
            mediaWasMissing = mediaDelete == MediaDeleteOutcome.ALREADY_MISSING
        )
    }
}
