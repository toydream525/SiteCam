package com.sitecam.app.core.database.repository

import android.util.Log
import com.sitecam.app.core.database.dao.WatermarkDao
import com.sitecam.app.core.database.entity.WatermarkFieldEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Application-owned FIFO: accepted edits survive navigation and retain input order. */
class WatermarkTemplateMutations(
    private val dao: WatermarkDao,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {
    private val _saveError = MutableStateFlow<Exception?>(null)
    val saveError = _saveError.asStateFlow()
    private val updates = Channel<suspend () -> Unit>(Channel.UNLIMITED)

    init {
        scope.launch {
            for (update in updates) {
                try {
                    update()
                    _saveError.value = null
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (t: Throwable) {
                    // A single failure (including an Error) must not end the
                    // consumer: with an unlimited channel trySend keeps
                    // succeeding and every later edit would be dropped.
                    _saveError.value = t as? Exception ?: RuntimeException(t.message, t)
                    Log.e("WatermarkTemplate", "Unable to save watermark setting", t)
                }
            }
        }
    }

    fun style(id: Long, styleType: String) = enqueue { dao.changeTemplateStyle(id, styleType) }
    fun fontSize(id: Long, scale: Float) = enqueue { dao.updateTemplateFontSize(id, scale) }
    fun opacity(id: Long, opacity: Float) = enqueue { dao.updateTemplateOpacity(id, opacity) }
    fun position(id: Long, position: String) = enqueue { dao.updateTemplatePosition(id, position) }
    fun fieldValue(id: Long, value: String) = enqueue { dao.updateFieldValue(id, value) }
    fun fieldEnabled(id: Long, enabled: Boolean) = enqueue { dao.updateFieldEnabled(id, enabled) }
    fun fieldLabel(id: Long, label: String) = enqueue { dao.updateFieldLabel(id, label) }
    fun deleteField(field: WatermarkFieldEntity) = enqueue { dao.deleteField(field) }
    fun moveField(templateId: Long, fieldId: Long, direction: Int) = enqueue {
        dao.moveField(templateId, fieldId, direction)
    }
    fun restoreFieldPresentation(templateId: Long) = enqueue { dao.restoreFieldPresentation(templateId) }
    fun addField(field: WatermarkFieldEntity) = enqueue {
        val order = (dao.getFieldsForTemplateSync(field.templateId).maxOfOrNull { it.displayOrder } ?: -1) + 1
        dao.insertField(field.copy(displayOrder = order))
    }

    private fun enqueue(update: suspend () -> Unit) {
        updates.trySend(update).getOrThrow()
    }
}
