package com.sitecam.app.core.camera

/**
 * Returns the layout direction for the space the UI can actually use.
 *
 * This deliberately does not inspect the camera sensor or the device
 * orientation sensor. Camera sensor orientation is metadata for capture
 * rotation, not a reason to swap a Compose layout on a narrow portrait
 * window.
 */
fun isLandscapeWindow(availableWidth: Float, availableHeight: Float): Boolean =
    availableWidth > availableHeight
