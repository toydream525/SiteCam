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
    return if (projectNames.size == 1) {
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date(timestamp))
        "${NamingEngine.sanitizeFileName(projectNames.single())}_$date.zip"
    } else {
        "工程档案_$timestamp.zip"
    }
}

fun selectExportMedia(items: List<MediaItemEntity>, projectIds: Set<Long>, mediaIds: Set<Long>?): List<MediaItemEntity> =
    items.filter { it.projectId in projectIds && (mediaIds == null || it.id in mediaIds) }
