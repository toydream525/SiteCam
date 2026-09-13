package com.sitecam.app.feature.camera

import android.graphics.RectF
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AddressRefreshPlacementTest {
    @Test
    fun choosesAnUncoveredPreviewCorner() {
        val corner = chooseAddressRefreshCorner(
            canvasWidth = 1080f,
            canvasHeight = 1440f,
            chipWidth = 360f,
            chipHeight = 96f,
            watermarkRect = RectF(24f, 1180f, 720f, 1416f)
        )

        assertEquals(AddressRefreshCorner.TOP_END, corner)
        val bounds = addressRefreshRectForCorner(
            corner,
            canvasWidth = 1080f,
            canvasHeight = 1440f,
            chipWidth = 360f,
            chipHeight = 96f
        )
        assertNotNull(bounds)
        assertFalse(RectF.intersects(bounds ?: error("missing selected bounds"), RectF(24f, 1180f, 720f, 1416f)))
    }

    @Test
    fun fallsBackOutsidePreviewWhenNoInFramePositionIsSafe() {
        val corner = chooseAddressRefreshCorner(
            canvasWidth = 500f,
            canvasHeight = 500f,
            chipWidth = 180f,
            chipHeight = 60f,
            watermarkRect = RectF(0f, 0f, 500f, 500f)
        )

        assertEquals(AddressRefreshCorner.OUTSIDE_PREVIEW, corner)
    }

    @Test
    fun coverFallbackSlotUsesOnlyARealFree48DpGap() {
        for (height in listOf(96f, 180f)) {
            val slot = smallCoverAddressFallbackSlot(height)
            assertEquals(null, slot)
        }
        val slot = smallCoverAddressFallbackSlot(320f)
        assertNotNull(slot)
        val controlsTop = (320f - 156f) / 2f
        val stackBottom = controlsTop + 156f
        assertTrue("fallback must clear the gallery stack", slot!!.bottom <= controlsTop || slot.top >= stackBottom)
        assertTrue("fallback keeps the 48dp touch target", slot.width() >= 48f)
        assertTrue("fallback keeps the 48dp touch target", slot.height() >= 48f)
    }

    @Test
    fun coverFallbackSlotCanUseAFreeBottomGapWhenTopIsTooShort() {
        val slot = smallCoverAddressFallbackSlot(260f)
        assertNotNull(slot)
        val controlsTop = (260f - 156f) / 2f
        assertTrue(slot!!.top >= controlsTop + 156f || slot.bottom <= controlsTop)
    }
}
