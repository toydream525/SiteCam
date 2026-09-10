package com.sitecam.app.core.camera

import android.content.pm.ActivityInfo
import android.view.Surface

enum class CaptureOrientation(val label: String, val degrees: Int?, val requestedOrientation: Int) {
    PORTRAIT("竖屏", 0, ActivityInfo.SCREEN_ORIENTATION_PORTRAIT),
    LANDSCAPE_LEFT("左横屏", 270, ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE),
    LANDSCAPE_RIGHT("右横屏", 90, ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE),
    // SENSOR follows portrait and both landscape grips while excluding the
    // upside-down portrait orientation that FULL_SENSOR permits.
    AUTO("自动", null, ActivityInfo.SCREEN_ORIENTATION_SENSOR);

    fun targetRotation(sensorDegrees: Int): Int = when (degrees ?: allowedSensorDegrees(sensorDegrees)) {
        90 -> Surface.ROTATION_270
        270 -> Surface.ROTATION_90
        else -> Surface.ROTATION_0
    }
}

/**
 * Maps sensor output to the three orientations accepted by the camera UI.
 * When the phone points down (180 degrees), retaining the caller's previous
 * value is handled by [OrientationManager]; this pure fallback keeps direct
 * capture callers from ever requesting an upside-down target.
 */
fun allowedSensorDegrees(sensorDegrees: Int): Int = when (((sensorDegrees % 360) + 360) % 360) {
    90, 270 -> ((sensorDegrees % 360) + 360) % 360
    else -> 0
}

/** Keep the most recent accepted direction while the sensor reports 180°. */
fun retainAllowedSensorDegrees(sensorDegrees: Int, previousAllowedDegrees: Int): Int {
    val normalized = ((sensorDegrees % 360) + 360) % 360
    return when (normalized) {
        0, 90, 270 -> normalized
        else -> allowedSensorDegrees(previousAllowedDegrees)
    }
}
