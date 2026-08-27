package com.sitecam.app.core.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SiteLocation(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double? = null,
    val accuracy: Float? = null,
    val timestamp: Long = System.currentTimeMillis()
)

class LocationTracker(private val context: Context) {

    private val _currentLocation = MutableStateFlow<SiteLocation?>(null)
    val currentLocation: StateFlow<SiteLocation?> = _currentLocation.asStateFlow()

    private var fusedClient: FusedLocationProviderClient? = null
    private var fusedCallback: LocationCallback? = null
    private var systemLocationManager: LocationManager? = null
    private var systemLocationListener: LocationListener? = null
    private var isUpdating: Boolean = false
    private var updateGeneration: Long = 0L

    init {
        try {
            fusedClient = LocationServices.getFusedLocationProviderClient(context)
        } catch (_: Exception) {
            fusedClient = null
        }
        systemLocationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
    }

    @SuppressLint("MissingPermission")
    fun startLocationUpdates() {
        val hasFine = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasFine && !hasCoarse) {
            clearLocation()
            return
        }
        if (isUpdating) return
        isUpdating = true
        val generation = ++updateGeneration

        // 1. Read last known location immediately
        readLastKnownLocation(generation)

        // 2. Try FusedLocationProviderClient first
        if (fusedClient != null) {
            try {
                val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000L)
                    .setMinUpdateDistanceMeters(2.0f)
                    .setMinUpdateIntervalMillis(2000L)
                    .build()

                val callback = object : LocationCallback() {
                    override fun onLocationResult(result: LocationResult) {
                        val location = result.lastLocation ?: return
                        updateLocation(location)
                    }
                }
                fusedCallback = callback
                fusedClient?.requestLocationUpdates(
                    request,
                    callback,
                    Looper.getMainLooper()
                )?.addOnFailureListener {
                    if (isUpdating && generation == updateGeneration) {
                        stopFusedUpdates()
                        startSystemLocationUpdates(generation)
                    }
                }?.addOnCanceledListener {
                    if (isUpdating && generation == updateGeneration) {
                        stopFusedUpdates()
                        startSystemLocationUpdates(generation)
                    }
                }
                return
            } catch (_: Exception) {
                // Fallback to System LocationManager
            }
        }

        // 3. Fallback: Android system LocationManager
        startSystemLocationUpdates(generation)
    }

    @SuppressLint("MissingPermission")
    private fun startSystemLocationUpdates(generation: Long) {
        if (!isUpdating || generation != updateGeneration || systemLocationListener != null) return
        try {
            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    if (generation == updateGeneration) updateLocation(location)
                }
                @Deprecated("Deprecated in Java")
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                override fun onProviderEnabled(provider: String) {}
                override fun onProviderDisabled(provider: String) {}
            }
            systemLocationListener = listener

            val providers = systemLocationManager?.getProviders(true) ?: emptyList()
            if (providers.isEmpty()) {
                systemLocationListener = null
                return
            }
            var requested = false
            for (provider in providers) {
                requested = runCatching {
                    systemLocationManager?.requestLocationUpdates(
                        provider,
                        3000L,
                        2.0f,
                        listener,
                        Looper.getMainLooper()
                    )
                    true
                }.getOrDefault(false) || requested
            }
            if (!requested) {
                systemLocationListener = null
            }
        } catch (_: Exception) {
            // Handled gracefully without crash
            systemLocationListener = null
        }
    }

    @SuppressLint("MissingPermission")
    private fun readLastKnownLocation(generation: Long) {
        try {
            fusedClient?.lastLocation?.addOnSuccessListener { location ->
                if (location != null && isUpdating && generation == updateGeneration) {
                    updateLocation(location)
                }
            }
        } catch (_: Exception) {}

        try {
            val gpsLoc = systemLocationManager?.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            val netLoc = systemLocationManager?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            val bestLoc = when {
                gpsLoc != null && netLoc != null -> if (gpsLoc.time >= netLoc.time) gpsLoc else netLoc
                gpsLoc != null -> gpsLoc
                else -> netLoc
            }
            if (bestLoc != null && _currentLocation.value == null &&
                isUpdating && generation == updateGeneration
            ) {
                updateLocation(bestLoc)
            }
        } catch (_: Exception) {}
    }

    fun stopLocationUpdates() {
        isUpdating = false
        updateGeneration++
        clearLocation()
        try {
            stopFusedUpdates()
            systemLocationListener?.let {
                systemLocationManager?.removeUpdates(it)
                systemLocationListener = null
            }
        } catch (_: Exception) {}
    }

    fun clearLocation() {
        _currentLocation.value = null
    }

    @SuppressLint("MissingPermission")
    private fun stopFusedUpdates() {
        fusedCallback?.let {
            runCatching { fusedClient?.removeLocationUpdates(it) }
            fusedCallback = null
        }
    }

    private fun updateLocation(location: Location) {
        if (!isUpdating) return
        val siteLocation = SiteLocation(
            latitude = location.latitude,
            longitude = location.longitude,
            altitude = if (location.hasAltitude()) location.altitude else null,
            accuracy = if (location.hasAccuracy()) location.accuracy else null,
            timestamp = location.time
        )
        _currentLocation.value = if (LocationFreshness.isFresh(siteLocation, System.currentTimeMillis())) {
            siteLocation
        } else {
            null
        }
    }
}
