package com.sitecam.app.feature.camera

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect

/**
 * Geometry for the explicit 48dp edit affordance, separate from the camera surface.
 *
 * Currently referenced only by [WatermarkEditEntryTest]: the production camera overlay resolves
 * its tap target through `WatermarkLayoutEngine`/`pointerInteropFilter` instead of these helpers.
 * Kept because the unit test pins the 48dp affordance geometry; do not delete without removing
 * that test too.
 */
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
