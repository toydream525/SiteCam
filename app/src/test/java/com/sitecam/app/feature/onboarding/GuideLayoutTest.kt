package com.sitecam.app.feature.onboarding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GuideLayoutTest {

    @Test
    fun largeFoldableInnerScreenUsesTwoColumns() {
        val metrics = GuideLayout.metrics(width = 1000f, windowHeight = 900f)
        assertTrue(metrics.wide)
        assertEquals(340f, metrics.rightColumn, .5f)
        assertEquals(580f, metrics.leftColumn, .5f)
        assertEquals(580f, metrics.artworkWidth, .5f)
    }

    @Test
    fun landscapeLargeWindowUsesTwoColumns() {
        val metrics = GuideLayout.metrics(width = 1200f, windowHeight = 800f)
        assertTrue(metrics.wide)
        assertEquals(GuideLayout.BLOCK_MAX_DP, metrics.blockWidth, .5f)
        assertEquals(596.5f, metrics.artworkWidth, .5f)
        assertTrue(metrics.artworkWidth > GuideLayout.singleArtworkWidth(1200f, 800f))
    }

    @Test
    fun nearSquareSmallWindowKeepsSingleColumn() {
        // 684x768 inner screen: two columns would shrink the artwork from 440 to 284vp.
        val metrics = GuideLayout.metrics(width = 684f, windowHeight = 768f)
        assertFalse(metrics.wide)
        assertEquals(284f, metrics.artworkWidth, .5f)
    }

    @Test
    fun tabletPortraitKeepsSingleColumn() {
        val metrics = GuideLayout.metrics(width = 800f, windowHeight = 1280f)
        assertFalse(metrics.wide)
    }

    @Test
    fun phoneWindowKeepsSingleColumn() {
        val metrics = GuideLayout.metrics(width = 390f, windowHeight = 844f)
        assertFalse(metrics.wide)
        assertEquals(358f, GuideLayout.singleArtworkWidth(390f, 844f), .5f)
    }

    @Test
    fun tabletPortraitArtworkMatchesTheAdoptedFigure() {
        assertEquals(768f, GuideLayout.singleArtworkWidth(800f, 1280f), .5f)
    }

    @Test
    fun twoColumnsNeverShrinkTheArtwork() {
        var wide = 0
        var width = 600f
        while (width <= 1600f) {
            var height = 400f
            while (height <= 1600f) {
                val metrics = GuideLayout.metrics(width, height)
                if (metrics.wide) {
                    wide++
                    assertTrue(
                        "wide layout shrank the artwork at ${width}x${height}",
                        metrics.artworkWidth + .01f >= GuideLayout.singleArtworkWidth(width, height)
                    )
                }
                height += 20f
            }
            width += 20f
        }
        assertTrue("expected the sweep to exercise the two-column layout", wide > 0)
    }

    @Test
    fun columnsFillTheBlockWidthAndArtworkFitsTheBody() {
        val metrics = GuideLayout.metrics(width = 1000f, windowHeight = 900f)
        assertEquals(
            metrics.blockWidth,
            metrics.leftColumn + GuideLayout.COLUMN_GAP_DP + metrics.rightColumn + 2 * GuideLayout.PAGE_PADDING_DP,
            .5f
        )
        assertTrue(metrics.artworkWidth / GuideLayout.ART_RATIO <= metrics.bodyHeight + .01f)
    }
}
