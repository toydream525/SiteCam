package com.sitecam.app.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "watermark_templates")
data class WatermarkTemplateEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val styleType: String = "CLASSIC", // CLASSIC, MINIMAL, INFO_BOARD
    val fontSizeScale: Float = 1.0f,
    val opacity: Float = 0.85f,
    @ColumnInfo(defaultValue = "0.6")
    val widthRatio: Float = 0.6f,
    val marginDp: Int = 16,
    val position: String = "BOTTOM_LEFT", // BOTTOM_LEFT, BOTTOM_RIGHT, TOP_LEFT, TOP_RIGHT
    val isDefault: Boolean = false
)
