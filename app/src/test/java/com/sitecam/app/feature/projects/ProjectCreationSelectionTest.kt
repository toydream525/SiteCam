package com.sitecam.app.feature.projects

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import com.sitecam.app.core.camera.CaptureOperationCoordinator
import com.sitecam.app.core.database.AppDatabase
import com.sitecam.app.core.database.entity.ProjectEntity
import com.sitecam.app.core.di.AppContainer
import com.sitecam.app.core.preferences.AppSettingsDataStore
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@OptIn(ExperimentalCoroutinesApi::class)
class ProjectCreationSelectionTest {
    @Test fun managementCreationKeepsSelectionAndExplicitCaptureCreationSelectsNewProject(): Unit = runBlocking {
        Dispatchers.setMain(Dispatchers.Unconfined)
        val context = RuntimeEnvironment.getApplication()
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val file = File(context.cacheDir, "create-selection-${UUID.randomUUID()}.preferences_pb")
        var model: ProjectViewModel? = null
        try {
            val settings = AppSettingsDataStore(context, PreferenceDataStoreFactory.create(scope = scope, produceFile = { file }))
            val existing = database.projectDao().insertProject(ProjectEntity(name = "当前工程 A"))
            settings.setSelectedProjectIdAndRecordRecent(existing)
            val coordinator = CaptureOperationCoordinator()
            val container = mockk<AppContainer>()
            every { container.database } returns database
            every { container.appContext } returns context
            every { container.settingsDataStore } returns settings
            every { container.captureOperationCoordinator } returns coordinator
            val viewModel = ProjectViewModel(container).also { model = it }
            val managementCreated = async(start = CoroutineStart.UNDISPATCHED) { viewModel.projectCreated.first() }
            viewModel.createProject("管理新建 B", "建筑", "", "")
            val b = withTimeout(10_000) { managementCreated.await() }
            assertNotEquals(existing, b)
            assertEquals("管理新建 B", database.projectDao().getProjectById(b)!!.name)
            assertEquals(existing, settings.selectedProjectId.first())
            assertEquals(listOf(existing), settings.recentProjectIds.first())

            val captureCreated = async(start = CoroutineStart.UNDISPATCHED) { viewModel.projectCreated.first() }
            viewModel.createProject("新建并使用 C", "建筑", "", "", selectForCapture = true)
            val c = withTimeout(10_000) { captureCreated.await() }
            assertEquals(c, settings.selectedProjectId.first())
            assertEquals(listOf(c, existing), settings.recentProjectIds.first())
        } finally {
            model?.viewModelScope?.coroutineContext?.job?.cancelAndJoin()
            scope.coroutineContext.job.cancelAndJoin()
            database.close(); file.delete(); Dispatchers.resetMain()
        }
    }
}
