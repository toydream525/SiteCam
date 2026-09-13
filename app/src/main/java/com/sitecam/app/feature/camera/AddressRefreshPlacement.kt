package com.sitecam.app.feature.camera

import android.graphics.RectF

/** Locations used by the address retry chip inside the live preview. */
enum class AddressRefreshCorner {
    TOP_START,
    TOP_END,
    BOTTOM_START,
    BOTTOM_END,
    TOP_CENTER,
    BOTTOM_CENTER,
    CENTER_START,
    CENTER_END,
    /** There is no in-frame candidate that clears the watermark protection. */
    OUTSIDE_PREVIEW
}

/**
 * Pick a preview edge that does not cover the watermark card.  The preview
 * itself is already laid out away from the camera shelves and shutter dock;
 * keeping the chip inside that frame also keeps it away from system insets
 * and the physical capture controls.
 */
fun chooseAddressRefreshCorner(
    canvasWidth: Float,
    canvasHeight: Float,
    chipWidth: Float,
    chipHeight: Float,
    watermarkRect: RectF?,
    margin: Float = 12f,
    gap: Float = 8f
): AddressRefreshCorner {
    val width = canvasWidth.coerceAtLeast(1f)
    val height = canvasHeight.coerceAtLeast(1f)
    val w = chipWidth.coerceAtMost((width - margin * 2).coerceAtLeast(1f))
    val h = chipHeight.coerceAtMost((height - margin * 2).coerceAtLeast(1f))
    val candidates = listOf(
        AddressRefreshCorner.TOP_END,
        AddressRefreshCorner.TOP_START,
        AddressRefreshCorner.BOTTOM_END,
        AddressRefreshCorner.BOTTOM_START,
        AddressRefreshCorner.TOP_CENTER,
        AddressRefreshCorner.BOTTOM_CENTER,
        AddressRefreshCorner.CENTER_END,
        AddressRefreshCorner.CENTER_START
    )

    fun rectFor(corner: AddressRefreshCorner): RectF = when (corner) {
        AddressRefreshCorner.TOP_START -> RectF(margin, margin, margin + w, margin + h)
        AddressRefreshCorner.TOP_END -> RectF(width - margin - w, margin, width - margin, margin + h)
        AddressRefreshCorner.BOTTOM_START -> RectF(margin, height - margin - h, margin + w, height - margin)
        AddressRefreshCorner.BOTTOM_END -> RectF(width - margin - w, height - margin - h, width - margin, height - margin)
        AddressRefreshCorner.TOP_CENTER -> RectF((width - w) / 2f, margin, (width + w) / 2f, margin + h)
        AddressRefreshCorner.BOTTOM_CENTER -> RectF((width - w) / 2f, height - margin - h, (width + w) / 2f, height - margin)
        AddressRefreshCorner.CENTER_START -> RectF(margin, (height - h) / 2f, margin + w, (height + h) / 2f)
        AddressRefreshCorner.CENTER_END -> RectF(width - margin - w, (height - h) / 2f, width - margin, (height + h) / 2f)
        AddressRefreshCorner.OUTSIDE_PREVIEW -> RectF()
    }

    val protected = watermarkRect?.let { RectF(it).apply { inset(-gap, -gap) } }
    fun overlapArea(a: RectF, b: RectF): Float {
        val left = maxOf(a.left, b.left)
        val top = maxOf(a.top, b.top)
        val right = minOf(a.right, b.right)
        val bottom = minOf(a.bottom, b.bottom)
        return if (right > left && bottom > top) (right - left) * (bottom - top) else 0f
    }

    // A minimum-overlap position is still an obstruction.  Returning an
    // explicit outside placement lets the UI keep the retry action available
    // without claiming that a full-frame watermark can be safely covered.
    return candidates.firstOrNull { corner ->
        val rect = rectFor(corner)
        protected?.let { card -> overlapArea(rect, card) == 0f } ?: true
    } ?: AddressRefreshCorner.OUTSIDE_PREVIEW
}

/**
 * Return the exact in-frame bounds used for a measured chip.  Keeping this
 * calculation beside the chooser prevents a caller from measuring one size
 * and placing another size at a guessed alignment.
 */
fun addressRefreshRectForCorner(
    corner: AddressRefreshCorner,
    canvasWidth: Float,
    canvasHeight: Float,
    chipWidth: Float,
    chipHeight: Float,
    margin: Float = 12f
): RectF? {
    if (corner == AddressRefreshCorner.OUTSIDE_PREVIEW) return null
    val width = canvasWidth.coerceAtLeast(1f)
    val height = canvasHeight.coerceAtLeast(1f)
    val w = chipWidth.coerceAtMost((width - margin * 2).coerceAtLeast(1f))
    val h = chipHeight.coerceAtMost((height - margin * 2).coerceAtLeast(1f))
    return when (corner) {
        AddressRefreshCorner.TOP_START -> RectF(margin, margin, margin + w, margin + h)
        AddressRefreshCorner.TOP_END -> RectF(width - margin - w, margin, width - margin, margin + h)
        AddressRefreshCorner.BOTTOM_START -> RectF(margin, height - margin - h, margin + w, height - margin)
        AddressRefreshCorner.BOTTOM_END -> RectF(width - margin - w, height - margin - h, width - margin, height - margin)
        AddressRefreshCorner.TOP_CENTER -> RectF((width - w) / 2f, margin, (width + w) / 2f, margin + h)
        AddressRefreshCorner.BOTTOM_CENTER -> RectF((width - w) / 2f, height - margin - h, (width + w) / 2f, height - margin)
        AddressRefreshCorner.CENTER_START -> RectF(margin, (height - h) / 2f, margin + w, (height + h) / 2f)
        AddressRefreshCorner.CENTER_END -> RectF(width - margin - w, (height - h) / 2f, width - margin, (height + h) / 2f)
        AddressRefreshCorner.OUTSIDE_PREVIEW -> null
    }
}

/**
 * The cover display has no top tool shelf. Reserve a real 48dp touch target
 * only in the free area above or below the complete gallery/shutter/flip
 * stack. Short cover windows return null so the caller can use a secondary
 * action instead of placing a target over another control.
 * Values are in the same dp-like coordinates used by CameraGeometry.
 */
fun smallCoverAddressFallbackSlot(
    windowHeight: Float,
    dockWidth: Float = 56f,
    requestedSize: Float = 48f,
    margin: Float = 2f
): RectF? {
    val height = windowHeight.coerceAtLeast(1f)
    val hasSecondaryControls = height >= 156f
    val controlsHeight = if (hasSecondaryControls) 156f else 44f
    val controlsTop = ((height - controlsHeight) / 2f).coerceAtLeast(0f)
    val freeTop = controlsTop
    val freeBottom = (height - controlsTop - controlsHeight).coerceAtLeast(0f)
    val availableHeight = maxOf(freeTop, freeBottom) - margin * 2f
    if (availableHeight < requestedSize || dockWidth < requestedSize + margin * 2f) return null
    val size = requestedSize
    val left = (dockWidth - size) / 2f
    val top = if (freeTop >= requestedSize + margin) margin else height - margin - size
    return RectF(left, top, left + size, top + size)
}
