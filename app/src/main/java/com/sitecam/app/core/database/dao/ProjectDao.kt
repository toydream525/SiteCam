package com.sitecam.app.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.sitecam.app.core.database.entity.ProjectEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects WHERE isArchived = 0 ORDER BY updatedAt DESC")
    fun getActiveProjects(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects ORDER BY updatedAt DESC")
    fun getAllProjects(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects WHERE id = :id LIMIT 1")
    suspend fun getProjectById(id: Long): ProjectEntity?

    @Query("SELECT * FROM projects WHERE id = :id LIMIT 1")
    fun getProjectByIdFlow(id: Long): Flow<ProjectEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProject(project: ProjectEntity): Long

    @Update
    suspend fun updateProject(project: ProjectEntity)

    @Delete
    suspend fun deleteProject(project: ProjectEntity)

    @Query("SELECT COUNT(*) FROM projects")
    suspend fun getProjectCount(): Int
}
