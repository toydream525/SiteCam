package com.sitecam.app.feature.annotation

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertTrue
import org.junit.Test

class TextNoteGeometryTest {
    @Test
    fun bubbleIsClampedInsideImageRect() {
        val rect = Rect(20f, 30f, 320f, 420f)
        val layout = TextNoteGeometry.layout(Offset(315f, 415f), "边缘问题说明", rect)
        assertTrue(layout.bubbleRect.left >= rect.left)
        assertTrue(layout.bubbleRect.top >= rect.top)
        assertTrue(layout.bubbleRect.right <= rect.right)
        assertTrue(layout.bubbleRect.bottom <= rect.bottom)
        assertTrue(layout.baseline.x >= rect.left)
        assertTrue(layout.baseline.y <= rect.bottom)
    }

    @Test
    fun longTextIsBoundedWithEllipsis() {
        val layout = TextNoteGeometry.layout(
            position = Offset(100f, 100f),
            text = "这是一个非常长的现场问题原因和整改要求说明，必须限制在图片范围内",
            contentRect = Rect(0f, 0f, 180f, 180f)
        )
        assertTrue(layout.text.endsWith("…"))
        assertTrue(layout.bubbleRect.right <= 180f)
    }
}
