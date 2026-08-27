package com.sitecam.app.feature.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.sitecam.app.core.camera.CameraDiagnostics
import com.sitecam.app.core.database.entity.WatermarkTemplateEntity
import com.sitecam.app.core.di.AppContainer
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val templates: List<WatermarkTemplateEntity> = emptyList(),
    val activeTemplateId: Long = 1L,
    val namingPattern: String = "{project}_{date}_{time}",
    val jpegQuality: Int = 95
)

class SettingsViewModel(
    private val appContainer: AppContainer
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = combine(
        appContainer.database.watermarkDao().getAllTemplates(),
        appContainer.settingsDataStore.activeTemplateId,
        appContainer.settingsDataStore.namingPattern,
        appContainer.settingsDataStore.jpegQuality
    ) { templates, activeId, pattern, quality ->
        SettingsUiState(
            templates = templates,
            activeTemplateId = activeId,
            namingPattern = pattern,
            jpegQuality = quality
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = SettingsUiState()
    )

    fun setActiveTemplate(templateId: Long) {
        viewModelScope.launch {
            appContainer.settingsDataStore.setActiveTemplateId(templateId)
        }
    }

    fun setJpegQuality(quality: Int) {
        viewModelScope.launch {
            appContainer.settingsDataStore.setJpegQuality(quality)
        }
    }

    fun copyDiagnostics(context: Context): String {
        val report = CameraDiagnostics.generateDiagnosticReport(context)
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Camera Diagnostics", report)
        clipboard.setPrimaryClip(clip)
        return report
    }

    companion object {
        fun provideFactory(appContainer: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return SettingsViewModel(appContainer) as T
                }
            }
    }
}
