package com.sitecam.app.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "annotations",
    foreignKeys = [
        ForeignKey(
            entity = MediaItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["mediaId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["mediaId"], unique = true)]
)
data class AnnotationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val mediaId: Long,
    val annotatedContentUri: String,
    val vectorDataJson: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
