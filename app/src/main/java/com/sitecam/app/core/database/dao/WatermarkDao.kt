package com.sitecam.app.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.sitecam.app.core.database.entity.WatermarkFieldEntity
import com.sitecam.app.core.database.entity.WatermarkTemplateEntity
import com.sitecam.app.core.watermark.model.WatermarkStyleCatalog
import com.sitecam.app.core.watermark.model.BuiltInWatermarkFieldKeys
import kotlinx.coroutines.flow.Flow
import androidx.room.Transaction

@Dao
interface WatermarkDao {
    @Query("SELECT * FROM watermark_templates ORDER BY isDefault DESC, id ASC")
    fun getAllTemplates(): Flow<List<WatermarkTemplateEntity>>

    @Query("SELECT * FROM watermark_templates WHERE id = :id LIMIT 1")
    suspend fun getTemplateById(id: Long): WatermarkTemplateEntity?

    @Query("SELECT * FROM watermark_templates WHERE id = :id LIMIT 1")
    fun observeTemplate(id: Long): Flow<WatermarkTemplateEntity?>

    // Each editor writes only the property it owns, so an in-flight slider
    // update can never restore a stale style or overwrite another setting.
    @Query("UPDATE watermark_templates SET styleType = :styleType WHERE id = :id")
    suspend fun updateTemplateStyle(id: Long, styleType: String)

    suspend fun changeTemplateStyle(id: Long, styleType: String) {
        if (WatermarkStyleCatalog.styles.none { it.id == styleType }) return
        updateTemplateStyle(id, styleType)
    }

    @Query("UPDATE watermark_templates SET fontSizeScale = :scale WHERE id = :id")
    suspend fun updateTemplateFontSize(id: Long, scale: Float)

    @Query("UPDATE watermark_templates SET opacity = :opacity WHERE id = :id")
    suspend fun updateTemplateOpacity(id: Long, opacity: Float)

    @Query("UPDATE watermark_templates SET position = :position WHERE id = :id")
    suspend fun updateTemplatePosition(id: Long, position: String)

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

    @Query("UPDATE watermark_fields SET label = :label WHERE id = :id")
    suspend fun updateFieldLabel(id: Long, label: String)

    @Query("UPDATE watermark_fields SET displayOrder = :displayOrder WHERE id = :id")
    suspend fun updateFieldOrder(id: Long, displayOrder: Int)

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
        updateFieldOrder(first.id, -first.id.toInt())
        updateFieldOrder(second.id, first.displayOrder)
        updateFieldOrder(first.id, second.displayOrder)
    }

    /** Reset presentation only; user-entered values, switches and custom fields remain intact. */
    @Transaction
    suspend fun restoreFieldPresentation(templateId: Long) {
        val defaults = com.sitecam.app.core.watermark.model.builtInWatermarkFieldsForTemplate(templateId)
        val current = getFieldsForTemplateSync(templateId)
        val byKey = defaults.associateBy { it.fieldKey }
        current.filter { it.fieldKey in byKey }.forEach { field ->
            val original = byKey.getValue(field.fieldKey)
            updateFieldLabel(field.id, original.label)
            updateFieldOrder(field.id, original.displayOrder)
        }
        current.filter { it.fieldKey !in byKey }.forEachIndexed { index, field ->
            updateFieldOrder(field.id, defaults.size + index)
        }
    }

    @Query("SELECT COUNT(*) FROM watermark_templates")
    suspend fun getTemplateCount(): Int
}
