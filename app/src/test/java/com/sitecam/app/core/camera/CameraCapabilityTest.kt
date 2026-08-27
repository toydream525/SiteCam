package com.sitecam.app.core.camera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraCapabilityTest {

    @Test
    fun testDefaultCameraCapability() {
        val capability = CameraCapability()
        assertEquals(1.0f, capability.minZoomRatio, 0.001f)
        assertEquals(5.0f, capability.maxZoomRatio, 0.001f)
        assertTrue(capability.zoomPillPresets.isNotEmpty())
    }

    @Test
    fun testCameraCapabilityPillPresets() {
        val capability = CameraCapability(
            minZoomRatio = 0.5f,
            maxZoomRatio = 10.0f,
            zoomPillPresets = listOf(0.5f, 1.0f, 2.0f, 5.0f, 10.0f)
        )
        assertEquals(5, capability.zoomPillPresets.size)
        assertTrue(capability.zoomPillPresets.contains(0.5f))
        assertTrue(capability.zoomPillPresets.contains(1.0f))
    }
}
