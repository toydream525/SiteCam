package com.sitecam.app.core.weather

import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import com.sitecam.app.core.location.LocationFreshness
import com.sitecam.app.core.location.SiteLocation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class SystemWeatherReading(val text: String, val city: String, val publishedAt: Long,
    val latitude: Double, val longitude: Double) {
    fun matches(location: SiteLocation?, now: Long): Boolean {
        if (location == null || !LocationFreshness.isFresh(location, now) || now - publishedAt !in 0..7_200_000L) return false
        val distance = FloatArray(1)
        Location.distanceBetween(latitude, longitude, location.latitude, location.longitude, distance)
        return distance[0] <= 25_000f
    }
}

/** Reads the documented Xiaomi system provider, only if this app is allowed access.
 * Other brands expose no common Android weather provider. No network service is substituted.
 * URI/column source: https://zhuti.designer.xiaomi.com/docs/blog/weatherApi.html
 */
class SystemWeatherReader(private val context: Context) {
    suspend fun read(): SystemWeatherReading? = withContext(Dispatchers.IO) {
        try {
            val provider = context.packageManager.resolveContentProvider("weather", 0) ?: return@withContext null
            if (!provider.exported || provider.applicationInfo.flags and
                (android.content.pm.ApplicationInfo.FLAG_SYSTEM or android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0) return@withContext null
            val permission = provider.readPermission
            if (!permission.isNullOrEmpty() && context.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) return@withContext null
            context.contentResolver.query(Uri.parse("content://weather/actualWeatherData/2"),
                arrayOf("description", "temperature", "city_name", "city_id", "publish_time"), null, null, null)?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                fun value(key: String): String = cursor.getColumnIndex(key).takeIf { it >= 0 }?.let { cursor.getString(it) }.orEmpty().trim()
                val coordinates = value("city_id").split('_')
                val latitude = coordinates.getOrNull(0)?.toDoubleOrNull() ?: return@use null
                val longitude = coordinates.getOrNull(1)?.toDoubleOrNull() ?: return@use null
                if (!latitude.isFinite() || !longitude.isFinite() || latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return@use null
                val description = value("description")
                val temperature = value("temperature")
                if (description.isBlank() || temperature.isBlank()) return@use null
                SystemWeatherReading("$description $temperature", value("city_name"),
                    value("publish_time").toLongOrNull() ?: return@use null, latitude, longitude)
            }
        } catch (_: SecurityException) { null }
        catch (_: Exception) { null }
    }
}
