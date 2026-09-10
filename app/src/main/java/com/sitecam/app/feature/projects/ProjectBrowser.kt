package com.sitecam.app.feature.projects

import com.sitecam.app.core.database.entity.MediaItemEntity
import com.sitecam.app.core.database.entity.ProjectEntity
import java.text.Collator
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class ProjectSort(val label: String) {
    LAST_CAPTURE("最近拍摄"), CREATED("创建时间"), UPDATED("编辑时间"), NAME("工程名称"), ROUTE("线路首字母")
}

data class ProjectStatistics(val photos: Int = 0, val videos: Int = 0, val issues: Int = 0,
    val firstCapture: Long? = null, val lastCapture: Long? = null) {
    fun dateLabel(): String {
        val first = firstCapture ?: return "尚未拍摄"
        val format = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)
        val start = format.format(Date(first))
        val end = format.format(Date(lastCapture ?: first))
        return if(start == end) start else "$start 至 $end"
    }
}

fun projectStatistics(items: List<MediaItemEntity>): Map<Long, ProjectStatistics> = items.groupBy { it.projectId }.mapValues { (_, media) ->
    ProjectStatistics(media.count { it.mediaType == "PHOTO" }, media.count { it.mediaType == "VIDEO" }, media.count { it.isIssue },
        media.minOfOrNull { it.captureTimestamp }, media.maxOfOrNull { it.captureTimestamp })
}

fun sortedProjects(projects: List<ProjectEntity>, stats: Map<Long, ProjectStatistics>, sort: ProjectSort, ascending: Boolean): List<ProjectEntity> {
    val collator = Collator.getInstance(Locale.CHINA).apply { strength = Collator.PRIMARY }
    return projects.sortedWith(Comparator { a, b ->
        val emptyA = when(sort) { ProjectSort.ROUTE -> a.routeName.isBlank(); ProjectSort.LAST_CAPTURE -> stats[a.id]?.lastCapture == null; else -> false }
        val emptyB = when(sort) { ProjectSort.ROUTE -> b.routeName.isBlank(); ProjectSort.LAST_CAPTURE -> stats[b.id]?.lastCapture == null; else -> false }
        if(emptyA != emptyB) return@Comparator if(emptyA) 1 else -1
        val compared = when(sort) {
            ProjectSort.LAST_CAPTURE -> (stats[a.id]?.lastCapture ?: 0).compareTo(stats[b.id]?.lastCapture ?: 0)
            ProjectSort.CREATED -> a.createdAt.compareTo(b.createdAt)
            ProjectSort.UPDATED -> a.updatedAt.compareTo(b.updatedAt)
            ProjectSort.NAME -> collator.compare(a.name.lowercase(Locale.ROOT), b.name.lowercase(Locale.ROOT))
            ProjectSort.ROUTE -> collator.compare(a.routeName.lowercase(Locale.ROOT), b.routeName.lowercase(Locale.ROOT))
        }
        if (compared == 0) {
            // Numeric sorts use the id as a deterministic recency tie-breaker;
            // descending creation order must keep a just-created row first
            // even when two inserts share the same millisecond timestamp.
            val tie = a.id.compareTo(b.id)
            if (sort == ProjectSort.NAME || sort == ProjectSort.ROUTE || ascending) tie else -tie
        } else if(ascending) compared else -compared
    })
}
