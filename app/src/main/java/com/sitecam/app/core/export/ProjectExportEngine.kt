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
    val missingAnnotationCount: Int,
    val destinationFailureCount: Int = 0
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

    suspend fun exportProjectToZip(projectId: Long,
        onProgress: (Int, Int, String) -> Unit = { _, _, _ -> }): ExportResult =
        exportProjectsToZip(setOf(projectId), onProgress = onProgress)

    suspend fun exportProjectToFolder(projectId: Long, treeUri: Uri,
        onProgress: (Int, Int, String) -> Unit = { _, _, _ -> }): FolderExportResult =
        exportProjectsToFolder(setOf(projectId), treeUri, onProgress = onProgress)

    suspend fun exportProjectsToZip(projectIds: Set<Long>, mediaIds: Set<Long>? = null,
        options: ExportOptions = ExportOptions(),
        onProgress: (Int, Int, String) -> Unit = { _, _, _ -> }): ExportResult =
        com.sitecam.app.core.media.MediaOperationCoordinator.withExclusive {
            withContext(Dispatchers.IO) {
                val staged = stage(projectIds, mediaIds, options, onProgress)
                try {
                    val name = "工程档案_${System.currentTimeMillis()}.zip"
                    val target = createExportTarget(name)
                    try {
                        ZipOutputStream(BufferedOutputStream(target.outputStream)).use { zip ->
                            val files = staged.root.walkTopDown().filter { it.isFile }.toList()
                            files.forEachIndexed { index, file ->
                                onProgress(index + 1, files.size, "写入压缩包：${file.name}")
                                zip.putNextEntry(ZipEntry(file.relativeTo(staged.root).invariantSeparatorsPath))
                                file.inputStream().use { it.copyTo(zip) }
                                zip.closeEntry()
                            }
                        }
                        target.finishSuccess()
                    } catch (t: Throwable) { target.cleanupFailure(); throw t }
                    ExportResult(target.uri, name, staged.photos, staged.videos, staged.issues,
                        staged.copied, staged.missing, staged.annotations, staged.missingAnnotations)
                } finally { staged.root.deleteRecursively() }
            }
        }

    suspend fun exportProjectsToFolder(projectIds: Set<Long>, treeUri: Uri, mediaIds: Set<Long>? = null,
        options: ExportOptions = ExportOptions(),
        onProgress: (Int, Int, String) -> Unit = { _, _, _ -> }): FolderExportResult =
        com.sitecam.app.core.media.MediaOperationCoordinator.withExclusive {
            withContext(Dispatchers.IO) {
                val staged = stage(projectIds, mediaIds, options, onProgress)
                try {
                    val name = "工程档案_${System.currentTimeMillis()}"
                    val parent = DocumentsContract.buildDocumentUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri))
                    val root = createUniqueDirectory(parent, name)
                    val dirs = mutableMapOf("" to root)
                    val files = staged.root.walkTopDown().filter { it.isFile }.sortedBy { if(it.name in setOf("project_info.json", "photo_index.csv", "export_report.json")) 1 else 0 }.toList()
                    val destinationFailures = mutableListOf<String>()
                    files.forEachIndexed { index, file ->
                        val relative = file.relativeTo(staged.root).invariantSeparatorsPath
                        onProgress(index + 1, files.size, "写入文件夹：${file.name}")
                        try {
                            var path = ""
                            var documentParent = root
                            relative.substringBeforeLast('/', "").split('/').filter { it.isNotBlank() }.forEach { segment ->
                                path = if (path.isEmpty()) segment else "$path/$segment"
                                documentParent = dirs.getOrPut(path) { createUniqueDirectory(documentParent, segment) }
                            }
                            val mime = when(file.extension) { "jpg" -> "image/jpeg"; "mp4" -> "video/mp4"; "csv" -> "text/csv"; else -> "application/json" }
                            val target = createUniqueDocument(documentParent, file.name, mime)
                            try {
                                context.contentResolver.openOutputStream(target, "w")?.use { out -> file.inputStream().use { it.copyTo(out) } }
                                    ?: error("无法写入 ${file.name}")
                            } catch (e: Exception) { runCatching { DocumentsContract.deleteDocument(context.contentResolver, target) }; throw e }
                        } catch (e: Exception) {
                            destinationFailures += "$relative: ${e.message}"
                            val projectDir = File(staged.root, relative.substringBefore('/'))
                            val relativeMedia = relative.substringAfter('/')
                            val csvFile = File(projectDir, "photo_index.csv")
                            if(csvFile.exists()) csvFile.writeText(csvFile.readLines().joinToString("\n", postfix = "\n") { line ->
                                if(line.contains(CsvEncoder.field(relativeMedia))) line.replace("\"COPIED\"", "\"FAILED\"") else line
                            })
                            val reportFile = File(projectDir, "export_report.json")
                            if(reportFile.exists()) {
                                val report = JSONObject(reportFile.readText())
                                val failures = report.optJSONArray("deliveryFailures") ?: org.json.JSONArray()
                                failures.put("$relativeMedia: ${e.message}"); report.put("deliveryFailures", failures)
                                val records = report.optJSONArray("files")
                                if(records != null) for(i in 0 until records.length()) {
                                    val record = records.getJSONObject(i)
                                    if(record.optString("exportPath") == relativeMedia && record.optBoolean("success")) {
                                        record.put("success", false); record.put("deliveryStatus", "FAILED")
                                        report.put("copiedMedia", report.optInt("copiedMedia") - 1); report.put("missingMedia", report.optInt("missingMedia") + 1)
                                    }
                                    if(record.optString("annotationExportPath") == relativeMedia && record.optBoolean("annotationSuccess")) {
                                        record.put("annotationSuccess", false)
                                        report.put("copiedAnnotations", report.optInt("copiedAnnotations") - 1); report.put("missingAnnotations", report.optInt("missingAnnotations") + 1)
                                    }
                                }
                                reportFile.writeText(report.toString(2))
                            }
                            if (relative.contains("/Photos/") || relative.contains("/Videos/") && !relative.endsWith(".json")) { staged.copied--; staged.missing++ }
                            if (relative.contains("/Annotations/")) { staged.annotations--; staged.missingAnnotations++ }
                        }
                    }
                    writeTextDocument(root, "export_report.json", JSONObject().apply {
                        put("copiedMedia", staged.copied); put("missingMedia", staged.missing)
                        put("copiedAnnotations", staged.annotations); put("missingAnnotations", staged.missingAnnotations)
                        put("destinationFailures", org.json.JSONArray(destinationFailures))
                    }.toString(2))
                    FolderExportResult(root, name, staged.photos, staged.videos, staged.issues,
                        staged.copied, staged.missing, staged.annotations, staged.missingAnnotations,
                        destinationFailures.size)
                } finally { staged.root.deleteRecursively() }
            }
        }

    private data class Staged(val root: File, var photos: Int = 0, var videos: Int = 0, var issues: Int = 0,
        var copied: Int = 0, var missing: Int = 0, var annotations: Int = 0, var missingAnnotations: Int = 0)

    private suspend fun stage(projectIds: Set<Long>, mediaIds: Set<Long>?, options: ExportOptions,
        onProgress: (Int, Int, String) -> Unit): Staged {
        require(projectIds.isNotEmpty()) { "请选择工程" }
        require(mediaIds == null || mediaIds.isNotEmpty()) { "请选择媒体" }
        val root = File(context.cacheDir, "export_${java.util.UUID.randomUUID()}").apply { mkdirs() }
        val result = Staged(root)
        try {
            val projects = projectIds.sorted().map { database.projectDao().getProjectById(it) ?: error("工程 $it 已不存在") }
            val all = database.mediaItemDao().getAllMediaItems().first()
            val chosen = selectExportMedia(all, projectIds, mediaIds)
            require(mediaIds == null || chosen.size == mediaIds.size) { "部分选中媒体已删除或移动，请刷新后重试" }
            val total = chosen.size.coerceAtLeast(1)
            var progress = 0
            for (project in projects) {
                val dir = File(root, "${NamingEngine.sanitizeFileName(project.name)}_p${project.id}").apply { mkdirs() }
                val items = chosen.filter { it.projectId == project.id }
                val annotationsBefore = result.annotations
                val missingAnnotationsBefore = result.missingAnnotations
                val issues = database.issueDao().getIssuesByProject(project.id).first().associateBy { it.mediaId }
                result.photos += items.count { it.mediaType == "PHOTO" }; result.videos += items.count { it.mediaType == "VIDEO" }
                result.issues += items.count { issues.containsKey(it.id) }
                File(dir, "project_info.json").writeText(JSONObject().apply {
                    put("projectId", project.id); put("projectName", project.name); put("routeName", project.routeName)
                    put("category", project.categoryName); put("address", project.address); put("description", project.description)
                    put("isCaptureLocked", project.isCaptureLocked); put("isArchived", project.isArchived)
                    put("createdAt", project.createdAt); put("updatedAt", project.updatedAt)
                    val videos = items.filter { it.mediaType == "VIDEO" }
                    val burned = videos.count { it.processingStatus == "READY" }
                    put("videoWatermarkBurnIn", burned == videos.size)
                    put("videoWatermarkBurnedCount", burned); put("videoWatermarkPendingCount", videos.size - burned)
                    put("videoWatermarkNote", if(burned == videos.size) "视频均已完成录制后烧录水印。" else "转码失败的视频保留原片，并附 watermark.json 供重试后处理；未伪称已烧录。")
                    put("firstCaptureTimestamp", items.minOfOrNull { it.captureTimestamp } ?: JSONObject.NULL)
                    put("lastCaptureTimestamp", items.maxOfOrNull { it.captureTimestamp } ?: JSONObject.NULL)
                    put("exportTimestamp", System.currentTimeMillis()); put("photoProfile", options.photoProfile?.name ?: "KEEP_ORIGINAL")
                    put("totalPhotos", items.count { it.mediaType == "PHOTO" }); put("totalVideos", items.count { it.mediaType == "VIDEO" })
                    put("totalIssues", items.count { issues.containsKey(it.id) })
                }.toString(2))
                val csv = StringBuilder("\uFEFF").appendLine(CsvEncoder.row(listOf("序号", "文件名", "类型", "拍摄时间", "工程地点", "GPS坐标", "是否为问题", "问题等级", "问题标题", "整改要求", "标注成品", "媒体导出状态", "媒体ID", "工程", "线路", "整改状态", "导出文件", "实际宽", "实际高", "实际字节", "文件交付状态")))
                val failures = mutableListOf<String>()
                val records = org.json.JSONArray()
                for (item in items) {
                    onProgress(++progress, total, item.fileName)
                    val folder = if (item.mediaType == "VIDEO") "Videos" else "Photos"
                    val file = File(dir, "$folder/${item.id}_${NamingEngine.sanitizeFileName(item.fileName)}")
                    var dimensions: Pair<Int, Int>? = null
                    val copied = runCatching {
                        dimensions = copySource(Uri.parse(item.contentUri), file, if(item.mediaType == "PHOTO") options.photoProfile else null)
                    }.onFailure { failures += "${item.fileName}: ${it.message}"; file.delete() }.isSuccess
                    if(copied) result.copied++ else result.missing++
                    var annotationPath = ""
                    database.issueDao().getAnnotationByMediaId(item.id)?.let { annotation ->
                        val annotated = File(dir, "Annotations/${item.id}_${annotatedName(item)}")
                        if(runCatching { copySource(Uri.parse(annotation.annotatedContentUri), annotated, options.photoProfile) }.isSuccess) {
                            result.annotations++; annotationPath = annotated.relativeTo(dir).invariantSeparatorsPath
                        } else { result.missingAnnotations++; failures += "标注 ${item.fileName}"; annotated.delete() }
                    }
                    if (item.mediaType == "VIDEO" && item.processingStatus != "READY") {
                        File(dir, "$folder/${file.name}.watermark.json").apply { parentFile?.mkdirs() }.writeText(JSONObject().apply {
                            put("sourceFile", file.name); put("watermarkBurnedIn", false); put("processingStatus", item.processingStatus)
                            put("captureTimestamp", item.captureTimestamp); put("width", item.width); put("height", item.height)
                            put("durationMs", item.duration); put("rotation", item.orientation)
                            put("watermarkSnapshotJson", item.watermarkSnapshotJson)
                        }.toString(2))
                    }
                    val issue = issues[item.id]
                    csv.appendLine(CsvEncoder.row(listOf(items.indexOf(item) + 1, item.fileName, item.mediaType,
                        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(item.captureTimestamp)),
                        item.addressText, if(item.latitude != null && item.longitude != null) String.format(Locale.US, "%.6f, %.6f", item.latitude, item.longitude) else "",
                        if(item.isIssue) "是" else "否", when(issue?.severity) { "CRITICAL" -> "严重"; "IMPORTANT" -> "重要"; "NORMAL" -> "一般"; else -> "" },
                        issue?.title.orEmpty(), issue?.description.orEmpty(), annotationPath, item.processingStatus,
                        item.id, project.name, project.routeName, when(issue?.status) { "PENDING" -> "待处理"; "IN_PROGRESS" -> "处理中"; "COMPLETED" -> "已完成"; else -> "" },
                        if(copied) file.relativeTo(dir).invariantSeparatorsPath else "",
                        dimensions?.first ?: item.width, dimensions?.second ?: item.height,
                        if(copied) file.length() else 0, if(copied) "COPIED" else "FAILED")))
                    records.put(JSONObject().apply { put("mediaId", item.id); put("exportPath", file.relativeTo(dir).invariantSeparatorsPath); put("annotationExportPath", annotationPath); put("annotationSuccess", annotationPath.isNotBlank()); put("success", copied); put("bytes", if(copied) file.length() else 0)
                        put("width", dimensions?.first ?: item.width); put("height", dimensions?.second ?: item.height)
                        put("issueStatus", issue?.status ?: JSONObject.NULL) })
                }
                File(dir, "photo_index.csv").writeText(csv.toString())
                File(dir, "export_report.json").writeText(JSONObject().apply {
                    put("files", records); put("failures", org.json.JSONArray(failures)); put("missingNames", org.json.JSONArray(failures))
                    val copied = (0 until records.length()).count { records.getJSONObject(it).getBoolean("success") }
                    put("copiedMedia", copied); put("missingMedia", records.length() - copied)
                    put("copiedAnnotations", result.annotations - annotationsBefore); put("missingAnnotations", result.missingAnnotations - missingAnnotationsBefore)
                    put("photoProfile", options.photoProfile?.name ?: "KEEP_ORIGINAL")
                    put("jpegQuality", options.photoProfile?.jpegQuality ?: JSONObject.NULL)
                }.toString(2))
            }
            return result
        } catch(t: Throwable) { root.deleteRecursively(); throw t }
    }

    private fun copySource(uri: Uri, file: File, profile: com.sitecam.app.core.media.PhotoQualityProfile?): Pair<Int, Int>? {
        file.parentFile?.mkdirs()
        if(profile == null) {
            context.contentResolver.openInputStream(uri)?.use { source -> file.outputStream().use { source.copyTo(it) } }
                ?: error("源文件不可读取")
            val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            android.graphics.BitmapFactory.decodeFile(file.absolutePath, bounds)
            return if(bounds.outWidth > 0) bounds.outWidth to bounds.outHeight else null
        }
        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it, null, bounds) }
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "图片无法解码" }
        val max = profile.maxLongEdge
        var sample = 1
        if(max != null) while(kotlin.math.max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= max) sample *= 2
        val bitmap = context.contentResolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it, null, android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }) }
            ?: error("图片无法解码")
        val resized = com.sitecam.app.core.media.PhotoCompression.resize(bitmap, profile)
        try {
            file.outputStream().use { require(resized.compress(android.graphics.Bitmap.CompressFormat.JPEG, profile.jpegQuality, it)) { "图片压缩失败" } }
            // Keep original capture-time and location evidence. Do not stamp export time as capture time.
            context.contentResolver.openInputStream(uri)?.use { input ->
                val original = androidx.exifinterface.media.ExifInterface(input)
                val output = androidx.exifinterface.media.ExifInterface(file.absolutePath)
                val tags = listOf("DateTime", "DateTimeOriginal", "DateTimeDigitized", "OffsetTime", "OffsetTimeOriginal", "OffsetTimeDigitized",
                    "SubSecTime", "SubSecTimeOriginal", "SubSecTimeDigitized", "GPSLatitude", "GPSLatitudeRef", "GPSLongitude", "GPSLongitudeRef",
                    "GPSAltitude", "GPSAltitudeRef", "GPSTimeStamp", "GPSDateStamp", "GPSProcessingMethod", "GPSHPositioningError",
                    "Orientation", "Make", "Model", "ImageDescription", "UserComment", "Copyright", "Artist", "Software",
                    "ExposureTime", "FNumber", "PhotographicSensitivity", "FocalLength", "WhiteBalance")
                tags.forEach { tag -> original.getAttribute(tag)?.let { output.setAttribute(tag, it) } }
                output.setAttribute(androidx.exifinterface.media.ExifInterface.TAG_IMAGE_WIDTH, resized.width.toString())
                output.setAttribute(androidx.exifinterface.media.ExifInterface.TAG_IMAGE_LENGTH, resized.height.toString())
                output.setAttribute(androidx.exifinterface.media.ExifInterface.TAG_PIXEL_X_DIMENSION, resized.width.toString())
                output.setAttribute(androidx.exifinterface.media.ExifInterface.TAG_PIXEL_Y_DIMENSION, resized.height.toString())
                output.saveAttributes()
            }
            return resized.width to resized.height
        } finally { if(resized !== bitmap) resized.recycle(); bitmap.recycle() }
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

    private fun annotatedName(item: MediaItemEntity): String = NamingEngine.sanitizeFileName(item.fileName.substringBeforeLast('.')) + "_annotated.jpg"

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
