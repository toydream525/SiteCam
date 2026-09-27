package com.sitecam.app.core.location

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
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
                    val callbackCompleted = AtomicBoolean(false)
                    continuation.invokeOnCancellation { callbackCompleted.set(true) }
                    geocoder.getFromLocation(latitude, longitude, 1, object : Geocoder.GeocodeListener {
                        override fun onGeocode(addresses: MutableList<Address>) {
                            val formatted = runCatching {
                                formatAddress(addresses.firstOrNull())
                            }.getOrDefault("")
                            continuation.resumeOnce(callbackCompleted, formatted)
                        }

                        override fun onError(errorMessage: String?) {
                            continuation.resumeOnce(callbackCompleted, "")
                        }
                    })
                }
                if (addressText.isNotBlank()) {
                    cache[cacheKey] = addressText
                }
                return@withContext addressText
            } else {
                // The pre-33 API is synchronous and can hang in the system
                // geocoder. Keep it off the coroutine's structured call path:
                // cancellation returns to the caller immediately while the
                // bounded daemon worker may finish later.
                val formatted = suspendCancellableCoroutine { continuation ->
                    val callbackCompleted = AtomicBoolean(false)
                    val task = geocoderExecutor.submit {
                        val result = try {
                            @Suppress("DEPRECATION")
                            formatAddress(geocoder.getFromLocation(latitude, longitude, 1)?.firstOrNull())
                        } catch (_: Exception) {
                            ""
                        }
                        continuation.resumeOnce(callbackCompleted, result)
                    }
                    continuation.invokeOnCancellation {
                        callbackCompleted.set(true)
                        task.cancel(true)
                    }
                }
                if (formatted.isNotBlank()) {
                    cache[cacheKey] = formatted
                }
                return@withContext formatted
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return@withContext ""
        }
    }

    private fun <T> CancellableContinuation<T>.resumeOnce(
        callbackCompleted: AtomicBoolean,
        value: T
    ) {
        if (callbackCompleted.compareAndSet(false, true)) resume(value)
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

    private companion object {
        private val geocoderExecutor = ThreadPoolExecutor(
            0,
            2,
            30L,
            TimeUnit.SECONDS,
            SynchronousQueue(),
            { runnable -> Thread(runnable, "SiteCamGeocoder").apply { isDaemon = true } },
            ThreadPoolExecutor.AbortPolicy()
        )
    }
}
