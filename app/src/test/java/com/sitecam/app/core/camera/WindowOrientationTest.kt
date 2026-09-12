package com.sitecam.app.core.camera

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WindowOrientationTest {

    @Test
    fun usesOnlyAvailableWindowDimensions() {
        assertFalse(isLandscapeWindow(1080f, 1920f))
        assertTrue(isLandscapeWindow(1920f, 1080f))
    }

    @Test
    fun squareWindowUsesRightDock() {
        assertTrue(isLandscapeWindow(1000f, 1000f))
        for (density in listOf(1f, 2f, 3f, 3.5f)) {
            assertTrue(isLandscapeWindow(400f, 400f + 1f / density, density))
            assertFalse(isLandscapeWindow(400f, 400f + 1.1f / density, density))
        }
    }
}
