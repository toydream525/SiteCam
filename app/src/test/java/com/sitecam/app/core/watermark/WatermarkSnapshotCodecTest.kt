package com.sitecam.app.core.watermark

import com.sitecam.app.core.watermark.model.BuiltInWatermarkFieldKeys
import com.sitecam.app.core.watermark.model.WatermarkData
import com.sitecam.app.core.watermark.model.WatermarkFieldItem
import com.sitecam.app.core.watermark.model.WatermarkSnapshotCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WatermarkSnapshotCodecTest {

    @Test
    fun roundTripKeepsCaptureTimeLocationAndCustomFields() {
        val original = WatermarkData(
            projectName = "现场工程",
            categoryName = "桥梁",
            captureTimestamp = 1787654321000L,
            latitude = 43.123456,
            longitude = 125.654321,
            altitude = 222.5,
            addressText = "施工现场",
            userName = "ninjaaqua",
            enabledSystemFields = setOf(BuiltInWatermarkFieldKeys.PROJECT_NAME, BuiltInWatermarkFieldKeys.GPS),
            systemValueOverrides = mapOf(BuiltInWatermarkFieldKeys.PROJECT_NAME to "固定工程名"),
            customFields = listOf(WatermarkFieldItem("LOT", "标段", "二标段", true)),
            fieldLabels = mapOf("PROJECT_NAME" to "今日水印"),
            fieldOrder = listOf("LOT", "PROJECT_NAME", "GPS"),
            styleType = "INFO_BOARD",
            fontSizeScale = 1.4f,
            opacity = 0.7f,
            marginDp = 20,
            position = "TOP_RIGHT"
        )

        val decoded = WatermarkSnapshotCodec.decode(WatermarkSnapshotCodec.encode(original))

        assertNotNull(decoded)
        assertEquals(original.projectName, decoded?.projectName)
        assertEquals(original.captureTimestamp, decoded?.captureTimestamp)
        assertEquals(original.latitude, decoded?.latitude)
        assertEquals(original.longitude, decoded?.longitude)
        assertEquals(original.enabledSystemFields, decoded?.enabledSystemFields)
        assertEquals(original.customFields, decoded?.customFields)
        assertEquals(original.fieldLabels, decoded?.fieldLabels)
        assertEquals(original.fieldOrder, decoded?.fieldOrder)
        assertEquals(original.styleType, decoded?.styleType)
        assertTrue(kotlin.math.abs(original.opacity - (decoded?.opacity ?: 0f)) < 0.001f)
    }

    @Test
    fun invalidOrMissingSnapshotDoesNotCrashRetryPath() {
        assertNull(WatermarkSnapshotCodec.decode(null))
        assertNull(WatermarkSnapshotCodec.decode("not-json"))
    }

    @Test
    fun explicitEmptyBuiltInFieldArrayRemainsEmpty() {
        val json = """
            {"projectName":"工程","enabledSystemFields":[],"customFields":[]}
        """.trimIndent()

        val decoded = WatermarkSnapshotCodec.decode(json)

        assertNotNull(decoded)
        assertTrue(decoded?.enabledSystemFields?.isEmpty() == true)
        assertNull(decoded?.fieldOrder)
        assertTrue(decoded?.fieldLabels?.isEmpty() == true)
    }

    @Test
    fun legacySnapshotDoesNotGainTheNewElevationField() {
        val json = """
            {"version":1,"projectName":"旧工程","altitude":0.0,
             "enabledSystemFields":["PROJECT_NAME","ELEVATION","GPS"]}
        """.trimIndent()

        val decoded = WatermarkSnapshotCodec.decode(json)

        assertNotNull(decoded)
        assertTrue(decoded?.enabledSystemFields?.contains(BuiltInWatermarkFieldKeys.PROJECT_NAME) == true)
        assertTrue(decoded?.enabledSystemFields?.contains(BuiltInWatermarkFieldKeys.GPS) == true)
        assertTrue(decoded?.enabledSystemFields?.contains(BuiltInWatermarkFieldKeys.ELEVATION) == false)
    }

    @Test
    fun currentSnapshotKeepsZeroAndNegativeElevationWhenEnabled() {
        listOf(0.0, -4.25).forEach { altitude ->
            val original = WatermarkData(
                projectName = "现场工程",
                altitude = altitude,
                locationStatus = "FRESH",
                enabledSystemFields = setOf(BuiltInWatermarkFieldKeys.ELEVATION)
            )
            val decoded = WatermarkSnapshotCodec.decode(WatermarkSnapshotCodec.encode(original))

            assertEquals(altitude, decoded?.altitude)
            assertTrue(decoded?.enabledSystemFields?.contains(BuiltInWatermarkFieldKeys.ELEVATION) == true)
            assertEquals(String.format(java.util.Locale.US, "%.1f m", altitude), decoded?.builtInValue(BuiltInWatermarkFieldKeys.ELEVATION))
        }
    }

    @Test
    fun unavailableElevationIsExplicitAndNeverZero() {
        val data = WatermarkData(
            altitude = null,
            locationStatus = "UNAVAILABLE",
            enabledSystemFields = setOf(BuiltInWatermarkFieldKeys.ELEVATION)
        )

        assertEquals("暂不可用", data.builtInValue(BuiltInWatermarkFieldKeys.ELEVATION))
    }

    @Test
    fun elevationIgnoresSavedOverrideWhenLocationIsStaleOrNonFinite() {
        val stale = WatermarkData(
            altitude = 18.5,
            locationStatus = "STALE",
            systemValueOverrides = mapOf(BuiltInWatermarkFieldKeys.ELEVATION to "旧海拔 999.0 m"),
            enabledSystemFields = setOf(BuiltInWatermarkFieldKeys.ELEVATION)
        )
        assertEquals("暂不可用", stale.builtInValue(BuiltInWatermarkFieldKeys.ELEVATION))

        val nan = WatermarkData(
            altitude = Double.NaN,
            locationStatus = "FRESH",
            systemValueOverrides = mapOf(BuiltInWatermarkFieldKeys.ELEVATION to "旧海拔"),
            enabledSystemFields = setOf(BuiltInWatermarkFieldKeys.ELEVATION)
        )
        val infinite = nan.copy(altitude = Double.POSITIVE_INFINITY)
        assertEquals("暂不可用", nan.builtInValue(BuiltInWatermarkFieldKeys.ELEVATION))
        assertEquals("暂不可用", infinite.builtInValue(BuiltInWatermarkFieldKeys.ELEVATION))
    }

    @Test
    fun otherBuiltInOverridesRemainSupported() {
        val data = WatermarkData(
            projectName = "实时工程",
            systemValueOverrides = mapOf(BuiltInWatermarkFieldKeys.PROJECT_NAME to "固定工程"),
            enabledSystemFields = setOf(BuiltInWatermarkFieldKeys.PROJECT_NAME)
        )
        assertEquals("固定工程", data.builtInValue(BuiltInWatermarkFieldKeys.PROJECT_NAME))
    }
}
