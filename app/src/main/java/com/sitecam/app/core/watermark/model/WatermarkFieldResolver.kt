package com.sitecam.app.core.watermark.model

import com.sitecam.app.core.database.entity.WatermarkFieldEntity

/** Stable keys used by the built-in watermark fields. */
object BuiltInWatermarkFieldKeys {
    const val PROJECT_NAME = "PROJECT_NAME"
    const val PROJECT_CATEGORY = "PROJECT_CATEGORY"
    const val DATE_TIME = "DATE_TIME"
    const val ADDRESS = "ADDRESS"
    const val GPS = "GPS"
    const val USER_NAME = "USER_NAME"

    val defaultLabels: Map<String, String> = mapOf(
        PROJECT_NAME to "工程名称", PROJECT_CATEGORY to "工程类型",
        DATE_TIME to "拍摄时间", ADDRESS to "拍摄地点",
        GPS to "经纬度", USER_NAME to "拍摄人"
    )

    val all: Set<String> = setOf(
        PROJECT_NAME,
        PROJECT_CATEGORY,
        DATE_TIME,
        ADDRESS,
        GPS,
        USER_NAME
    )
}

fun builtInWatermarkFieldsForTemplate(templateId: Long): List<WatermarkFieldEntity> = listOf(
    WatermarkFieldEntity(templateId = templateId, fieldKey = BuiltInWatermarkFieldKeys.PROJECT_NAME, label = "工程名称", displayOrder = 0),
    WatermarkFieldEntity(templateId = templateId, fieldKey = BuiltInWatermarkFieldKeys.PROJECT_CATEGORY, label = "工程类型", displayOrder = 1),
    WatermarkFieldEntity(templateId = templateId, fieldKey = BuiltInWatermarkFieldKeys.DATE_TIME, label = "拍摄时间", displayOrder = 2),
    WatermarkFieldEntity(templateId = templateId, fieldKey = BuiltInWatermarkFieldKeys.ADDRESS, label = "拍摄地点", displayOrder = 3),
    WatermarkFieldEntity(templateId = templateId, fieldKey = BuiltInWatermarkFieldKeys.GPS, label = "经纬度", displayOrder = 4),
    WatermarkFieldEntity(templateId = templateId, fieldKey = BuiltInWatermarkFieldKeys.USER_NAME, label = "拍摄人", defaultValue = "施工员", displayOrder = 5, isEnabled = false)
)

data class ResolvedWatermarkFields(
    val enabledSystemFields: Set<String>,
    val userName: String,
    val systemValueOverrides: Map<String, String>,
    val customFields: List<WatermarkFieldItem>,
    val fieldLabels: Map<String, String> = emptyMap(),
    val fieldOrder: List<String>? = null
)

/**
 * Keeps template switches meaningful: built-in fields control the matching
 * system value, while only unknown keys become custom rows. Empty templates
 * retain the historical all-system-fields default for old databases.
 */
fun resolveWatermarkFields(fields: List<WatermarkFieldEntity>): ResolvedWatermarkFields {
    if (fields.isEmpty()) {
        return ResolvedWatermarkFields(
            enabledSystemFields = BuiltInWatermarkFieldKeys.all,
            userName = "",
            systemValueOverrides = emptyMap(),
            customFields = emptyList()
        )
    }

    val systemFields = fields.filter { it.fieldKey in BuiltInWatermarkFieldKeys.all }
    val enabled = if (systemFields.isEmpty()) {
        BuiltInWatermarkFieldKeys.all
    } else {
        systemFields.filter { it.isEnabled }.mapTo(linkedSetOf()) { it.fieldKey }
    }
    val userName = fields.firstOrNull { it.fieldKey == BuiltInWatermarkFieldKeys.USER_NAME && it.isEnabled }
        ?.defaultValue
        .orEmpty()

    val overrides = systemFields
        .filter { it.fieldKey != BuiltInWatermarkFieldKeys.USER_NAME && it.defaultValue.isNotBlank() }
        .associate { it.fieldKey to it.defaultValue }

    val custom = fields
        .filter { it.fieldKey !in BuiltInWatermarkFieldKeys.all && it.isEnabled }
        .map {
            WatermarkFieldItem(
                key = it.fieldKey,
                label = it.label,
                value = it.defaultValue,
                isEnabled = true
            )
        }

    val ordered = fields.sortedBy { it.displayOrder }
    return ResolvedWatermarkFields(
        enabled, userName, overrides, custom,
        fieldLabels = ordered.associate { it.fieldKey to it.label },
        fieldOrder = ordered.map { it.fieldKey }
    )
}
