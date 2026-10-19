package com.sitecam.app.feature.gallery

import android.graphics.Canvas
import android.graphics.Bitmap
import android.net.Uri
import androidx.room.withTransaction
import com.sitecam.app.core.database.entity.MediaItemEntity
import com.sitecam.app.core.database.entity.ProjectEntity
import com.sitecam.app.core.di.AppContainer
import com.sitecam.app.core.media.MediaOperationCoordinator
import com.sitecam.app.core.media.PhotoQualityProfile
import com.sitecam.app.core.watermark.model.*
import com.sitecam.app.core.watermark.renderer.WatermarkBitmapRenderer
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/** Owns a local import draft. No template or camera-selection writes occur here. */
data class PhotoImportState(val project: ProjectEntity, val uri: Uri, val metadata: ImportedPhotoSource.Metadata,
    val data: WatermarkData, val preview: Bitmap? = null, val loading: Boolean = true, val saving: Boolean = false,
    val previewStale: Boolean = false, val timeText: String = "", val timeError: Boolean = false,
    val profile: PhotoQualityProfile = PhotoQualityProfile.ORIGINAL)

class PhotoImportController(private val container: AppContainer, private val scope: CoroutineScope,
    private val message: suspend (String) -> Unit) {
    private val _state = MutableStateFlow<PhotoImportState?>(null)
    val state = _state.asStateFlow()
    private var source: Bitmap? = null
    private var loadJob: Job? = null
    private data class PreviewRequest(val generation: Long, val state: PhotoImportState, val source: Bitmap)
    private val previews = kotlinx.coroutines.channels.Channel<PreviewRequest>(kotlinx.coroutines.channels.Channel.CONFLATED)
    private val previewWorker = scope.launch {
        for (request in previews) {
            if (request.generation != generation) continue
            var output: Bitmap? = null
            try {
                // A single conflated worker owns rendering. New edits replace the
                // pending request without launching overlapping bitmap work.
                withContext(NonCancellable + Dispatchers.Default) {
                    if (request.generation == generation && !request.source.isRecycled) {
                        output = request.source.copy(Bitmap.Config.ARGB_8888, true)
                        val target = request.state.profile.targetSize(request.state.metadata.width, request.state.metadata.height)
                        val layout = com.sitecam.app.core.watermark.engine.WatermarkLayoutEngine.calculateLayout(
                            target.first.toFloat(), target.second.toFloat(), request.state.data)
                        val canvas = Canvas(output!!)
                        canvas.scale(output!!.width.toFloat() / target.first, output!!.height.toFloat() / target.second)
                        com.sitecam.app.core.watermark.renderer.WatermarkCanvasPainter.draw(canvas, layout)
                    }
                }
                if (request.generation == generation && output != null) {
                    _state.value = _state.value?.copy(preview = output, loading = false, previewStale = false)
                    output = null
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (request.generation == generation) { _state.value = _state.value?.copy(loading = false, previewStale = true); message(e.message ?: "无法预览") } }
            catch (_: OutOfMemoryError) { if (request.generation == generation) { _state.value = _state.value?.copy(loading = false, previewStale = true); message("照片过大，无法预览") } }
            finally { output?.takeIf { !it.isRecycled }?.recycle() }
        }
    }
    private var generation = 0L
    private fun timeFormat() = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).apply { isLenient = false }
    fun begin(uri: Uri, projectId: Long) {
        if (_state.value?.saving == true || loadJob?.isActive == true) return
        loadJob = scope.launch {
            try {
                val project = container.database.projectDao().getProjectById(projectId) ?: error("工程不存在")
                check(!project.isArchived && !project.isCaptureLocked) { "此工程已归档或锁定，无法导入" }
                val metadata = withContext(Dispatchers.IO) { ImportedPhotoSource.metadata(container.appContext, uri) }
                val dao = container.database.watermarkDao()
                val template = dao.getTemplateById(project.watermarkTemplateId) ?: dao.getDefaultTemplate()
                val rawFields = template?.let { dao.getFieldsForTemplateSync(it.id) }.orEmpty()
                val fields = resolveWatermarkFields(rawFields)
                val customFields = rawFields.filter { it.fieldKey !in BuiltInWatermarkFieldKeys.all }.map {
                    WatermarkFieldItem(it.fieldKey, it.label, it.defaultValue, it.isEnabled)
                }
                val timestamp = metadata.captureTimestamp ?: System.currentTimeMillis()
                // A saved address/GPS override belongs to the camera template, not to this imported image.
                val overrides = fields.systemValueOverrides - setOf(BuiltInWatermarkFieldKeys.DATE_TIME, BuiltInWatermarkFieldKeys.ADDRESS,
                    BuiltInWatermarkFieldKeys.GPS, BuiltInWatermarkFieldKeys.ELEVATION)
                val labels = fields.fieldLabels + (BuiltInWatermarkFieldKeys.DATE_TIME to if (metadata.captureTimestamp == null) "导入时间" else "拍摄时间")
                val data = WatermarkData(projectName = project.name, categoryName = project.categoryName,
                    captureTimestamp = timestamp, locationStatus = "UNAVAILABLE", addressText = "", userName = fields.userName,
                    enabledSystemFields = fields.enabledSystemFields, systemValueOverrides = overrides, customFields = customFields,
                    fieldLabels = labels, fieldOrder = fields.fieldOrder, styleType = template?.styleType ?: "CLASSIC",
                    fontSizeScale = template?.fontSizeScale ?: 1f, widthRatio = template?.widthRatio ?: .6f,
                    opacity = template?.opacity ?: .85f, marginDp = template?.marginDp ?: 16, position = template?.position ?: "BOTTOM_LEFT",
                    captureTimeZoneId = java.util.TimeZone.getDefault().id)
                val profile = container.settingsDataStore.photoQualityProfile.first()
                val bitmap = withContext(Dispatchers.IO) { ImportedPhotoSource.decode(container.appContext, uri, 1280, metadata.orientation) }
                source = bitmap
                _state.value = PhotoImportState(project, uri, metadata, data, timeText = timeFormat().format(Date(timestamp)), profile = profile)
                render()
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { message(e.message ?: "导入失败") }
            catch (_: OutOfMemoryError) { message("照片过大，无法导入") }
        }
    }
    fun dismiss() {
        if (_state.value?.saving == true) return
        generation++; loadJob?.cancel()
        _state.value = null
        source = null
    }
    fun update(transform: (WatermarkData) -> WatermarkData) {
        val current = _state.value ?: return
        if (current.saving) return
        _state.value = current.copy(data = transform(current.data))
        render()
    }
    fun timeChanged(text: String) {
        val current = _state.value ?: return
        if (current.saving) return
        val position = java.text.ParsePosition(0)
        val parsed = timeFormat().parse(text, position)?.takeIf { position.index == text.length }
        _state.value = current.copy(timeText = text, timeError = parsed == null,
            data = if (parsed != null) current.data.copy(captureTimestamp = parsed.time) else current.data)
        if (parsed != null) render()
    }
    private fun render() {
        val current = _state.value ?: return
        val original = source ?: return
        val expected = ++generation
        _state.value = current.copy(loading = true, previewStale = true)
        previews.trySend(PreviewRequest(expected, current, original))
    }
    fun save() {
        val current = _state.value ?: return
        if (current.saving || current.loading || current.timeError) return
        _state.value = current.copy(saving = true)
        scope.launch {
            var decodedSource: Bitmap? = null
            var output: Bitmap? = null
            var savedUri: Uri? = null
            var committed = false
            try {
                if (current.previewStale) {
                    // Never persist a style that was never successfully rendered
                    // for the current draft.  Retry once against the latest state.
                    render()
                    val previewed = kotlinx.coroutines.withTimeoutOrNull(30_000) {
                        state.first { it == null || !it.loading }
                    }
                    if (previewed == null || previewed.previewStale || previewed.preview == null) {
                        error("预览生成失败，请修改后重试")
                    }
                }
                MediaOperationCoordinator.withExclusive {
                    val profile = current.profile
                    val gallery = container.settingsDataStore.saveToSystemGallery.first()
                    val project = container.database.projectDao().getProjectById(current.project.id) ?: error("工程不存在")
                    check(!project.isArchived && !project.isCaptureLocked) { "此工程已归档或锁定，无法导入" }
                    withContext(NonCancellable + Dispatchers.IO) {
                        decodedSource = ImportedPhotoSource.decode(container.appContext, current.uri, profile.maxLongEdge, current.metadata.orientation)
                    }
                    withContext(NonCancellable) {
                        output = WatermarkBitmapRenderer.renderWatermarkOnBitmap(decodedSource!!, current.data, qualityProfile = profile)
                    }
                    currentCoroutineContext().ensureActive()
                    val result = withContext(NonCancellable + Dispatchers.IO) {
                        container.mediaStoreManager.savePhotoToMediaStore(output!!,
                            "IMPORT_${UUID.randomUUID()}.jpg", project.name, current.data, quality = profile.jpegQuality,
                            saveToSystemGallery = gallery).also { savedUri = it.uri }
                    }
                    currentCoroutineContext().ensureActive()
                    withContext(NonCancellable) {
                        container.database.withTransaction {
                            val destination = container.database.projectDao().getProjectById(project.id) ?: error("工程不存在")
                            check(!destination.isArchived && !destination.isCaptureLocked) { "此工程已归档或锁定，无法导入" }
                            container.database.mediaItemDao().insertMediaItem(MediaItemEntity(projectId = project.id,
                                contentUri = result.uri.toString(), filePath = result.filePath, fileName = result.fileName,
                                width = result.width, height = result.height, captureTimestamp = current.data.captureTimestamp,
                                addressText = current.data.addressText, locationStatus = "UNAVAILABLE",
                                watermarkSnapshotJson = WatermarkSnapshotCodec.encode(current.data)))
                        }
                        committed = true; savedUri = null
                    }
                }
                _state.value = null; source = null
                message("照片已保存")
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { message(e.message ?: "保存失败") }
            catch (_: OutOfMemoryError) { message("照片过大，请降低照片画质后重试") }
            finally {
                withContext(NonCancellable + Dispatchers.IO) { savedUri?.let { container.mediaStoreManager.deleteMediaUri(it) } }
                output?.takeIf { !it.isRecycled }?.recycle()
                decodedSource?.takeIf { !it.isRecycled }?.recycle()
                if (!committed) _state.value = _state.value?.copy(saving = false)
            }
        }
    }
}
