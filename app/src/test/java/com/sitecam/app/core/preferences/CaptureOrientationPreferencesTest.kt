package com.sitecam.app.core.preferences

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import com.sitecam.app.core.camera.CaptureOrientation
import io.mockk.mockk
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class CaptureOrientationPreferencesTest {
    @get:Rule val temporary = TemporaryFolder()
    @Test fun absentOrInvalidDirectionDefaultsToAutoButManualSelectionSurvivesRestart() = runBlocking {
        val file = File(temporary.newFolder(), "camera.preferences_pb")
        val context = mockk<Context>()
        var scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val store = PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
            val settings = AppSettingsDataStore(context, store)
            assertEquals(CaptureOrientation.AUTO, settings.captureOrientation.first())
            store.edit { it[AppSettingsDataStore.CAPTURE_ORIENTATION] = "unknown-obsolete-value" }
            assertEquals(CaptureOrientation.AUTO, settings.captureOrientation.first())
            settings.setCaptureOrientation(CaptureOrientation.LANDSCAPE_RIGHT)
            assertEquals(CaptureOrientation.LANDSCAPE_RIGHT, settings.captureOrientation.first())
            scope.coroutineContext[Job]!!.cancelAndJoin()
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            val reopened = AppSettingsDataStore(context, PreferenceDataStoreFactory.create(scope = scope, produceFile = { file }))
            assertEquals(CaptureOrientation.LANDSCAPE_RIGHT, reopened.captureOrientation.first())
        } finally { scope.coroutineContext[Job]!!.cancelAndJoin() }
    }

    @Test fun shutterSoundDefaultsOnAndSurvivesRestartWhenDisabled() = runBlocking {
        val file = File(temporary.newFolder(), "camera.preferences_pb")
        val context = mockk<Context>()
        var scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val store = PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
            val settings = AppSettingsDataStore(context, store)
            assertEquals(true, settings.shutterSoundEnabled.first())
            settings.setShutterSoundEnabled(false)
            assertEquals(false, settings.shutterSoundEnabled.first())
            scope.coroutineContext[Job]!!.cancelAndJoin()
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            val reopened = AppSettingsDataStore(context, PreferenceDataStoreFactory.create(scope = scope, produceFile = { file }))
            assertEquals(false, reopened.shutterSoundEnabled.first())
        } finally { scope.coroutineContext[Job]!!.cancelAndJoin() }
    }
}
