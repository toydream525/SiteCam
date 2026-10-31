package com.sitecam.app.core.export

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import com.sitecam.app.core.media.MediaOperationCoordinator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class ExportArchive(val uri: Uri, val name: String, val size: Long, val date: Long) {
    val id: String get() = uri.toString()
}
data class ArchiveListing(val files: List<ExportArchive>, val unreadableDirectories: Int = 0)
data class ArchiveDeletion(val deleted: Set<String>, val failed: List<String>)

class ExportArchiveRepository(context: Context) {
    private val context = context.applicationContext
    private val preferences = this.context.getSharedPreferences("export_archive_folders", Context.MODE_PRIVATE)

    fun rememberDirectory(uri: Uri) {
        val directories = preferences.getStringSet("directories", emptySet()).orEmpty().toMutableSet()
        directories.add(uri.toString())
        preferences.edit().putStringSet("directories", directories).apply()
    }

    suspend fun list(): ArchiveListing = MediaOperationCoordinator.withExclusive {
        withContext(Dispatchers.IO) { read() }
    }

    suspend fun delete(ids: Set<String>): ArchiveDeletion = MediaOperationCoordinator.withExclusive {
        withContext(Dispatchers.IO) {
            val current = read().files.associateBy { it.id }
            val deleted = mutableSetOf<String>()
            val failed = mutableListOf<String>()
            for (id in ids) {
                val file = current[id]
                if (file == null) { failed.add(id); continue }
                try {
                    val removed = when {
                        file.uri.scheme == "file" -> File(requireNotNull(file.uri.path)).delete()
                        DocumentsContract.isDocumentUri(context, file.uri) -> DocumentsContract.deleteDocument(context.contentResolver, file.uri)
                        else -> context.contentResolver.delete(file.uri, null, null) > 0
                    }
                    if (removed) deleted.add(id) else failed.add(file.name)
                } catch (_: Exception) { failed.add(file.name) }
            }
            ArchiveDeletion(deleted, failed)
        }
    }

    private fun read(): ArchiveListing {
        val files = linkedMapOf<String, ExportArchive>()
        var unreadable = 0
        fun add(file: ExportArchive) { if (file.name.endsWith(".zip", true)) files[file.id] = file }
        if (Build.VERSION.SDK_INT >= 29) {
            val columns = arrayOf(MediaStore.Downloads._ID, MediaStore.Downloads.DISPLAY_NAME,
                MediaStore.Downloads.SIZE, MediaStore.Downloads.DATE_MODIFIED)
            val path = "${Environment.DIRECTORY_DOWNLOADS}/SiteCam/Exports"
            try {
                context.contentResolver.query(MediaStore.Downloads.EXTERNAL_CONTENT_URI, columns,
                    "${MediaStore.Downloads.RELATIVE_PATH} IN (?, ?) AND ${MediaStore.Downloads.IS_PENDING} = 0 AND ${MediaStore.MediaColumns.OWNER_PACKAGE_NAME} = ?",
                    arrayOf(path, "$path/", context.packageName), null)?.use { cursor ->
                    while (cursor.moveToNext()) add(ExportArchive(
                        ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cursor.getLong(0)),
                        cursor.getString(1).orEmpty(), cursor.getLong(2), cursor.getLong(3) * 1000))
                }
            } catch (_: Exception) { unreadable++ }
        }
        val roots = mutableListOf(File(context.filesDir, "SiteCam/Exports"), File(context.cacheDir, "exports"))
        context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)?.let { roots.add(File(it, "SiteCam/Exports")) }
        if (Build.VERSION.SDK_INT < 29 && ContextCompat.checkSelfPermission(context,
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            @Suppress("DEPRECATION")
            val publicRoot = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            roots.add(File(publicRoot, "SiteCam/Exports"))
        }
        for (root in roots.distinctBy { it.absolutePath }) {
            if (!root.exists()) continue
            try {
                val parent = root.canonicalFile
                val children = root.listFiles() ?: error("无法读取目录")
                children.filter { it.isFile && it.canonicalFile.parentFile == parent }.forEach {
                    add(ExportArchive(Uri.fromFile(it.canonicalFile), it.name, it.length(), it.lastModified()))
                }
            } catch (_: Exception) { unreadable++ }
        }
        for (saved in preferences.getStringSet("directories", emptySet()).orEmpty()) {
            try {
                val root = Uri.parse(saved)
                val children = DocumentsContract.buildChildDocumentsUriUsingTree(root, DocumentsContract.getTreeDocumentId(root))
                val columns = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.COLUMN_SIZE, DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                val cursor = context.contentResolver.query(children, columns, android.os.Bundle.EMPTY, null) ?: error("目录不可用")
                cursor.use {
                    while (it.moveToNext()) if (it.getString(2) != DocumentsContract.Document.MIME_TYPE_DIR) {
                        add(ExportArchive(DocumentsContract.buildDocumentUriUsingTree(root, it.getString(0)),
                            it.getString(1).orEmpty(), it.getLong(3), it.getLong(4)))
                    }
                }
            } catch (_: Exception) { unreadable++ }
        }
        return ArchiveListing(files.values.sortedWith(compareByDescending<ExportArchive> { it.date }.thenBy { it.name }), unreadable)
    }
}
