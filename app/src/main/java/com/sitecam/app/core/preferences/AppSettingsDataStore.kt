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

class AppSettingsDataStore(private val context: Context) {

    companion object {
        val SELECTED_PROJECT_ID = longPreferencesKey("selected_project_id")
        val FLASH_MODE = stringPreferencesKey("flash_mode")
        val ORIENTATION_LOCKED = booleanPreferencesKey("orientation_locked")
        val LOCKED_ORIENTATION = intPreferencesKey("locked_orientation")
        val ACTIVE_TEMPLATE_ID = longPreferencesKey("active_template_id")
        val NAMING_PATTERN = stringPreferencesKey("naming_pattern")
        val JPEG_QUALITY = intPreferencesKey("jpeg_quality")
        val QUICK_ISSUE_MODE = booleanPreferencesKey("quick_issue_mode")
        val EXPORT_TREE_URI = stringPreferencesKey("export_tree_uri")
    }

    val selectedProjectId: Flow<Long?> = context.dataStore.data.map { preferences ->
        preferences[SELECTED_PROJECT_ID]
    }

    val flashMode: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[FLASH_MODE] ?: "AUTO"
    }

    val orientationLocked: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[ORIENTATION_LOCKED] ?: false
    }

    val lockedOrientation: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[LOCKED_ORIENTATION] ?: 0
    }

    val activeTemplateId: Flow<Long> = context.dataStore.data.map { preferences ->
        preferences[ACTIVE_TEMPLATE_ID] ?: 1L
    }

    val namingPattern: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[NAMING_PATTERN] ?: "{project}_{date}_{time}"
    }

    val jpegQuality: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[JPEG_QUALITY] ?: 95
    }

    val quickIssueMode: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[QUICK_ISSUE_MODE] ?: false
    }

    val exportTreeUri: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[EXPORT_TREE_URI]
    }

    suspend fun setSelectedProjectId(projectId: Long?) {
        context.dataStore.edit { preferences ->
            if (projectId == null) preferences.remove(SELECTED_PROJECT_ID)
            else preferences[SELECTED_PROJECT_ID] = projectId
        }
    }

    suspend fun setFlashMode(flashMode: String) {
        context.dataStore.edit { preferences ->
            preferences[FLASH_MODE] = flashMode
        }
    }

    suspend fun setOrientationLocked(locked: Boolean, orientation: Int = 0) {
        context.dataStore.edit { preferences ->
            preferences[ORIENTATION_LOCKED] = locked
            preferences[LOCKED_ORIENTATION] = orientation
        }
    }

    suspend fun setActiveTemplateId(templateId: Long) {
        context.dataStore.edit { preferences ->
            preferences[ACTIVE_TEMPLATE_ID] = templateId
        }
    }

    suspend fun setNamingPattern(pattern: String) {
        context.dataStore.edit { preferences ->
            preferences[NAMING_PATTERN] = pattern
        }
    }

    suspend fun setJpegQuality(quality: Int) {
        context.dataStore.edit { preferences ->
            preferences[JPEG_QUALITY] = quality.coerceIn(70, 100)
        }
    }

    suspend fun setQuickIssueMode(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[QUICK_ISSUE_MODE] = enabled
        }
    }

    suspend fun setExportTreeUri(uri: String?) {
        context.dataStore.edit { preferences ->
            if (uri.isNullOrBlank()) preferences.remove(EXPORT_TREE_URI)
            else preferences[EXPORT_TREE_URI] = uri
        }
    }

}
