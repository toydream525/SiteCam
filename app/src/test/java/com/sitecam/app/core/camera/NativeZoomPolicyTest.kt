package com.sitecam.app.core.camera

import androidx.camera.core.CameraSelector
import org.junit.Assert.assertEquals
import org.junit.Test

class NativeZoomPolicyTest {
    private fun lens(id: String, ratio: Float, max: Float = 8f) = CameraLensCapability(
        cameraId = id, lensFacing = CameraSelector.LENS_FACING_BACK,
        equivalentZoomRatio = ratio, photoBindable = true, maxSessionZoomRatio = max)

    @Test fun onlyMeasuredPublicNativeStopsBecomePresets() {
        val main = lens("main", 1f)
        val tele = lens("tele", 3.2f)
        val hidden = lens("hidden", 5f).copy(appAccessible = false)
        val available = NativeZoomPolicy.usableLenses(listOf(main, tele, hidden), main)
        assertEquals(listOf(1f, 3.2f), NativeZoomPolicy.presets(available))
    }

    @Test fun continuousZoomCrossesOpticalBoundaryWithoutThrashing() {
        val main = lens("main", 1f)
        val tele = lens("tele", 3.2f)
        val lenses = listOf(main, tele)
        assertEquals(main, NativeZoomPolicy.targetLens(lenses, main, 3.22f))
        assertEquals(tele, NativeZoomPolicy.targetLens(lenses, main, 3.3f))
        assertEquals(tele, NativeZoomPolicy.targetLens(lenses, tele, 3.21f))
        assertEquals(main, NativeZoomPolicy.targetLens(lenses, tele, 3.19f))
    }

    @Test fun nativeButtonSelectsExactNativeBoundary() {
        val main = lens("main", 1f)
        val tele = lens("tele", 3.2f)
        assertEquals(tele, NativeZoomPolicy.targetLens(listOf(main, tele), main, 3.2f, true))
    }

    @Test fun logicalPhysicalMetadataDoesNotInventNativeShortcuts() {
        val main = lens("logical", 1f).copy(isLogicalCamera = true,
            minSessionZoomRatio = .54f, physicalMemberIds = setOf("wide", "main", "tele"))
        assertEquals(listOf(1f), NativeZoomPolicy.presets(listOf(main)))
        assertEquals(main, NativeZoomPolicy.targetLens(listOf(main), main, 3.2f, true))
    }

    @Test fun independentWideCameraExtendsReachBelowCurrentSession() {
        val main = lens("main", 1f)
        val wide = lens("wide", .54f)
        assertEquals(wide, NativeZoomPolicy.targetLens(listOf(main, wide), main, .6f))
        assertEquals(.54f, NativeZoomPolicy.displayRange(wide).start, .0001f)
    }

    @Test fun explicitlyBoundPhysicalMemberUsesLogicalZoomCoordinates() {
        val main = lens("logical", 1f).copy(isLogicalCamera = true)
        val physicalTele = lens("physicalTele", 3.2f).copy(parentLogicalCameraId = "logical",
            minSessionZoomRatio = 3.2f, maxSessionZoomRatio = 8f)
        assertEquals(listOf(1f, 3.2f), NativeZoomPolicy.presets(listOf(main, physicalTele)))
        assertEquals(1f, CameraZoomMapping.referenceRatioForActiveLens(physicalTele), .0001f)
        assertEquals(8f, NativeZoomPolicy.displayRange(physicalTele).endInclusive, .0001f)
        assertEquals(physicalTele, NativeZoomPolicy.targetLens(listOf(main, physicalTele), main, 3.2f, true))
    }
}
