package com.sitecam.app.core.camera

import android.view.Surface

/** Keeps CameraX use cases on the current physical display rotation. */
fun normalizeDisplayRotation(rotation: Int): Int = when (rotation) {
    Surface.ROTATION_0,
    Surface.ROTATION_90,
    Surface.ROTATION_180,
    Surface.ROTATION_270 -> rotation
    else -> Surface.ROTATION_0
}

fun isQuarterTurnDisplayRotation(rotation: Int): Boolean =
    normalizeDisplayRotation(rotation) == Surface.ROTATION_90 ||
        normalizeDisplayRotation(rotation) == Surface.ROTATION_270

/**
 * Resolves the CameraX rotation for the currently usable window.
 *
 * A few device builds keep Display.rotation unchanged when the physical
 * handset flips between the two landscape grips. OrientationManager already
 * exposes that physical direction as 90/270 degrees, whose CameraX surface
 * rotations are 270/90 respectively. Only use that sensor signal for a
 * landscape window; before a valid landscape reading arrives, retain the
 * display rotation supplied by the window.
 */
fun resolveCameraTargetRotation(
    orientationDegrees: Int,
    windowIsLandscape: Boolean,
    displayRotation: Int
): Int {
    val fallbackRotation = normalizeDisplayRotation(displayRotation)
    if (!windowIsLandscape) return fallbackRotation

    return when (orientationDegrees) {
        90 -> Surface.ROTATION_270
        270 -> Surface.ROTATION_90
        else -> fallbackRotation
    }
}
