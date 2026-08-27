package com.sitecam.app.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.sitecam.app.core.database.entity.WatermarkFieldEntity
import com.sitecam.app.core.database.entity.WatermarkTemplateEntity
import com.sitecam.app.core.watermark.model.BuiltInWatermarkFieldKeys
import kotlinx.coroutines.flow.Flow
import androidx.room.Transaction

@Dao
interface WatermarkDao {
    @Query("SELECT * FROM watermark_templates ORDER BY isDefault DESC, id ASC")
    fun getAllTemplates(): Flow<List<WatermarkTemplateEntity>>

    @Query("SELECT * FROM watermark_templates WHERE id = :id LIMIT 1")
    suspend fun getTemplateById(id: Long): WatermarkTemplateEntity?

    @Query("SELECT * FROM watermark_templates WHERE isDefault = 1 LIMIT 1")
    suspend fun getDefaultTemplate(): WatermarkTemplateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTemplate(template: WatermarkTemplateEntity): Long

    @Update
    suspend fun updateTemplate(template: WatermarkTemplateEntity)

    @Delete
    suspend fun deleteTemplate(template: WatermarkTemplateEntity)

    @Query("SELECT * FROM watermark_fields WHERE templateId = :templateId ORDER BY displayOrder ASC")
    fun getFieldsForTemplate(templateId: Long): Flow<List<WatermarkFieldEntity>>

    @Query("SELECT * FROM watermark_fields WHERE templateId = :templateId ORDER BY displayOrder ASC")
    suspend fun getFieldsForTemplateSync(templateId: Long): List<WatermarkFieldEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertField(field: WatermarkFieldEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFields(fields: List<WatermarkFieldEntity>)

    @Update
    suspend fun updateField(field: WatermarkFieldEntity)

    @Delete
    suspend fun deleteField(field: WatermarkFieldEntity)

    @Transaction
    suspend fun ensureBuiltInFields(templateId: Long, defaults: List<WatermarkFieldEntity>) {
        val existingKeys = getFieldsForTemplateSync(templateId).mapTo(mutableSetOf()) { it.fieldKey }
        val missing = defaults.filter {
            it.fieldKey in BuiltInWatermarkFieldKeys.all && it.fieldKey !in existingKeys
        }
        if (missing.isNotEmpty()) insertFields(missing)
    }

    @Transaction
    suspend fun swapFieldOrder(first: WatermarkFieldEntity, second: WatermarkFieldEntity) {
        // Temporary order prevents a unique-order schema introduced later
        // from observing duplicate displayOrder values mid-swap.
        updateField(first.copy(displayOrder = -first.id.toInt()))
        updateField(second.copy(displayOrder = first.displayOrder))
        updateField(first.copy(displayOrder = second.displayOrder))
    }

    @Query("SELECT COUNT(*) FROM watermark_templates")
    suspend fun getTemplateCount(): Int
}
