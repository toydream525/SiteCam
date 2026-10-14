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

    @Test fun nearNominalPresetsRequestTheirDisplayedValueAcrossLenses() {
        for ((measured, expected) in listOf(1.98f to 2f, 2.47f to 2.5f, 2.96f to 3f,
            3.46f to 3.5f, 4.90f to 5f, 5.92f to 6f, 9.82f to 10f)) {
            val tele = lens("tele", measured, 12f)
            val main = lens("main", 1f, 12f)
            assertEquals(listOf(expected), NativeZoomPolicy.nativeRatios(tele))
            assertEquals(tele, NativeZoomPolicy.targetLens(listOf(main, tele), main, expected, true))
        }
    }

    @Test fun realNonstandardStopsAndUnavailableReachArePreserved() {
        for (ratio in listOf(.54f, .8f, 1f, 2.8f, 3.2f, 3.6f, 5.1f)) {
            assertEquals(ratio, NativeZoomPolicy.shortcutRatio(ratio, .5f..12f), .0001f)
        }
        assertEquals(4.9f, NativeZoomPolicy.shortcutRatio(4.9f, 1f..4.95f), .0001f)
        assertEquals(4.94f, CameraZoomMapping.referenceRatioForActiveLens(lens("tele", 4.94f)), .0001f)
    }

    @Test fun logicalMemberPresetsNormalizeWithoutInventingOtherStops() {
        val main = lens("logical", 1f).copy(isLogicalCamera=true)
        val physical = lens("tele", 4.94f).copy(parentLogicalCameraId="logical",
            minSessionZoomRatio=4.94f, maxSessionZoomRatio=8f)
        assertEquals(listOf(1f, 5f), NativeZoomPolicy.presets(listOf(main, physical)))
        assertEquals(physical, NativeZoomPolicy.targetLens(listOf(main, physical), main, 5f, true))
        assertEquals(4.94f, physical.equivalentZoomRatio!!, .0001f)
    }
}
