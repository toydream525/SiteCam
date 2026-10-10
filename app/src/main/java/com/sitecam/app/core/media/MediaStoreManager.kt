package com.sitecam.app.core.media

import android.content.ContentValues
import android.content.Context
import android.content.IntentSender
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.media.MediaMetadataRetriever
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.sitecam.app.core.watermark.model.WatermarkData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.OutputStream

data class SavedMediaResult(
    val uri: Uri,
    val fileName: String,
    val filePath: String,
    val width: Int,
    val height: Int,
    val duration: Long = 0L,
    val rotation: Int = 0
)

enum class MediaDeleteOutcome { DELETED, ALREADY_MISSING, FAILED }

/**
 * A deletion denied without a consent request cannot be retried, while a
 * consent request must be shown to the user before anything is deleted.
 * [ConsentRequired] therefore stays distinct from [MediaDeleteOutcome.FAILED],
 * whose callers release the local index as a final state.
 */
sealed interface MediaDeletionOutcome {
    data object Deleted : MediaDeletionOutcome
    data object AlreadyMissing : MediaDeletionOutcome
    data class ConsentRequired(val intentSender: IntentSender) : MediaDeletionOutcome
    data object Failed : MediaDeletionOutcome
}

/** Why a provider refused to delete; only a permission refusal is recoverable. */
private enum class RefusalReason { PERMISSION }

class MediaStoreManager(private val context: Context) {

    // The refusal is consumed by the very next query for that URI, so no
    // authorization dialog can be offered for an unrelated later delete.
    private val lastRefusal = java.util.concurrent.ConcurrentHashMap<Uri, RefusalReason>()

    suspend fun savePhotoToMediaStore(
        bitmap: Bitmap,
        fileName: String,
        projectName: String,
        watermarkData: WatermarkData,
        quality: Int = 95,
        orientation: Int = 0,
        saveToSystemGallery: Boolean = false
    ): SavedMediaResult = withContext(Dispatchers.IO) {
        val sanitizedProject = NamingEngine.sanitizeFileName(projectName)
        val relativePath = "${Environment.DIRECTORY_PICTURES}/SiteCam/$sanitizedProject"
        if (resolveMediaSaveTarget(saveToSystemGallery, Build.VERSION.SDK_INT, hasLegacyWritePermission()) == MediaSaveTarget.APP_PRIVATE) {
            return@withContext savePhotoToPrivateExternalFiles(
                bitmap = bitmap,
                fileName = fileName,
                projectName = projectName,
                watermarkData = watermarkData,
                quality = quality,
                orientation = orientation
            )
        }

        var legacyFile: File? = null
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.WIDTH, bitmap.width)
            put(MediaStore.Images.Media.HEIGHT, bitmap.height)
            put(MediaStore.Images.Media.DATE_TAKEN, watermarkData.captureTimestamp)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, relativePath)
                put(MediaStore.Images.Media.IS_PENDING, 1)
            } else {
                @Suppress("DEPRECATION")
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "SiteCam/$sanitizedProject")
                if (!dir.exists()) dir.mkdirs()
                val targetFile = createUniqueMediaFile(dir, fileName)
                legacyFile = targetFile
                put(MediaStore.MediaColumns.DISPLAY_NAME, targetFile.name)
                put(MediaStore.Images.Media.DATA, targetFile.absolutePath)
            }
        }

        val resolver = context.contentResolver
        val imageUri = try {
            resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                ?: throw IllegalStateException("Failed to create MediaStore entry for $fileName")
        } catch (error: Exception) { legacyFile?.delete(); throw error }

        try {
            val outputStream: OutputStream? = resolver.openOutputStream(imageUri)
            outputStream?.use { stream ->
                check(bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)) {
                    "无法压缩照片"
                }
                stream.flush()
            } ?: throw IllegalStateException("Failed to open output stream for $imageUri")

            // Write EXIF
            ExifPreserver.writeExifAttributesToUri(context, imageUri, watermarkData, orientation)

            // Mark pending 0 on Android 10+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                // An update that touches no row leaves the photo invisible in
                // the gallery, so it must fail the save instead of reporting it.
                check(resolver.update(imageUri, contentValues, null, null) > 0) {
                    "照片未能完成发布"
                }
            }

            SavedMediaResult(
                uri = imageUri,
                fileName = resolveDisplayName(imageUri, fileName),
                filePath = relativePath,
                width = bitmap.width,
                height = bitmap.height
            )
        } catch (e: Exception) {
            resolver.delete(imageUri, null, null)
            legacyFile?.delete()
            throw e
        }
    }

    suspend fun saveVideoToMediaStore(
        tempVideoFile: File,
        fileName: String,
        projectName: String,
        timestamp: Long,
        saveToSystemGallery: Boolean = false
    ): SavedMediaResult = withContext(Dispatchers.IO) {
        require(tempVideoFile.isFile && tempVideoFile.length() > 0L) {
            "录像临时文件不存在或为空"
        }
        val metadata = readVideoMetadata(tempVideoFile)
        require(metadata.width > 0 && metadata.height > 0) {
            "无法读取录像真实宽高"
        }
        val sanitizedProject = NamingEngine.sanitizeFileName(projectName)
        val relativePath = "${Environment.DIRECTORY_PICTURES}/SiteCam/$sanitizedProject"
        if (resolveMediaSaveTarget(saveToSystemGallery, Build.VERSION.SDK_INT, hasLegacyWritePermission()) == MediaSaveTarget.APP_PRIVATE) {
            return@withContext saveVideoToPrivateExternalFiles(
                tempVideoFile = tempVideoFile,
                fileName = fileName,
                projectName = projectName,
                metadata = metadata
            )
        }

        var legacyFile: File? = null
        val contentValues = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.DATE_TAKEN, timestamp)
            put(MediaStore.Video.Media.WIDTH, metadata.width)
            put(MediaStore.Video.Media.HEIGHT, metadata.height)
            put(MediaStore.Video.Media.DURATION, metadata.durationMs)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, relativePath)
                put(MediaStore.Video.Media.IS_PENDING, 1)
            } else {
                @Suppress("DEPRECATION")
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "SiteCam/$sanitizedProject")
                if (!dir.exists()) dir.mkdirs()
                val targetFile = createUniqueMediaFile(dir, fileName)
                legacyFile = targetFile
                put(MediaStore.MediaColumns.DISPLAY_NAME, targetFile.name)
                put(MediaStore.Video.Media.DATA, targetFile.absolutePath)
            }
        }

        val resolver = context.contentResolver
        val videoUri = try {
            resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues)
                ?: throw IllegalStateException("Failed to create MediaStore entry for video $fileName")
        } catch (error: Exception) { legacyFile?.delete(); throw error }

        try {
            resolver.openOutputStream(videoUri)?.use { out ->
                FileInputStream(tempVideoFile).use { inStream -> inStream.copyTo(out) }
            } ?: throw IllegalStateException("Failed to open output stream for video $videoUri")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.Video.Media.IS_PENDING, 0)
                // A zero-row update would leave the recording pending and
                // invisible to the system gallery while the DB says READY.
                check(resolver.update(videoUri, contentValues, null, null) > 0) {
                    "录像未能完成发布"
                }
            }

            SavedMediaResult(
                uri = videoUri,
                fileName = resolveDisplayName(videoUri, fileName),
                filePath = relativePath,
                width = metadata.width,
                height = metadata.height,
                duration = metadata.durationMs,
                rotation = metadata.rotation
            )
        } catch (e: Exception) {
            resolver.delete(videoUri, null, null)
            legacyFile?.delete()
            throw e
        }
    }

    private fun readVideoMetadata(file: File): VideoMetadata {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            VideoMetadata(
                width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                    ?.toIntOrNull()?.coerceAtLeast(1) ?: 0,
                height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                    ?.toIntOrNull()?.coerceAtLeast(1) ?: 0,
                durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull()?.coerceAtLeast(0L) ?: 0L,
                rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                    ?.toIntOrNull()?.let { ((it % 360) + 360) % 360 } ?: 0
            )
        } finally {
            retriever.release()
        }
    }

    /** Probe a finished recording before exposing it as a retryable original. */
    fun inspectRecordedVideo(file: File): VideoMetadata {
        require(file.isFile && file.length() > 0) { "录像没有有效画面" }
        val metadata = readVideoMetadata(file)
        require(metadata.width > 0 && metadata.height > 0) { "无法读取录像尺寸" }
        // API 26 has no scaled frame decoder: getFrameAtTime would allocate a
        // full-size bitmap (~33 MB for 4K) just to validate the recording, so
        // the decoded width/height above stay the size check there.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(file.absolutePath)
                val frame = retriever.getScaledFrameAtTime(
                    0L,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                    64,
                    64
                )
                requireNotNull(frame) { "录像没有可读取的画面" }.recycle()
            } finally { retriever.release() }
        }
        return metadata
    }

    suspend fun deleteMediaUriDetailed(uri: Uri): MediaDeleteOutcome = when (val outcome = deleteMediaOutcome(uri)) {
        is MediaDeletionOutcome.AlreadyMissing -> MediaDeleteOutcome.ALREADY_MISSING
        MediaDeletionOutcome.Deleted -> MediaDeleteOutcome.DELETED
        // A denied or unavailable consent request leaves the local index in place.
        else -> MediaDeleteOutcome.FAILED
    }

    /** Missing content is safe to clean from the local index. */
    suspend fun deleteMediaUri(uri: Uri): Boolean =
        deleteMediaUriDetailed(uri) != MediaDeleteOutcome.FAILED

    suspend fun deleteMediaOutcome(uri: Uri): MediaDeletionOutcome = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        try {
            if (resolver.delete(uri, null, null) > 0) return@withContext MediaDeletionOutcome.Deleted
            mediaDeletionFallback(uri)
        } catch (error: SecurityException) {
            // On Android 11+ the provider refuses outright, so the authorization
            // the system is willing to grant replaces the raw failure.
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.Q) {
                lastRefusal[uri] = RefusalReason.PERMISSION
                requestDeleteConsent(listOf(uri))?.let { return@withContext MediaDeletionOutcome.ConsentRequired(it) }
            }
            resolveDeleteConsent(error) ?: mediaDeletionFallback(uri)
        }
    }

    /**
     * Android 11+ rejects deleting another app's media until the user approves a
     * system dialog, so the caller must show the request before retrying.
     */
    suspend fun requestDeleteConsent(uris: List<Uri>): IntentSender? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || uris.isEmpty()) return null
        return runCatching { MediaStore.createDeleteRequest(context.contentResolver, uris).intentSender }
            .getOrNull()
    }

    /**
     * Reports the authorization a refused delete still needs, so a caller that
     * only sees [MediaDeleteOutcome.FAILED] can still ask the user.  Only
     * callers that already know the delete was refused may use this: it is
     * queried after a failed `resolver.delete` on the same URI.
     */
    suspend fun authorizationRequiredFor(uri: Uri): MediaDeletionOutcome? {
        // Only a permission refusal can be resolved by the system dialog; any
        // other refusal reason stays final.
        if (lastRefusal.remove(uri) != RefusalReason.PERMISSION) return null
        return requestDeleteConsent(listOf(uri))?.let { MediaDeletionOutcome.ConsentRequired(it) }
    }

    private fun resolveDeleteConsent(error: SecurityException): MediaDeletionOutcome? {
        // API 26-28 have no recoverable media permission: touching the
        // RecoverableSecurityException class outside the Q guard would fail to
        // load on those devices.
        if (Build.VERSION.SDK_INT != Build.VERSION_CODES.Q) return null
        @Suppress("DEPRECATION")
        val sender = (error as? android.app.RecoverableSecurityException)?.userAction?.actionIntent?.intentSender
        return sender?.let { MediaDeletionOutcome.ConsentRequired(it) }
    }

    private suspend fun mediaDeletionFallback(uri: Uri): MediaDeletionOutcome {
        // FileProvider and vendor MediaStore providers do not all expose
        // the `_id` projection. Probe readability after a zero-count
        // delete so app-private API 26-28 fallback files are handled too.
        val stillReadable = runCatching {
            context.contentResolver.openInputStream(uri)?.use { true } ?: false
        }.getOrDefault(false)
        if (stillReadable) return MediaDeletionOutcome.Failed
        return MediaDeletionOutcome.AlreadyMissing
    }

    private fun resolveDisplayName(uri: Uri, fallback: String): String {
        return context.contentResolver.query(
            uri,
            arrayOf(MediaStore.MediaColumns.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0).orEmpty() else ""
        }?.takeIf { it.isNotBlank() } ?: fallback
    }

    private fun hasLegacyWritePermission(): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

    private fun privateMediaDirectory(projectName: String): File =
        File(
            context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: context.filesDir,
            "SiteCam/${NamingEngine.sanitizeFileName(projectName)}"
        ).apply { mkdirs() }

    private fun savePhotoToPrivateExternalFiles(
        bitmap: Bitmap,
        fileName: String,
        projectName: String,
        watermarkData: WatermarkData,
        quality: Int,
        orientation: Int
    ): SavedMediaResult {
        val file = createUniqueMediaFile(privateMediaDirectory(projectName), fileName)
        try {
            FileOutputStream(file).use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output)) { "无法压缩照片" }
            }
            ExifPreserver.writeExifAttributes(file, watermarkData, orientation)
            return SavedMediaResult(
                uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file),
                fileName = file.name,
                filePath = file.absolutePath,
                width = bitmap.width,
                height = bitmap.height
            )
        } catch (error: Exception) {
            file.delete()
            throw error
        }
    }

    private fun saveVideoToPrivateExternalFiles(
        tempVideoFile: File,
        fileName: String,
        projectName: String,
        metadata: VideoMetadata
    ): SavedMediaResult {
        val file = createUniqueMediaFile(privateMediaDirectory(projectName), fileName)
        try {
            FileInputStream(tempVideoFile).use { input ->
                FileOutputStream(file).use { output -> input.copyTo(output) }
            }
            return SavedMediaResult(
                uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file),
                fileName = file.name,
                filePath = file.absolutePath,
                width = metadata.width,
                height = metadata.height,
                duration = metadata.durationMs,
                rotation = metadata.rotation
            )
        } catch (error: Exception) {
            file.delete()
            throw error
        }
    }
}

data class VideoMetadata(
    val width: Int,
    val height: Int,
    val durationMs: Long,
    val rotation: Int
)
