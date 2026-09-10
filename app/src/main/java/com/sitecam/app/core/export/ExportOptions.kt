package com.sitecam.app.core.export

import com.sitecam.app.core.media.PhotoQualityProfile
import com.sitecam.app.core.database.entity.MediaItemEntity

data class ExportOptions(val photoProfile: PhotoQualityProfile? = null)

fun selectExportMedia(items: List<MediaItemEntity>, projectIds: Set<Long>, mediaIds: Set<Long>?): List<MediaItemEntity> =
    items.filter { it.projectId in projectIds && (mediaIds == null || it.id in mediaIds) }
