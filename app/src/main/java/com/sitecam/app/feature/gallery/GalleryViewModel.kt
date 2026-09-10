package com.sitecam.app.feature.gallery

import android.content.Context
import android.content.Intent
import android.content.ClipData
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.sitecam.app.core.database.entity.*
import com.sitecam.app.core.di.AppContainer
import com.sitecam.app.core.export.ExportOptions
import com.sitecam.app.core.media.MediaOperationCoordinator
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

enum class GalleryFilter { ALL, TODAY, ISSUES_ONLY }

data class CaptureDateRange(val start: LocalDate? = null, val endInclusive: LocalDate? = null) {
    fun contains(timestamp: Long, zone: ZoneId = ZoneId.systemDefault()): Boolean {
        val date = java.time.Instant.ofEpochMilli(timestamp).atZone(zone).toLocalDate()
        return (start == null || !date.isBefore(start)) && (endInclusive == null || !date.isAfter(endInclusive))
    }
    companion object {
        fun parse(start: String, end: String): CaptureDateRange {
            fun first(value: String): LocalDate? = if(value.isBlank()) null else when(value.length) {
                4 -> java.time.Year.parse(value).atDay(1)
                7 -> java.time.YearMonth.parse(value).atDay(1)
                else -> LocalDate.parse(value)
            }
            fun last(value: String): LocalDate? = if(value.isBlank()) null else when(value.length) {
                4 -> java.time.Year.parse(value).atMonth(12).atEndOfMonth()
                7 -> java.time.YearMonth.parse(value).atEndOfMonth()
                else -> LocalDate.parse(value)
            }
            val a = first(start); val b = last(end.ifBlank { start })
            require(a == null || b == null || !a.isAfter(b)) { "结束日期不能早于开始日期" }
            return CaptureDateRange(a, b)
        }
    }
}

data class GalleryUiState(
    val mediaItems: List<MediaItemEntity> = emptyList(), val issueByMediaId: Map<Long, IssueEntity> = emptyMap(),
    val currentProject: ProjectEntity? = null, val selectedFilter: GalleryFilter = GalleryFilter.ALL,
    val isLoading: Boolean = false, val projects: List<ProjectEntity> = emptyList(), val checkedIds: Set<Long> = emptySet(),
    val dateRange: CaptureDateRange = CaptureDateRange(), val severity: String? = null, val status: String? = null,
    val category: String? = null, val progress: String = ""
)
sealed interface GalleryUiEvent { data class Message(val text: String) : GalleryUiEvent }

class GalleryViewModel(private val appContainer: AppContainer, initialProjectId: Long? = null) : ViewModel() {
    private data class Filters(val project: Long? = null, val filter: GalleryFilter = GalleryFilter.ALL,
        val date: CaptureDateRange = CaptureDateRange(), val severity: String? = null, val status: String? = null,
        val category: String? = null, val checked: Set<Long> = emptySet())
    private val filters = MutableStateFlow(Filters(project = initialProjectId))
    private val busy = MutableStateFlow(false)
    private val progress = MutableStateFlow("")
    private val _events = MutableSharedFlow<GalleryUiEvent>()
    val events = _events.asSharedFlow()
    private val base = combine(appContainer.database.mediaItemDao().getAllMediaItems(),
        appContainer.database.issueDao().getAllIssues(), appContainer.database.projectDao().getAllProjects(), filters) { media, issues, projects, filter ->
        val issueMap = issues.associateBy { it.mediaId }
        val projectMap = projects.associateBy { it.id }
        val items = media.filter { item ->
            (filter.project == null || item.projectId == filter.project) &&
                (filter.category == null || projectMap[item.projectId]?.categoryName == filter.category) &&
                filter.date.contains(item.captureTimestamp) &&
                (filter.filter != GalleryFilter.ISSUES_ONLY || item.isIssue) &&
                (filter.filter != GalleryFilter.TODAY || CaptureDateRange(LocalDate.now(), LocalDate.now()).contains(item.captureTimestamp)) &&
                (filter.severity == null || issueMap[item.id]?.severity == filter.severity) &&
                (filter.status == null || issueMap[item.id]?.status == filter.status)
        }.sortedWith(compareByDescending<MediaItemEntity> { it.captureTimestamp }.thenByDescending { it.id })
        GalleryUiState(items, issueMap, projectMap[filter.project], filter.filter, projects = projects,
            checkedIds = filter.checked.intersect(items.map { it.id }.toSet()), dateRange = filter.date,
            severity = filter.severity, status = filter.status, category = filter.category)
    }
    val uiState = combine(base, busy, progress) { state, working, text -> state.copy(isLoading = working, progress = text) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), GalleryUiState())
    fun setFilter(value: GalleryFilter) { filters.value = filters.value.copy(filter = value, checked = emptySet()) }
    fun setProject(id: Long?) { filters.value = filters.value.copy(project = id, checked = emptySet()) }
    fun setCategory(value: String?) { filters.value = filters.value.copy(category = value, checked = emptySet()) }
    fun setSeverity(value: String?) { filters.value = filters.value.copy(severity = value, checked = emptySet()) }
    fun setStatus(value: String?) { filters.value = filters.value.copy(status = value, checked = emptySet()) }
    fun setDateRange(value: CaptureDateRange) { filters.value = filters.value.copy(date = value, checked = emptySet()) }
    fun toggleChecked(id: Long) { val old = filters.value.checked; filters.value = filters.value.copy(checked = if(id in old) old - id else old + id) }
    fun selectAll() { filters.value = filters.value.copy(checked = uiState.value.mediaItems.map { it.id }.toSet()) }
    fun clearSelection() { filters.value = filters.value.copy(checked = emptySet()) }
    private fun operate(action: suspend () -> Unit) {
        if(!busy.compareAndSet(false, true)) return
        viewModelScope.launch { try { action() } catch(e: Exception) { _events.emit(GalleryUiEvent.Message("操作失败：${e.message}")) }
            finally { busy.value = false; progress.value = "" } }
    }
    fun moveSelected(projectId: Long) {
        val ids = uiState.value.checkedIds.toList()
        operate {
            MediaOperationCoordinator.withExclusive { appContainer.database.mediaItemDao().moveMediaToProject(ids, projectId) }
            clearSelection(); _events.emit(GalleryUiEvent.Message("已将 ${ids.size} 项移入工程，关联问题已同步"))
        }
    }
    fun deleteMedia(item: MediaItemEntity) = deleteIds(setOf(item.id))
    fun deleteSelected() = deleteIds(uiState.value.checkedIds)
    private fun deleteIds(ids: Set<Long>) = operate {
        var success = 0; val failed = mutableListOf<String>()
        ids.forEach { id ->
            val item = appContainer.database.mediaItemDao().getMediaItemById(id)
            if(item != null) {
                progress.value = "删除：${item.fileName}"
                val result = appContainer.mediaDeletionCoordinator.delete(item)
                if(result.success) success++ else failed += result.message ?: item.fileName
            }
        }
        clearSelection(); _events.emit(GalleryUiEvent.Message("已删除 $success 项；失败 ${failed.size} 项" + if(failed.isEmpty()) "" else "，已保留索引：${failed.first()}"))
    }
    fun saveIssue(item: MediaItemEntity, title: String, severity: String, description: String, status: String) = operate {
        MediaOperationCoordinator.withExclusive {
            appContainer.database.issueDao().saveIssueAndMarkMedia(IssueEntity(projectId = item.projectId, mediaId = item.id,
                title = title, severity = severity, description = description, status = status))
        }
    }
    fun removeIssue(id: Long) = operate { MediaOperationCoordinator.withExclusive { appContainer.database.issueDao().removeIssueAndUnmarkMedia(id) } }
    fun shareMedia(context: Context, item: MediaItemEntity) = share(context, listOf(item))
    fun shareSelected(context: Context) = share(context, uiState.value.mediaItems.filter { it.id in uiState.value.checkedIds })
    private fun share(context: Context, items: List<MediaItemEntity>) {
        if(items.isEmpty() || busy.value) return
        runCatching {
            val uris = ArrayList(items.map { Uri.parse(it.contentUri) })
            val intent = Intent(if(items.size == 1) Intent.ACTION_SEND else Intent.ACTION_SEND_MULTIPLE).apply {
                type = if(items.all { it.mediaType == "PHOTO" }) "image/*" else if(items.all { it.mediaType == "VIDEO" }) "video/*" else "*/*"
                if(items.size == 1) putExtra(Intent.EXTRA_STREAM, uris.first()) else putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                clipData = ClipData.newRawUri("工程媒体", uris.first()).apply { uris.drop(1).forEach { addItem(ClipData.Item(it)) } }
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "分享选中媒体"))
        }.onFailure { viewModelScope.launch { _events.emit(GalleryUiEvent.Message("无法分享：${it.message}")) } }
    }
    fun exportSelected(context: Context, options: ExportOptions, treeUri: Uri? = null) {
        val ids = uiState.value.checkedIds
        val projects = uiState.value.mediaItems.filter { it.id in ids }.map { it.projectId }.toSet()
        if(ids.isEmpty()) return
        operate {
            val callback: (Int, Int, String) -> Unit = { current, total, name -> progress.value = "$current/$total：$name" }
            if(treeUri == null) {
                val result = appContainer.exportEngine.exportProjectsToZip(projects, ids, options, callback)
                _events.emit(GalleryUiEvent.Message("已导出 ${result.copiedFileCount} 项；失败 ${result.missingMediaCount + result.missingAnnotationCount} 个文件"))
                appContainer.exportEngine.shareExportedZip(context, result)
            } else {
                runCatching { context.contentResolver.takePersistableUriPermission(treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
                val result = appContainer.exportEngine.exportProjectsToFolder(projects, treeUri, ids, options, callback)
                _events.emit(GalleryUiEvent.Message("已导出 ${result.copiedFileCount} 项；缺失 ${result.missingMediaCount + result.missingAnnotationCount} 个文件，目标写入失败 ${result.destinationFailureCount} 项"))
            }
        }
    }
    companion object {
        fun provideFactory(appContainer: AppContainer, projectId: Long? = null): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T = GalleryViewModel(appContainer, projectId) as T
        }
    }
}
