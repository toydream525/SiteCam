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

    private val orientationListener = object : OrientationEventListener(context) {
        override fun onOrientationChanged(orientation: Int) {
            if (orientation == ORIENTATION_UNKNOWN || isLocked) return

            // Map angle to nearest 90-degree step: 0, 90, 180, 270
            val normalizedDegrees = when {
                orientation >= 315 || orientation < 45 -> 0
                orientation in 45..134 -> 90
                orientation in 135..224 -> 180
                orientation in 225..314 -> 270
                else -> 0
            }

            if (_orientationDegrees.value != normalizedDegrees) {
                _orientationDegrees.value = normalizedDegrees
            }
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
            lockedDegree = _orientationDegrees.value
        }
    }

    /** Restore the persisted lock and the exact orientation captured with it. */
    fun restoreLockedState(locked: Boolean, degrees: Int) {
        val normalized = ((degrees % 360) + 360) % 360
        lockedDegree = when (normalized) {
            90, 180, 270 -> normalized
            else -> 0
        }
        if (locked) {
            isLocked = true
            _orientationDegrees.value = lockedDegree
        } else {
            isLocked = false
        }
    }

    fun getSurfaceRotation(): Int {
        return when (_orientationDegrees.value) {
            90 -> Surface.ROTATION_270
            180 -> Surface.ROTATION_180
            270 -> Surface.ROTATION_90
            else -> Surface.ROTATION_0
        }
    }
}
