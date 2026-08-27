package com.sitecam.app.feature.annotation

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageContentRectTest {

    @Test
    fun fitMappingIncludesLetterboxOffsets() {
        val content = ImageContentRect.forFit(
            imageWidth = 4000,
            imageHeight = 3000,
            viewWidth = 1000f,
            viewHeight = 1000f
        )

        assertEquals(0f, content.left, 0.001f)
        assertEquals(125f, content.top, 0.001f)
        assertEquals(1000f, content.width, 0.001f)
        assertEquals(0f, content.toBitmap(Offset(0f, 125f)).x, 0.001f)
        assertEquals(0f, content.toBitmap(Offset(0f, 125f)).y, 0.001f)
        assertEquals(4000f, content.toBitmap(Offset(1000f, 875f)).x, 0.001f)
        assertEquals(3000f, content.toBitmap(Offset(1000f, 875f)).y, 0.001f)
    }

    @Test
    fun contentRectKnowsInteractiveBounds() {
        val content = ImageContentRect.forFit(1920, 1080, 600f, 1000f)
        assertTrue(content.contains(Offset(300f, 500f)))
        assertTrue(!content.contains(Offset(300f, 10f)))
    }
}
