package com.sitecam.app.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.sitecam.app.core.database.entity.ProjectCategoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectCategoryDao {
    @Query("SELECT * FROM project_categories ORDER BY displayOrder ASC, id ASC")
    fun getAllCategories(): Flow<List<ProjectCategoryEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCategory(category: ProjectCategoryEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCategories(categories: List<ProjectCategoryEntity>)

    @Update
    suspend fun updateCategory(category: ProjectCategoryEntity)

    @Delete
    suspend fun deleteCategory(category: ProjectCategoryEntity)

    @Query("SELECT COUNT(*) FROM project_categories")
    suspend fun getCategoryCount(): Int
}
