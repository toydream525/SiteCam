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
    val addressText: String = "",
    val userName: String = "",
    /** Built-in rows enabled by the current template. */
    val enabledSystemFields: Set<String> = BuiltInWatermarkFieldKeys.all,
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
        systemValueOverrides[key]?.takeIf { it.isNotBlank() }?.let { return it }
        return when (key) {
            BuiltInWatermarkFieldKeys.PROJECT_NAME -> projectName
            BuiltInWatermarkFieldKeys.PROJECT_CATEGORY -> categoryName
            BuiltInWatermarkFieldKeys.DATE_TIME -> "$formattedDate $formattedTime"
            BuiltInWatermarkFieldKeys.ADDRESS -> addressText.takeIf { it.isNotBlank() }
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
