package com.sitecam.app.feature.camera

import com.sitecam.app.core.location.LocationFreshness
import com.sitecam.app.core.location.SiteLocation
import kotlinx.coroutines.async
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AddressRefreshCoordinatorTest {
    private val now = 1_800_000_000_000L

    @Test
    fun freshFixIsGeocodedBeforeAnyNewLocationRequest() = runTest {
        val fix = SiteLocation(31.2, 121.5, timestamp = now - 1_000L)
        val source = FakeAddressRefreshSource(fix)
        var geocoded: SiteLocation? = null

        val result = performAddressRefresh(
            source = source,
            reverseGeocode = {
                geocoded = it
                "上海市"
            },
            nowMs = { now },
            locationTimeoutMs = 100L,
            addressTimeoutMs = 100L
        )

        assertEquals(AddressRefreshResult.Success(fix, "上海市"), result)
        assertEquals(fix, geocoded)
        assertEquals(0, source.requestCount)
        assertEquals(0, source.clearCount)
    }

    @Test
    fun staleLastKnownFixIsClearedAndOnlyFreshRequestedFixIsAccepted() = runTest {
        val stale = SiteLocation(
            31.2,
            121.5,
            timestamp = now - LocationFreshness.MAX_AGE_MS - 1L
        )
        val fresh = SiteLocation(31.21, 121.51, timestamp = now - 1_000L)
        val source = FakeAddressRefreshSource(stale)
        val geocoded = async {
            performAddressRefresh(
                source = source,
                reverseGeocode = { "新地址" },
                nowMs = { now },
                locationTimeoutMs = 1_000L,
                addressTimeoutMs = 100L
            )
        }
        runCurrent()
        source.locations.value = stale
        runCurrent()
        source.locations.value = fresh

        assertEquals(AddressRefreshResult.Success(fresh, "新地址"), geocoded.await())
        assertEquals(1, source.clearCount)
        assertEquals(1, source.requestCount)
    }

    @Test
    fun blankOrTimedOutReverseGeocodeIsAddressFailure() = runTest {
        val fix = SiteLocation(31.2, 121.5, timestamp = now - 1_000L)
        val blank = performAddressRefresh(
            source = FakeAddressRefreshSource(fix),
            reverseGeocode = { "" },
            nowMs = { now },
            addressTimeoutMs = 100L
        )
        assertEquals(
            AddressRefreshResult.Failure(AddressRefreshFailure.ADDRESS),
            blank
        )

        val timedOut = performAddressRefresh(
            source = FakeAddressRefreshSource(fix),
            reverseGeocode = {
                delay(100L)
                "never shown"
            },
            nowMs = { now },
            addressTimeoutMs = 1L
        )
        assertEquals(
            AddressRefreshResult.Failure(AddressRefreshFailure.ADDRESS),
            timedOut
        )
    }

    @Test
    fun permissionRevokedWhileReverseGeocoderIsSuspendedCannotRestoreAddress() = runTest {
        val fix = SiteLocation(31.2, 121.5, timestamp = now - 1_000L)
        val source = FakeAddressRefreshSource(fix)
        val geocoderGate = CompletableDeferred<Unit>()
        val pending = async {
            performAddressRefresh(
                source = source,
                reverseGeocode = {
                    geocoderGate.await()
                    "已撤权的地址"
                },
                nowMs = { now },
                addressTimeoutMs = 1_000L
            )
        }
        runCurrent()
        source.permission = false
        source.locations.value = null
        geocoderGate.complete(Unit)

        assertEquals(
            AddressRefreshResult.Failure(AddressRefreshFailure.PERMISSION),
            pending.await()
        )
    }

    @Test
    fun locationChangedWhileReverseGeocoderIsSuspendedCannotPairAddressWithNewFix() = runTest {
        val original = SiteLocation(31.2, 121.5, timestamp = now - 1_000L)
        val replacement = SiteLocation(31.3, 121.6, timestamp = now - 500L)
        val source = FakeAddressRefreshSource(original)
        val geocoderGate = CompletableDeferred<Unit>()
        val pending = async {
            performAddressRefresh(
                source = source,
                reverseGeocode = {
                    geocoderGate.await()
                    "旧坐标地址"
                },
                nowMs = { now },
                addressTimeoutMs = 1_000L
            )
        }
        runCurrent()
        source.locations.value = replacement
        geocoderGate.complete(Unit)

        assertEquals(
            AddressRefreshResult.Failure(AddressRefreshFailure.LOCATION),
            pending.await()
        )
    }

    @Test
    fun permissionFailureDoesNotStartLocationRequest() = runTest {
        val source = FakeAddressRefreshSource(
            initial = null,
            permission = false
        )

        val result = performAddressRefresh(
            source = source,
            reverseGeocode = { "unreachable" },
            nowMs = { now }
        )

        assertEquals(AddressRefreshResult.Failure(AddressRefreshFailure.PERMISSION), result)
        assertEquals(0, source.requestCount)
    }

    @Test
    fun automaticLookupCannotCommitDuringManualRetry() {
        assertTrue(
            !canCommitAutomaticAddress(
                manualGenerationAtStart = 4L,
                currentManualGeneration = 4L,
                manualAddressActive = true,
                locationGenerationAtStart = 8L,
                currentLocationGeneration = 8L,
                sameLocation = true
            )
        )
        assertTrue(
            !canCommitAutomaticAddress(
                manualGenerationAtStart = 3L,
                currentManualGeneration = 4L,
                manualAddressActive = false,
                locationGenerationAtStart = 8L,
                currentLocationGeneration = 8L,
                sameLocation = true
            )
        )
        assertTrue(
            canCommitAutomaticAddress(
                manualGenerationAtStart = 4L,
                currentManualGeneration = 4L,
                manualAddressActive = false,
                locationGenerationAtStart = 8L,
                currentLocationGeneration = 8L,
                sameLocation = true
            )
        )
    }

    private class FakeAddressRefreshSource(
        initial: SiteLocation?,
        var permission: Boolean = true
    ) : AddressRefreshSource {
        val locations = MutableStateFlow<SiteLocation?>(initial)
        var clearCount = 0
        var requestCount = 0

        override val currentLocation = locations
        override fun hasLocationPermission(): Boolean = permission
        override fun clearLocation() {
            clearCount += 1
            locations.value = null
        }
        override fun requestFreshLocation() {
            requestCount += 1
        }
    }
}
