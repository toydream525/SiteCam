package com.sitecam.app.core.media

import org.junit.Assert.assertEquals
import org.junit.Test

class VideoOrientationTest {
    @Test
    fun portraitMetadataSwapsPresentedDimensionsAndMapsOverlayBack() {
        assertEquals(VideoDisplaySize(720, 1280), displaySizeForVideoRotation(1280, 720, 90))
        assertEquals(-90, overlayRotationToCodedPixels(90))
        assertEquals(VideoDisplaySize(720, 1280), displaySizeForVideoRotation(1280, 720, 270))
        assertEquals(90, overlayRotationToCodedPixels(270))
    }

    @Test
    fun landscapeMetadataKeepsDimensions() {
        assertEquals(VideoDisplaySize(1920, 1080), displaySizeForVideoRotation(1920, 1080, 0))
        assertEquals(VideoDisplaySize(1920, 1080), displaySizeForVideoRotation(1920, 1080, 180))
        assertEquals(180, overlayRotationToCodedPixels(180))
    }

    @Test
    fun rotationsAreNormalized() {
        assertEquals(90, normalizedVideoRotation(-270))
        assertEquals(0, normalizedVideoRotation(720))
    }

    @Test
    fun portraitDisplayBottomLeftMapsToCodedRightBottomForNinetyDegrees() {
        val coded = displayPointToCodedPixels(
            VideoPoint(16f, 1200f),
            codedWidth = 1280f,
            codedHeight = 720f,
            rotationDegrees = 90
        )
        assertEquals(1200f, coded.x)
        assertEquals(704f, coded.y)
    }
}
