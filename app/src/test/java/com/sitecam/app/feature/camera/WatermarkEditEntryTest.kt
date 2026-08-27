package com.sitecam.app.feature.camera

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WatermarkEditEntryTest {
    @Test
    fun hitTestingIsLimitedToExplicitEditEntry() {
        val entry = watermarkEditEntryRect(1080f, 1920f)
        assertTrue(isWatermarkEditEntryHit(Offset(entry.left + 1f, entry.top + 1f), entry))
        assertFalse(isWatermarkEditEntryHit(Offset(540f, 960f), entry))
    }
}
