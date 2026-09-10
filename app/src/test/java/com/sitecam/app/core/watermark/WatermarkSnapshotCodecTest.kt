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
}
