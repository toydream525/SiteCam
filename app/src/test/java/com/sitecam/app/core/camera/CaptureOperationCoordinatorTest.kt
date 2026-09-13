package com.sitecam.app.core.camera

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class CaptureOperationCoordinatorTest {
    @Test fun captureReservationBlocksLockUntilSavingFinishes() = runTest {
        val coordinator = CaptureOperationCoordinator()
        assertTrue(coordinator.tryBegin(1) { true })
        assertFalse(coordinator.tryBegin(1) { true })
        var locked = false
        try { coordinator.withProjectIdle(1) { locked = true }; fail("Lock should be blocked") }
        catch (_: CaptureInProgressException) { }
        assertFalse(locked)
        coordinator.finish(1)
        coordinator.withProjectIdle(1) { locked = true }
        assertFalse(coordinator.tryBegin(1) { !locked })
        assertTrue(coordinator.tryBegin(2) { true })
    }

    @Test fun globalProjectSelectionIsBlockedByAnyActiveCapture() = runTest {
        val coordinator = CaptureOperationCoordinator()
        assertTrue(coordinator.tryBegin(17) { true })
        var selected = false
        try {
            coordinator.withAllProjectsIdle { selected = true }
            fail("Selection should wait for every capture to finish")
        } catch (_: CaptureInProgressException) {
            // Expected: project 17 is still reserving the global selection.
        }
        assertFalse(selected)
        coordinator.finish(17)
        coordinator.withAllProjectsIdle { selected = true }
        assertTrue(selected)
    }
    @Test fun manualOrientationIgnoresSensorAndAutoUsesIt() {
        assertEquals(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR, CaptureOrientation.AUTO.requestedOrientation)
        assertEquals(android.view.Surface.ROTATION_0, CaptureOrientation.PORTRAIT.targetRotation(270))
        assertEquals(android.view.Surface.ROTATION_90, CaptureOrientation.LANDSCAPE_LEFT.targetRotation(0))
        assertEquals(android.view.Surface.ROTATION_270, CaptureOrientation.LANDSCAPE_RIGHT.targetRotation(270))
        assertEquals(android.view.Surface.ROTATION_0, CaptureOrientation.AUTO.targetRotation(180))
    }

    @Test fun upsideDownSensorReadingRetainsTheLastAllowedDirection() {
        assertEquals(0, retainAllowedSensorDegrees(180, 0))
        assertEquals(90, retainAllowedSensorDegrees(180, 90))
        assertEquals(270, retainAllowedSensorDegrees(180, 270))
        assertEquals(0, retainAllowedSensorDegrees(181, 180))
    }
}
