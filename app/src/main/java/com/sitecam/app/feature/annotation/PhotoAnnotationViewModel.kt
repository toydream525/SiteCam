package com.sitecam.app.feature.annotation

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.RectF
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.sitecam.app.core.database.entity.AnnotationEntity
import com.sitecam.app.core.database.entity.MediaItemEntity
import com.sitecam.app.core.di.AppContainer
import com.sitecam.app.core.media.MediaDeleteOutcome
import com.sitecam.app.core.watermark.model.WatermarkData
import com.sitecam.app.feature.annotation.model.AnnotationElement
import com.sitecam.app.feature.annotation.model.AnnotationTool
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

data class TextEditorState(val target: TextEditTarget?, val position: Offset, val text: String, val width: Float, val height: Float,
    val fontId: String = "", val color: Color = Color(0xFFF44336), val whiteboard: Boolean = false)

data class AnnotationUiState(
    val mediaItem: MediaItemEntity? = null,
    val selectedTool: AnnotationTool = AnnotationTool.ARROW,
    val selectedColor: Color = Color(0xFFF44336), // Engineering Red
    val selectedStrokeWidth: Float = 10f,
    val elements: List<AnnotationElement> = emptyList(),
    val undoStack: List<List<AnnotationElement>> = emptyList(),
    val previewBitmap: Bitmap? = null,
    val textDragPreview: Bitmap? = null,
    val textEditor: TextEditorState? = null,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val imageWidth: Int = 0,
    val imageHeight: Int = 0,
    val loadError: String? = null,
    val isSaving: Boolean = false
)

data class AnnotationSaveResult(
    val uri: Uri,
    val oldProductCleanupWarning: Boolean
)

class PhotoAnnotationViewModel(
    private val appContainer: AppContainer,
    private val mediaId: Long
) : ViewModel() {

    private val _uiState = MutableStateFlow(AnnotationUiState())
    val uiState: StateFlow<AnnotationUiState> = _uiState.asStateFlow()

    private val _saveCompleted = MutableSharedFlow<AnnotationSaveResult>()
    val saveCompleted: SharedFlow<AnnotationSaveResult> = _saveCompleted.asSharedFlow()
    private val _saveFailed = MutableSharedFlow<String>()
    val saveFailed: SharedFlow<String> = _saveFailed.asSharedFlow()

    private var steps = emptyList<EditStep>()
    private data class EditState(val steps: List<EditStep>, val elements: List<AnnotationElement>)
    private val history = mutableListOf<EditState>()
    private val future = mutableListOf<EditState>()
    private var sourceWidth = 1
    private var sourceHeight = 1
    private var sourceUri: Uri? = null
    private var legacyProduct = false
    private var recipeBaseUri: String? = null
    /** The stored base is required by non-empty vector text but cannot be read. */
    private var baseLost = false
    private data class TextPreviewRequest(val session: Long, val recipe: List<EditStep>)
    private var textPreviewSession = 0L
    private val textPreviews = kotlinx.coroutines.channels.Channel<TextPreviewRequest>(kotlinx.coroutines.channels.Channel.CONFLATED)
    private val textPreviewWorker = viewModelScope.launch {
        for (request in textPreviews) {
            if (request.session != textPreviewSession) continue
            var bitmap: Bitmap? = null
            try {
                withContext(Dispatchers.Default) {
                    bitmap = renderRecipe(appContainer.appContext, request.recipe, 1600)
                }
                // A newer pointer position does not invalidate a completed frame
                // in this drag. Only cancellation or an editor change ends it.
                if (request.session == textPreviewSession) {
                    _uiState.value = _uiState.value.copy(textDragPreview = bitmap)
                    bitmap = null
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                continue
            } catch (_: OutOfMemoryError) {
                continue
            } finally {
                bitmap?.recycle()
            }
            kotlinx.coroutines.delay(16)
        }
    }
    private var viewportWidth = 0f
    private var viewportHeight = 0f
    private var queuedViewport: Pair<Float, Float>? = null
    private var failedViewport: Pair<Float, Float>? = null
    private var failedViewportRetries = 0
    fun viewportChanged(width: Float, height: Float) {
        if (width <= 0 || height <= 0) return
        if (_uiState.value.loadError != null) { viewportWidth = width; viewportHeight = height; return }
        if (_uiState.value.isSaving) { queuedViewport = width to height; return }
        // A new size gets its own retry budget for the render attempt below.
        if (failedViewport != null && failedViewport != (width to height)) failedViewportRetries = 0
        failedViewport = null
        if (viewportWidth > 0 && viewportHeight > 0 && (width != viewportWidth || height != viewportHeight)) {
            fun bake(state: EditState): EditState = if (state.elements.isEmpty()) state else EditState(state.steps + EditStep.Draw(state.elements, viewportWidth, viewportHeight), emptyList())
            val nextHistory = history.map(::bake)
            val nextFuture = future.map(::bake)
            if (_uiState.value.elements.isNotEmpty()) {
                refreshPreview(bake(editState()), nextHistory, nextFuture,
                    onCommitted = { viewportWidth = width; viewportHeight = height; failedViewportRetries = 0 },
                    onFailed = {
                        // The one automatic retry for this size failed too: keep
                        // editing and saving usable instead of blocking forever.
                        if (failedViewportRetries >= 1) {
                            failedViewport = null
                            failedViewportRetries = 0
                            viewModelScope.launch { _saveFailed.emit("编辑视图尺寸处理失败，请重新调整后再试") }
                        } else {
                            failedViewport = width to height
                        }
                    })
                return
            }
            history.clear(); history.addAll(nextHistory)
            future.clear(); future.addAll(nextFuture)
        }
        viewportWidth = width; viewportHeight = height
    }
    private fun editState() = EditState(steps, _uiState.value.elements)
    private fun retryViewportIfNeeded(): Boolean {
        val failed = failedViewport ?: return false
        // Spend the single automatic retry for this size, then let the caller
        // continue: a second failure clears the flag in viewportChanged.
        failedViewportRetries += 1
        viewportChanged(failed.first, failed.second)
        return true
    }
    private fun checkpoint() {
        history.add(EditState(steps, _uiState.value.elements)); future.clear()
        _uiState.value = _uiState.value.copy(canUndo = true, canRedo = false)
    }
    fun applyTransform(kind: String, width: Float, height: Float, crop: androidx.compose.ui.geometry.Rect? = null) {
        if (_uiState.value.loadError != null || _uiState.value.isSaving || width <= 0 || height <= 0 || retryViewportIfNeeded()) return
        val before = editState()
        val draw = if (before.elements.isEmpty()) emptyList() else listOf(EditStep.Draw(before.elements, width, height))
        val next = EditState(steps + draw + EditStep.Transform(kind, crop?.left ?: 0f, crop?.top ?: 0f, crop?.right ?: 1f, crop?.bottom ?: 1f), emptyList())
        refreshPreview(next, history + before, emptyList())
    }
    private var previewGeneration = 0L
    private fun refreshPreview(
        next: EditState = editState(),
        nextHistory: List<EditState> = history.toList(),
        nextFuture: List<EditState> = future.toList(),
        onCommitted: () -> Unit = {},
        onFailed: () -> Unit = {}
    ) {
        val generation = ++previewGeneration
        textPreviewSession++
        _uiState.value = _uiState.value.copy(isSaving = true, textDragPreview = null)
        viewModelScope.launch {
            try {
                val rendered = withContext(Dispatchers.IO) { renderRecipe(appContainer.appContext, next.steps, 1600) }
                if (generation == previewGeneration) {
                    steps = next.steps
                    history.clear(); history.addAll(nextHistory)
                    future.clear(); future.addAll(nextFuture)
                    _uiState.value = _uiState.value.copy(previewBitmap = rendered, imageWidth = rendered.width,
                        imageHeight = rendered.height, elements = next.elements, textDragPreview = null,
                        canUndo = history.isNotEmpty(), canRedo = future.isNotEmpty())
                    onCommitted()
                } else rendered.recycle()
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (generation == previewGeneration) { onFailed(); _saveFailed.emit(e.message ?: "无法预览编辑") } }
            catch (_: OutOfMemoryError) { if (generation == previewGeneration) { onFailed(); _saveFailed.emit("图片太大，无法预览，请关闭其他应用后重试") } }
            finally {
                if (generation == previewGeneration) {
                    _uiState.value = _uiState.value.copy(isSaving = false)
                    queuedViewport?.let { queuedViewport = null; viewportChanged(it.first, it.second) }
                }
            }
        }
    }
    private fun renderRecipe(context: Context, recipe: List<EditStep>, decodeLimit: Int = Int.MAX_VALUE): Bitmap {
        val source = decodeBitmapBounded(context, sourceUri ?: error("原图未就绪"), decodeLimit)
        var bitmap = source.copy(Bitmap.Config.ARGB_8888, true)
        source.recycle()
        for (step in recipe) when (step) {
            is EditStep.Draw -> {
                val rect = ImageContentRect.forFit(bitmap.width, bitmap.height, step.width, step.height)
                val canvas = Canvas(bitmap)
                canvas.save(); canvas.clipRect(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat())
                step.elements.forEach { renderElementOnCanvas(canvas, it, rect, bitmap.width, bitmap.height) }; canvas.restore()
            }
            is EditStep.Transform -> {
                val next = EditRecipe.transform(bitmap, step)
                if (next !== bitmap) bitmap.recycle()
                bitmap = if (next.isMutable) next else next.copy(Bitmap.Config.ARGB_8888, true).also { next.recycle() }
            }
        }
        return bitmap
    }

    init {
        loadMedia()
    }

    private fun loadMedia() {
        viewModelScope.launch {
            val item = appContainer.database.mediaItemDao().getMediaItemById(mediaId)
            if (item == null) {
                _uiState.value = _uiState.value.copy(mediaItem = null)
                return@launch
            }
            val resolvedItem = withContext(Dispatchers.IO) {
                runCatching {
                    val resolvedUri = resolveReadableSourceUri(appContainer.appContext, item)
                    if (resolvedUri.toString() == item.contentUri) item else item.copy(contentUri = resolvedUri.toString())
                }.getOrDefault(item)
            }
            val dimensions = if (resolvedItem.width > 0 && resolvedItem.height > 0) {
                resolvedItem.width to resolvedItem.height
            } else {
                imageDimensions(Uri.parse(resolvedItem.contentUri)) ?: (0 to 0)
            }
            val annotation = appContainer.database.issueDao().getAnnotationByMediaId(mediaId)
            val rawRecipe = annotation?.vectorDataJson
            val decodedRecipe = rawRecipe?.let(EditRecipe::decodeOrNull)
            val restored = decodedRecipe.orEmpty()
            val malformedRecipe = !rawRecipe.isNullOrBlank() && decodedRecipe == null
            val storedBase = annotation?.vectorDataJson?.let(EditRecipe::baseUri)
            baseLost = false
            legacyProduct = false
            recipeBaseUri = storedBase
            val stored = storedBase?.let(Uri::parse)
            val storedReadable = stored != null && isReadable(stored)
            // A failed save may already have copied the base into app-private
            // storage even though the annotation row was never replaced.
            val baseCandidate = if (!storedReadable && (storedBase != null || restored.isEmpty()))
                AnnotationBaseStore.existing(appContainer.appContext, mediaId)
            else null
            val recoverableBase = if (baseCandidate != null && isReadable(baseCandidate)) baseCandidate else null
            when {
                annotation == null -> {
                    sourceUri = Uri.parse(resolvedItem.contentUri)
                    steps = emptyList()
                }
                malformedRecipe -> {
                    // Preserve the stored row instead of flattening a recipe we
                    // cannot read; display the last product and block saving.
                    baseLost = true
                    sourceUri = Uri.parse(annotation.annotatedContentUri)
                    legacyProduct = true
                    steps = emptyList()
                }
                storedReadable -> {
                    sourceUri = stored!!
                    steps = restored
                }
                recoverableBase != null -> {
                    sourceUri = recoverableBase
                    recipeBaseUri = recoverableBase.toString()
                    steps = restored
                }
                storedBase != null && restored.isEmpty() -> {
                    // No vector record can be lost; the flattened product is the
                    // only remaining source and becomes a base on the next save.
                    sourceUri = Uri.parse(annotation.annotatedContentUri)
                    recipeBaseUri = annotation.annotatedContentUri
                    legacyProduct = true
                    steps = emptyList()
                }
                storedBase != null -> {
                    // Do not silently flatten a recipe whose base disappeared:
                    // the old JSON stays untouched and saving is blocked.
                    baseLost = true
                    sourceUri = Uri.parse(annotation.annotatedContentUri)
                    legacyProduct = true
                    steps = emptyList()
                }
                restored.isEmpty() -> {
                    // Legacy flattened product without a base pointer.
                    sourceUri = Uri.parse(annotation.annotatedContentUri)
                    recipeBaseUri = annotation.annotatedContentUri
                    legacyProduct = true
                    steps = emptyList()
                }
                else -> {
                    sourceUri = Uri.parse(resolvedItem.contentUri)
                    recipeBaseUri = null
                    steps = restored
                }
            }
            val sourceDimensions = imageDimensions(sourceUri!!)
            if (sourceDimensions == null) {
                baseLost = true
                _uiState.value = _uiState.value.copy(
                    mediaItem = resolvedItem.copy(contentUri = sourceUri.toString()),
                    imageWidth = dimensions.first.coerceAtLeast(0),
                    imageHeight = dimensions.second.coerceAtLeast(0),
                    loadError = if (malformedRecipe) "标注记录已损坏，且底图或成品无法读取，无法继续编辑"
                        else "标注底图或成品无法读取，无法继续编辑"
                )
                return@launch
            }
            sourceWidth = sourceDimensions.first
            sourceHeight = sourceDimensions.second
            steps = if (legacyProduct) emptyList() else restored
            _uiState.value = _uiState.value.copy(
                mediaItem = resolvedItem,
                imageWidth = dimensions.first.coerceAtLeast(0),
                imageHeight = dimensions.second.coerceAtLeast(0),
                loadError = when {
                    malformedRecipe -> "标注记录已损坏，无法继续编辑"
                    baseLost -> "标注底图已丢失，无法继续编辑"
                    else -> null
                }
            )
            refreshPreview()
        }
    }

    private suspend fun imageDimensions(uri: Uri): Pair<Int, Int>? = withContext(Dispatchers.IO) {
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            appContainer.appContext.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
            if (bounds.outWidth > 0 && bounds.outHeight > 0) bounds.outWidth to bounds.outHeight else null
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        } catch (_: OutOfMemoryError) {
            null
        }
    }

    fun selectTool(tool: AnnotationTool) {
        if (_uiState.value.loadError != null) return
        _uiState.value = _uiState.value.copy(selectedTool = tool)
    }

    fun selectColor(color: Color) {
        if (_uiState.value.loadError != null) return
        _uiState.value = _uiState.value.copy(selectedColor = color)
    }

    fun selectStrokeWidth(width: Float) {
        if (_uiState.value.loadError != null) return
        _uiState.value = _uiState.value.copy(selectedStrokeWidth = width)
    }

    fun addElement(element: AnnotationElement): Boolean {
        if (_uiState.value.loadError != null || _uiState.value.isSaving || retryViewportIfNeeded()) return false
        checkpoint(); _uiState.value = _uiState.value.copy(elements = _uiState.value.elements + element)
        return true
    }
    fun openTextEditor(target: TextEditTarget?, position: Offset, width: Float, height: Float) {
        if (_uiState.value.loadError != null) return
        _uiState.value = _uiState.value.copy(textEditor = TextEditorState(target, position, target?.note?.text.orEmpty(), width, height,
            target?.note?.fontId.orEmpty(), target?.note?.color ?: _uiState.value.selectedColor, target?.note?.whiteboard ?: false))
    }
    fun textFontChanged(fontId: String) { _uiState.value.textEditor?.let { _uiState.value = _uiState.value.copy(textEditor = it.copy(fontId = fontId)) } }
    fun textColorChanged(value: String?) { if (value != null) _uiState.value.textEditor?.let { _uiState.value = _uiState.value.copy(textEditor = it.copy(color = Color(android.graphics.Color.parseColor(value)))) } }
    fun textBoardChanged(value: Boolean) { _uiState.value.textEditor?.let { _uiState.value = _uiState.value.copy(textEditor = it.copy(whiteboard = value)) } }
    fun textDraftChanged(text: String) {
        if (_uiState.value.loadError != null) return
        _uiState.value.textEditor?.let { _uiState.value = _uiState.value.copy(textEditor = it.copy(text = text)) }
    }
    fun dismissTextEditor() { _uiState.value = _uiState.value.copy(textEditor = null) }
    /** Back/outside taps must not race a confirmed edit that is still rendering. */
    fun dismissTextEditorIfIdle() { if (!_uiState.value.isSaving) dismissTextEditor() }
    fun confirmTextEditor() {
        val editor = _uiState.value.textEditor ?: return
        if (_uiState.value.loadError != null || _uiState.value.isSaving || editor.text.isBlank()) return
        val note = editor.target?.note?.copy(text = editor.text, color = editor.color, fontId = editor.fontId, whiteboard = editor.whiteboard) ?: AnnotationElement.TextNote(editor.position,
            editor.text, editor.color, _uiState.value.selectedStrokeWidth, editor.fontId, editor.whiteboard)
        when {
            editor.target != null -> updateText(editor.target, note) { dismissTextEditor() }
            viewportWidth == editor.width && viewportHeight == editor.height -> {
                if (addElement(note)) dismissTextEditor()
                else viewModelScope.launch { _saveFailed.emit("编辑视图正在恢复，请稍后重试") }
            }
            else -> {
                // The dialog captures the canvas before the keyboard resize.  Bake
                // all live elements with that same canvas, keeping the new note on
                // top instead of letting older elements paint over it.
                val live = _uiState.value.elements + note
                refreshPreview(EditState(steps + EditStep.Draw(live, editor.width, editor.height), emptyList()),
                    history + editState(), emptyList(), onCommitted = { dismissTextEditor() })
            }
        }
    }
    fun textTargets(width: Float, height: Float): List<TextEditTarget> =
        EditableTextRecipe.targets(steps, _uiState.value.elements, sourceWidth, sourceHeight, width, height)

    private fun changedText(target: TextEditTarget, note: AnnotationElement.TextNote): EditState? {
        if (target.stepIndex == null && _uiState.value.elements.getOrNull(target.elementIndex) == target.note) {
            return EditState(steps, _uiState.value.elements.toMutableList().apply { set(target.elementIndex, note) })
        }
        // Opening the keyboard can resize the canvas and move a live vector
        // batch into the recipe. Keep the dialog attached to that same note.
        val stepIndex = target.stepIndex?.takeIf { (steps.getOrNull(it) as? EditStep.Draw)?.elements?.getOrNull(target.elementIndex) == target.note }
            ?: steps.indices.reversed().firstOrNull { index -> (steps[index] as? EditStep.Draw)?.elements?.contains(target.note) == true }
            ?: return null
        val draw = steps[stepIndex] as EditStep.Draw
        val index = if (target.stepIndex == stepIndex) target.elementIndex else draw.elements.indexOfLast { it == target.note }
        return EditState(steps.toMutableList().apply {
            set(stepIndex, draw.copy(elements = draw.elements.toMutableList().apply { set(index, note) }))
        }, _uiState.value.elements)
    }
    fun updateText(target: TextEditTarget, note: AnnotationElement.TextNote, onCommitted: (() -> Unit)? = null) {
        if (_uiState.value.loadError != null || _uiState.value.isSaving) { clearTextPreview(); return }
        if (target.note == note) { clearTextPreview(); onCommitted?.invoke(); return }
        val next = changedText(target, note)
        if (next == null) {
            clearTextPreview()
            viewModelScope.launch { _saveFailed.emit("文字位置已变化，请重新选择") }
            return
        }
        clearTextPreview()
        refreshPreview(next, history + editState(), emptyList(), onCommitted = { onCommitted?.invoke() })
    }
    fun clearTextPreview() {
        textPreviewSession++
        _uiState.value = _uiState.value.copy(textDragPreview = null)
    }
    fun previewText(target: TextEditTarget, note: AnnotationElement.TextNote, width: Float, height: Float) {
        if (_uiState.value.loadError != null) return
        val next = changedText(target, note) ?: return
        val recipe = next.steps + if (next.elements.isEmpty()) emptyList() else listOf(EditStep.Draw(next.elements, width, height))
        // Only pending positions are conflated; an in-flight frame from the same
        // session may be displayed even when more Move events have arrived.
        textPreviews.trySend(TextPreviewRequest(textPreviewSession, recipe))
    }
    fun undo() {
        if (_uiState.value.loadError != null || _uiState.value.isSaving || history.isEmpty() || retryViewportIfNeeded()) return
        refreshPreview(history.last(), history.dropLast(1), future + editState())
    }
    fun redo() {
        if (_uiState.value.loadError != null || _uiState.value.isSaving || future.isEmpty() || retryViewportIfNeeded()) return
        refreshPreview(future.last(), history + editState(), future.dropLast(1))
    }
    fun clearAll() {
        if (_uiState.value.loadError == null && !_uiState.value.isSaving && !retryViewportIfNeeded()) refreshPreview(EditState(emptyList(), emptyList()), history + editState(), emptyList())
    }

    fun saveAnnotatedImage(
        context: Context,
        viewWidth: Float,
        viewHeight: Float
    ) {
        val media = _uiState.value.mediaItem ?: return
        _uiState.value.loadError?.let { error -> viewModelScope.launch { _saveFailed.emit(error) }; return }
        if (_uiState.value.isSaving || viewWidth <= 0 || viewHeight <= 0 || retryViewportIfNeeded()) return
        if (baseLost) {
            viewModelScope.launch { _saveFailed.emit("标注底图已丢失，无法保存；旧记录不会被覆盖") }
            return
        }
        clearTextPreview()
        val recipe = steps + if (_uiState.value.elements.isEmpty()) emptyList() else listOf(EditStep.Draw(_uiState.value.elements, viewWidth, viewHeight))

        _uiState.value = _uiState.value.copy(isSaving = true)

        viewModelScope.launch(Dispatchers.IO) {
            val previousBaseUri = recipeBaseUri
            com.sitecam.app.core.media.MediaOperationCoordinator.withExclusive {
            var originalBitmap: Bitmap? = null
            var annotatedBitmap: Bitmap? = null
            var savedUri: Uri? = null
            try {
                check(appContainer.database.mediaItemDao().getMediaItemById(media.id) != null) { "媒体已删除" }
                // The base must be app-private before it is written into the
                // recipe.  System-gallery products can be deleted by the user.
                if (legacyProduct || previousBaseUri?.let { !AnnotationBaseStore.isStoredBase(context, media.id, Uri.parse(it)) } == true) {
                    val storedBase = AnnotationBaseStore.store(context, media.id, sourceUri ?: error("原图未就绪"))
                        ?: error("标注底图保存失败，请重试")
                    sourceUri = storedBase
                    recipeBaseUri = storedBase.toString()
                }
                val profile = appContainer.settingsDataStore.photoQualityProfile.first()
                val gallery = appContainer.settingsDataStore.saveToSystemGallery.first()
                val sourceSize = sourceWidth to sourceHeight
                var effectiveWidth = sourceSize.first.toDouble()
                var effectiveHeight = sourceSize.second.toDouble()
                recipe.filterIsInstance<EditStep.Transform>().forEach { step ->
                    if (step.kind == "crop") { effectiveWidth *= step.right - step.left; effectiveHeight *= step.bottom - step.top }
                    if (step.kind == "rotate") { val swap = effectiveWidth; effectiveWidth = effectiveHeight; effectiveHeight = swap }
                }
                val decodeLimit = if (legacyProduct || profile.maxLongEdge == null || maxOf(effectiveWidth, effectiveHeight) <= 0) Int.MAX_VALUE
                    else (maxOf(sourceSize.first, sourceSize.second) * minOf(1.0, profile.maxLongEdge.toDouble() / maxOf(effectiveWidth, effectiveHeight))).toInt().coerceAtLeast(1)
                val rendered = renderRecipe(context, recipe, decodeLimit)
                originalBitmap = rendered
                val workingBitmap = com.sitecam.app.core.media.PhotoCompression.resize(rendered, profile)
                annotatedBitmap = workingBitmap

                val project = appContainer.database.projectDao().getProjectById(media.projectId)
                val projectName = project?.name ?: "默认工程"
                val annotatedFileName = media.fileName.substringBeforeLast(".") + "_annotated.jpg"

                val saveResult = appContainer.mediaStoreManager.savePhotoToMediaStore(
                    bitmap = workingBitmap,
                    fileName = annotatedFileName,
                    projectName = projectName,
                    watermarkData = WatermarkData(
                        projectName = projectName,
                        captureTimestamp = media.captureTimestamp,
                        latitude = media.latitude,
                        longitude = media.longitude,
                        altitude = media.altitude,
                        locationStatus = media.locationStatus,
                        captureTimeZoneId = com.sitecam.app.core.watermark.model.WatermarkSnapshotCodec
                            .decode(media.watermarkSnapshotJson)?.captureTimeZoneId,
                        addressText = media.addressText
                    ),
                    quality = profile.jpegQuality,
                    saveToSystemGallery = gallery
                )
                savedUri = saveResult.uri

                // Keep the Room pointer valid throughout replacement: publish
                // the new physical product first, atomically replace the DB
                // pointer, then clean the old product best-effort.  A cleanup
                // failure must never roll the pointer back to a deleted file.
                val oldAnnotation = appContainer.database.issueDao().getAnnotationByMediaId(media.id)
                appContainer.database.issueDao().replaceAnnotation(
                    AnnotationEntity(
                        mediaId = media.id,
                        annotatedContentUri = saveResult.uri.toString(),
                        vectorDataJson = EditRecipe.encode(recipe, recipeBaseUri)
                    )
                )
                // Ownership is transferred to Room at this point.  The new
                // product must never be deleted by a later cancellation or
                // exception while cleaning up the old product/emitting UI.
                savedUri = null

                val oldCleanupWarning = oldAnnotation
                    ?.takeIf { it.annotatedContentUri != recipeBaseUri && it.annotatedContentUri != media.contentUri }
                    ?.let { appContainer.mediaStoreManager.deleteMediaUriDetailed(Uri.parse(it.annotatedContentUri)) == MediaDeleteOutcome.FAILED }
                    ?: false
                val oldBaseCleanupWarning = previousBaseUri
                    ?.takeIf { it != recipeBaseUri && it != oldAnnotation?.annotatedContentUri && it != media.contentUri }
                    ?.let { appContainer.mediaStoreManager.deleteMediaUriDetailed(Uri.parse(it)) == MediaDeleteOutcome.FAILED }
                    ?: false
                _saveCompleted.emit(AnnotationSaveResult(saveResult.uri, oldCleanupWarning || oldBaseCleanupWarning))
            } catch (e: CancellationException) {
                // Before DB replacement, the temporary product is ours to
                // clean up.  After replacement savedUri is null, so
                // cancellation cannot invalidate the committed pointer.
                savedUri?.let { appContainer.mediaStoreManager.deleteMediaUri(it) }
                throw e
            } catch (e: Exception) {
                savedUri?.let { appContainer.mediaStoreManager.deleteMediaUri(it) }
                _saveFailed.emit(e.message ?: "保存标注图失败")
            } catch (e: OutOfMemoryError) {
                savedUri?.let { appContainer.mediaStoreManager.deleteMediaUri(it) }
                _saveFailed.emit("图片过大，已限制采样尺寸，请降低原图分辨率后重试")
            } finally {
                annotatedBitmap?.takeIf { !it.isRecycled }?.recycle()
                if (originalBitmap !== annotatedBitmap) {
                    originalBitmap?.takeIf { !it.isRecycled }?.recycle()
                }
                _uiState.value = _uiState.value.copy(isSaving = false)
            }
            }
        }
    }

    private suspend fun isReadable(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            appContainer.appContext.contentResolver.openInputStream(uri)?.use { true } ?: false
        }.getOrDefault(false)
    }

    private fun resolveReadableSourceUri(context: Context, media: MediaItemEntity): Uri {
        val resolver = context.contentResolver
        val original = Uri.parse(media.contentUri)
        val originalReadable = runCatching {
            resolver.openFileDescriptor(original, "r")?.use { true } ?: false
        }.getOrDefault(false)
        if (originalReadable) return original

        throw IllegalStateException("无法读取原始图片，请确认照片仍存在于 SiteCam 工程相册")
    }

    private fun decodeBitmapBounded(context: Context, uri: Uri, maxDimension: Int = 4096): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val boundsStream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("无法读取原始图片")
        boundsStream.use { BitmapFactory.decodeStream(it, null, bounds) }
        // Bounds-only decoding deliberately returns null; only outWidth/outHeight
        // indicate whether the source was decoded successfully.
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw IllegalStateException("原始图片尺寸无效")
        }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxDimension && sample < 128) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: throw IllegalStateException("无法解码原始图片")
    }

    private fun renderElementOnCanvas(
        canvas: Canvas,
        element: AnnotationElement,
        contentRect: ImageContentRect,
        bitmapWidth: Int,
        bitmapHeight: Int
    ) {
        val scaleX = bitmapWidth / contentRect.width
        val scaleY = bitmapHeight / contentRect.height
        val avgScale = (scaleX + scaleY) / 2f
        fun map(point: androidx.compose.ui.geometry.Offset): androidx.compose.ui.geometry.Offset =
            contentRect.toBitmap(point)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = element.color.toArgb()
            strokeWidth = element.strokeWidth * avgScale
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        when (element) {
            is AnnotationElement.Arrow -> {
                val start = map(element.start)
                val end = map(element.end)
                val startX = start.x
                val startY = start.y
                val endX = end.x
                val endY = end.y

                canvas.drawLine(startX, startY, endX, endY, paint)

                // Arrow head math
                val angle = atan2((endY - startY).toDouble(), (endX - startX).toDouble())
                val headLength = 32f * avgScale
                val headAngle = Math.PI / 6

                val x1 = endX - headLength * cos(angle - headAngle).toFloat()
                val y1 = endY - headLength * sin(angle - headAngle).toFloat()
                val x2 = endX - headLength * cos(angle + headAngle).toFloat()
                val y2 = endY - headLength * sin(angle + headAngle).toFloat()

                val path = Path().apply {
                    moveTo(endX, endY)
                    lineTo(x1, y1)
                    moveTo(endX, endY)
                    lineTo(x2, y2)
                }
                canvas.drawPath(path, paint)
            }
            is AnnotationElement.Rectangle -> {
                val topLeft = map(element.topLeft)
                val bottomRight = map(element.bottomRight)
                val left = topLeft.x
                val top = topLeft.y
                val right = bottomRight.x
                val bottom = bottomRight.y
                canvas.drawRect(left, top, right, bottom, paint)
            }
            is AnnotationElement.Circle -> {
                val center = map(element.center)
                val cx = center.x
                val cy = center.y
                val r = element.radius * avgScale
                canvas.drawCircle(cx, cy, r, paint)
            }
            is AnnotationElement.Freehand -> {
                if (element.points.size > 1) {
                    val path = Path().apply {
                        val first = map(element.points[0])
                        moveTo(first.x, first.y)
                        for (i in 1 until element.points.size) {
                            val point = map(element.points[i])
                            lineTo(point.x, point.y)
                        }
                    }
                    canvas.drawPath(path, paint)
                }
            }
            is AnnotationElement.TextNote -> {
                val layout = TextNoteGeometry.layout(
                    position = element.position,
                    text = element.text,
                    contentRect = contentRect.rect,
                    fontId = element.fontId
                )
                val bubbleTopLeft = map(layout.bubbleRect.topLeft)
                val bubbleBottomRight = map(layout.bubbleRect.bottomRight)
                val baseline = map(layout.baseline)
                val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = element.color.toArgb()
                    textSize = TextNoteGeometry.TEXT_SIZE * avgScale
                    typeface = com.sitecam.app.core.text.FontLibrary.typeface(element.fontId); isFakeBoldText = true
                }
                // Background bubble for high contrast
                val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = android.graphics.Color.parseColor(if (element.whiteboard) "#FAFAF7" else "#CC121212")
                    style = Paint.Style.FILL
                }
                canvas.drawRoundRect(
                    bubbleTopLeft.x,
                    bubbleTopLeft.y,
                    bubbleBottomRight.x,
                    bubbleBottomRight.y,
                    8f * avgScale,
                    8f * avgScale,
                    bgPaint
                )
                if (element.whiteboard) {
                    bgPaint.style = Paint.Style.STROKE; bgPaint.strokeWidth = 2f * avgScale
                    bgPaint.color = android.graphics.Color.parseColor("#17212B")
                    canvas.drawRoundRect(bubbleTopLeft.x, bubbleTopLeft.y, bubbleBottomRight.x, bubbleBottomRight.y, 4f * avgScale, 4f * avgScale, bgPaint)
                }
                canvas.drawText(layout.text, baseline.x, baseline.y, textPaint)
            }
            is AnnotationElement.Mosaic -> {
                drawMosaicMask(
                    canvas = canvas,
                    points = element.points.map(::map),
                    strokeWidth = element.strokeWidth * avgScale
                )
            }
        }
    }

    /**
     * A bounded block mask is intentional: it obscures pixels without
     * allocating a second full-resolution bitmap (which previously made a
     * long mosaic stroke an OOM risk). The same block geometry is rendered in
     * the Compose preview.
     */
    private fun drawMosaicMask(canvas: Canvas, points: List<androidx.compose.ui.geometry.Offset>, strokeWidth: Float) {
        if (points.isEmpty()) return
        val cellSize = (strokeWidth / 3f).coerceIn(4f, 96f)
        val half = (strokeWidth / 2f).coerceAtLeast(cellSize)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val samples = MosaicGeometry.samplePolyline(points, (cellSize / 2f).coerceAtLeast(1f))
        for (point in samples) {
            val minX = kotlin.math.floor((point.x - half) / cellSize).toInt()
            val maxX = kotlin.math.floor((point.x + half) / cellSize).toInt()
            val minY = kotlin.math.floor((point.y - half) / cellSize).toInt()
            val maxY = kotlin.math.floor((point.y + half) / cellSize).toInt()
            for (x in minX..maxX) {
                for (y in minY..maxY) {
                    paint.color = if ((x + y) and 1 == 0) {
                        android.graphics.Color.rgb(24, 24, 24)
                    } else {
                        android.graphics.Color.rgb(72, 72, 72)
                    }
                    canvas.drawRect(
                        RectF(x * cellSize, y * cellSize, (x + 1) * cellSize, (y + 1) * cellSize),
                        paint
                    )
                }
            }
        }
    }

    companion object {
        fun provideFactory(appContainer: AppContainer, mediaId: Long): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return PhotoAnnotationViewModel(appContainer, mediaId) as T
                }
            }
    }
}
