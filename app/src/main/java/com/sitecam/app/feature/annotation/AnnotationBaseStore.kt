package com.sitecam.app.feature.annotation

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Copies a flattened legacy annotation into app-private storage before it becomes
 * the base of a vector recipe.  A system-gallery product is user-visible and can
 * be deleted or moved, so it must never be the only copy needed to re-edit text.
 */
object AnnotationBaseStore {
    private const val DIRECTORY = "SiteCam/annotation_bases"

    private fun directory(context: Context): File =
        File(context.filesDir, DIRECTORY).apply { mkdirs() }

    private fun fileFor(context: Context, mediaId: Long): File =
        File(directory(context), "media_$mediaId.base")

    fun uriFor(context: Context, mediaId: Long): Uri = Uri.fromFile(fileFor(context, mediaId))

    /** A base copied by an earlier save attempt, even if that attempt failed. */
    fun existing(context: Context, mediaId: Long): Uri? = runCatching {
        val file = fileFor(context, mediaId)
        if (file.isFile && file.length() > 0L) uriFor(context, mediaId) else null
    }.getOrNull()

    fun isStoredBase(context: Context, mediaId: Long, uri: Uri): Boolean =
        uri.scheme == "file" && uri.path == fileFor(context, mediaId).absolutePath

    /** Returns null when the source cannot be copied or exposed safely. */
    suspend fun store(context: Context, mediaId: Long, source: Uri): Uri? = withContext(Dispatchers.IO) {
        val target = fileFor(context, mediaId)
        if (target.isFile && target.length() > 0L) return@withContext uriFor(context, mediaId)
        val temp = File.createTempFile("base_", ".tmp", directory(context))
        try {
            context.contentResolver.openInputStream(source)?.use { input ->
                temp.outputStream().use { output -> input.copyTo(output) }
            } ?: return@withContext null
            if (temp.length() <= 0L) return@withContext null
            target.delete()
            if (!temp.renameTo(target)) return@withContext null
            uriFor(context, mediaId)
        } catch (_: Exception) {
            null
        } finally {
            if (temp.exists()) temp.delete()
        }
    }
}
