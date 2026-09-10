package com.sitecam.app.core.watermark

import com.sitecam.app.core.database.entity.WatermarkFieldEntity
import com.sitecam.app.core.watermark.engine.WatermarkLayoutEngine
import com.sitecam.app.core.watermark.model.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WatermarkFieldPresentationTest {
    private fun configuredData(): WatermarkData {
        val fields = builtInWatermarkFieldsForTemplate(1).map {
            when (it.fieldKey) {
                "PROJECT_NAME" -> it.copy(label = "今日水印", defaultValue = "原有工程内容", displayOrder = 3)
                "PROJECT_CATEGORY" -> it.copy(displayOrder = 1)
                "DATE_TIME" -> it.copy(displayOrder = 4)
                "ADDRESS" -> it.copy(displayOrder = 2)
                else -> it.copy(displayOrder = it.displayOrder + 2)
            }
        } + WatermarkFieldEntity(templateId = 1, fieldKey = "CUSTOM_FIRST", label = "首行标签", defaultValue = "首行内容", displayOrder = 0)
        val resolved = resolveWatermarkFields(fields.reversed())
        return WatermarkData(
            projectName = "实时工程名称", addressText = "现场地址",
            enabledSystemFields = resolved.enabledSystemFields,
            systemValueOverrides = resolved.systemValueOverrides,
            customFields = resolved.customFields, fieldLabels = resolved.fieldLabels,
            fieldOrder = resolved.fieldOrder
        )
    }

    @Test fun everyStyleUsesSavedLabelsAndMixedOrderWithoutChangingContents() {
        val data = configuredData()
        assertEquals("原有工程内容", data.builtInValue("PROJECT_NAME"))
        assertEquals("实时工程名称", data.projectName)
        for (style in WatermarkStyleCatalog.styles) {
            val layout = WatermarkLayoutEngine.calculateLayout(1080f, 1440f, data.copy(styleType = style.id))
            assertEquals(style.id, listOf("CUSTOM_FIRST", "PROJECT_CATEGORY", "ADDRESS", "PROJECT_NAME", "DATE_TIME"),
                layout.lines.mapNotNull { it.fieldKey }.distinct())
            val projectText = layout.lines.filter { it.fieldKey == "PROJECT_NAME" }.joinToString("") { it.label + it.value }
            assertTrue("${style.id}: $projectText", projectText.contains("今日水印"))
            assertTrue(style.id, projectText.contains("原有工程内容"))
            assertFalse(style.id, projectText.contains("工程名称"))
            if (style.id == "ENGINEERING_BLUE") {
                assertNull(layout.headerRect)
                val firstRow = layout.lines.first { it.fieldKey == "CUSTOM_FIRST" }
                val blueAreas = layout.decorations.filter { it.color == layout.accentColor }
                assertTrue(blueAreas.isNotEmpty())
                assertTrue("Blue title background must not cover preceding custom rows", blueAreas.none { it.rect.contains(firstRow.x, firstRow.y) })
            }
        }
    }

    @Test fun blankAndDisabledFieldsStayHiddenWhileTheirOrderAndLabelsSurvive() {
        val data = configuredData().copy(
            enabledSystemFields = setOf("PROJECT_NAME"),
            customFields = listOf(WatermarkFieldItem("CUSTOM_FIRST", "首行标签", ""), WatermarkFieldItem("OFF", "关闭项", "不显示", false))
        )
        for (style in WatermarkStyleCatalog.styles) {
            val decoded = WatermarkSnapshotCodec.decode(WatermarkSnapshotCodec.encode(data.copy(styleType = style.id)))!!
            assertEquals(data.fieldOrder, decoded.fieldOrder)
            assertEquals(data.fieldLabels, decoded.fieldLabels)
            val layout = WatermarkLayoutEngine.calculateLayout(720f, 960f, decoded)
            assertEquals(listOf("PROJECT_NAME"), layout.lines.mapNotNull { it.fieldKey }.distinct())
        }
    }

    @Test fun longRenamedLabelsRemainCompleteAndInsideCardInEveryStyle() {
        val label = "今日水印现场施工记录标签".repeat(8)
        for (style in WatermarkStyleCatalog.styles) {
            val data = configuredData().copy(styleType = style.id, fieldLabels = mapOf("PROJECT_NAME" to label))
            val layout = WatermarkLayoutEngine.calculateLayout(360f, 480f, data)
            val text = layout.lines.filter { it.fieldKey == "PROJECT_NAME" }.joinToString("") { it.label + it.value }
            assertTrue(style.id, text.contains(label))
            assertTrue(style.id, layout.cardRect.top >= 0 && layout.cardRect.bottom <= 480.1f)
            assertTrue(style.id, layout.cardRect.left >= 0 && layout.cardRect.right <= 360.1f)
        }
    }

    @Test fun oldSnapshotsKeepTheirHistoricalLabelsAndStyleOrdering() {
        for ((style, first) in listOf("ENGINEERING_BLUE" to "PROJECT_NAME", "TIME_LOCATION" to "DATE_TIME")) {
            val old = WatermarkSnapshotCodec.decode("""{"styleType":"$style","projectName":"旧工程"}""")!!
            assertNull(old.fieldOrder)
            assertTrue(old.fieldLabels.isEmpty())
            val layout = WatermarkLayoutEngine.calculateLayout(1080f,1440f,old)
            assertEquals(first, layout.lines.first().fieldKey)
            if (style == "ENGINEERING_BLUE") assertNotNull(layout.headerRect)
        }
    }
}
