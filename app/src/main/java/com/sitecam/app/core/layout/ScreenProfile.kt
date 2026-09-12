package com.sitecam.app.core.layout

enum class ScreenFamily { PHONE, WIDE, SMALL_FOLD }

data class ScreenProfile(val large: Boolean = false, val smallCover: Boolean = false)

/** Physical display dimensions, not the app's split-window dimensions. */
fun screenProfile(model: String, width: Float, height: Float, innerFold: Boolean): ScreenProfile {
    val name = model.lowercase().replace(Regex("[ _-]"), "")
    val short = minOf(width, height)
    val long = maxOf(width, height)
    val smallFold = name.contains("flip") || name.contains("pocket") || name.contains("razr") ||
        name.contains("smf7")
    val small = smallFold || (short < 360 && long < 400)
    val cover = small && long < 600 && !innerFold
    val wide = name.contains("purax") || (short >= 420 && long / short <= 1.85f)
    return ScreenProfile(large = !small && (innerFold || wide || short >= 600), smallCover = cover)
}
