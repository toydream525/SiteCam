package com.sitecam.app.feature.onboarding

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Stores separate completion markers for the feature and permission guides.
 *
 * This deliberately has its own DataStore file. The guide can therefore be
 * read before the camera graph is created without touching camera/settings
 * preferences or changing any existing setting.
 */
private val Context.onboardingDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "sitecam_onboarding_preferences")

data class OnboardingState(
    val hasDismissedGuide: Boolean = false,
    val hasHandledPermissionGuide: Boolean = false,
    val requestedPermissions: Set<String> = emptySet()
)

class OnboardingPreferences(
    context: Context,
    private val store: DataStore<Preferences> = context.onboardingDataStore
) {

    companion object {
        private val HAS_HANDLED_PERMISSION_GUIDE = booleanPreferencesKey("has_handled_permission_guide")
        private val REQUESTED_PERMISSIONS = stringSetPreferencesKey("requested_capture_permissions")
        private val HAS_DISMISSED_GUIDE = booleanPreferencesKey("has_dismissed_feature_guide")
    }

    val state: Flow<OnboardingState> = store.data.map { preferences ->
        OnboardingState(
            hasDismissedGuide = preferences[HAS_DISMISSED_GUIDE] ?: false,
            hasHandledPermissionGuide = preferences[HAS_HANDLED_PERMISSION_GUIDE] ?: false,
            requestedPermissions = preferences[REQUESTED_PERMISSIONS] ?: emptySet()
        )
    }

    val shouldShow: Flow<Boolean> = state.map { onboardingState ->
        !onboardingState.hasDismissedGuide
    }

    suspend fun markGuideDismissed() {
        store.edit { preferences ->
            preferences[HAS_DISMISSED_GUIDE] = true
        }
    }

    suspend fun markPermissionGuideHandled() {
        store.edit { it[HAS_HANDLED_PERMISSION_GUIDE] = true }
    }

    suspend fun markPermissionsRequested(permissions: Collection<String>) {
        store.edit { it[REQUESTED_PERMISSIONS] = (it[REQUESTED_PERMISSIONS] ?: emptySet()) + permissions }
    }

}
