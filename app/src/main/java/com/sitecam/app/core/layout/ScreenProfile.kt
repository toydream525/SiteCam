package com.sitecam.app.core.layout

enum class ScreenFamily { PHONE, WIDE, SMALL_FOLD }

data class ScreenProfile(val large: Boolean = false, val smallCover: Boolean = false)

/** Window-local dp coordinates; a fold may have zero width or height. */
data class LayoutRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width get() = (right - left).coerceAtLeast(0f)
    val height get() = (bottom - top).coerceAtLeast(0f)
    val area get() = width * height
    fun translated(x: Float, y: Float) = copy(left = left + x, right = right + x, top = top + y, bottom = bottom + y)
}

/** Subtract every fold, including zero-width separating lines, before choosing content panes. */
fun unobstructedRegions(width: Float, height: Float, folds: List<LayoutRect>, gap: Float = 8f): List<LayoutRect> {
    var regions = listOf(LayoutRect(0f, 0f, width.coerceAtLeast(0f), height.coerceAtLeast(0f)))
    for (fold in folds.sortedWith(compareBy<LayoutRect> { it.left }.thenBy { it.top })) {
        regions = regions.flatMap { region ->
            if (fold.right < region.left || fold.left > region.right ||
                fold.bottom < region.top || fold.top > region.bottom) return@flatMap listOf(region)
            if (fold.height >= fold.width) {
                listOf(
                    region.copy(right = minOf(region.right, fold.left - gap)),
                    region.copy(left = maxOf(region.left, fold.right + gap))
                )
            } else {
                listOf(
                    region.copy(bottom = minOf(region.bottom, fold.top - gap)),
                    region.copy(top = maxOf(region.top, fold.bottom + gap))
                )
            }.filter { it.width > 0f && it.height > 0f }
        }
    }
    return regions
}

data class PaneArrangement(val primary: LayoutRect, val secondary: LayoutRect? = null) {
    val split get() = secondary != null
}

fun adaptivePaneArrangement(
    width: Float, height: Float, fontScale: Float,
    folds: List<LayoutRect> = emptyList(), allowTwoPane: Boolean = true,
    primaryMinWidth: Float = 320f, secondaryMinWidth: Float = 360f,
    primaryFraction: Float = .4f
): PaneArrangement {
    val regions = unobstructedRegions(width, height, folds)
    val fallback = regions.maxByOrNull { it.area } ?: LayoutRect(0f, 0f, 0f, 0f)
    if (!allowTwoPane) return PaneArrangement(fallback)
    val textScale = fontScale.coerceAtLeast(1f)
    // Only text capacity grows; touch targets and icons keep their native dimensions.
    val primaryMinimum = primaryMinWidth + 144f * (textScale - 1f)
    val secondaryMinimum = secondaryMinWidth + 176f * (textScale - 1f)
    val minimumTotal = maxOf(840f, primaryMinimum + secondaryMinimum + 24f)
    val candidates = regions.flatMap { left -> regions.mapNotNull { right ->
        val top = maxOf(left.top, right.top)
        val bottom = minOf(left.bottom, right.bottom)
        if (left.right <= right.left && left.width >= primaryMinimum && right.width >= secondaryMinimum &&
            left.width + right.width >= minimumTotal && bottom - top >= 160f) {
            PaneArrangement(left.copy(top = top, bottom = bottom), right.copy(top = top, bottom = bottom))
        } else null
    } }
    candidates.maxByOrNull { it.primary.area + (it.secondary?.area ?: 0f) }?.let { return it }
    val region = regions.filter { it.width >= minimumTotal && it.height >= 160f }.maxByOrNull { it.area }
        ?: return PaneArrangement(fallback)
    val listWidth = (region.width * primaryFraction).coerceIn(primaryMinimum, region.width - secondaryMinimum - 24f)
    return PaneArrangement(region.copy(right = region.left + listWidth), region.copy(left = region.left + listWidth + 24f))
}

/** Classify the available window; model names never override its usable size. */
@Suppress("UNUSED_PARAMETER")
fun screenProfile(model: String, width: Float, height: Float, innerFold: Boolean): ScreenProfile {
    val short = minOf(width, height)
    val long = maxOf(width, height)
    val cover = short < 360 && long < 420 && !innerFold
    val wide = short >= 420 && long / short <= 1.85f
    return ScreenProfile(large = !cover && (wide || short >= 600), smallCover = cover)
}
