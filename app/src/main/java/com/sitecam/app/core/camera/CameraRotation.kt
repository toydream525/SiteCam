package com.sitecam.app.core.camera

import android.view.Surface

/*
 * Current use of this file:
 *  - [resolveCameraTargetRotation] below is the production entry point used by the camera screen.
 *  - The pure helpers `normalizeDisplayRotation`, `safeCameraTargetRotation` and
 *    `isQuarterTurnDisplayRotation` are referenced only by `CameraRotationTest` today; they are kept
 *    so the test keeps pinning the accepted rotation set.
 */

/** Keeps CameraX use cases on the current physical display rotation. */
fun normalizeDisplayRotation(rotation: Int): Int = when (rotation) {
    Surface.ROTATION_0,
    Surface.ROTATION_90,
    Surface.ROTATION_180,
    Surface.ROTATION_270 -> rotation
    else -> Surface.ROTATION_0
}

/** CameraX target rotations accepted by SiteCam (portrait + two landscapes). */
fun safeCameraTargetRotation(rotation: Int): Int = when (normalizeDisplayRotation(rotation)) {
    Surface.ROTATION_180 -> Surface.ROTATION_0
    else -> normalizeDisplayRotation(rotation)
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
    // Android SENSOR can keep a portrait window at ROTATION_0 while the
    // physical device reports the excluded 180-degree grip. Never propagate
    // ROTATION_180 into preview, still capture, or video targets.
    val fallbackRotation = safeCameraTargetRotation(displayRotation)
    if (!windowIsLandscape) return fallbackRotation

    return when (orientationDegrees) {
        90 -> Surface.ROTATION_270
        270 -> Surface.ROTATION_90
        else -> fallbackRotation
    }
}
