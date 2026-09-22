package com.sitecam.app.core.camera

import android.content.Context
import android.os.SystemClock
import android.os.Handler
import android.os.Looper
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
    private val stabilizer = OrientationStabilizer()
    private val handler = Handler(Looper.getMainLooper())
    private var latestAngle = OrientationEventListener.ORIENTATION_UNKNOWN
    private val settleOrientation = Runnable { applySensorOrientation() }

    private fun resetPendingOrientation() {
        handler.removeCallbacks(settleOrientation)
        stabilizer.reset()
    }

    private fun applySensorOrientation() {
        handler.removeCallbacks(settleOrientation)
        if (isLocked) return
        val now = SystemClock.elapsedRealtime()
        val allowedDegrees = stabilizer.update(latestAngle, now, lastAllowedDegree)
        _orientationDegrees.value = allowedDegrees
        lastAllowedDegree = allowedDegrees
        // OrientationEventListener may stop callbacks when the angle stays exactly still.
        stabilizer.remainingDelay(now)?.let { handler.postDelayed(settleOrientation, it) }
    }

    private val orientationListener = object : OrientationEventListener(context) {
        override fun onOrientationChanged(orientation: Int) {
            if (isLocked) return
            latestAngle = orientation
            applySensorOrientation()
        }
    }

    fun startListening() {
        if (orientationListener.canDetectOrientation()) {
            orientationListener.enable()
        }
    }

    fun stopListening() {
        resetPendingOrientation()
        orientationListener.disable()
    }

    fun setLocked(locked: Boolean) {
        resetPendingOrientation()
        isLocked = locked
        if (locked) {
            lockedDegree = if (_orientationDegrees.value == 180) lastAllowedDegree else _orientationDegrees.value
            lastAllowedDegree = lockedDegree
            _orientationDegrees.value = lockedDegree
        }
    }

    /** Restore the persisted lock and the exact orientation captured with it. */
    fun restoreLockedState(locked: Boolean, degrees: Int) {
        resetPendingOrientation()
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
