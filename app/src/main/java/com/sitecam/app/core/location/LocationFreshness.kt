package com.sitecam.app.core.location

/** Small, deterministic policy shared by capture code and unit tests. */
object LocationFreshness {
    const val MAX_AGE_MS: Long = 5 * 60 * 1000L
    private const val ALLOWED_FUTURE_SKEW_MS: Long = 30 * 1000L

    /**
     * A fix keeps the moment the platform actually measured it. Only a fix
     * that carries no usable time at all falls back to the moment it was
     * received. Stamping a cached fix with the current clock would let it pass
     * [isFresh] and be recorded as a current reading in the watermark evidence.
     */
    fun evidenceTimestamp(fixTimeMs: Long, receivedAtMs: Long): Long =
        if (fixTimeMs > 0L) fixTimeMs else receivedAtMs

    fun isFresh(location: SiteLocation?, nowMs: Long, maxAgeMs: Long = MAX_AGE_MS): Boolean {
        if (location == null || location.timestamp <= 0L) return false
        val age = nowMs - location.timestamp
        return age in -ALLOWED_FUTURE_SKEW_MS..maxAgeMs
    }
}
