package com.sitecam.app.core.database.repository

import android.util.Log
import com.sitecam.app.core.database.dao.WatermarkDao
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
                } catch (error: Exception) {
                    _saveError.value = error
                    Log.e("WatermarkTemplate", "Unable to save watermark setting", error)
                }
            }
        }
    }

    fun style(id: Long, styleType: String) = enqueue { dao.changeTemplateStyle(id, styleType) }
    fun fontSize(id: Long, scale: Float) = enqueue { dao.updateTemplateFontSize(id, scale) }
    fun opacity(id: Long, opacity: Float) = enqueue { dao.updateTemplateOpacity(id, opacity) }
    fun position(id: Long, position: String) = enqueue { dao.updateTemplatePosition(id, position) }

    private fun enqueue(update: suspend () -> Unit) {
        updates.trySend(update).getOrThrow()
    }
}
