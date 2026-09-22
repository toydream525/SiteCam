package com.sitecam.app.core.camera

/** Require a deliberate turn held for 650 ms; ignore boundary jitter and upside-down grips. */
internal class OrientationStabilizer {
    private var pending: Int? = null
    private var pendingSince = 0L

    fun reset() {
        pending = null
    }

    fun remainingDelay(nowMillis: Long): Long? =
        pending?.let { (650L - (nowMillis - pendingSince)).coerceAtLeast(0L) }

    fun update(angle: Int, nowMillis: Long, current: Int): Int {
        val candidate = when (angle) {
            in 0..30, in 330..359 -> 0
            in 60..120 -> 90
            in 240..300 -> 270
            else -> null
        }
        if (candidate == null || candidate == current) {
            reset()
            return current
        }
        if (pending != candidate) {
            pending = candidate
            pendingSince = nowMillis
            return current
        }
        if (nowMillis - pendingSince < 650L) return current
        reset()
        return candidate
    }
}
