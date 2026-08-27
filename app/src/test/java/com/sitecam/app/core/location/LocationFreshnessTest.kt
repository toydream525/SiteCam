package com.sitecam.app.core.location

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
}
