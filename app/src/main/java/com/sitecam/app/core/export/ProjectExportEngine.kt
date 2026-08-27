package com.sitecam.app.core.export

import android.content.Context
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.sitecam.app.core.database.AppDatabase
import com.sitecam.app.core.database.entity.IssueEntity
import com.sitecam.app.core.database.entity.MediaItemEntity
import com.sitecam.app.core.media.NamingEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class ExportResult(
    val zipUri: Uri,
    val zipName: String,
    val totalPhotos: Int,
    val totalVideos: Int,
    val totalIssues: Int,
    val copiedFileCount: Int = 0,
    val missingMediaCount: Int = 0,
    val copiedAnnotationCount: Int = 0,
    val missingAnnotationCount: Int = 0
)

data class FolderExportResult(
    val folderUri: Uri,
    val folderName: String,
    val totalPhotos: Int,
    val totalVideos: Int,
    val totalIssues: Int,
    val copiedFileCount: Int,
    val missingMediaCount: Int,
    val copiedAnnotationCount: Int,
    val missingAnnotationCount: Int
)

/** RFC 4180-compatible CSV field escaping. */
object CsvEncoder {
    fun field(value: Any?): String = "\"${value?.toString().orEmpty().replace("\"", "\"\"")}\""

    fun row(values: List<Any?>): String = values.joinToString(",", transform = ::field)
}

class ProjectExportEngine(
    private val context: Context,
    private val database: AppDatabase
) {

    suspend fun exportProjectToZip(
        projectId: Long,
        onProgress: (current: Int, total: Int, currentFileName: String) -> Unit = { _, _, _ -> }
    ): ExportResult = withContext(Dispatchers.IO) {
        val project = database.projectDao().getProjectById(projectId)
            ?: throw IllegalArgumentException("Project with id $projectId not found")
        val mediaList: List<MediaItemEntity> = database.mediaItemDao().getMediaItemsByProject(projectId).first()
        val issueList: List<IssueEntity> = database.issueDao().getIssuesByProject(projectId).first()
        val issueMap = issueList.associateBy { it.mediaId }
        val annotations = mediaList.associate { it.id to database.issueDao().getAnnotationByMediaId(it.id) }

        val totalPhotos = mediaList.count { it.mediaType == "PHOTO" }
        val totalVideos = mediaList.count { it.mediaType == "VIDEO" }
        val burnedVideos = mediaList.count { it.mediaType == "VIDEO" && it.processingStatus == "READY" }
        val totalIssues = mediaList.count { it.isIssue }
        val timestamp = System.currentTimeMillis()
        val timeStr = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date(timestamp))
        val sanitizedName = NamingEngine.sanitizeFileName(project.name)
        val zipName = "p${project.id}_${sanitizedName}_${timeStr}.zip"
        val target = createExportTarget(zipName)

        var copiedFileCount = 0
        var missingMediaCount = 0
        var copiedAnnotationCount = 0
        var missingAnnotationCount = 0
        val missingNames = mutableListOf<String>()

        try {
            ZipOutputStream(BufferedOutputStream(target.outputStream)).use { zos ->
                val jsonContent = JSONObject().apply {
                put("projectId", project.id)
                put("projectName", project.name)
                put("category", project.categoryName)
                put("address", project.address)
                put("description", project.description)
                put("exportTimestamp", timestamp)
                put("totalPhotos", totalPhotos)
                put("totalVideos", totalVideos)
                put("totalIssues", totalIssues)
                put("videoWatermarkBurnIn", burnedVideos == totalVideos)
                put("videoWatermarkBurnedCount", burnedVideos)
                put("videoWatermarkPendingCount", totalVideos - burnedVideos)
                put(
                    "videoWatermarkNote",
                    if (burnedVideos == totalVideos) "视频均已完成录制后烧录水印。"
                    else "转码失败的视频保留原片，并附 watermark.json 供重试后处理；未伪称已烧录。"
                )
            }
                putTextEntry(zos, "project_info.json", jsonContent.toString(2))

                val csv = StringBuilder().append('\uFEFF')
                csv.appendLine(
                CsvEncoder.row(
                    listOf("序号", "文件名", "类型", "拍摄时间", "工程地点", "GPS坐标", "是否为问题", "问题等级", "问题标题", "整改要求", "标注成品", "媒体导出状态")
                )
            )
                val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                mediaList.forEachIndexed { index, item ->
                val issue = issueMap[item.id]
                val annotation = annotations[item.id]
                csv.appendLine(
                    CsvEncoder.row(
                        listOf(
                            index + 1,
                            item.fileName,
                            item.mediaType,
                            dateFormat.format(Date(item.captureTimestamp)),
                            item.addressText,
                            if (item.latitude != null && item.longitude != null) String.format(Locale.US, "%.6f, %.6f", item.latitude, item.longitude) else "",
                            if (item.isIssue) "是" else "否",
                            when (issue?.severity) {
                                "CRITICAL" -> "严重"
                                "IMPORTANT" -> "重要"
                                else -> if (item.isIssue) "一般" else ""
                            },
                            issue?.title.orEmpty(),
                            issue?.description.orEmpty(),
                            if (annotation != null) "Annotations/${annotatedName(item)}" else "",
                            item.processingStatus
                        )
                    )
                )
            }
                putTextEntry(zos, "photo_index.csv", csv.toString())

                val totalEntries = mediaList.size + mediaList.count { annotations[it.id] != null }
                var progress = 0
                for (item in mediaList) {
                progress++
                onProgress(progress, totalEntries.coerceAtLeast(1), item.fileName)
                val folder = if (item.mediaType == "VIDEO") "Videos" else "Photos"
                if (copyUriEntry(zos, Uri.parse(item.contentUri), "$folder/${item.fileName}")) {
                    copiedFileCount++
                } else {
                    missingMediaCount++
                    missingNames += item.fileName
                }

                annotations[item.id]?.let { annotation ->
                    progress++
                    onProgress(progress, totalEntries.coerceAtLeast(1), annotatedName(item))
                    if (copyUriEntry(zos, Uri.parse(annotation.annotatedContentUri), "Annotations/${annotatedName(item)}")) {
                        copiedAnnotationCount++
                    } else {
                        missingAnnotationCount++
                        missingNames += annotatedName(item)
                    }
                }

                // Honest runnable fallback: downstream tools receive the
                // source metadata needed to burn a watermark during transcode.
                if (item.mediaType == "VIDEO" && item.processingStatus != "READY") {
                    val sidecar = JSONObject().apply {
                        put("sourceFile", item.fileName)
                        put("width", item.width)
                        put("height", item.height)
                        put("durationMs", item.duration)
                        put("rotation", item.orientation)
                        put("captureTimestamp", item.captureTimestamp)
                        put("watermarkBurnedIn", false)
                        put("processingStatus", item.processingStatus)
                        put("projectName", project.name)
                        put("category", project.categoryName)
                        put("address", item.addressText)
                        put("latitude", item.latitude ?: JSONObject.NULL)
                        put("longitude", item.longitude ?: JSONObject.NULL)
                        if (item.watermarkSnapshotJson.isNotBlank()) {
                            runCatching { put("watermarkSnapshot", JSONObject(item.watermarkSnapshotJson)) }
                        }
                    }
                    putTextEntry(zos, "Videos/${item.fileName}.watermark.json", sidecar.toString(2))
                }
            }

                val report = JSONObject().apply {
                put("copiedMedia", copiedFileCount)
                put("missingMedia", missingMediaCount)
                put("copiedAnnotations", copiedAnnotationCount)
                put("missingAnnotations", missingAnnotationCount)
                put("missingNames", missingNames)
            }
                putTextEntry(zos, "export_report.json", report.toString(2))
            }
            target.finishSuccess()
        } catch (t: Throwable) {
            target.cleanupFailure()
            throw t
        }

        ExportResult(
            zipUri = target.uri,
            zipName = zipName,
            totalPhotos = totalPhotos,
            totalVideos = totalVideos,
            totalIssues = totalIssues,
            copiedFileCount = copiedFileCount,
            missingMediaCount = missingMediaCount,
            copiedAnnotationCount = copiedAnnotationCount,
            missingAnnotationCount = missingAnnotationCount
        )
    }

    /**
     * Export the same archive contents as real files below a user-selected
     * SAF tree. This path is usable on Huawei Android and HarmonyOS devices
     * whose file managers cannot open or unpack a downloaded ZIP.
     */
    suspend fun exportProjectToFolder(
        projectId: Long,
        treeUri: Uri,
        onProgress: (current: Int, total: Int, currentFileName: String) -> Unit = { _, _, _ -> }
    ): FolderExportResult = withContext(Dispatchers.IO) {
        val project = database.projectDao().getProjectById(projectId)
            ?: throw IllegalArgumentException("Project with id $projectId not found")
        val mediaList = database.mediaItemDao().getMediaItemsByProject(projectId).first()
        val issueList = database.issueDao().getIssuesByProject(projectId).first()
        val issueMap = issueList.associateBy { it.mediaId }
        val annotations = mediaList.associate { it.id to database.issueDao().getAnnotationByMediaId(it.id) }
        val timestamp = System.currentTimeMillis()
        val timeStr = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date(timestamp))
        val folderName = "p${project.id}_${NamingEngine.sanitizeFileName(project.name)}_$timeStr"
        // ACTION_OPEN_DOCUMENT_TREE returns a tree URI. DocumentsContract's
        // createDocument APIs require the corresponding document URI as the
        // parent, which is not interchangeable on all vendor providers.
        val rootParent = runCatching {
            DocumentsContract.buildDocumentUriUsingTree(
                treeUri,
                DocumentsContract.getTreeDocumentId(treeUri)
            )
        }.getOrElse {
            throw IllegalArgumentException("无效的导出目录授权，请重新选择目录", it)
        }
        val root = createUniqueDirectory(rootParent, folderName)
        val photos = createUniqueDirectory(root, "Photos")
        val videos = createUniqueDirectory(root, "Videos")
        val annotationsDir = createUniqueDirectory(root, "Annotations")
        val totalPhotos = mediaList.count { it.mediaType == "PHOTO" }
        val totalVideos = mediaList.count { it.mediaType == "VIDEO" }
        val totalIssues = mediaList.count { it.isIssue }
        val totalEntries = mediaList.size + annotations.values.count { it != null }
        var progress = 0
        var copiedFileCount = 0
        var missingMediaCount = 0
        var copiedAnnotationCount = 0
        var missingAnnotationCount = 0
        val missingNames = mutableListOf<String>()

        val projectInfo = JSONObject().apply {
            put("projectId", project.id)
            put("projectName", project.name)
            put("category", project.categoryName)
            put("address", project.address)
            put("description", project.description)
            put("exportTimestamp", timestamp)
            put("totalPhotos", totalPhotos)
            put("totalVideos", totalVideos)
            put("totalIssues", totalIssues)
            put("format", "SiteCam-folder-v1")
        }
        writeTextDocument(root, "project_info.json", projectInfo.toString(2))
        val csv = StringBuilder().append('\uFEFF')
            .appendLine(CsvEncoder.row(listOf("序号", "文件名", "类型", "拍摄时间", "工程地点", "GPS坐标", "是否为问题", "问题等级", "问题标题", "整改要求", "标注成品", "媒体导出状态")))
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        mediaList.forEachIndexed { index, item ->
            val issue = issueMap[item.id]
            val annotation = annotations[item.id]
            csv.appendLine(
                CsvEncoder.row(
                    listOf(
                        index + 1, item.fileName, item.mediaType,
                        dateFormat.format(Date(item.captureTimestamp)), item.addressText,
                        if (item.latitude != null && item.longitude != null) String.format(Locale.US, "%.6f, %.6f", item.latitude, item.longitude) else "",
                        if (item.isIssue) "是" else "否",
                        when (issue?.severity) { "CRITICAL" -> "严重"; "IMPORTANT" -> "重要"; else -> if (item.isIssue) "一般" else "" },
                        issue?.title.orEmpty(), issue?.description.orEmpty(),
                        if (annotation != null) "Annotations/${annotatedName(item)}" else "", item.processingStatus
                    )
                )
            )
        }
        writeTextDocument(root, "photo_index.csv", csv.toString(), "text/csv")

        for (item in mediaList) {
            progress++
            onProgress(progress, totalEntries.coerceAtLeast(1), item.fileName)
            val parent = if (item.mediaType == "VIDEO") videos else photos
            val target = createUniqueDocument(parent, item.fileName, if (item.mediaType == "VIDEO") "video/mp4" else "image/jpeg")
            if (copyUriToDocument(Uri.parse(item.contentUri), target)) copiedFileCount++
            else {
                runCatching { context.contentResolver.delete(target, null, null) }
                missingMediaCount++
                missingNames += item.fileName
            }
            annotations[item.id]?.let { annotation ->
                progress++
                val name = annotatedName(item)
                onProgress(progress, totalEntries.coerceAtLeast(1), name)
                val annotationTarget = createUniqueDocument(annotationsDir, name, "image/jpeg")
                if (copyUriToDocument(Uri.parse(annotation.annotatedContentUri), annotationTarget)) copiedAnnotationCount++
                else {
                    runCatching { context.contentResolver.delete(annotationTarget, null, null) }
                    missingAnnotationCount++
                    missingNames += name
                }
            }
            if (item.mediaType == "VIDEO" && item.processingStatus != "READY") {
                val sidecar = JSONObject().apply {
                    put("sourceFile", item.fileName)
                    put("captureTimestamp", item.captureTimestamp)
                    put("width", item.width)
                    put("height", item.height)
                    put("durationMs", item.duration)
                    put("rotation", item.orientation)
                    put("watermarkBurnedIn", false)
                    put("processingStatus", item.processingStatus)
                    if (item.watermarkSnapshotJson.isNotBlank()) {
                        runCatching { put("watermarkSnapshot", JSONObject(item.watermarkSnapshotJson)) }
                    }
                }
                writeTextDocument(videos, "${item.fileName}.watermark.json", sidecar.toString(2))
            }
        }
        val report = JSONObject().apply {
            put("copiedMedia", copiedFileCount)
            put("missingMedia", missingMediaCount)
            put("copiedAnnotations", copiedAnnotationCount)
            put("missingAnnotations", missingAnnotationCount)
            put("missingNames", missingNames)
        }
        writeTextDocument(root, "export_report.json", report.toString(2))
        FolderExportResult(
            folderUri = root,
            folderName = folderName,
            totalPhotos = totalPhotos,
            totalVideos = totalVideos,
            totalIssues = totalIssues,
            copiedFileCount = copiedFileCount,
            missingMediaCount = missingMediaCount,
            copiedAnnotationCount = copiedAnnotationCount,
            missingAnnotationCount = missingAnnotationCount
        )
    }

    private fun putTextEntry(zos: ZipOutputStream, name: String, value: String) {
        zos.putNextEntry(ZipEntry(name))
        zos.write(value.toByteArray(Charsets.UTF_8))
        zos.closeEntry()
    }

    private fun copyUriEntry(zos: ZipOutputStream, uri: Uri, entryPath: String): Boolean {
        return try {
            val inputStream: InputStream = context.contentResolver.openInputStream(uri) ?: return false
            inputStream.use { input ->
                zos.putNextEntry(ZipEntry(entryPath))
                input.copyTo(zos)
                zos.closeEntry()
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun annotatedName(item: MediaItemEntity): String = item.fileName.substringBeforeLast('.') + "_annotated.jpg"

    private data class ExportTarget(
        val uri: Uri,
        val outputStream: OutputStream,
        val finishSuccess: () -> Unit,
        val cleanupFailure: () -> Unit
    )

    private fun createExportTarget(displayName: String): ExportTarget {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, displayName)
                put(MediaStore.Downloads.MIME_TYPE, "application/zip")
                put(
                    MediaStore.Downloads.RELATIVE_PATH,
                    "${Environment.DIRECTORY_DOWNLOADS}/SiteCam/Exports"
                )
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("无法创建导出文件")
            val output = resolver.openOutputStream(uri, "w")
                ?: run {
                    resolver.delete(uri, null, null)
                    throw IllegalStateException("无法写入导出文件")
                }
            ExportTarget(
                uri = uri,
                outputStream = output,
                finishSuccess = {
                    resolver.update(
                        uri,
                        ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) },
                        null,
                        null
                    )
                },
                cleanupFailure = { resolver.delete(uri, null, null) }
            )
        } else {
            @Suppress("DEPRECATION")
            // API 26-28 needs WRITE_EXTERNAL_STORAGE for a public Downloads
            // path. If the user declines it, keep export usable in the
            // app-owned external directory and expose it through FileProvider.
            val baseDir = if (hasLegacyWritePermission()) {
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            } else {
                context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
            }
            val exportDir = File(baseDir, "SiteCam/Exports").apply { mkdirs() }
            val file = collisionSafeFile(exportDir, displayName)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            ExportTarget(
                uri = uri,
                outputStream = FileOutputStream(file),
                finishSuccess = {},
                cleanupFailure = { file.delete() }
            )
        }
    }

    private fun hasLegacyWritePermission(): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

    private fun collisionSafeFile(parent: File, baseName: String): File {
        val dot = baseName.lastIndexOf('.')
        val stem = if (dot > 0) baseName.substring(0, dot) else baseName
        val extension = if (dot > 0) baseName.substring(dot) else ""
        for (index in 0..100) {
            val candidate = if (index == 0) baseName else "$stem ($index)$extension"
            val file = File(parent, candidate)
            if (!file.exists()) return file
        }
        throw IllegalStateException("无法创建不重复的导出文件: $baseName")
    }

    fun shareExportedZip(context: Context, result: ExportResult) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, result.zipUri)
            putExtra(Intent.EXTRA_SUBJECT, "工程档案包: ${result.zipName}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "分享工程档案包"))
    }

    fun createExportFolderIntent(initialUri: Uri? = null): Intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
        addFlags(
            Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && initialUri != null) {
            putExtra(DocumentsContract.EXTRA_INITIAL_URI, initialUri)
        }
    }

    private fun childNames(parent: Uri): Set<String> {
        return runCatching {
            val documentId = runCatching { DocumentsContract.getDocumentId(parent) }
                .getOrElse { DocumentsContract.getTreeDocumentId(parent) }
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(parent, documentId)
            buildSet {
                context.contentResolver.query(
                    childrenUri,
                    arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                    null,
                    null,
                    null
                )?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    while (cursor.moveToNext() && nameIndex >= 0) add(cursor.getString(nameIndex))
                }
            }
        }.getOrDefault(emptySet())
    }

    private fun createUniqueDirectory(parent: Uri, baseName: String): Uri {
        val resolver = context.contentResolver
        val existing = childNames(parent)
        for (index in 0..100) {
            val candidate = if (index == 0) baseName else "$baseName ($index)"
            if (candidate in existing) continue
            runCatching {
                DocumentsContract.createDocument(
                    resolver,
                    parent,
                    DocumentsContract.Document.MIME_TYPE_DIR,
                    candidate
                )
            }
                .getOrNull()?.let { return it }
        }
        throw IllegalStateException("无法创建导出目录: $baseName")
    }

    private fun createUniqueDocument(parent: Uri, baseName: String, mimeType: String): Uri {
        val resolver = context.contentResolver
        val existing = childNames(parent)
        val dot = baseName.lastIndexOf('.')
        val stem = if (dot > 0) baseName.substring(0, dot) else baseName
        val extension = if (dot > 0) baseName.substring(dot) else ""
        for (index in 0..100) {
            val candidate = if (index == 0) baseName else "$stem ($index)$extension"
            if (candidate in existing) continue
            runCatching { DocumentsContract.createDocument(resolver, parent, mimeType, candidate) }
                .getOrNull()?.let { return it }
        }
        throw IllegalStateException("无法创建导出文件: $baseName")
    }

    private fun writeTextDocument(
        parent: Uri,
        name: String,
        text: String,
        mimeType: String = "application/json"
    ) {
        val document = createUniqueDocument(parent, name, mimeType)
        try {
            context.contentResolver.openOutputStream(document, "w")?.use {
                it.write(text.toByteArray(Charsets.UTF_8))
            } ?: throw IllegalStateException("无法写入导出清单: $name")
        } catch (error: Exception) {
            runCatching { context.contentResolver.delete(document, null, null) }
            throw error
        }
    }

    private fun copyUriToDocument(source: Uri, target: Uri): Boolean = runCatching {
        context.contentResolver.openInputStream(source)?.use { sourceStream ->
            context.contentResolver.openOutputStream(target, "w")?.use { targetStream ->
                sourceStream.copyTo(targetStream)
                true
            } ?: false
        } ?: false
    }.getOrDefault(false)
}
