package com.sitecam.app.feature.camera

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect

/** Geometry for the explicit 48dp edit affordance, separate from the camera surface. */
fun watermarkEditEntryRect(
    containerWidth: Float,
    containerHeight: Float,
    entrySize: Float = 48f,
    bottomInset: Float = 192f,
    startInset: Float = 12f
): Rect {
    val left = startInset.coerceAtLeast(0f)
    val top = (containerHeight - bottomInset - entrySize).coerceAtLeast(0f)
    return Rect(left, top, left + entrySize, top + entrySize)
}

fun isWatermarkEditEntryHit(point: Offset, entryRect: Rect): Boolean = entryRect.contains(point)
