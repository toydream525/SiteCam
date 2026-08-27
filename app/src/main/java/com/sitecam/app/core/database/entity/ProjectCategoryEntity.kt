package com.sitecam.app.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "project_categories")
data class ProjectCategoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val isSystemDefault: Boolean = false,
    val displayOrder: Int = 0
)
