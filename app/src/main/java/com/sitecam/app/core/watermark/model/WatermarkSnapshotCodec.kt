package com.sitecam.app.core.watermark.model

import org.json.JSONArray
import org.json.JSONObject

/**
 * Stable, self-contained capture metadata used when a video needs a later
 * watermark retry or is exported to another device. Unknown fields are
 * ignored so snapshots made by an older build remain readable.
 */
object WatermarkSnapshotCodec {
    // Version 1 snapshots predate the opt-in elevation row. Keep the version
    // boundary explicit so a legacy snapshot can never gain that row merely
    // because the current build knows about it.
    private const val VERSION = 2

    fun encode(data: WatermarkData): String = JSONObject().apply {
        put("version", VERSION)
        put("projectName", data.projectName)
        put("categoryName", data.categoryName)
        put("captureTimestamp", data.captureTimestamp)
        putNullable("latitude", data.latitude)
        putNullable("longitude", data.longitude)
        putNullable("altitude", data.altitude)
        put("locationStatus", data.locationStatus)
        put("addressText", data.addressText)
        put("userName", data.userName)
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
        put("fontSizeScale", data.fontSizeScale.toDouble())
        put("opacity", data.opacity.toDouble())
        put("marginDp", data.marginDp)
        put("position", data.position)
    }.toString()

    fun decode(json: String?): WatermarkData? {
        if (json.isNullOrBlank()) return null
        return runCatching {
            val root = JSONObject(json)
            val snapshotVersion = root.optInt("version", 0)
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
                    if (snapshotVersion < VERSION) remove(BuiltInWatermarkFieldKeys.ELEVATION)
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
                latitude = root.optNullableDouble("latitude"),
                longitude = root.optNullableDouble("longitude"),
                altitude = root.optNullableDouble("altitude"),
                locationStatus = root.optString(
                    "locationStatus",
                    if (root.optNullableDouble("altitude") != null) "FRESH" else "UNAVAILABLE"
                ),
                addressText = root.optString("addressText"),
                userName = root.optString("userName"),
                enabledSystemFields = enabled,
                systemValueOverrides = overrides,
                customFields = customFields,
                fieldLabels = labels,
                fieldOrder = order,
                styleType = root.optString("styleType", "CLASSIC"),
                fontSizeScale = root.optDouble("fontSizeScale", 1.0).toFloat(),
                opacity = root.optDouble("opacity", 0.85).toFloat(),
                marginDp = root.optInt("marginDp", 16),
                position = root.optString("position", "BOTTOM_LEFT")
            )
        }.getOrNull()
    }

    private fun JSONObject.putNullable(key: String, value: Double?) {
        put(key, value?.takeIf { it.isFinite() } ?: JSONObject.NULL)
    }

    private fun JSONObject.optNullableDouble(key: String): Double? =
        if (isNull(key)) null else optDouble(key).takeUnless { !it.isFinite() }
}
