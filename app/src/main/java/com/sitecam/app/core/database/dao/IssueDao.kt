package com.sitecam.app.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.sitecam.app.core.database.entity.AnnotationEntity
import com.sitecam.app.core.database.entity.IssueEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface IssueDao {
    @Query("SELECT * FROM issues WHERE projectId = :projectId ORDER BY createdAt DESC")
    fun getIssuesByProject(projectId: Long): Flow<List<IssueEntity>>

    @Query("SELECT * FROM issues ORDER BY createdAt DESC, id DESC")
    fun getAllIssues(): Flow<List<IssueEntity>>

    @Query("SELECT * FROM issues WHERE mediaId = :mediaId ORDER BY createdAt DESC, id DESC LIMIT 1")
    suspend fun getIssueByMediaId(mediaId: Long): IssueEntity?

    @Query("SELECT * FROM issues WHERE mediaId = :mediaId ORDER BY createdAt DESC, id DESC LIMIT 1")
    fun getIssueByMediaIdFlow(mediaId: Long): Flow<IssueEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertIssue(issue: IssueEntity): Long

    @Update
    suspend fun updateIssue(issue: IssueEntity)

    @Delete
    suspend fun deleteIssue(issue: IssueEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAnnotation(annotation: AnnotationEntity): Long

    @Query("SELECT * FROM annotations WHERE mediaId = :mediaId ORDER BY createdAt DESC, id DESC LIMIT 1")
    suspend fun getAnnotationByMediaId(mediaId: Long): AnnotationEntity?

    @androidx.room.Delete
    suspend fun deleteAnnotation(annotation: AnnotationEntity)

    @Query("UPDATE media_items SET isIssue = 1 WHERE id = :mediaId")
    suspend fun markMediaAsIssue(mediaId: Long)

    @Transaction
    suspend fun insertIssueAndMarkMedia(issue: IssueEntity) {
        insertIssue(issue)
        markMediaAsIssue(issue.mediaId)
    }

    @Transaction
    suspend fun replaceAnnotation(annotation: AnnotationEntity) {
        getAnnotationByMediaId(annotation.mediaId)?.let { deleteAnnotation(it) }
        insertAnnotation(annotation)
    }
}
