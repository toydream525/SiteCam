package com.sitecam.app.core.media

import android.content.Context
import android.database.sqlite.SQLiteException
import android.util.AtomicFile
import androidx.core.content.FileProvider
import com.sitecam.app.core.database.dao.MediaItemDao
import com.sitecam.app.core.database.entity.MediaItemEntity
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

/** The manifest exists only until Room takes ownership of the original recording. */
class PendingVideoStore(private val context: Context, private val mediaDao: MediaItemDao) {
    data class Record(val file: File, val manifest: File, val media: MediaItemEntity, val quickIssueMode: Boolean = false)

    private val directory get() = File(context.filesDir, "SiteCam/pending-video")

    fun prepare(template: MediaItemEntity, quickIssueMode: Boolean = false): Record {
        val folder = File(directory, "${template.captureTimestamp}_${System.nanoTime()}")
        check(folder.mkdirs()) { "无法准备录像保存位置" }
        val file = File(folder, "recording.mp4")
        val media = template.copy(
            contentUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file).toString(),
            filePath = file.absolutePath,
            processingStatus = "FAILED_VIDEO_WATERMARK_RETRY"
        )
        val manifest = File(folder, "capture.json")
        val atomic = AtomicFile(manifest)
        var stream: java.io.FileOutputStream? = null
        try {
            stream = atomic.startWrite()
            stream.write(encode(media, quickIssueMode).toByteArray(Charsets.UTF_8))
            atomic.finishWrite(stream)
        } catch (error: Exception) {
            atomic.failWrite(stream)
            folder.delete()
            throw error
        }
        return Record(file, manifest, media, quickIssueMode)
    }

    /** Only visits this feature's pending manifests; never scans unrelated media. */
    fun pending(): List<Record> = directory.listFiles().orEmpty().mapNotNull { folder ->
        val manifest = File(folder, "capture.json")
        runCatching {
            val json = AtomicFile(manifest).openRead().use { it.readBytes().toString(Charsets.UTF_8) }
            val file = File(folder, "recording.mp4")
            Record(file, manifest, decode(json).copy(
                contentUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file).toString(),
                filePath = file.absolutePath
            ), JSONObject(json).optBoolean("quickIssueMode"))
        }.getOrNull()
    }

    /**
     * A failed insert leaves the durable manifest for the next camera resume.
     *
     * A deleted project can never satisfy the `projectId` foreign key again, so
     * that case drops the record instead of retrying on every camera resume.
     * [projectExists] is optional: when supplied it is checked before the
     * insert, otherwise the foreign-key failure of the insert is treated as the
     * same unrecoverable condition.
     */
    suspend fun publish(
        record: Record,
        metadata: VideoMetadata,
        projectExists: (suspend (Long) -> Boolean)? = null
    ): MediaItemEntity = withContext(NonCancellable) {
        val addressed = record.media.copy(
            width = metadata.width, height = metadata.height, duration = metadata.durationMs,
            orientation = metadata.rotation
        )
        if (projectExists != null && !projectExists(addressed.projectId)) {
            discard(record)
            return@withContext addressed
        }
        val existing = mediaDao.getMediaItemByContentUri(record.media.contentUri)
        val result = existing ?: try {
            addressed.copy(id = mediaDao.insertMediaItem(addressed))
        } catch (error: SQLiteException) {
            if (!isMissingProjectFailure(error)) throw error
            discard(record)
            return@withContext addressed
        }
        // Remove the replay record before allowing the row's URI to change.
        // If this cannot finish, the existing original remains the retry target.
        AtomicFile(record.manifest).delete()
        check(!record.manifest.exists()) { "录像已保留，保存状态暂未更新" }
        result
    }

    /** SQLite reports code 787 (SQLITE_CONSTRAINT_FOREIGNKEY) when the owning project row is gone. */
    private fun isMissingProjectFailure(error: SQLiteException): Boolean {
        val message = error.message.orEmpty()
        return message.contains("FOREIGN KEY", ignoreCase = true) || message.contains("787")
    }

    fun discard(record: Record) {
        record.file.delete()
        AtomicFile(record.manifest).delete()
        record.file.parentFile?.delete()
    }

    private fun encode(item: MediaItemEntity, quickIssueMode: Boolean) = JSONObject().apply {
        put("quickIssueMode", quickIssueMode)
        put("projectId", item.projectId)
        put("fileName", item.fileName)
        put("captureTimestamp", item.captureTimestamp)
        put("latitude", item.latitude ?: JSONObject.NULL)
        put("longitude", item.longitude ?: JSONObject.NULL)
        put("altitude", item.altitude ?: JSONObject.NULL)
        put("locationAccuracy", item.locationAccuracy ?: JSONObject.NULL)
        put("locationTimestamp", item.locationTimestamp ?: JSONObject.NULL)
        put("locationStatus", item.locationStatus)
        put("addressText", item.addressText)
        put("watermarkSnapshotJson", item.watermarkSnapshotJson)
        put("createdAt", item.createdAt)
    }.toString()

    private fun decode(json: String): MediaItemEntity {
        val item = JSONObject(json)
        fun number(key: String) = if (item.isNull(key)) null else item.getDouble(key)
        return MediaItemEntity(
            projectId = item.getLong("projectId"), mediaType = "VIDEO", contentUri = "",
            fileName = item.getString("fileName"), captureTimestamp = item.getLong("captureTimestamp"),
            latitude = number("latitude"), longitude = number("longitude"), altitude = number("altitude"),
            locationAccuracy = number("locationAccuracy")?.toFloat(),
            locationTimestamp = if (item.isNull("locationTimestamp")) null else item.getLong("locationTimestamp"),
            locationStatus = item.getString("locationStatus"), addressText = item.getString("addressText"),
            watermarkSnapshotJson = item.getString("watermarkSnapshotJson"),
            createdAt = item.getLong("createdAt"), processingStatus = "FAILED_VIDEO_WATERMARK_RETRY"
        )
    }
}
