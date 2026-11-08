package com.sitecam.app.core.export

import com.sitecam.app.core.media.PhotoQualityProfile
import com.sitecam.app.core.database.entity.MediaItemEntity
import com.sitecam.app.core.media.NamingEngine
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ExportOptions(val photoProfile: PhotoQualityProfile? = null)

fun exportZipName(projectNames: List<String>, timestamp: Long): String {
    require(projectNames.isNotEmpty()) { "请选择工程" }
    val name = NamingEngine.sanitizeFileName(projectNames.first()).ifBlank { "工程" }
    val prefix = name.substring(0, name.offsetByCodePoints(0, minOf(54, name.codePointCount(0, name.length))))
    val suffix = if (projectNames.size > 1) "等${projectNames.size}个工程" else ""
    val date = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date(timestamp))
    return "$prefix${suffix}_$date.zip"
}

fun selectExportMedia(items: List<MediaItemEntity>, projectIds: Set<Long>, mediaIds: Set<Long>?): List<MediaItemEntity> =
    items.filter { it.projectId in projectIds && (mediaIds == null || it.id in mediaIds) }
