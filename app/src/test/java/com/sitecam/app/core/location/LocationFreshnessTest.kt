package com.sitecam.app.core.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationFreshnessTest {
    @Test
    fun staleOrMissingFixIsNeverUsedForCapture() {
        val now = 1_000_000L
        assertTrue(LocationFreshness.isFresh(SiteLocation(1.0, 2.0, timestamp = now - 1_000L), now))
        assertFalse(LocationFreshness.isFresh(SiteLocation(1.0, 2.0, timestamp = now - LocationFreshness.MAX_AGE_MS - 1), now))
        assertFalse(LocationFreshness.isFresh(null, now))
    }

    @Test
    fun aCachedFixKeepsItsOwnMeasurementTime() {
        val receivedAt = 5_000_000L
        val staleFixTime = receivedAt - 30 * 60 * 1000L
        assertEquals(staleFixTime, LocationFreshness.evidenceTimestamp(staleFixTime, receivedAt))
        // The relabelled reading would have passed the freshness gate, which is
        // exactly the evidence corruption this policy prevents.
        assertFalse(LocationFreshness.isFresh(
            SiteLocation(1.0, 2.0, timestamp = LocationFreshness.evidenceTimestamp(staleFixTime, receivedAt)),
            receivedAt
        ))
    }

    @Test
    fun aFixWithoutATimeFallsBackToTheReceiveMoment() {
        val receivedAt = 5_000_000L
        assertEquals(receivedAt, LocationFreshness.evidenceTimestamp(0L, receivedAt))
        assertEquals(receivedAt, LocationFreshness.evidenceTimestamp(-1L, receivedAt))
    }
}

