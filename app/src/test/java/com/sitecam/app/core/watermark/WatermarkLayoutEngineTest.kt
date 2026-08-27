package com.sitecam.app.core.watermark

import com.sitecam.app.core.watermark.engine.WatermarkLayoutEngine
import com.sitecam.app.core.watermark.model.WatermarkData
import com.sitecam.app.core.watermark.model.WatermarkFieldItem
import com.sitecam.app.core.watermark.model.BuiltInWatermarkFieldKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import android.graphics.Paint

@RunWith(RobolectricTestRunner::class)
class WatermarkLayoutEngineTest {

    @Test
    fun testClassicLayoutCalculations() {
        val data = WatermarkData(
            projectName = "长春配电改造项目",
            categoryName = "电力工程",
            captureTimestamp = 1787654321000L,
            latitude = 43.886842,
            longitude = 125.324501,
            addressText = "吉林省长春市朝阳区人民大街",
            customFields = listOf(
                WatermarkFieldItem("BUILDER", "施工单位", "省电力建设总公司", true)
            ),
            styleType = "CLASSIC"
        )

        val result = WatermarkLayoutEngine.calculateLayout(
            canvasWidth = 1080f,
            canvasHeight = 1920f,
            data = data
        )

        assertNotNull(result)
        assertTrue("Card rect height should be positive", result.cardRect.height() > 0)
        assertTrue("Card rect width should be positive", result.cardRect.width() > 0)
        assertNotNull("Accent bar should exist for classic style", result.accentBarRect)
        assertTrue("Should have multiple lines", result.lines.size >= 5)

        // Verify first line is project name
        val firstLine = result.lines[0]
        assertEquals("工程名称: ", firstLine.label)
        assertEquals("长春配电改造项目", firstLine.value)
        assertTrue("First line should be highlighted", firstLine.isHighlight)
    }

    @Test
    fun testLongAddressTextWrapping() {
        val data = WatermarkData(
            projectName = "长春市高新技术产业园区电网扩建工程",
            categoryName = "电力工程",
            addressText = "吉林省长春市高新技术产业开发区超群街与硅谷大街交汇处长春光机所公寓A区三号楼工地",
            styleType = "CLASSIC"
        )

        val result = WatermarkLayoutEngine.calculateLayout(
            canvasWidth = 1080f,
            canvasHeight = 1920f,
            data = data
        )

        assertNotNull(result)
        // With long address text, total lines should increase due to wrapping
        assertTrue("Should wrap long lines", result.lines.size >= 4)
    }

    @Test
    fun testLandscapeLayoutCalculations() {
        val data = WatermarkData(
            projectName = "横屏模式工程留档",
            categoryName = "市政",
            addressText = "现场施工一号区",
            styleType = "CLASSIC"
        )

        val result = WatermarkLayoutEngine.calculateLayout(
            canvasWidth = 1920f,
            canvasHeight = 1080f,
            data = data
        )

        assertNotNull(result)
        assertTrue("Card should fit within canvas height", result.cardRect.bottom <= 1080f)
        assertTrue("Card should fit within canvas width", result.cardRect.right <= 1920f)
    }

    @Test
    fun testMinimalLayoutCalculations() {
        val data = WatermarkData(
            projectName = "市政道路排水改造",
            categoryName = "市政",
            addressText = "长春市高新区",
            styleType = "MINIMAL"
        )

        val result = WatermarkLayoutEngine.calculateLayout(
            canvasWidth = 1080f,
            canvasHeight = 1920f,
            data = data
        )

        assertNotNull(result)
        assertTrue(result.lines.isNotEmpty())
        assertEquals(null, result.accentBarRect)
    }

    @Test
    fun testInfoBoardLayoutCalculations() {
        val data = WatermarkData(
            projectName = "高铁桥梁二标段",
            categoryName = "桥梁",
            addressText = "104国道跨线桥",
            styleType = "INFO_BOARD"
        )

        val result = WatermarkLayoutEngine.calculateLayout(
            canvasWidth = 2160f,
            canvasHeight = 3840f,
            data = data
        )

        assertNotNull(result)
        assertNotNull("Header rect should exist for InfoBoard", result.headerRect)
        assertTrue("Lines should contain header item", result.lines.any { it.value == "工程施工现场留档记录" })
    }

    @Test
    fun addressAndGpsSwitchesAreIndependentAcrossAllStyles() {
        val styles = listOf("CLASSIC", "MINIMAL", "INFO_BOARD")
        val cases = listOf(
            setOf(BuiltInWatermarkFieldKeys.ADDRESS) to (true to false),
            setOf(BuiltInWatermarkFieldKeys.GPS) to (false to true),
            setOf(BuiltInWatermarkFieldKeys.ADDRESS, BuiltInWatermarkFieldKeys.GPS) to (true to true),
            emptySet<String>() to (false to false)
        )

        for (style in styles) {
            for ((enabled, expected) in cases) {
                val result = WatermarkLayoutEngine.calculateLayout(
                    1080f,
                    1920f,
                    WatermarkData(
                        addressText = "现场地址",
                        latitude = 43.886842,
                        longitude = 125.324501,
                        enabledSystemFields = enabled,
                        styleType = style
                    )
                )
                val renderedText = result.lines.joinToString("|") { it.label + it.value }
                assertEquals("address switch for $style/$enabled", expected.first, renderedText.contains("现场地址"))
                assertEquals("GPS switch for $style/$enabled", expected.second, renderedText.contains("43.886842"))
            }
        }
    }

    @Test
    fun classicLongProjectNameStaysInsideCanvasAndUsesAtMostTwoTitleLines() {
        val result = WatermarkLayoutEngine.calculateLayout(
            720f,
            1280f,
            WatermarkData(
                projectName = "超长中文工程现场安全隐患整改专项施工留档项目名称",
                styleType = "CLASSIC"
            )
        )
        assertTrue(result.cardRect.left >= 0f)
        assertTrue(result.cardRect.top >= 0f)
        assertTrue(result.cardRect.right <= 720f)
        assertTrue(result.cardRect.bottom <= 1280f)
        assertTrue(result.lines.take(2).count { it.isHighlight } <= 2)
        assertTrue(result.lines.first().value.isNotBlank())
    }

    @Test
    fun everyStyleBoundsLongFieldsInsideCardOnSmallAndLandscapeCanvases() {
        val longText = "吉林省长春市高新技术产业开发区超群街与硅谷大街交汇处".repeat(8)
        val data = WatermarkData(
            projectName = longText,
            categoryName = longText,
            addressText = longText,
            userName = longText,
            customFields = (0 until 12).map {
                WatermarkFieldItem("CUSTOM_$it", "字段$it$longText", longText, true)
            },
            enabledSystemFields = BuiltInWatermarkFieldKeys.all,
            styleType = "CLASSIC",
            fontSizeScale = 2.2f,
            marginDp = 80
        )
        for (style in listOf("CLASSIC", "MINIMAL", "INFO_BOARD")) {
            for ((width, height) in listOf(240f to 180f, 180f to 240f, 960f to 540f)) {
                val result = WatermarkLayoutEngine.calculateLayout(
                    width,
                    height,
                    data.copy(styleType = style)
                )
                assertTrue("$style card left", result.cardRect.left >= 0f)
                assertTrue("$style card top", result.cardRect.top >= 0f)
                assertTrue("$style card right", result.cardRect.right <= width + 0.01f)
                assertTrue("$style card bottom", result.cardRect.bottom <= height + 0.01f)
                result.lines.forEach { line ->
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        textSize = line.textSize
                        isFakeBoldText = line.isBold
                    }
                    val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        textSize = line.textSize
                        isFakeBoldText = line.label.isNotEmpty()
                    }
                    val lineRight = line.x + labelPaint.measureText(line.label) + paint.measureText(line.value)
                    assertTrue("$style text right", lineRight <= result.cardRect.right + 1f)
                    assertTrue("$style text left", line.x >= result.cardRect.left - 1f)
                    assertTrue("$style text bottom y=${line.y} bottom=${line.y + paint.fontMetrics.bottom} card=${result.cardRect}", line.y + paint.fontMetrics.bottom <= result.cardRect.bottom + 1f)
                    assertTrue("$style text top", line.y + paint.fontMetrics.top >= result.cardRect.top - 1f)
                }
            }
        }
    }

    @Test
    fun minimumFontScaleKeepsGpsAndEveryEnabledLogicalFieldOnShortCanvases() {
        val data = WatermarkData(
            projectName = "工程名_TOKEN",
            categoryName = "工程类_TOKEN",
            addressText = "地址_TOKEN",
            userName = "人员_TOKEN",
            latitude = 43.123456,
            longitude = 125.654321,
            enabledSystemFields = BuiltInWatermarkFieldKeys.all,
            customFields = listOf(
                WatermarkFieldItem("C1", "自定义1", "字段1_TOKEN", true),
                WatermarkFieldItem("C2", "自定义2", "字段2_TOKEN", true),
                WatermarkFieldItem("C3", "自定义3", "字段3_TOKEN", true),
                WatermarkFieldItem("C4", "自定义4", "字段4_TOKEN", true)
            ),
            fontSizeScale = 0.6f
        )
        val requiredValues = listOf(
            "工程名_TOKEN", "工程类_TOKEN", "地址_TOKEN", "人员_TOKEN",
            "43.123456", "字段1_TOKEN", "字段2_TOKEN", "字段3_TOKEN", "字段4_TOKEN"
        )

        for (style in listOf("CLASSIC", "MINIMAL", "INFO_BOARD")) {
            for ((width, height) in listOf(360f to 160f, 160f to 360f, 640f to 180f)) {
                val result = WatermarkLayoutEngine.calculateLayout(
                    width,
                    height,
                    data.copy(styleType = style)
                )
                val rendered = result.lines.joinToString("|") { it.label + it.value }
                requiredValues.forEach { value ->
                    assertTrue("$style $width x $height lost $value: $rendered", rendered.contains(value))
                }
                assertTrue(result.cardRect.left >= 0f)
                assertTrue(result.cardRect.top >= 0f)
                assertTrue(result.cardRect.right <= width + 0.01f)
                assertTrue(result.cardRect.bottom <= height + 0.01f)
                result.lines.forEach { line ->
                    val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        textSize = line.textSize
                        isFakeBoldText = line.isBold
                    }
                    val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        textSize = line.textSize
                        isFakeBoldText = line.label.isNotEmpty()
                    }
                    val lineRight = line.x + labelPaint.measureText(line.label) + valuePaint.measureText(line.value)
                    assertTrue("$style $width x $height text right", lineRight <= result.cardRect.right + 1f)
                    assertTrue("$style $width x $height text left", line.x >= result.cardRect.left - 1f)
                    assertTrue(
                        "$style $width x $height text top",
                        line.y + valuePaint.fontMetrics.top >= result.cardRect.top - 1f
                    )
                    assertTrue(
                        "$style $width x $height text bottom",
                        line.y + valuePaint.fontMetrics.bottom <= result.cardRect.bottom + 1f
                    )
                }
            }
        }
    }
}
