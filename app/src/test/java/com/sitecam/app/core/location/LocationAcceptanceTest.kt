package com.sitecam.app.core.location

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationAcceptanceTest {

    private fun fix(
        latitude: Double = 31.23,
        longitude: Double = 121.47,
        accuracy: Float? = 8f,
        timestamp: Long = 1_000_000L,
        altitude: Double? = null
    ) = SiteLocation(latitude, longitude, altitude, accuracy, timestamp)

    @Test
    fun nonFiniteAndOutOfRangeCoordinatesAreNotEvidence() {
        val now = 1_000_000L
        assertFalse(LocationAcceptance.isValidFix(Double.NaN, 121.47))
        assertFalse(LocationAcceptance.isValidFix(31.23, Double.POSITIVE_INFINITY))
        assertFalse(LocationAcceptance.isValidFix(91.0, 121.47))
        assertFalse(LocationAcceptance.isValidFix(31.23, 181.0))
        assertFalse(LocationAcceptance.isValidFix(0.0, 0.0))
        assertTrue(LocationAcceptance.isValidFix(31.23, 121.47))
        assertTrue(LocationAcceptance.isValidFix(-89.9, -179.9))
    }

    @Test
    fun aCoarseFixNeverReplacesAPreciseOneThatIsStillFresh() {
        val now = 1_000_000L
        val precise = fix(accuracy = 10f, timestamp = now - 1_000L)
        val coarse = fix(accuracy = 1_500f, timestamp = now - 1_000L)
        assertFalse(LocationAcceptance.isBetter(coarse, precise, now))
        assertTrue(LocationAcceptance.isBetter(precise, coarse, now))
    }

    @Test
    fun aCoarseFixTakesOverOnceThePreciseOneHasAgedOut() {
        val now = 1_000_000L
        val agedPrecise = fix(accuracy = 10f, timestamp = now - LocationFreshness.MAX_AGE_MS - 1)
        val coarse = fix(accuracy = 1_500f, timestamp = now - 1_000L)
        assertTrue(LocationAcceptance.isBetter(coarse, agedPrecise, now))
    }

    @Test
    fun anOlderFixNeverReplacesANewerOne() {
        val now = 1_000_000L
        val newer = fix(accuracy = 40f, timestamp = now - 1_000L)
        val older = fix(accuracy = 5f, timestamp = now - 3_000L)
        assertFalse(LocationAcceptance.isBetter(older, newer, now))
    }

    @Test
    fun theFirstFixIsAlwaysAccepted() {
        assertTrue(LocationAcceptance.isBetter(fix(), null, 1_000_000L))
    }
}
