package com.sitecam.app.core.media

import kotlin.math.roundToInt

enum class PhotoQualityProfile(val maxLongEdge: Int?, val jpegQuality: Int, val label: String) {
    SMALL(1280, 65, "更小文件"), STANDARD(1920, 75, "标准省空间"),
    CLEAR(2560, 85, "较清晰"), ORIGINAL(null, 95, "原尺寸高清");

    fun targetSize(width: Int, height: Int): Pair<Int, Int> {
        require(width > 0 && height > 0)
        val scale = maxLongEdge?.let { minOf(1.0, it.toDouble() / maxOf(width, height)) } ?: 1.0
        return maxOf(1, (width * scale).roundToInt()) to maxOf(1, (height * scale).roundToInt())
    }
}
