package com.sitecam.app.core.watermark

import com.sitecam.app.core.database.entity.WatermarkFieldEntity
import com.sitecam.app.core.watermark.model.BuiltInWatermarkFieldKeys
import com.sitecam.app.core.watermark.model.resolveWatermarkFields
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WatermarkFieldResolverTest {

    @Test
    fun builtInSwitchesDoNotBecomeDuplicateCustomRows() {
        val result = resolveWatermarkFields(
            listOf(
                WatermarkFieldEntity(
                    templateId = 1,
                    fieldKey = BuiltInWatermarkFieldKeys.PROJECT_NAME,
                    label = "工程名称",
                    displayOrder = 0,
                    isEnabled = false
                ),
                WatermarkFieldEntity(
                    templateId = 1,
                    fieldKey = BuiltInWatermarkFieldKeys.ADDRESS,
                    label = "拍摄地点",
                    displayOrder = 1,
                    isEnabled = true
                ),
                WatermarkFieldEntity(
                    templateId = 1,
                    fieldKey = "BUILDER",
                    label = "施工单位",
                    defaultValue = "甲方",
                    displayOrder = 2,
                    isEnabled = true
                )
            )
        )

        assertTrue(BuiltInWatermarkFieldKeys.ADDRESS in result.enabledSystemFields)
        assertTrue(BuiltInWatermarkFieldKeys.PROJECT_NAME !in result.enabledSystemFields)
        assertEquals(listOf("BUILDER"), result.customFields.map { it.key })
    }
}
