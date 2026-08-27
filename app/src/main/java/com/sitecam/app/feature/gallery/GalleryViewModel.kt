package com.sitecam.app.feature.gallery

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.sitecam.app.core.database.entity.MediaItemEntity
import com.sitecam.app.core.database.entity.ProjectEntity
import com.sitecam.app.core.database.entity.IssueEntity
import com.sitecam.app.core.di.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class GalleryFilter {
    ALL, TODAY, ISSUES_ONLY
}

data class GalleryUiState(
    val mediaItems: List<MediaItemEntity> = emptyList(),
    val issueByMediaId: Map<Long, IssueEntity> = emptyMap(),
    val currentProject: ProjectEntity? = null,
    val selectedFilter: GalleryFilter = GalleryFilter.ALL,
    val isLoading: Boolean = false
)

sealed interface GalleryUiEvent {
    data class Message(val text: String) : GalleryUiEvent
}

class GalleryViewModel(
    private val appContainer: AppContainer,
    private val initialProjectId: Long? = null
) : ViewModel() {

    private val _selectedFilter = MutableStateFlow(GalleryFilter.ALL)
    val selectedFilter: StateFlow<GalleryFilter> = _selectedFilter.asStateFlow()

    private val _currentProjectId = MutableStateFlow(initialProjectId)
    private val _events = MutableSharedFlow<GalleryUiEvent>()
    val events = _events.asSharedFlow()

    private val rawMediaFlow = if (initialProjectId != null) {
        appContainer.database.mediaItemDao().getMediaItemsByProject(initialProjectId)
    } else {
        appContainer.database.mediaItemDao().getAllMediaItems()
    }
    private val issueFlow = if (initialProjectId != null) {
        appContainer.database.issueDao().getIssuesByProject(initialProjectId)
    } else {
        appContainer.database.issueDao().getAllIssues()
    }

    val uiState: StateFlow<GalleryUiState> = combine(
        rawMediaFlow,
        issueFlow,
        _selectedFilter,
        _currentProjectId
    ) { items, issues, filter, projectId ->
        val filtered = when (filter) {
            GalleryFilter.ALL -> items
            GalleryFilter.TODAY -> {
                val todayStr = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
                items.filter {
                    val itemDateStr = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date(it.captureTimestamp))
                    itemDateStr == todayStr
                }
            }
            GalleryFilter.ISSUES_ONLY -> items.filter { it.isIssue }
        }

        val project = if (projectId != null) {
            appContainer.database.projectDao().getProjectById(projectId)
        } else null

        GalleryUiState(
            mediaItems = filtered,
            issueByMediaId = issues.associateBy { it.mediaId },
            currentProject = project,
            selectedFilter = filter,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = GalleryUiState()
    )

    fun setFilter(filter: GalleryFilter) {
        _selectedFilter.value = filter
    }

    fun deleteMedia(item: MediaItemEntity) {
        viewModelScope.launch {
            val result = appContainer.mediaDeletionCoordinator.delete(item)
            if (!result.success) {
                _events.emit(GalleryUiEvent.Message(result.message ?: "媒体删除失败，已保留工程索引"))
                return@launch
            }
            _events.emit(
                GalleryUiEvent.Message(
                    if (result.mediaWasMissing) "媒体已不存在，失效索引已清理" else "媒体及标注成品已删除"
                )
            )
        }
    }

    fun shareMedia(context: Context, item: MediaItemEntity) {
        try {
            val uri = Uri.parse(item.contentUri)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = if (item.mediaType == "VIDEO") "video/mp4" else "image/jpeg"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, if (item.mediaType == "VIDEO") "分享工程视频" else "分享工程照片"))
        } catch (_: Exception) {
            viewModelScope.launch { _events.emit(GalleryUiEvent.Message("打开分享失败，请检查文件是否仍存在")) }
        }
    }

    companion object {
        fun provideFactory(appContainer: AppContainer, projectId: Long? = null): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return GalleryViewModel(appContainer, projectId) as T
                }
            }
    }
}
