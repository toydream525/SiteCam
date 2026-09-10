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
    val isSelected: Boolean,
    val statistics: ProjectStatistics = ProjectStatistics()
)

data class ProjectsScreenUiState(
    val projects: List<ProjectItemUiState> = emptyList(),
    val categories: List<ProjectCategoryEntity> = emptyList(),
    val selectedProjectId: Long? = null,
    val isExporting: Boolean = false,
    val exportProgress: String = "",
    val search: String = "",
    val archiveFilter: String = "ACTIVE",
    val sort: ProjectSort = ProjectSort.LAST_CAPTURE,
    val ascending: Boolean = false,
    val checkedIds: Set<Long> = emptySet()
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
    private val _projectCreated = MutableSharedFlow<Long>()
    val projectCreated: SharedFlow<Long> = _projectCreated.asSharedFlow()
    val exportTreeUri: StateFlow<String?> = appContainer.settingsDataStore.exportTreeUri.stateIn(
        scope = viewModelScope,
        // The picker reads .value synchronously when it opens. Eagerly start
        // the DataStore flow so a recreated screen has the persisted URI
        // before the user taps the export-directory action.
        started = SharingStarted.Eagerly,
        initialValue = null
    )

    private val browserPrefs = appContainer.appContext.getSharedPreferences("project_browser", Context.MODE_PRIVATE)
    private data class Browser(val search: String = "", val archive: String = "ACTIVE",
        val sort: ProjectSort = ProjectSort.LAST_CAPTURE, val ascending: Boolean = false,
        val checked: Set<Long> = emptySet())
    private val browser = MutableStateFlow(Browser(
        sort = runCatching { ProjectSort.valueOf(browserPrefs.getString("sort", "LAST_CAPTURE")!!) }.getOrDefault(ProjectSort.LAST_CAPTURE),
        ascending = browserPrefs.getBoolean("ascending", false)))
    private val projectsAndStats = combine(appContainer.database.projectDao().getAllProjects(),
        appContainer.database.mediaItemDao().getAllMediaItems()) { projects, media -> projects to projectStatistics(media) }
    private val basics = combine(projectsAndStats, appContainer.database.projectCategoryDao().getAllCategories(),
        appContainer.settingsDataStore.selectedProjectId, browser) { pair, categories, selected, filters ->
        val projects = pair.first.filter {
            (filters.archive == "ALL" || it.isArchived == (filters.archive == "ARCHIVED")) &&
                (it.name.contains(filters.search, true) || it.routeName.contains(filters.search, true))
        }
        ProjectsScreenUiState(
            projects = sortedProjects(projects, pair.second, filters.sort, filters.ascending).map {
                ProjectItemUiState(it, it.id == selected, pair.second[it.id] ?: ProjectStatistics()) },
            categories = categories, selectedProjectId = selected, search = filters.search,
            archiveFilter = filters.archive, sort = filters.sort, ascending = filters.ascending,
            checkedIds = filters.checked.intersect(projects.map { it.id }.toSet()))
    }
    val uiState = combine(basics, _isExporting, _exportProgress) { state, exporting, progress ->
        state.copy(isExporting = exporting, exportProgress = progress)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ProjectsScreenUiState())

    fun setSearch(value: String) { browser.value = browser.value.copy(search = value, checked = emptySet()) }
    fun setArchiveFilter(value: String) { browser.value = browser.value.copy(archive = value, checked = emptySet()) }
    fun setSort(sort: ProjectSort, ascending: Boolean) {
        browser.value = browser.value.copy(sort = sort, ascending = ascending)
        browserPrefs.edit().putString("sort", sort.name).putBoolean("ascending", ascending).apply()
    }
    fun toggleChecked(id: Long) { val ids = browser.value.checked; browser.value = browser.value.copy(checked = if(id in ids) ids - id else ids + id) }
    fun selectAll() { browser.value = browser.value.copy(checked = uiState.value.projects.map { it.project.id }.toSet()) }
    fun clearSelection() { browser.value = browser.value.copy(checked = emptySet()) }

    fun editProject(project: ProjectEntity, name: String, route: String, category: String, address: String, description: String) {
        if(name.isBlank()) return
        viewModelScope.launch {
            runCatching {
                val current = appContainer.database.projectDao().getProjectById(project.id) ?: error("工程已删除")
                appContainer.database.projectDao().updateProject(current.copy(name = name.trim(), routeName = route.trim(),
                    categoryName = category, address = address.trim(), description = description.trim(), updatedAt = System.currentTimeMillis()))
            }.onFailure { _toastEvent.emit("编辑失败：${it.message}") }
        }
    }

    fun batchChange(ids: Set<Long>, category: String? = null, archived: Boolean? = null, locked: Boolean? = null) {
        if(_isExporting.value) return
        viewModelScope.launch {
            var success = 0; val errors = mutableListOf<String>()
            ids.forEach { id ->
                runCatching {
                    appContainer.captureOperationCoordinator.withProjectIdle(id) {
                        val project = appContainer.database.projectDao().getProjectById(id) ?: error("工程已删除")
                        appContainer.database.projectDao().updateProject(project.copy(categoryName = category ?: project.categoryName,
                            isArchived = archived ?: project.isArchived, isCaptureLocked = locked ?: project.isCaptureLocked,
                            updatedAt = System.currentTimeMillis()))
                    }
                }.onSuccess { success++ }.onFailure { errors += it.message ?: "操作失败" }
            }
            clearSelection()
            _toastEvent.emit("已更新 $success 个工程" + if(errors.isEmpty()) "" else "；${errors.size} 个失败：${errors.first()}")
        }
    }

    fun selectProject(projectId: Long) {
        viewModelScope.launch {
            val project = appContainer.database.projectDao().getProjectById(projectId)
            if(project == null || project.isArchived || project.isCaptureLocked) {
                _toastEvent.emit("已归档或锁定的工程不能设为拍摄工程"); return@launch
            }
            appContainer.settingsDataStore.setSelectedProjectId(projectId)
        }
    }

    fun createProject(
        name: String,
        category: String,
        address: String,
        description: String,
        route: String = ""
    ) {
        if (name.isBlank()) return
        viewModelScope.launch {
            try {
                val newProject = ProjectEntity(
                    name = name.trim(),
                    categoryName = category.ifBlank { "建筑" },
                    address = address.trim(),
                    description = description.trim(),
                    routeName = route.trim()
                )
                val newId = appContainer.database.projectDao().insertProject(newProject)
                // Creation has a durable, visible result: show active projects
                // in newest-first creation order so this row stays at the top
                // after reopening the project list as well.
                browser.value = browser.value.copy(
                    search = "",
                    archive = "ACTIVE",
                    sort = ProjectSort.CREATED,
                    ascending = false,
                    checked = emptySet()
                )
                browserPrefs.edit()
                    .putString("sort", ProjectSort.CREATED.name)
                    .putBoolean("ascending", false)
                    .apply()
                appContainer.settingsDataStore.setSelectedProjectId(newId)
                _projectCreated.emit(newId)
            } catch (e: Exception) {
                _toastEvent.emit("工程创建失败: ${e.message ?: "未知错误"}")
            }
        }
    }

    fun deleteProject(project: ProjectEntity) = deleteProjects(setOf(project.id))

    fun deleteProjects(ids: Set<Long>) {
        if(!_isExporting.compareAndSet(false, true)) return
        viewModelScope.launch {
            var removed = 0; var mediaRemoved = 0; var failed = 0; val errors = mutableListOf<String>()
            try {
                for(id in ids) {
                    runCatching {
                        appContainer.captureOperationCoordinator.withProjectIdle(id) {
                            val project = appContainer.database.projectDao().getProjectById(id) ?: return@withProjectIdle
                            val media = appContainer.database.mediaItemDao().getMediaItemsByProject(id).first()
                            var projectFailed = false
                            for(item in media) {
                                _exportProgress.value = "删除：${item.fileName}"
                                val result = appContainer.mediaDeletionCoordinator.delete(item)
                                if(result.success) mediaRemoved++ else { failed++; projectFailed = true; errors += result.message ?: item.fileName }
                            }
                            if(!projectFailed) {
                                appContainer.database.projectDao().deleteProject(project)
                                if(appContainer.settingsDataStore.selectedProjectId.first() == id) appContainer.settingsDataStore.setSelectedProjectId(null)
                                removed++
                            }
                        }
                    }.onFailure { failed++; errors += it.message ?: "删除失败" }
                }
                clearSelection()
                _toastEvent.emit("已删除 $removed 个工程、$mediaRemoved 个媒体；失败 $failed 项" + if(errors.isEmpty()) "" else "，已保留失败索引：${errors.first()}")
            } finally { _isExporting.value = false; _exportProgress.value = "" }
        }
    }

    fun exportProjectZip(context: Context, projectId: Long) = exportProjectsZip(context, setOf(projectId))

    fun exportProjectsZip(context: Context, ids: Set<Long>, options: com.sitecam.app.core.export.ExportOptions = com.sitecam.app.core.export.ExportOptions()) {
        if (!_isExporting.compareAndSet(false, true)) {
            viewModelScope.launch { _toastEvent.emit("已有工程正在导出，请稍候") }
            return
        }
        viewModelScope.launch {
            try {
                val result = appContainer.exportEngine.exportProjectsToZip(
                    projectIds = ids, options = options,
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

    fun exportProjectFolder(context: Context, projectId: Long, treeUri: Uri) = exportProjectsFolder(context, setOf(projectId), treeUri)

    fun exportProjectsFolder(context: Context, ids: Set<Long>, treeUri: Uri, options: com.sitecam.app.core.export.ExportOptions = com.sitecam.app.core.export.ExportOptions()) {
        if (!_isExporting.compareAndSet(false, true)) {
            viewModelScope.launch {
                _toastEvent.emit("已有工程正在导出，请稍候")
            }
            return
        }
        viewModelScope.launch {
            try {
                persistExportTreeUri(context, treeUri)
                val result: FolderExportResult = appContainer.exportEngine.exportProjectsToFolder(
                    projectIds = ids, options = options,
                    treeUri = treeUri,
                    onProgress = { current, total, fileName ->
                        _exportProgress.value = "正在导出 ($current/$total): $fileName"
                    }
                )
                val warning = result.missingMediaCount + result.missingAnnotationCount
                val warningText = if (warning > 0) "，缺失 ${warning} 个文件（详见 export_report.json）" else ""
                _toastEvent.emit("工程文件夹：已导出 ${result.copiedFileCount} 个媒体${warningText}，目标写入失败 ${result.destinationFailureCount} 项")
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
