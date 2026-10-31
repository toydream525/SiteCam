package com.sitecam.app.feature.camera

import androidx.lifecycle.ViewModelStore
import com.sitecam.app.core.di.AppContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FlashApplicationTest {
    @Test fun cameraChangesBeforeQueuedPreferenceWritesRun() {
        Dispatchers.setMain(StandardTestDispatcher())
        val store = ViewModelStore()
        try {
            val container = AppContainer(RuntimeEnvironment.getApplication())
            val model = CameraViewModel(container)
            store.put("camera", model)
            val field = container.cameraManager.javaClass.getDeclaredField("currentFlashMode").apply { isAccessible = true }
            model.toggleFlashMode()
            assertEquals("ON", field.get(container.cameraManager))
            model.toggleFlashMode()
            assertEquals("OFF", field.get(container.cameraManager))
        } finally { store.clear(); Dispatchers.resetMain() }
    }
}
