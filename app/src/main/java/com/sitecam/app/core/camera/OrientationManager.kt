package com.sitecam.app.core.camera

import android.content.Context
import android.view.OrientationEventListener
import android.view.Surface
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class OrientationManager(context: Context) {

    private val _orientationDegrees = MutableStateFlow(0)
    val orientationDegrees: StateFlow<Int> = _orientationDegrees.asStateFlow()

    private var isLocked: Boolean = false
    private var lockedDegree: Int = 0
    private var lastAllowedDegree: Int = 0

    private val orientationListener = object : OrientationEventListener(context) {
        override fun onOrientationChanged(orientation: Int) {
            if (orientation == ORIENTATION_UNKNOWN || isLocked) return

            // Map angle to nearest 90-degree step. Keep the previous allowed
            // direction at 180 degrees so the preview never flips upside
            // down when the camera points toward the floor.
            val normalizedDegrees = when {
                orientation >= 315 || orientation < 45 -> 0
                orientation in 45..134 -> 90
                orientation in 135..224 -> 180
                orientation in 225..314 -> 270
                else -> 0
            }

            val allowedDegrees = retainAllowedSensorDegrees(normalizedDegrees, lastAllowedDegree)

            if (_orientationDegrees.value != allowedDegrees) {
                _orientationDegrees.value = allowedDegrees
            }
            lastAllowedDegree = allowedDegrees
        }
    }

    fun startListening() {
        if (orientationListener.canDetectOrientation()) {
            orientationListener.enable()
        }
    }

    fun stopListening() {
        orientationListener.disable()
    }

    fun setLocked(locked: Boolean) {
        isLocked = locked
        if (locked) {
            lockedDegree = if (_orientationDegrees.value == 180) lastAllowedDegree else _orientationDegrees.value
            lastAllowedDegree = lockedDegree
            _orientationDegrees.value = lockedDegree
        }
    }

    /** Restore the persisted lock and the exact orientation captured with it. */
    fun restoreLockedState(locked: Boolean, degrees: Int) {
        val normalized = ((degrees % 360) + 360) % 360
        lockedDegree = when (normalized) {
            90, 270 -> normalized
            180 -> lastAllowedDegree
            else -> 0
        }
        if (locked) {
            isLocked = true
            lastAllowedDegree = lockedDegree
            _orientationDegrees.value = lockedDegree
        } else {
            isLocked = false
            if (_orientationDegrees.value == 180) _orientationDegrees.value = lastAllowedDegree
        }
    }

    fun getSurfaceRotation(): Int {
        return when (if (_orientationDegrees.value == 180) lastAllowedDegree else _orientationDegrees.value) {
            90 -> Surface.ROTATION_270
            270 -> Surface.ROTATION_90
            else -> Surface.ROTATION_0
        }
    }
}
