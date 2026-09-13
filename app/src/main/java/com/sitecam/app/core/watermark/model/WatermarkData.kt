package com.sitecam.app.core.watermark.model

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class WatermarkFieldItem(
    val key: String,
    val label: String,
    val value: String,
    val isEnabled: Boolean = true
)

data class WatermarkData(
    val projectName: String = "未命名工程",
    val categoryName: String = "建筑",
    val captureTimestamp: Long = System.currentTimeMillis(),
    val latitude: Double? = null,
    val longitude: Double? = null,
    val altitude: Double? = null,
    /** FRESH, STALE, or UNAVAILABLE for location-dependent values. */
    val locationStatus: String = "FRESH",
    val addressText: String = "",
    val userName: String = "",
    /** Built-in rows enabled by the current template. */
    val enabledSystemFields: Set<String> = BuiltInWatermarkFieldKeys.defaultEnabled,
    /** Optional quick-edit overrides for built-in rows. Blank means use live project/location/time data. */
    val systemValueOverrides: Map<String, String> = emptyMap(),
    val customFields: List<WatermarkFieldItem> = emptyList(),
    val fieldLabels: Map<String, String> = emptyMap(),
    /** Null preserves the layout of snapshots captured before template ordering was supported. */
    val fieldOrder: List<String>? = null,
    val styleType: String = "CLASSIC", // CLASSIC, MINIMAL, INFO_BOARD
    val fontSizeScale: Float = 1.0f,
    val opacity: Float = 0.85f,
    val marginDp: Int = 16,
    val position: String = "BOTTOM_LEFT" // BOTTOM_LEFT, BOTTOM_RIGHT, TOP_LEFT, TOP_RIGHT
) {
    val formattedDate: String
        get() = SimpleDateFormat("yyyy.MM.dd", Locale.getDefault()).format(Date(captureTimestamp))

    val formattedTime: String
        get() = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(captureTimestamp))

    fun builtInValue(key: String): String? {
        // Elevation is a live reading from the frozen capture location. A
        // template's saved/default text must never turn an unavailable or
        // stale reading into a believable altitude; other built-in fields
        // retain their existing quick-edit override behavior.
        if (key != BuiltInWatermarkFieldKeys.ELEVATION) {
            systemValueOverrides[key]?.takeIf { it.isNotBlank() }?.let { return it }
        }
        return when (key) {
            BuiltInWatermarkFieldKeys.PROJECT_NAME -> projectName
            BuiltInWatermarkFieldKeys.PROJECT_CATEGORY -> categoryName
            BuiltInWatermarkFieldKeys.DATE_TIME -> "$formattedDate $formattedTime"
            BuiltInWatermarkFieldKeys.ADDRESS -> addressText.takeIf { it.isNotBlank() }
            BuiltInWatermarkFieldKeys.ELEVATION -> {
                val validAltitude = altitude?.takeIf { it.isFinite() }
                if (locationStatus.equals("FRESH", ignoreCase = true) && validAltitude != null) {
                    String.format(Locale.US, "%.1f m", validAltitude)
                } else {
                    "暂不可用"
                }
            }
            BuiltInWatermarkFieldKeys.GPS -> formattedGps
            BuiltInWatermarkFieldKeys.USER_NAME -> userName.takeIf { it.isNotBlank() }
            else -> null
        }
    }

    val formattedGps: String?
        get() = if (latitude != null && longitude != null) {
            String.format(Locale.US, "%.6f, %.6f", latitude, longitude)
        } else {
            null
        }
}
