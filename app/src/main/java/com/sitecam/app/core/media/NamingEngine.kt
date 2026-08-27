package com.sitecam.app.core.media

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object NamingEngine {

    fun generateFileName(
        projectName: String,
        categoryName: String = "",
        timestamp: Long = System.currentTimeMillis(),
        pattern: String = "{project}_{date}_{time}",
        extension: String = "jpg",
        index: Int = 0
    ): String {
        val dateFormat = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
        // Milliseconds avoid collisions when a user captures repeatedly in
        // the same second; MediaStore's actual DISPLAY_NAME is read back too.
        val timeFormat = SimpleDateFormat("HHmmss_SSS", Locale.getDefault())
        val dateStr = dateFormat.format(Date(timestamp))
        val timeStr = timeFormat.format(Date(timestamp))

        val sanitizedProject = sanitizeFileName(if (projectName.isBlank()) "SiteCam" else projectName)
        val sanitizedCategory = sanitizeFileName(categoryName)

        var fileName = pattern
            .replace("{project}", sanitizedProject)
            .replace("{category}", sanitizedCategory)
            .replace("{date}", dateStr)
            .replace("{time}", timeStr)

        if (index > 0) {
            fileName += String.format(Locale.US, "_%03d", index)
        }

        return "$fileName.$extension"
    }

    fun sanitizeFileName(name: String): String {
        return name
            .replace("[/\\\\:*?\"<>|\\s+]".toRegex(), "_")
            .trim('_')
            .ifBlank { "SiteCam" }
    }
}
