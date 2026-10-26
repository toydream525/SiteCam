package com.sitecam.app.core.watermark.model

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

/**
 * Stable, self-contained capture metadata used when a video needs a later
 * watermark retry or is exported to another device. Unknown fields are
 * ignored so snapshots made by an older build remain readable.
 */
object WatermarkSnapshotCodec {
    private const val TAG = "WatermarkSnapshot"
    // Version 1 snapshots predate the opt-in elevation row. Keep the version
    // boundary explicit so a legacy snapshot can never gain that row merely
    // because the current build knows about it.
    private const val VERSION = 4
    private const val ELEVATION_VERSION = 2
    // Values outside these bounds cannot come from the editor and make the
    // stamp unreadable or blank, so a damaged snapshot is pulled back in.
    private const val MARGIN_DP_MIN = 0
    private const val MARGIN_DP_MAX = 200
    private const val DEFAULT_FONT_SIZE_SCALE = 1.0
    private const val DEFAULT_OPACITY = 0.85

    fun encode(data: WatermarkData): String = JSONObject().apply {
        put("version", VERSION)
        put("projectName", data.projectName)
        put("categoryName", data.categoryName)
        put("captureTimestamp", data.captureTimestamp)
        data.captureTimeZoneId?.let { put("captureTimeZoneId", it) }
        data.saveToSystemGallery?.let { put("saveToSystemGallery", it) }
        putNullable("latitude", data.latitude)
        putNullable("longitude", data.longitude)
        putNullable("altitude", data.altitude)
        put("locationStatus", data.locationStatus)
        put("addressText", data.addressText)
        put("userName", data.userName)
        put("weatherText", data.weatherText)
        put("enabledSystemFields", JSONArray(data.enabledSystemFields.toList()))
        put("systemValueOverrides", JSONObject(data.systemValueOverrides))
        put("customFields", JSONArray().apply {
            data.customFields.forEach { field ->
                put(JSONObject().apply {
                    put("key", field.key)
                    put("label", field.label)
                    put("value", field.value)
                    put("isEnabled", field.isEnabled)
                })
            }
        })
        put("fieldLabels", JSONObject(data.fieldLabels))
        data.fieldOrder?.let { put("fieldOrder", JSONArray(it)) }
        put("styleType", data.styleType)
        // org.json rejects NaN/Infinity, and a failed encode after the photo
        // reached MediaStore would leave an orphan file, so fall back here.
        putFinite("fontSizeScale", data.fontSizeScale.toDouble(), DEFAULT_FONT_SIZE_SCALE)
        data.widthRatio?.let { putFinite("widthRatio", it.toDouble(), 0.6) }
        putFinite("opacity", data.opacity.toDouble(), DEFAULT_OPACITY)
        put("marginDp", data.marginDp)
        put("position", data.position)
    }.toString()

    fun decode(json: String?): WatermarkData? {
        if (json.isNullOrBlank()) return null
        return runCatching {
            val root = JSONObject(json)
            val snapshotVersion = root.optInt("version", 0)
            // A newer writer may store settings this build cannot interpret.
            if (snapshotVersion > VERSION) {
                Log.w(TAG, "Ignoring watermark snapshot version $snapshotVersion, supported $VERSION")
                return@runCatching null
            }
            val enabledArray = root.optJSONArray("enabledSystemFields")
            // A missing field is an old snapshot and should use the historical
            // default. An explicitly present empty array means the user
            // disabled every built-in field and must remain empty.
            val enabled = if (enabledArray == null) {
                // Snapshots from before the opt-in elevation row existed keep
                // the historical built-in defaults.
                BuiltInWatermarkFieldKeys.defaultEnabled
            } else {
                buildSet {
                    val array = enabledArray
                    for (index in 0 until array.length()) add(array.optString(index))
                    if (snapshotVersion < ELEVATION_VERSION) remove(BuiltInWatermarkFieldKeys.ELEVATION)
                }
            }
            val overrides = buildMap {
                root.optJSONObject("systemValueOverrides")?.let { objectValue ->
                    val keys = objectValue.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        put(key, objectValue.optString(key))
                    }
                }
            }
            val customFields = buildList {
                root.optJSONArray("customFields")?.let { array ->
                    for (index in 0 until array.length()) {
                        val item = array.optJSONObject(index) ?: continue
                        add(
                            WatermarkFieldItem(
                                key = item.optString("key", "CUSTOM_$index"),
                                label = item.optString("label", "自定义字段"),
                                value = item.optString("value"),
                                isEnabled = item.optBoolean("isEnabled", true)
                            )
                        )
                    }
                }
            }
            val labels = buildMap {
                root.optJSONObject("fieldLabels")?.let { labels ->
                    val keys = labels.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        put(key, labels.optString(key))
                    }
                }
            }
            val order = root.optJSONArray("fieldOrder")?.let { array ->
                List(array.length()) { array.optString(it) }
            }
            WatermarkData(
                projectName = root.optString("projectName", "未命名工程"),
                categoryName = root.optString("categoryName", "建筑"),
                captureTimestamp = root.optLong("captureTimestamp", System.currentTimeMillis()),
                captureTimeZoneId = root.optString("captureTimeZoneId").takeIf { it.isNotBlank() },
                saveToSystemGallery = if (root.has("saveToSystemGallery")) root.optBoolean("saveToSystemGallery") else null,
                latitude = root.optNullableDouble("latitude"),
                longitude = root.optNullableDouble("longitude"),
                altitude = root.optNullableDouble("altitude"),
                locationStatus = root.optString(
                    "locationStatus",
                    if (root.optNullableDouble("altitude") != null) "FRESH" else "UNAVAILABLE"
                ),
                addressText = root.optString("addressText"),
                userName = root.optString("userName"),
                weatherText = root.optString("weatherText"),
                enabledSystemFields = enabled,
                systemValueOverrides = overrides,
                customFields = customFields,
                fieldLabels = labels,
                fieldOrder = order,
                styleType = root.optString("styleType", "CLASSIC"),
                fontSizeScale = root.optDouble("fontSizeScale", DEFAULT_FONT_SIZE_SCALE).toFloat()
                    .takeIf { it.isFinite() } ?: DEFAULT_FONT_SIZE_SCALE.toFloat(),
                widthRatio = if (root.has("widthRatio") && !root.isNull("widthRatio")) {
                    root.optDouble("widthRatio", 0.6).toFloat().let { if (it.isFinite()) it.coerceIn(.3f, .9f) else .6f }
                } else null,
                opacity = root.optDouble("opacity", DEFAULT_OPACITY).toFloat()
                    .takeIf { it.isFinite() }?.coerceIn(0f,1f) ?: DEFAULT_OPACITY.toFloat(),
                marginDp = root.optInt("marginDp", 16).coerceIn(MARGIN_DP_MIN, MARGIN_DP_MAX),
                position = root.optString("position", "BOTTOM_LEFT")
            )
        }.getOrElse { error ->
            // Keep the historical null result, but stop hiding the cause: a
            // damaged snapshot silently degrades to the default watermark.
            Log.w(TAG, "Unable to read watermark snapshot", error)
            null
        }
    }

    private fun JSONObject.putNullable(key: String, value: Double?) {
        put(key, value?.takeIf { it.isFinite() } ?: JSONObject.NULL)
    }
    private fun JSONObject.putFinite(key: String, value: Double, fallback: Double) {
        put(key, value.takeIf { it.isFinite() } ?: fallback)
    }

    private fun JSONObject.optNullableDouble(key: String): Double? =
        if (isNull(key)) null else optDouble(key).takeUnless { !it.isFinite() }

    /** Why a stored snapshot could not be reused, for callers that can tell the user. */
    sealed interface DecodeResult {
        data class Success(val data: WatermarkData) : DecodeResult

        /** No snapshot was stored: the media predates snapshots or never had one. */
        data object Missing : DecodeResult

        /** The snapshot is damaged, or was written by a newer build. */
        data class Unavailable(val reason: String) : DecodeResult
    }

    /**
     * Same decoding as [decode], but the caller can distinguish an absent
     * snapshot from one that is unusable — a retried video watermark silently
     * falls back to the default template otherwise.
     */
    fun decodeResult(json: String?): DecodeResult {
        if (json.isNullOrBlank()) return DecodeResult.Missing
        decode(json)?.let { return DecodeResult.Success(it) }
        return DecodeResult.Unavailable(unavailableReason(json))
    }

    private fun unavailableReason(json: String): String = runCatching {
        val version = JSONObject(json).optInt("version", 0)
        if (version > VERSION) "unsupported-version-$version" else "unreadable"
    }.getOrDefault("unreadable")
}
