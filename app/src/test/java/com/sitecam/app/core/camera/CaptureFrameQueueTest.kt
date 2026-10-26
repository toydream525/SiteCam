package com.sitecam.app.core.camera

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CaptureFrameQueueTest {
    @Test fun slowStoragePreservesEveryFrameWithAtMostThreeLiveFrames() = runTest {
        val live = mutableSetOf<Int>()
        val saved = mutableListOf<Int>()
        var captured = 0
        var peak = 0
        val count = captureAndSaveFrames(true, { captured < 12 }, {
            (++captured).also { live += it; peak = maxOf(peak, live.size) }
        }, { frame -> delay(1000); saved += frame; live -= frame }, { live -= it })
        assertEquals(12, count)
        assertEquals((1..12).toList(), saved)
        assertTrue(peak <= 3)
        assertTrue(live.isEmpty())
    }

    @Test fun stopDrainsAlreadyCapturedFrames() = runTest {
        var keepGoing = true
        var captured = 0
        val saved = mutableListOf<Int>()
        captureAndSaveFrames(true, { keepGoing }, { ++captured }, {
            delay(300); saved += it; keepGoing = false
        }, {})
        assertEquals((1..captured).toList(), saved)
        assertTrue(captured in 1..4)
    }

    @Test fun cameraFailureStillSavesFramesAlreadyInQueue() = runTest {
        var captured = 0
        val saved = mutableListOf<Int>()
        try {
            captureAndSaveFrames(true, { true }, {
                if (captured == 3) error("camera disconnected")
                ++captured
            }, { delay(500); saved += it }, {})
            fail("capture must report the camera failure")
        } catch (error: IllegalStateException) { assertEquals("camera disconnected", error.message) }
        assertEquals(listOf(1, 2, 3), saved)
    }

    @Test fun normalShutterSavesOneFrameEvenWhenContinuePredicateStaysTrue() = runTest {
        var captured = 0
        var saved = 0
        captureAndSaveFrames(false, { true }, { ++captured }, { saved++ }, {})
        assertEquals(1, captured)
        assertEquals(1, saved)
    }
}
