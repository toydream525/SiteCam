package com.sitecam.app.feature.camera

import com.sitecam.app.core.location.LocationFreshness
import com.sitecam.app.core.location.SiteLocation
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/** Failure reasons that can be shown without conflating location and address services. */
enum class AddressRefreshFailure {
    PERMISSION,
    LOCATION,
    ADDRESS
}

sealed interface AddressRefreshResult {
    data class Success(val location: SiteLocation, val address: String) : AddressRefreshResult
    data class Failure(val reason: AddressRefreshFailure) : AddressRefreshResult
}

internal fun sameAddressLocation(first: SiteLocation?, second: SiteLocation): Boolean =
    first != null &&
        first.timestamp == second.timestamp &&
        first.latitude == second.latitude &&
        first.longitude == second.longitude

/**
 * The small source boundary makes the retry order explicit and testable:
 * use a fresh fix immediately, otherwise request a new fix, then geocode it.
 */
interface AddressRefreshSource {
    val currentLocation: StateFlow<SiteLocation?>

    fun hasLocationPermission(): Boolean

    fun clearLocation()

    fun requestFreshLocation()
}

/**
 * Resolve one manual address retry.  A stale last-known value is never
 * accepted as the result of the new location request, and a fresh existing
 * value is deliberately reverse-geocoded before starting another request.
 */
suspend fun performAddressRefresh(
    source: AddressRefreshSource,
    reverseGeocode: suspend (SiteLocation) -> String,
    nowMs: () -> Long = { System.currentTimeMillis() },
    locationTimeoutMs: Long = 8_000L,
    addressTimeoutMs: Long = 8_000L
): AddressRefreshResult {
    if (!source.hasLocationPermission()) {
        return AddressRefreshResult.Failure(AddressRefreshFailure.PERMISSION)
    }

    val existing = source.currentLocation.value?.takeIf {
        LocationFreshness.isFresh(it, nowMs())
    }
    val location = existing ?: run {
        source.clearLocation()
        source.requestFreshLocation()
        withTimeoutOrNull(locationTimeoutMs) {
            source.currentLocation
                .filterNotNull()
                .first { LocationFreshness.isFresh(it, nowMs()) }
        }
    }

    if (!source.hasLocationPermission()) {
        return AddressRefreshResult.Failure(AddressRefreshFailure.PERMISSION)
    }
    if (location == null || !LocationFreshness.isFresh(location, nowMs())) {
        return AddressRefreshResult.Failure(AddressRefreshFailure.LOCATION)
    }

    val address = withTimeoutOrNull(addressTimeoutMs) {
        reverseGeocode(location)
    } ?: return AddressRefreshResult.Failure(AddressRefreshFailure.ADDRESS)

    // The fix can be cleared, replaced, or lose permission while the network
    // geocoder is suspended. Never pair the returned address with that newer
    // (or absent) fix, and never restore an address after permission revocation.
    if (!source.hasLocationPermission()) {
        return AddressRefreshResult.Failure(AddressRefreshFailure.PERMISSION)
    }
    if (!sameAddressLocation(source.currentLocation.value, location) ||
        !LocationFreshness.isFresh(source.currentLocation.value, nowMs())
    ) {
        return AddressRefreshResult.Failure(AddressRefreshFailure.LOCATION)
    }
    return if (address.isBlank()) {
        AddressRefreshResult.Failure(AddressRefreshFailure.ADDRESS)
    } else {
        AddressRefreshResult.Success(location, address)
    }
}

/**
 * Automatic reverse-geocoding may commit only while no manual retry owns the
 * request.  This guard covers the race where an automatic lookup started just
 * before the user tapped retry and finished during that manual operation.
 */
internal fun canCommitAutomaticAddress(
    manualGenerationAtStart: Long,
    currentManualGeneration: Long,
    manualAddressActive: Boolean,
    locationGenerationAtStart: Long,
    currentLocationGeneration: Long,
    sameLocation: Boolean
): Boolean = !manualAddressActive &&
    manualGenerationAtStart == currentManualGeneration &&
    locationGenerationAtStart == currentLocationGeneration &&
    sameLocation
