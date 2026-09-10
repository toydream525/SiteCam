package com.sitecam.app.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.sitecam.app.core.database.entity.MediaItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaItemDao {
    @Query("SELECT * FROM media_items WHERE isUnavailable = 0 AND projectId = :projectId ORDER BY captureTimestamp DESC")
    fun getMediaItemsByProject(projectId: Long): Flow<List<MediaItemEntity>>

    @Query("SELECT * FROM media_items WHERE isUnavailable = 0 ORDER BY captureTimestamp DESC")
    fun getAllMediaItems(): Flow<List<MediaItemEntity>>

    @Query("SELECT * FROM media_items WHERE isUnavailable = 0 AND isIssue = 1 ORDER BY captureTimestamp DESC")
    fun getIssueMediaItems(): Flow<List<MediaItemEntity>>

    @Query("SELECT * FROM media_items WHERE id = :id LIMIT 1")
    suspend fun getMediaItemById(id: Long): MediaItemEntity?

    @Query("SELECT * FROM media_items WHERE isUnavailable = 0 AND id = :id LIMIT 1")
    fun getMediaItemByIdFlow(id: Long): Flow<MediaItemEntity?>

    @Query("SELECT * FROM media_items WHERE isUnavailable = 0 ORDER BY captureTimestamp DESC LIMIT 1")
    fun getLatestMediaItem(): Flow<MediaItemEntity?>

    @Query("SELECT * FROM media_items WHERE isUnavailable = 0 AND projectId = :projectId ORDER BY captureTimestamp DESC LIMIT 1")
    fun getLatestMediaItemForProject(projectId: Long): Flow<MediaItemEntity?>

    @Query("SELECT * FROM media_items")
    suspend fun getMediaForAvailabilitySync(): List<MediaItemEntity>

    @Query("UPDATE media_items SET isUnavailable = :unavailable WHERE id = :id AND contentUri = :uri")
    suspend fun setUnavailable(id: Long, uri: String, unavailable: Boolean)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMediaItem(item: MediaItemEntity): Long

    @Update
    suspend fun updateMediaItem(item: MediaItemEntity)

    @Delete
    suspend fun deleteMediaItem(item: MediaItemEntity)

    @Query("DELETE FROM media_items WHERE id = :id")
    suspend fun deleteMediaItemById(id: Long)

    @Query("SELECT COUNT(*) FROM media_items WHERE isUnavailable = 0 AND projectId = :projectId AND mediaType = 'PHOTO'")
    fun getPhotoCountForProject(projectId: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM media_items WHERE isUnavailable = 0 AND projectId = :projectId AND mediaType = 'VIDEO'")
    fun getVideoCountForProject(projectId: Long): Flow<Int>
    @Query("UPDATE media_items SET projectId = :projectId WHERE id IN (:mediaIds)")
    suspend fun moveMediaRows(mediaIds: List<Long>, projectId: Long)

    @Query("UPDATE issues SET projectId = :projectId, updatedAt = :updatedAt WHERE mediaId IN (:mediaIds)")
    suspend fun moveIssueRows(mediaIds: List<Long>, projectId: Long, updatedAt: Long)

    @androidx.room.Transaction
    suspend fun moveMediaToProject(mediaIds: List<Long>, projectId: Long) {
        moveMediaRows(mediaIds, projectId)
        moveIssueRows(mediaIds, projectId, System.currentTimeMillis())
    }
}
