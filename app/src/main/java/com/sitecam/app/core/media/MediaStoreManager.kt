package com.sitecam.app.core.media

import android.content.ContentValues
import android.content.Context
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

class MediaStoreManager(private val context: Context) {

    suspend fun savePhotoToMediaStore(
        bitmap: Bitmap,
        fileName: String,
        projectName: String,
        watermarkData: WatermarkData,
        quality: Int = 95,
        orientation: Int = 0
    ): SavedMediaResult = withContext(Dispatchers.IO) {
        val sanitizedProject = NamingEngine.sanitizeFileName(projectName)
        val relativePath = "${Environment.DIRECTORY_PICTURES}/SiteCam/$sanitizedProject"
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q && !hasLegacyWritePermission()) {
            return@withContext savePhotoToPrivateExternalFiles(
                bitmap = bitmap,
                fileName = fileName,
                projectName = projectName,
                watermarkData = watermarkData,
                quality = quality,
                orientation = orientation
            )
        }

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
                val targetFile = File(dir, fileName)
                put(MediaStore.Images.Media.DATA, targetFile.absolutePath)
            }
        }

        val resolver = context.contentResolver
        val imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            ?: throw IllegalStateException("Failed to create MediaStore entry for $fileName")

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
                resolver.update(imageUri, contentValues, null, null)
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
            throw e
        }
    }

    suspend fun saveVideoToMediaStore(
        tempVideoFile: File,
        fileName: String,
        projectName: String,
        timestamp: Long
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
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q && !hasLegacyWritePermission()) {
            return@withContext saveVideoToPrivateExternalFiles(
                tempVideoFile = tempVideoFile,
                fileName = fileName,
                projectName = projectName,
                metadata = metadata
            )
        }

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
                val targetFile = File(dir, fileName)
                put(MediaStore.Video.Media.DATA, targetFile.absolutePath)
            }
        }

        val resolver = context.contentResolver
        val videoUri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues)
            ?: throw IllegalStateException("Failed to create MediaStore entry for video $fileName")

        try {
            resolver.openOutputStream(videoUri)?.use { out ->
                FileInputStream(tempVideoFile).use { inStream -> inStream.copyTo(out) }
            } ?: throw IllegalStateException("Failed to open output stream for video $videoUri")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.Video.Media.IS_PENDING, 0)
                resolver.update(videoUri, contentValues, null, null)
            }

            tempVideoFile.delete()

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

    suspend fun deleteMediaUriDetailed(uri: Uri): MediaDeleteOutcome = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        try {
            if (resolver.delete(uri, null, null) > 0) return@withContext MediaDeleteOutcome.DELETED
            // FileProvider and vendor MediaStore providers do not all expose
            // the `_id` projection. Probe readability after a zero-count
            // delete so app-private API 26-28 fallback files are handled too.
            val stillReadable = runCatching {
                resolver.openInputStream(uri)?.use { true } ?: false
            }.getOrDefault(false)
            if (stillReadable) MediaDeleteOutcome.FAILED else MediaDeleteOutcome.ALREADY_MISSING
        } catch (_: Exception) {
            MediaDeleteOutcome.FAILED
        }
    }

    /** Missing content is safe to clean from the local index. */
    suspend fun deleteMediaUri(uri: Uri): Boolean =
        deleteMediaUriDetailed(uri) != MediaDeleteOutcome.FAILED

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
        val file = File(privateMediaDirectory(projectName), fileName)
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
        val file = File(privateMediaDirectory(projectName), fileName)
        try {
            FileInputStream(tempVideoFile).use { input ->
                FileOutputStream(file).use { output -> input.copyTo(output) }
            }
            tempVideoFile.delete()
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

private data class VideoMetadata(
    val width: Int,
    val height: Int,
    val durationMs: Long,
    val rotation: Int
)
