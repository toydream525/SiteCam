package com.sitecam.app.feature.onboarding

/**
 * Geometry for the first-launch guide, shared by both layout branches so the
 * phone, tablet and large-window arrangements keep the same numbers.
 *
 * Heights are window heights on both platforms; the 64vp header is subtracted
 * here, so nothing depends on how each platform reports its content area.
 */
object GuideLayout {
    /** Source crop ratio of the adopted four-step artwork (386 x 445). */
    const val ART_RATIO = 386f / 445f
    const val HEADER_DP = 64f
    const val PAGE_PADDING_DP = 24f
    const val COLUMN_GAP_DP = 32f
    const val BLOCK_MAX_DP = 1100f
    const val RIGHT_MIN_DP = 320f
    const val RIGHT_MAX_DP = 380f
    const val BUTTON_MIN_DP = 52f
    const val WIDE_MIN_WIDTH_DP = 600f
    const val WIDE_MAX_ASPECT = 1.35f

    data class Metrics(
        val wide: Boolean,
        val blockWidth: Float,
        val rightColumn: Float,
        val leftColumn: Float,
        val artworkWidth: Float,
        val bodyHeight: Float
    )

    fun contentHeight(windowHeight: Float): Float = windowHeight - HEADER_DP

    /** Single column: the artwork is bounded by the window width and the window height. */
    fun singleArtworkWidth(width: Float, windowHeight: Float): Float =
        maxOf(100f, minOf(width - 32f, maxOf(160f, windowHeight - 260f) * ART_RATIO))

    /**
     * Two columns are used only when the left column keeps the artwork at least
     * as large as the single column would, so a wide window never shrinks it.
     */
    fun metrics(width: Float, windowHeight: Float): Metrics {
        val block = minOf(width, BLOCK_MAX_DP)
        val right = (block * .34f).coerceIn(RIGHT_MIN_DP, RIGHT_MAX_DP)
        val left = block - 2 * PAGE_PADDING_DP - COLUMN_GAP_DP - right
        val body = contentHeight(windowHeight) - 2 * PAGE_PADDING_DP
        val artwork = maxOf(100f, minOf(left, body * ART_RATIO))
        val wide = width >= WIDE_MIN_WIDTH_DP && contentHeight(windowHeight) < width * WIDE_MAX_ASPECT &&
            artwork >= singleArtworkWidth(width, windowHeight)
        return Metrics(wide, block, right, left, artwork, body)
    }
}
