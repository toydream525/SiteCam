package com.sitecam.app.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val categoryName: String = "建筑",
    val address: String = "",
    val description: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isArchived: Boolean = false,
    val coverMediaId: Long? = null,
    val watermarkTemplateId: Long = 1L
)
