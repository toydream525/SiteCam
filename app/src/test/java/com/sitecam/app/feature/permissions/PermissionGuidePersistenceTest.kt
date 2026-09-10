package com.sitecam.app.feature.permissions

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.sitecam.app.feature.onboarding.OnboardingPreferences
import io.mockk.mockk
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class PermissionGuidePersistenceTest {
    @get:Rule val temporary = TemporaryFolder()
    @Test fun permissionCompletionPersistsIndependentlyOfFeatureTutorialMarker() = runBlocking {
        val file = File(temporary.newFolder(), "guide.preferences_pb")
        val context = mockk<Context>() // Injected store means Android context is never accessed.
        var storeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val store = PreferenceDataStoreFactory.create(scope = storeScope, produceFile = { file })
            val preferences = OnboardingPreferences(context, store)
            assertFalse(preferences.state.first().hasDismissedGuide)
            assertFalse(preferences.state.first().hasHandledPermissionGuide)
            preferences.markGuideDismissed()
            assertFalse("Finishing tutorial must not skip the permission guide", preferences.state.first().hasHandledPermissionGuide)
            preferences.markPermissionsRequested(CapturePermissions().permissionsToRequest())
            preferences.markPermissionGuideHandled()
            storeScope.coroutineContext[Job]!!.cancelAndJoin()
            storeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            val reopened = OnboardingPreferences(context, PreferenceDataStoreFactory.create(scope = storeScope, produceFile = { file }))
            val persisted = reopened.state.first()
            assertTrue(persisted.hasDismissedGuide)
            assertTrue(persisted.hasHandledPermissionGuide)
            assertEquals(CapturePermissions().permissionsToRequest().toSet(), persisted.requestedPermissions)
            assertEquals(StartupGate.APP, resolveStartupGate(persisted, CapturePermissions()))
            reopened.markGuideDismissed() // Replaying the tutorial cannot reset handled permissions.
            assertTrue(reopened.state.first().hasHandledPermissionGuide)
        } finally { storeScope.coroutineContext[Job]!!.cancelAndJoin() }
    }
}
