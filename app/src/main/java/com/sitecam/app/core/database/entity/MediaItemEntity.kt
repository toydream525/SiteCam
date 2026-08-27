package com.sitecam.app.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "media_items",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["projectId"]),
        Index(value = ["captureTimestamp"]),
        Index(value = ["isIssue"])
    ]
)
data class MediaItemEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val projectId: Long,
    val mediaType: String = "PHOTO", // "PHOTO" or "VIDEO"
    val contentUri: String,
    val filePath: String = "",
    val fileName: String,
    val width: Int = 0,
    val height: Int = 0,
    val duration: Long = 0L,
    val captureTimestamp: Long,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val altitude: Double? = null,
    val locationAccuracy: Float? = null,
    /** Timestamp of the location fix used for this capture, if any. */
    val locationTimestamp: Long? = null,
    /** FRESH, STALE, or UNAVAILABLE. */
    val locationStatus: String = "UNAVAILABLE",
    val addressText: String = "",
    val orientation: Int = 0,
    val isIssue: Boolean = false,
    val isFavorite: Boolean = false,
    val processingStatus: String = "READY", // "PROCESSING", "READY", "FAILED"
    /** Immutable capture-time watermark configuration for video retry/export. */
    val watermarkSnapshotJson: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
