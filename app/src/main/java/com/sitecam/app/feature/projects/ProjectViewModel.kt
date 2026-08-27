package com.sitecam.app.feature.projects

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.sitecam.app.core.database.entity.ProjectCategoryEntity
import com.sitecam.app.core.database.entity.ProjectEntity
import com.sitecam.app.core.database.AppDatabase
import com.sitecam.app.core.export.FolderExportResult
import com.sitecam.app.core.di.AppContainer
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ProjectItemUiState(
    val project: ProjectEntity,
    val isSelected: Boolean
)

data class ProjectsScreenUiState(
    val projects: List<ProjectItemUiState> = emptyList(),
    val categories: List<ProjectCategoryEntity> = emptyList(),
    val selectedProjectId: Long? = null,
    val isExporting: Boolean = false,
    val exportProgress: String = ""
)

class ProjectViewModel(
    private val appContainer: AppContainer
) : ViewModel() {

    init {
        viewModelScope.launch {
            AppDatabase.ensureDefaultData(appContainer.database)
        }
    }

    private val _isExporting = MutableStateFlow(false)
    private val _exportProgress = MutableStateFlow("")
    private val _toastEvent = MutableSharedFlow<String>()
    val toastEvent: SharedFlow<String> = _toastEvent.asSharedFlow()
    private val _projectCreated = MutableSharedFlow<Unit>()
    val projectCreated: SharedFlow<Unit> = _projectCreated.asSharedFlow()
    val exportTreeUri: StateFlow<String?> = appContainer.settingsDataStore.exportTreeUri.stateIn(
        scope = viewModelScope,
        // The picker reads .value synchronously when it opens. Eagerly start
        // the DataStore flow so a recreated screen has the persisted URI
        // before the user taps the export-directory action.
        started = SharingStarted.Eagerly,
        initialValue = null
    )

    val uiState: StateFlow<ProjectsScreenUiState> = combine(
        appContainer.database.projectDao().getActiveProjects(),
        appContainer.database.projectCategoryDao().getAllCategories(),
        appContainer.settingsDataStore.selectedProjectId,
        _isExporting,
        _exportProgress
    ) { projects, categories, selectedId, exporting, progress ->
        val projectItems = projects.map { project ->
            ProjectItemUiState(
                project = project,
                isSelected = project.id == selectedId
            )
        }
        ProjectsScreenUiState(
            projects = projectItems,
            categories = categories,
            selectedProjectId = selectedId,
            isExporting = exporting,
            exportProgress = progress
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = ProjectsScreenUiState()
    )

    fun selectProject(projectId: Long) {
        viewModelScope.launch {
            appContainer.settingsDataStore.setSelectedProjectId(projectId)
        }
    }

    fun createProject(
        name: String,
        category: String,
        address: String,
        description: String
    ) {
        if (name.isBlank()) return
        viewModelScope.launch {
            try {
                val newProject = ProjectEntity(
                    name = name.trim(),
                    categoryName = category.ifBlank { "建筑" },
                    address = address.trim(),
                    description = description.trim()
                )
                val newId = appContainer.database.projectDao().insertProject(newProject)
                appContainer.settingsDataStore.setSelectedProjectId(newId)
                _projectCreated.emit(Unit)
            } catch (e: Exception) {
                _toastEvent.emit("工程创建失败: ${e.message ?: "未知错误"}")
            }
        }
    }

    fun deleteProject(project: ProjectEntity) {
        if (_isExporting.value) {
            viewModelScope.launch { _toastEvent.emit("工程正在导出，请完成后再删除") }
            return
        }
        viewModelScope.launch {
            val media = appContainer.database.mediaItemDao().getMediaItemsByProject(project.id).first()
            val failures = mutableListOf<String>()
            var cleanedIndexes = 0
            for (item in media) {
                val result = appContainer.mediaDeletionCoordinator.delete(item)
                if (result.success) {
                    cleanedIndexes++
                } else {
                    failures += "${item.fileName}${result.message?.let { "（$it）" } ?: ""}"
                }
            }
            if (failures.isNotEmpty()) {
                _toastEvent.emit("已清理 $cleanedIndexes 项；${failures.size} 项删除失败，已保留工程与索引：${failures.take(3).joinToString()}${if (failures.size > 3) "…" else ""}")
                return@launch
            }
            try {
                appContainer.database.projectDao().deleteProject(project)
                if (appContainer.settingsDataStore.selectedProjectId.first() == project.id) {
                    val next = appContainer.database.projectDao().getActiveProjects().first().firstOrNull()
                    appContainer.settingsDataStore.setSelectedProjectId(next?.id)
                }
                _toastEvent.emit("工程及其媒体已删除")
            } catch (e: Exception) {
                _toastEvent.emit("工程删除失败，数据库仍保留: ${e.message ?: "未知错误"}")
            }
        }
    }

    fun exportProjectZip(context: Context, projectId: Long) {
        if (!_isExporting.compareAndSet(false, true)) {
            viewModelScope.launch { _toastEvent.emit("已有工程正在导出，请稍候") }
            return
        }
        viewModelScope.launch {
            try {
                val result = appContainer.exportEngine.exportProjectToZip(
                    projectId = projectId,
                    onProgress = { current, total, fileName ->
                        _exportProgress.value = "正在打包 ($current/$total): $fileName"
                    }
                )
                val warning = result.missingMediaCount + result.missingAnnotationCount
                val warningText = if (warning > 0) "，缺失 ${warning} 个文件（详见 export_report.json）" else ""
                _toastEvent.emit("工程档案包生成成功！${result.totalPhotos} 张照片、${result.totalVideos} 个视频${warningText}")
                appContainer.exportEngine.shareExportedZip(context, result)
            } catch (e: Exception) {
                _toastEvent.emit("导出失败: ${e.message ?: "未知错误"}")
            } finally {
                _isExporting.value = false
                _exportProgress.value = ""
            }
        }
    }

    fun exportProjectFolder(context: Context, projectId: Long, treeUri: Uri) {
        if (!_isExporting.compareAndSet(false, true)) {
            viewModelScope.launch {
                _toastEvent.emit("已有工程正在导出，请稍候")
            }
            return
        }
        viewModelScope.launch {
            try {
                persistExportTreeUri(context, treeUri)
                val result: FolderExportResult = appContainer.exportEngine.exportProjectToFolder(
                    projectId = projectId,
                    treeUri = treeUri,
                    onProgress = { current, total, fileName ->
                        _exportProgress.value = "正在导出 ($current/$total): $fileName"
                    }
                )
                val warning = result.missingMediaCount + result.missingAnnotationCount
                val warningText = if (warning > 0) "，缺失 ${warning} 个文件（详见 export_report.json）" else ""
                _toastEvent.emit("工程文件夹导出成功${warningText}")
            } catch (error: Exception) {
                _toastEvent.emit("文件夹导出失败: ${error.message ?: "未知错误"}")
            } finally {
                _isExporting.value = false
                _exportProgress.value = ""
            }
        }
    }

    fun persistExportTreeUri(context: Context, uri: Uri): Boolean {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        val persisted = runCatching {
            context.contentResolver.takePersistableUriPermission(uri, flags)
            true
        }.getOrElse {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                true
            }.getOrDefault(false)
        }
        viewModelScope.launch {
            appContainer.settingsDataStore.setExportTreeUri(uri.toString())
            if (!persisted) _toastEvent.emit("系统未授予长期目录权限，下次导出需重新选择目录")
        }
        return persisted
    }

    fun createExportFolderIntent(): Intent =
        appContainer.exportEngine.createExportFolderIntent()

    /** Initial URI is only reused when this app still has a persisted write grant. */
    fun initialExportTreeUri(context: Context): Uri? {
        val persisted = exportTreeUri.value?.let(Uri::parse) ?: return null
        return context.contentResolver.persistedUriPermissions
            .firstOrNull { it.uri == persisted && it.isWritePermission }
            ?.uri
    }

    fun openExportFolder(context: Context) {
        runCatching {
            context.startActivity(createExportFolderIntent())
        }.onFailure { error ->
            viewModelScope.launch {
                _toastEvent.emit("无法打开目录选择器: ${error.message ?: "设备不支持系统文件选择器"}")
            }
        }
    }

    companion object {
        fun provideFactory(appContainer: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return ProjectViewModel(appContainer) as T
                }
            }
    }
}
