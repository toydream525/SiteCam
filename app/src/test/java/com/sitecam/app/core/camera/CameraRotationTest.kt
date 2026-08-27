package com.sitecam.app.core.camera

import android.view.Surface
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraRotationTest {
    @Test
    fun validDisplayRotationsAreKept() {
        assertTrue(normalizeDisplayRotation(Surface.ROTATION_90) == Surface.ROTATION_90)
        assertTrue(normalizeDisplayRotation(Surface.ROTATION_270) == Surface.ROTATION_270)
        assertTrue(isQuarterTurnDisplayRotation(Surface.ROTATION_90))
        assertTrue(isQuarterTurnDisplayRotation(Surface.ROTATION_270))
    }

    @Test
    fun invalidRotationFallsBackToPortraitAndLandscapeCheckIsFalse() {
        assertTrue(normalizeDisplayRotation(-1) == Surface.ROTATION_0)
        assertFalse(isQuarterTurnDisplayRotation(Surface.ROTATION_0))
        assertFalse(isQuarterTurnDisplayRotation(Surface.ROTATION_180))
    }

    @Test
    fun physicalLandscapeDirectionsMapToOppositeCameraSurfaceRotations() {
        assertTrue(
            resolveCameraTargetRotation(
                orientationDegrees = 90,
                windowIsLandscape = true,
                displayRotation = Surface.ROTATION_90
            ) == Surface.ROTATION_270
        )
        assertTrue(
            resolveCameraTargetRotation(
                orientationDegrees = 270,
                windowIsLandscape = true,
                displayRotation = Surface.ROTATION_270
            ) == Surface.ROTATION_90
        )
    }

    @Test
    fun oppositeLandscapeDirectionsChangeRotationEvenWhenDisplayRotationIsStale() {
        val firstGrip = resolveCameraTargetRotation(
            orientationDegrees = 90,
            windowIsLandscape = true,
            displayRotation = Surface.ROTATION_90
        )
        val oppositeGrip = resolveCameraTargetRotation(
            orientationDegrees = 270,
            windowIsLandscape = true,
            displayRotation = Surface.ROTATION_90
        )

        assertTrue(firstGrip == Surface.ROTATION_270)
        assertTrue(oppositeGrip == Surface.ROTATION_90)
        assertTrue(firstGrip != oppositeGrip)
    }

    @Test
    fun missingLandscapeReadingFallsBackToDisplayRotation() {
        assertTrue(
            resolveCameraTargetRotation(
                orientationDegrees = 0,
                windowIsLandscape = true,
                displayRotation = Surface.ROTATION_270
            ) == Surface.ROTATION_270
        )
        assertTrue(
            resolveCameraTargetRotation(
                orientationDegrees = 90,
                windowIsLandscape = false,
                displayRotation = Surface.ROTATION_180
            ) == Surface.ROTATION_180
        )
    }
}
