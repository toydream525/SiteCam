package com.sitecam.app.core.location

/**
 * Deterministic acceptance rules for a newly reported fix, shared by the
 * tracker and unit tests. LocationManager and FusedLocationProvider can both
 * deliver cached or coarse fixes, so age alone is not enough: a fresh
 * city-level reading must not overwrite a precise one that is still usable.
 */
object LocationAcceptance {

    /**
     * A reading with no finite coordinate, an out-of-range coordinate, or the
     * 0/0 "no fix" sentinel some chipsets emit is not evidence of anything.
     */
    fun isValidFix(latitude: Double, longitude: Double): Boolean =
        latitude.isFinite() && longitude.isFinite() &&
            latitude in -90.0..90.0 && longitude in -180.0..180.0 &&
            (latitude != 0.0 || longitude != 0.0)

    /**
     * A materially coarser fix may only take over once the better reading has
     * aged out of the freshness window. Otherwise the watermark coordinates
     * alternate between GPS and network fixes on every callback.
     */
    fun isBetter(candidate: SiteLocation, current: SiteLocation?, nowMs: Long): Boolean {
        if (current == null) return true
        if (candidate.timestamp < current.timestamp) return false
        val candidateAccuracy = candidate.accuracy ?: Float.MAX_VALUE
        val currentAccuracy = current.accuracy ?: Float.MAX_VALUE
        if (candidateAccuracy <= currentAccuracy * COARSER_ACCURACY_FACTOR) return true
        return !LocationFreshness.isFresh(current, nowMs)
    }

    /** Above this multiple the candidate counts as a materially coarser fix. */
    const val COARSER_ACCURACY_FACTOR: Float = 2f
}
