package com.sitecam.app.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "watermark_fields",
    foreignKeys = [
        ForeignKey(
            entity = WatermarkTemplateEntity::class,
            parentColumns = ["id"],
            childColumns = ["templateId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["templateId"])]
)
data class WatermarkFieldEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val templateId: Long,
    val fieldKey: String, // PROJECT_NAME, PROJECT_CATEGORY, DATE, TIME, ADDRESS, GPS, USER_NAME, CUSTOM
    val label: String,
    val defaultValue: String = "",
    val displayOrder: Int,
    val isEnabled: Boolean = true
)
