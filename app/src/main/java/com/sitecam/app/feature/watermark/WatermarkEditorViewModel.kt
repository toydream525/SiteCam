package com.sitecam.app.feature.watermark

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.sitecam.app.core.database.entity.WatermarkFieldEntity
import com.sitecam.app.core.database.entity.WatermarkTemplateEntity
import com.sitecam.app.core.di.AppContainer
import com.sitecam.app.core.watermark.model.WatermarkData
import com.sitecam.app.core.watermark.model.builtInWatermarkFieldsForTemplate
import com.sitecam.app.core.watermark.model.resolveWatermarkFields
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class WatermarkEditorUiState(
    val template: WatermarkTemplateEntity? = null,
    val fields: List<WatermarkFieldEntity> = emptyList(),
    val previewData: WatermarkData = WatermarkData()
)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class WatermarkEditorViewModel(
    private val appContainer: AppContainer,
    private val templateId: Long
) : ViewModel() {

    private val _template = MutableStateFlow<WatermarkTemplateEntity?>(null)
    val template: StateFlow<WatermarkTemplateEntity?> = _template.asStateFlow()

    val uiState: StateFlow<WatermarkEditorUiState> = combine(
        _template,
        _template.flatMapLatest { template ->
            template?.let { appContainer.database.watermarkDao().getFieldsForTemplate(it.id) }
                ?: flowOf(emptyList())
        }
    ) { template, fields ->
        val resolvedFields = resolveWatermarkFields(fields)

        val previewData = WatermarkData(
            projectName = "长春配电改造工程",
            categoryName = "电力工程",
            addressText = "吉林省长春市朝阳区施工现场",
            latitude = 43.886842,
            longitude = 125.324501,
            userName = resolvedFields.userName,
            enabledSystemFields = resolvedFields.enabledSystemFields,
            systemValueOverrides = resolvedFields.systemValueOverrides,
            customFields = resolvedFields.customFields,
            styleType = template?.styleType ?: "CLASSIC",
            fontSizeScale = template?.fontSizeScale ?: 1.0f,
            opacity = template?.opacity ?: 0.85f,
            marginDp = template?.marginDp ?: 16,
            position = template?.position ?: "BOTTOM_LEFT"
        )

        WatermarkEditorUiState(
            template = template,
            fields = fields,
            previewData = previewData
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = WatermarkEditorUiState()
    )

    init {
        loadTemplate()
    }

    private fun loadTemplate() {
        viewModelScope.launch {
            val t = appContainer.database.watermarkDao().getTemplateById(templateId)
                ?: appContainer.database.watermarkDao().getDefaultTemplate()
            t?.let {
                appContainer.database.watermarkDao().ensureBuiltInFields(
                    it.id,
                    builtInWatermarkFieldsForTemplate(it.id)
                )
                appContainer.database.watermarkDao().observeTemplate(it.id).collect { latest ->
                    _template.value = latest
                }
            }
        }
    }

    fun updateStyleType(styleType: String) {
        val id = _template.value?.id ?: return
        appContainer.watermarkTemplateMutations.style(id, styleType)
    }

    fun updateFontSizeScale(scale: Float) {
        val id = _template.value?.id ?: return
        appContainer.watermarkTemplateMutations.fontSize(id, scale)
    }

    fun updateOpacity(opacity: Float) {
        val id = _template.value?.id ?: return
        appContainer.watermarkTemplateMutations.opacity(id, opacity)
    }

    fun updatePosition(position: String) {
        val id = _template.value?.id ?: return
        appContainer.watermarkTemplateMutations.position(id, position)
    }

    fun toggleField(field: WatermarkFieldEntity) {
        viewModelScope.launch {
            appContainer.database.watermarkDao().updateField(
                field.copy(isEnabled = !field.isEnabled)
            )
        }
    }

    fun addCustomField(label: String, defaultValue: String) {
        if (label.isBlank()) return
        val currentTemplateId = _template.value?.id ?: return
        val currentFields = uiState.value.fields
        val newOrder = (currentFields.maxOfOrNull { it.displayOrder } ?: 0) + 1
        val newField = WatermarkFieldEntity(
            templateId = currentTemplateId,
            fieldKey = "CUSTOM_${System.currentTimeMillis()}",
            label = label.trim(),
            defaultValue = defaultValue.trim(),
            displayOrder = newOrder,
            isEnabled = true
        )
        viewModelScope.launch {
            appContainer.database.watermarkDao().insertField(newField)
        }
    }

    fun updateField(field: WatermarkFieldEntity, newLabel: String, newDefaultValue: String) {
        viewModelScope.launch {
            appContainer.database.watermarkDao().updateField(
                field.copy(label = newLabel.trim(), defaultValue = newDefaultValue.trim())
            )
        }
    }

    fun deleteField(field: WatermarkFieldEntity) {
        viewModelScope.launch {
            appContainer.database.watermarkDao().deleteField(field)
        }
    }

    fun moveFieldUp(index: Int) {
        val fields = uiState.value.fields
        if (index <= 0 || index >= fields.size) return
        val current = fields[index]
        val prev = fields[index - 1]

        viewModelScope.launch {
            appContainer.database.watermarkDao().swapFieldOrder(current, prev)
        }
    }

    fun moveFieldDown(index: Int) {
        val fields = uiState.value.fields
        if (index < 0 || index >= fields.size - 1) return
        val current = fields[index]
        val next = fields[index + 1]

        viewModelScope.launch {
            appContainer.database.watermarkDao().swapFieldOrder(current, next)
        }
    }

    companion object {
        fun provideFactory(appContainer: AppContainer, templateId: Long): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return WatermarkEditorViewModel(appContainer, templateId) as T
                }
            }
    }
}
