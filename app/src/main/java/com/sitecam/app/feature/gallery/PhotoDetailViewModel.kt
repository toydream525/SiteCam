@file:android.annotation.SuppressLint("UnsafeOptInUsageError")

package com.sitecam.app.feature.gallery

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.sitecam.app.core.database.entity.MediaItemEntity
import com.sitecam.app.core.di.AppContainer
import com.sitecam.app.core.watermark.model.WatermarkData
import com.sitecam.app.core.watermark.model.WatermarkSnapshotCodec
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

sealed interface PhotoDetailEvent {
    data class Share(val item: MediaItemEntity) : PhotoDetailEvent
    data object Deleted : PhotoDetailEvent
    data class RetryCompleted(val cleanupWarning: Boolean) : PhotoDetailEvent
    data class Error(val message: String) : PhotoDetailEvent
}

class PhotoDetailViewModel(
    private val appContainer: AppContainer,
    private val mediaId: Long
) : ViewModel() {
    val mediaItem = appContainer.database.mediaItemDao().getMediaItemByIdFlow(mediaId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), null)
    val issue = appContainer.database.issueDao().getIssueByMediaIdFlow(mediaId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), null)

    private val _annotation = kotlinx.coroutines.flow.MutableStateFlow<com.sitecam.app.core.database.entity.AnnotationEntity?>(null)
    val annotation = _annotation.asStateFlow()
    init { refreshAnnotation() }
    fun refreshAnnotation() { viewModelScope.launch { _annotation.value = appContainer.database.issueDao().getAnnotationByMediaId(mediaId) } }
    fun saveIssue(title: String, severity: String, description: String, status: String) {
        viewModelScope.launch {
            runCatching { appContainer.database.issueDao().saveIssueAndMarkMedia(com.sitecam.app.core.database.entity.IssueEntity(
                mediaId = mediaId, projectId = mediaItem.value?.projectId ?: 0L,
                title = title, severity = severity, description = description, status = status)) }
                .onFailure { _events.emit(PhotoDetailEvent.Error(it.message ?: "保存问题失败")) }
        }
    }
    fun removeIssue() { viewModelScope.launch { appContainer.database.issueDao().removeIssueAndUnmarkMedia(mediaId) } }

    private val _isRetrying = kotlinx.coroutines.flow.MutableStateFlow(false)
    val isRetrying: kotlinx.coroutines.flow.StateFlow<Boolean> = _isRetrying.asStateFlow()

    private val _events = MutableSharedFlow<PhotoDetailEvent>()
    val events: SharedFlow<PhotoDetailEvent> = _events.asSharedFlow()

    fun requestShare(edited: Boolean = false) {
        viewModelScope.launch {
            mediaItem.value?.let { _events.emit(PhotoDetailEvent.Share(if (edited && _annotation.value != null) it.copy(contentUri = _annotation.value!!.annotatedContentUri) else it)) }
                ?: _events.emit(PhotoDetailEvent.Error("媒体记录不存在"))
        }
    }

    fun delete() {
        viewModelScope.launch {
            val item = mediaItem.value
            if (item == null) {
                _events.emit(PhotoDetailEvent.Error("媒体记录不存在"))
                return@launch
            }
            val result = appContainer.mediaDeletionCoordinator.delete(item)
            if (!result.success) {
                _events.emit(PhotoDetailEvent.Error(result.message ?: "媒体删除失败，已保留工程索引"))
                return@launch
            }
            _events.emit(PhotoDetailEvent.Deleted)
        }
    }

    fun retryVideoWatermark(context: Context) {
        if (_isRetrying.value) return
        _isRetrying.value = true
        viewModelScope.launch(Dispatchers.IO) {
            com.sitecam.app.core.media.MediaOperationCoordinator.withExclusive {
            val item = appContainer.database.mediaItemDao().getMediaItemById(mediaId)
            if (item == null || item.mediaType != "VIDEO") {
                _isRetrying.value = false
                _events.emit(PhotoDetailEvent.Error("仅视频支持水印转码重试"))
                return@withExclusive
            }
            if (item.processingStatus == "READY") {
                _isRetrying.value = false
                _events.emit(PhotoDetailEvent.Error("该视频已完成水印烧录"))
                return@withExclusive
            }
            val input = File(context.cacheDir, "retry_input_${item.id}_${System.nanoTime()}.mp4")
            val output = File(context.cacheDir, "retry_output_${item.id}_${System.nanoTime()}.mp4")
            var newUri: Uri? = null
            try {
                context.contentResolver.openInputStream(Uri.parse(item.contentUri))?.use { source ->
                    input.outputStream().use { target -> source.copyTo(target) }
                } ?: throw IllegalStateException("原视频不存在，无法重试")

                val project = appContainer.database.projectDao().getProjectById(item.projectId)
                val snapshot = WatermarkSnapshotCodec.decode(item.watermarkSnapshotJson)
                val data = snapshot ?: WatermarkData(
                    projectName = project?.name ?: "默认工程",
                    categoryName = project?.categoryName ?: "建筑",
                    captureTimestamp = item.captureTimestamp,
                    latitude = item.latitude,
                    longitude = item.longitude,
                    altitude = item.altitude,
                    addressText = item.addressText
                )
                appContainer.videoWatermarkTranscoder.transcode(input, output, data)
                val saved = appContainer.mediaStoreManager.saveVideoToMediaStore(
                    tempVideoFile = output,
                    fileName = item.fileName,
                    projectName = project?.name ?: "默认工程",
                    timestamp = item.captureTimestamp,
                    saveToSystemGallery = Uri.parse(item.contentUri).authority == android.provider.MediaStore.AUTHORITY
                )
                newUri = saved.uri
                try {
                    appContainer.database.mediaItemDao().updateMediaItem(
                        item.copy(
                            contentUri = saved.uri.toString(),
                            filePath = saved.filePath,
                            fileName = saved.fileName,
                            width = saved.width,
                            height = saved.height,
                            duration = saved.duration,
                            orientation = saved.rotation,
                            processingStatus = "READY"
                        )
                    )
                } catch (dbError: Exception) {
                    appContainer.mediaStoreManager.deleteMediaUri(saved.uri)
                    newUri = null
                    throw dbError
                }
                newUri = null
                val oldDelete = appContainer.mediaStoreManager.deleteMediaUriDetailed(Uri.parse(item.contentUri))
                _events.emit(PhotoDetailEvent.RetryCompleted(cleanupWarning = oldDelete == com.sitecam.app.core.media.MediaDeleteOutcome.FAILED))
            } catch (e: Exception) {
                newUri?.let { appContainer.mediaStoreManager.deleteMediaUri(it) }
                _events.emit(PhotoDetailEvent.Error("视频水印重试失败: ${e.message ?: "未知错误"}"))
            } finally {
                input.delete()
                output.delete()
                _isRetrying.value = false
            }
            }
        }
    }

    companion object {
        fun provideFactory(appContainer: AppContainer, mediaId: Long): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    PhotoDetailViewModel(appContainer, mediaId) as T
            }
    }
}
