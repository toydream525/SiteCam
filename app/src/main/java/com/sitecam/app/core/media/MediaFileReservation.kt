package com.sitecam.app.core.media

import java.io.File

/** Atomically reserves a distinct path before a writer opens it. */
internal fun createUniqueMediaFile(directory: File, name: String): File {
    val safeName = NamingEngine.sanitizeFileName(name)
    val stem = safeName.substringBeforeLast('.', safeName)
    val ext = safeName.substringAfterLast('.', "")
    var index = 0
    while (true) {
        val candidate = File(directory, if (index == 0) safeName else "$stem ($index)${if (ext.isEmpty()) "" else ".$ext"}")
        if (candidate.createNewFile()) return candidate
        index++
    }
}
