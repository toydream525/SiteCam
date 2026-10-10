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
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

class ReverseGeocoder(private val context: Context) {

    private data class CacheEntry(val text: String, val resolvedAtMs: Long)

    /** Bounded LRU so a long session cannot grow the address cache without end. */
    private val cache = object : LinkedHashMap<String, CacheEntry>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CacheEntry>): Boolean =
            size > MAX_CACHE_ENTRIES
    }

    private val cacheLock = Any()

    suspend fun getAddressText(
        latitude: Double,
        longitude: Double,
        forceRefresh: Boolean = false
    ): String = withContext(Dispatchers.IO) {
        val cacheKey = String.format(Locale.US, "%.4f_%.4f", latitude, longitude)
        val now = System.currentTimeMillis()
        if (!forceRefresh) readCache(cacheKey, now)?.let { return@withContext it }

        if (!Geocoder.isPresent()) {
            return@withContext ""
        }

        val addressText = try {
            // A pre-33 system geocoder call is a synchronous Binder call that
            // can hang indefinitely; the caller must not wait for it.
            withTimeoutOrNull(GEOCODE_TIMEOUT_MS) { resolveAddress(latitude, longitude) } ?: ""
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            ""
        }
        if (addressText.isNotBlank()) writeCache(cacheKey, addressText, now)
        addressText
    }

    private suspend fun resolveAddress(latitude: Double, longitude: Double): String {
        val geocoder = Geocoder(context, Locale.getDefault())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return suspendCancellableCoroutine { continuation ->
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
        }
        // Keep the synchronous call off the coroutine's structured call path:
        // cancellation returns to the caller immediately while the bounded
        // daemon worker may finish later.
        return suspendCancellableCoroutine { continuation ->
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
    }

    private fun readCache(cacheKey: String, nowMs: Long): String? = synchronized(cacheLock) {
        val entry = cache[cacheKey] ?: return null
        if (nowMs - entry.resolvedAtMs > CACHE_TTL_MS) {
            cache.remove(cacheKey)
            return null
        }
        entry.text
    }

    private fun writeCache(cacheKey: String, text: String, nowMs: Long) {
        synchronized(cacheLock) { cache[cacheKey] = CacheEntry(text, nowMs) }
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
        const val GEOCODE_TIMEOUT_MS = 6_000L
        const val CACHE_TTL_MS = 10 * 60 * 1000L
        const val MAX_CACHE_ENTRIES = 64
        const val GEOCODER_QUEUE_CAPACITY = 8

        /**
         * The system geocoder is a single serialized service. A bounded queue
         * with a caller-runs fallback keeps bursts working instead of
         * rejecting them outright when both workers are inside a Binder call.
         */
        private val geocoderExecutor = ThreadPoolExecutor(
            2,
            2,
            30L,
            TimeUnit.SECONDS,
            LinkedBlockingQueue(GEOCODER_QUEUE_CAPACITY),
            { runnable -> Thread(runnable, "SiteCamGeocoder").apply { isDaemon = true } },
            ThreadPoolExecutor.CallerRunsPolicy()
        )
    }
}
