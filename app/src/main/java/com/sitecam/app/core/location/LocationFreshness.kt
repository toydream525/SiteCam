package com.sitecam.app.core.location

/** Small, deterministic policy shared by capture code and unit tests. */
object LocationFreshness {
    const val MAX_AGE_MS: Long = 5 * 60 * 1000L
    private const val ALLOWED_FUTURE_SKEW_MS: Long = 30 * 1000L

    fun isFresh(location: SiteLocation?, nowMs: Long, maxAgeMs: Long = MAX_AGE_MS): Boolean {
        if (location == null || location.timestamp <= 0L) return false
        val age = nowMs - location.timestamp
        return age in -ALLOWED_FUTURE_SKEW_MS..maxAgeMs
    }
}
