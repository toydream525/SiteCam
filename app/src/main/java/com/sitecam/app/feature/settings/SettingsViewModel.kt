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
    val photoQualityProfile: com.sitecam.app.core.media.PhotoQualityProfile = com.sitecam.app.core.media.PhotoQualityProfile.STANDARD,
    val saveToSystemGallery: Boolean = false,
    val shutterSoundEnabled: Boolean = true
)

class SettingsViewModel(
    private val appContainer: AppContainer
) : ViewModel() {

    private val baseUiState = combine(
        appContainer.database.watermarkDao().getAllTemplates(),
        appContainer.settingsDataStore.activeTemplateId,
        appContainer.settingsDataStore.namingPattern,
        appContainer.settingsDataStore.photoQualityProfile,
        appContainer.settingsDataStore.saveToSystemGallery
    ) { templates, activeId, pattern, quality, gallery ->
        SettingsUiState(
            templates = templates,
            activeTemplateId = activeId,
            namingPattern = pattern,
            photoQualityProfile = quality,
            saveToSystemGallery = gallery
        )
    }

    val uiState: StateFlow<SettingsUiState> = combine(
        baseUiState,
        appContainer.settingsDataStore.shutterSoundEnabled
    ) { state, shutterSound -> state.copy(shutterSoundEnabled = shutterSound) }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = SettingsUiState()
    )

    fun setActiveTemplate(templateId: Long) {
        viewModelScope.launch {
            appContainer.settingsDataStore.setActiveTemplateId(templateId)
        }
    }

    fun setPhotoQualityProfile(profile: com.sitecam.app.core.media.PhotoQualityProfile) {
        viewModelScope.launch { appContainer.settingsDataStore.setPhotoQualityProfile(profile) }
    }
    fun setSaveToSystemGallery(enabled: Boolean) {
        viewModelScope.launch { appContainer.settingsDataStore.setSaveToSystemGallery(enabled) }
    }
    fun setShutterSoundEnabled(enabled: Boolean) {
        viewModelScope.launch { appContainer.settingsDataStore.setShutterSoundEnabled(enabled) }
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
