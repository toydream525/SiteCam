package com.sitecam.app.core.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "sitecam_preferences")

class AppSettingsDataStore(
    context: Context,
    private val store: DataStore<Preferences> = context.dataStore
) {

    companion object {
        val PROJECT_SELECTION_CLEARED = booleanPreferencesKey("project_selection_cleared")
        val SELECTED_PROJECT_ID = longPreferencesKey("selected_project_id")
        val FLASH_MODE = stringPreferencesKey("flash_mode")
        val ORIENTATION_LOCKED = booleanPreferencesKey("orientation_locked")
        val LOCKED_ORIENTATION = intPreferencesKey("locked_orientation")
        val ACTIVE_TEMPLATE_ID = longPreferencesKey("active_template_id")
        val NAMING_PATTERN = stringPreferencesKey("naming_pattern")
        val PHOTO_PROFILE = stringPreferencesKey("photo_profile")
        val SAVE_TO_GALLERY = booleanPreferencesKey("save_to_gallery")
        val CAPTURE_ORIENTATION = stringPreferencesKey("capture_orientation")
        val SHUTTER_SOUND_ENABLED = booleanPreferencesKey("shutter_sound_enabled")
        val JPEG_QUALITY = intPreferencesKey("jpeg_quality")
        val QUICK_ISSUE_MODE = booleanPreferencesKey("quick_issue_mode")
        val EXPORT_TREE_URI = stringPreferencesKey("export_tree_uri")
    }

    val projectSelectionCleared = store.data.map { it[PROJECT_SELECTION_CLEARED] ?: false }

    val selectedProjectId: Flow<Long?> = store.data.map { preferences ->
        preferences[SELECTED_PROJECT_ID]
    }

    val flashMode: Flow<String> = store.data.map { preferences ->
        preferences[FLASH_MODE] ?: "AUTO"
    }

    val orientationLocked: Flow<Boolean> = store.data.map { preferences ->
        preferences[ORIENTATION_LOCKED] ?: false
    }

    val lockedOrientation: Flow<Int> = store.data.map { preferences ->
        preferences[LOCKED_ORIENTATION] ?: 0
    }

    val activeTemplateId: Flow<Long> = store.data.map { preferences ->
        preferences[ACTIVE_TEMPLATE_ID] ?: 1L
    }

    val namingPattern: Flow<String> = store.data.map { preferences ->
        preferences[NAMING_PATTERN] ?: "{project}_{date}_{time}"
    }

    val photoQualityProfile = store.data.map {
        runCatching { com.sitecam.app.core.media.PhotoQualityProfile.valueOf(it[PHOTO_PROFILE].orEmpty()) }
            .getOrDefault(com.sitecam.app.core.media.PhotoQualityProfile.STANDARD)
    }
    val saveToSystemGallery = store.data.map { it[SAVE_TO_GALLERY] ?: false }
    val captureOrientation = store.data.map {
        runCatching { com.sitecam.app.core.camera.CaptureOrientation.valueOf(it[CAPTURE_ORIENTATION].orEmpty()) }
            .getOrDefault(com.sitecam.app.core.camera.CaptureOrientation.AUTO)
    }
    val shutterSoundEnabled: Flow<Boolean> = store.data.map { it[SHUTTER_SOUND_ENABLED] ?: true }
    suspend fun setPhotoQualityProfile(value: com.sitecam.app.core.media.PhotoQualityProfile) {
        store.edit { it[PHOTO_PROFILE] = value.name }
    }
    suspend fun setSaveToSystemGallery(value: Boolean) { store.edit { it[SAVE_TO_GALLERY] = value } }
    suspend fun setCaptureOrientation(value: com.sitecam.app.core.camera.CaptureOrientation) {
        store.edit { it[CAPTURE_ORIENTATION] = value.name }
    }
    suspend fun setShutterSoundEnabled(value: Boolean) {
        store.edit { it[SHUTTER_SOUND_ENABLED] = value }
    }

    val jpegQuality: Flow<Int> = store.data.map { preferences ->
        preferences[JPEG_QUALITY] ?: 95
    }

    val quickIssueMode: Flow<Boolean> = store.data.map { preferences ->
        preferences[QUICK_ISSUE_MODE] ?: false
    }

    val exportTreeUri: Flow<String?> = store.data.map { preferences ->
        preferences[EXPORT_TREE_URI]
    }

    suspend fun setSelectedProjectId(projectId: Long?) {
        store.edit { preferences ->
            preferences[PROJECT_SELECTION_CLEARED] = projectId == null
            if (projectId == null) preferences.remove(SELECTED_PROJECT_ID)
            else preferences[SELECTED_PROJECT_ID] = projectId
        }
    }

    suspend fun setFlashMode(flashMode: String) {
        store.edit { preferences ->
            preferences[FLASH_MODE] = flashMode
        }
    }

    suspend fun setOrientationLocked(locked: Boolean, orientation: Int = 0) {
        store.edit { preferences ->
            preferences[ORIENTATION_LOCKED] = locked
            preferences[LOCKED_ORIENTATION] = orientation
        }
    }

    suspend fun setActiveTemplateId(templateId: Long) {
        store.edit { preferences ->
            preferences[ACTIVE_TEMPLATE_ID] = templateId
        }
    }

    suspend fun setNamingPattern(pattern: String) {
        store.edit { preferences ->
            preferences[NAMING_PATTERN] = pattern
        }
    }

    suspend fun setJpegQuality(quality: Int) {
        store.edit { preferences ->
            preferences[JPEG_QUALITY] = quality.coerceIn(70, 100)
        }
    }

    suspend fun setQuickIssueMode(enabled: Boolean) {
        store.edit { preferences ->
            preferences[QUICK_ISSUE_MODE] = enabled
        }
    }

    suspend fun setExportTreeUri(uri: String?) {
        store.edit { preferences ->
            if (uri.isNullOrBlank()) preferences.remove(EXPORT_TREE_URI)
            else preferences[EXPORT_TREE_URI] = uri
        }
    }

}
