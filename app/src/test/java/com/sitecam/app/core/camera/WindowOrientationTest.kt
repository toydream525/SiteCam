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
    fun squareWindowIsPortraitByDefault() {
        assertFalse(isLandscapeWindow(1000f, 1000f))
    }
}
