package com.sitecam.app.core.location

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume

class ReverseGeocoder(private val context: Context) {

    private val cache = ConcurrentHashMap<String, String>()

    suspend fun getAddressText(
        latitude: Double,
        longitude: Double,
        forceRefresh: Boolean = false
    ): String = withContext(Dispatchers.IO) {
        val cacheKey = String.format(Locale.US, "%.4f_%.4f", latitude, longitude)
        if (!forceRefresh) cache[cacheKey]?.let { return@withContext it }

        if (!Geocoder.isPresent()) {
            return@withContext ""
        }

        try {
            val geocoder = Geocoder(context, Locale.getDefault())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val addressText: String = suspendCancellableCoroutine { continuation ->
                    geocoder.getFromLocation(latitude, longitude, 1, object : Geocoder.GeocodeListener {
                        override fun onGeocode(addresses: MutableList<Address>) {
                            val addr = addresses.firstOrNull()
                            val formatted = formatAddress(addr)
                            continuation.resume(formatted)
                        }

                        override fun onError(errorMessage: String?) {
                            continuation.resume("")
                        }
                    })
                }
                if (addressText.isNotBlank()) {
                    cache[cacheKey] = addressText
                }
                return@withContext addressText
            } else {
                @Suppress("DEPRECATION")
                val addresses = geocoder.getFromLocation(latitude, longitude, 1)
                val addr = addresses?.firstOrNull()
                val formatted = formatAddress(addr)
                if (formatted.isNotBlank()) {
                    cache[cacheKey] = formatted
                }
                return@withContext formatted
            }
        } catch (_: Exception) {
            return@withContext ""
        }
    }

    private fun formatAddress(address: Address?): String {
        if (address == null) return ""
        val admin = address.adminArea ?: ""
        val locality = address.locality ?: ""
        val subLocality = address.subLocality ?: ""
        val thoroughfare = address.thoroughfare ?: ""
        val feature = address.featureName ?: ""

        val parts = listOf(admin, locality, subLocality, thoroughfare, feature)
            .filter { it.isNotBlank() }
            .distinct()

        return if (parts.isNotEmpty()) {
            parts.joinToString("")
        } else {
            address.getAddressLine(0) ?: ""
        }
    }
}
