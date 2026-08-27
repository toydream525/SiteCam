package com.sitecam.app.feature.annotation

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlin.math.min

/** The actual image rectangle produced by ContentScale.Fit. */
data class ImageContentRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val scale: Float
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val rect: Rect get() = Rect(left, top, right, bottom)

    fun contains(point: Offset): Boolean = rect.contains(point)

    /** Convert a point in the Compose view to a point in source pixels. */
    fun toBitmap(point: Offset): Offset = Offset(
        x = ((point.x - left) / scale).coerceIn(0f, (width / scale).coerceAtLeast(0f)),
        y = ((point.y - top) / scale).coerceIn(0f, (height / scale).coerceAtLeast(0f))
    )

    companion object {
        fun forFit(
            imageWidth: Int,
            imageHeight: Int,
            viewWidth: Float,
            viewHeight: Float
        ): ImageContentRect {
            require(imageWidth > 0 && imageHeight > 0) { "Image dimensions must be positive" }
            require(viewWidth > 0f && viewHeight > 0f) { "View dimensions must be positive" }
            val scale = min(viewWidth / imageWidth, viewHeight / imageHeight)
            val contentWidth = imageWidth * scale
            val contentHeight = imageHeight * scale
            val left = (viewWidth - contentWidth) / 2f
            val top = (viewHeight - contentHeight) / 2f
            return ImageContentRect(left, top, left + contentWidth, top + contentHeight, scale)
        }
    }
}
