package com.sitecam.app.core.camera

import org.junit.Assert.assertEquals
import org.junit.Test

class OrientationStabilizerTest {
    @Test fun boundaryJitterDoesNotRotate() {
        val filter = OrientationStabilizer()
        listOf(44, 46, 59, 45, 31).forEachIndexed { i, angle ->
            assertEquals(0, filter.update(angle, i * 1000L, 0))
        }
    }

    @Test fun deliberateTurnMustRemainStableAndReturnUsesSameDelay() {
        val filter = OrientationStabilizer()
        assertEquals(0, filter.update(70, 0, 0))
        assertEquals(0, filter.update(90, 649, 0))
        assertEquals(90, filter.update(95, 650, 0))
        assertEquals(90, filter.update(45, 2000, 90))
        assertEquals(90, filter.update(20, 2100, 90))
        assertEquals(0, filter.update(10, 2750, 90))
    }

    @Test fun unknownBoundaryAndUpsideDownReadingsCancelPendingTurn() {
        for (interruption in listOf(-1, 45, 180)) {
            val filter = OrientationStabilizer()
            assertEquals(0, filter.update(90, 0, 0))
            assertEquals(0, filter.update(interruption, 500, 0))
            assertEquals(0, filter.update(90, 700, 0))
            assertEquals(90, filter.update(90, 1350, 0))
        }
    }

    @Test fun stopOrLockClearsPendingTurnAndOppositeLandscapeIsSupported() {
        val filter = OrientationStabilizer()
        assertEquals(90, filter.update(270, 0, 90))
        filter.reset()
        assertEquals(90, filter.update(270, 1000, 90))
        assertEquals(270, filter.update(270, 1650, 90))
    }
}
