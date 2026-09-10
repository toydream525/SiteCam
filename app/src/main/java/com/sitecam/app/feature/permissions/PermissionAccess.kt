package com.sitecam.app.feature.permissions

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.sitecam.app.feature.onboarding.OnboardingState

data class CapturePermissions(
    val camera: Boolean = false,
    val coarseLocation: Boolean = false,
    val fineLocation: Boolean = false,
    val microphone: Boolean = false,
    val cameraNeedsSettings: Boolean = false,
    val locationNeedsSettings: Boolean = false,
    val microphoneNeedsSettings: Boolean = false
) {
    val location: Boolean get() = coarseLocation || fineLocation
    val allGranted: Boolean get() = camera && location && microphone

    /** Location is requested as a pair, as required by Android 12's accuracy choice. */
    fun permissionsToRequest(): List<String> = buildList {
        if (!camera && !cameraNeedsSettings) add(Manifest.permission.CAMERA)
        if (!microphone && !microphoneNeedsSettings) add(Manifest.permission.RECORD_AUDIO)
        if (!location && !locationNeedsSettings) {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
    }
}

enum class StartupGate { LOADING, FEATURE_GUIDE, PERMISSION_GUIDE, APP }

fun resolveStartupGate(state: OnboardingState?, permissions: CapturePermissions?, keepPermissionGuideOpen: Boolean = false): StartupGate = when {
    state == null || permissions == null -> StartupGate.LOADING
    !state.hasDismissedGuide -> StartupGate.FEATURE_GUIDE
    keepPermissionGuideOpen -> StartupGate.PERMISSION_GUIDE
    state.hasHandledPermissionGuide || permissions.allGranted -> StartupGate.APP
    else -> StartupGate.PERMISSION_GUIDE
}

fun Context.permissionActivity(): Activity? {
    var candidate: Context = this
    while (candidate is ContextWrapper && candidate !is Activity) candidate = candidate.baseContext
    return candidate as? Activity
}

object PermissionAccess {
    fun read(context: Context, requested: Set<String> = emptySet()): CapturePermissions {
        fun granted(permission: String) = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        val activity = context.permissionActivity()
        fun blocked(permission: String) = !granted(permission) && permission in requested && activity != null &&
            !ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
        val coarse = granted(Manifest.permission.ACCESS_COARSE_LOCATION)
        val fine = granted(Manifest.permission.ACCESS_FINE_LOCATION)
        val locationRequested = Manifest.permission.ACCESS_COARSE_LOCATION in requested || Manifest.permission.ACCESS_FINE_LOCATION in requested
        val locationBlocked = !coarse && !fine && locationRequested && activity != null &&
            !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.ACCESS_COARSE_LOCATION) &&
            !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.ACCESS_FINE_LOCATION)
        return CapturePermissions(
            camera = granted(Manifest.permission.CAMERA), coarseLocation = coarse, fineLocation = fine,
            microphone = granted(Manifest.permission.RECORD_AUDIO), cameraNeedsSettings = blocked(Manifest.permission.CAMERA),
            locationNeedsSettings = locationBlocked, microphoneNeedsSettings = blocked(Manifest.permission.RECORD_AUDIO)
        )
    }
}
