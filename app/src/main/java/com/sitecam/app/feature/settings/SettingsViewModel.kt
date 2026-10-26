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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SettingsUiState(
    val templates: List<WatermarkTemplateEntity> = emptyList(),
    val activeTemplateId: Long = 1L,
    val namingPattern: String = "{project}_{date}_{time}",
    val photoQualityProfile: com.sitecam.app.core.media.PhotoQualityProfile = com.sitecam.app.core.media.PhotoQualityProfile.STANDARD,
    val saveToSystemGallery: Boolean = false,
    val shutterSoundEnabled: Boolean = true,
    val cameraControlStyle: String = "NORMAL",
    val snapshotEnabled: Boolean = true,
    val snapshotShutterSpeed: Int = 250
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
        appContainer.settingsDataStore.shutterSoundEnabled,
        appContainer.settingsDataStore.cameraControlStyle,
        appContainer.settingsDataStore.snapshotEnabled,
        appContainer.settingsDataStore.snapshotShutterSpeed
    ) { state, shutterSound, controlStyle, snapshot, speed -> state.copy(shutterSoundEnabled = shutterSound, cameraControlStyle = controlStyle, snapshotEnabled = snapshot, snapshotShutterSpeed = speed) }.stateIn(
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
    fun setCameraControlStyle(style: String) {
        viewModelScope.launch { appContainer.settingsDataStore.setCameraControlStyle(style) }
    }
    fun setSnapshotEnabled(value: Boolean) { viewModelScope.launch { appContainer.settingsDataStore.setSnapshotEnabled(value) } }
    fun setSnapshotShutterSpeed(value: Int) { viewModelScope.launch { appContainer.settingsDataStore.setSnapshotShutterSpeed(value) } }

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    /**
     * Builds the diagnostics report off the main thread: it walks every camera
     * ID through binder calls, which is enough to jank this page.
     */
    fun copyDiagnostics(context: Context) {
        val appContext = context.applicationContext
        viewModelScope.launch {
            val report = withContext(Dispatchers.IO) {
                runCatching { CameraDiagnostics.generateDiagnosticReport(appContext) }.getOrDefault("")
            }
            if (report.isBlank()) {
                _messages.tryEmit("诊断信息生成失败，请重试")
                return@launch
            }
            val clipboard = appContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Camera Diagnostics", report))
            _messages.tryEmit("诊断信息已复制")
        }
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
