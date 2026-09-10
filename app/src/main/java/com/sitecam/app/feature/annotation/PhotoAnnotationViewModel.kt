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

data class AnnotationUiState(
    val mediaItem: MediaItemEntity? = null,
    val selectedTool: AnnotationTool = AnnotationTool.ARROW,
    val selectedColor: Color = Color(0xFFF44336), // Engineering Red
    val selectedStrokeWidth: Float = 10f,
    val elements: List<AnnotationElement> = emptyList(),
    val undoStack: List<List<AnnotationElement>> = emptyList(),
    val previewBitmap: Bitmap? = null,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val imageWidth: Int = 0,
    val imageHeight: Int = 0,
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
    private var sourceUri: Uri? = null
    private var legacyProduct = false
    private var viewportWidth = 0f
    private var viewportHeight = 0f
    fun viewportChanged(width: Float, height: Float) {
        if (width <= 0 || height <= 0) return
        if (viewportWidth > 0 && viewportHeight > 0 && (width != viewportWidth || height != viewportHeight)) {
            fun bake(state: EditState): EditState = if (state.elements.isEmpty()) state else EditState(state.steps + EditStep.Draw(state.elements, viewportWidth, viewportHeight), emptyList())
            for (index in history.indices) history[index] = bake(history[index])
            for (index in future.indices) future[index] = bake(future[index])
            if (_uiState.value.elements.isNotEmpty()) {
                steps = steps + EditStep.Draw(_uiState.value.elements, viewportWidth, viewportHeight)
                _uiState.value = _uiState.value.copy(elements = emptyList())
                refreshPreview()
            }
        }
        viewportWidth = width; viewportHeight = height
    }
    private fun checkpoint() {
        history.add(EditState(steps, _uiState.value.elements)); future.clear()
        _uiState.value = _uiState.value.copy(canUndo = true, canRedo = false)
    }
    fun applyTransform(kind: String, width: Float, height: Float, crop: androidx.compose.ui.geometry.Rect? = null) {
        if (_uiState.value.isSaving || width <= 0 || height <= 0) return
        checkpoint()
        if (_uiState.value.elements.isNotEmpty()) steps = steps + EditStep.Draw(_uiState.value.elements, width, height)
        steps = steps + EditStep.Transform(kind, crop?.left ?: 0f, crop?.top ?: 0f, crop?.right ?: 1f, crop?.bottom ?: 1f)
        _uiState.value = _uiState.value.copy(elements = emptyList())
        refreshPreview()
    }
    private var previewGeneration = 0L
    private fun refreshPreview() {
        val generation = ++previewGeneration
        val recipe = steps
        _uiState.value = _uiState.value.copy(isSaving = true)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val rendered = renderRecipe(appContainer.appContext, recipe, 1600)
                if (generation == previewGeneration) {
                    _uiState.value = _uiState.value.copy(previewBitmap = rendered, imageWidth = rendered.width, imageHeight = rendered.height)
                } else rendered.recycle()
            } catch (e: Exception) { _saveFailed.emit(e.message ?: "无法预览编辑") }
            catch (_: OutOfMemoryError) { _saveFailed.emit("图片太大，无法预览，请关闭其他应用后重试") }
            finally { if (generation == previewGeneration) _uiState.value = _uiState.value.copy(isSaving = false) }
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
                withContext(Dispatchers.IO) {
                    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    appContainer.appContext.contentResolver.openInputStream(Uri.parse(resolvedItem.contentUri))?.use {
                        BitmapFactory.decodeStream(it, null, options)
                    }
                    options.outWidth to options.outHeight
                }
            }
            val annotation = appContainer.database.issueDao().getAnnotationByMediaId(mediaId)
            val restored = annotation?.vectorDataJson?.let(EditRecipe::decode).orEmpty()
            legacyProduct = annotation != null && restored.isEmpty()
            sourceUri = if (legacyProduct) Uri.parse(annotation!!.annotatedContentUri) else Uri.parse(resolvedItem.contentUri)
            steps = restored
            _uiState.value = _uiState.value.copy(
                mediaItem = resolvedItem,
                imageWidth = dimensions.first.coerceAtLeast(0),
                imageHeight = dimensions.second.coerceAtLeast(0)
            )
            refreshPreview()
        }
    }

    fun selectTool(tool: AnnotationTool) {
        _uiState.value = _uiState.value.copy(selectedTool = tool)
    }

    fun selectColor(color: Color) {
        _uiState.value = _uiState.value.copy(selectedColor = color)
    }

    fun selectStrokeWidth(width: Float) {
        _uiState.value = _uiState.value.copy(selectedStrokeWidth = width)
    }

    fun addElement(element: AnnotationElement) {
        if (_uiState.value.isSaving) return
        checkpoint(); _uiState.value = _uiState.value.copy(elements = _uiState.value.elements + element)
    }
    fun undo() {
        if (_uiState.value.isSaving || history.isEmpty()) return
        future.add(EditState(steps, _uiState.value.elements))
        val previous = history.removeAt(history.lastIndex); steps = previous.steps
        _uiState.value = _uiState.value.copy(elements = previous.elements, canUndo = history.isNotEmpty(), canRedo = true)
        refreshPreview()
    }
    fun redo() {
        if (_uiState.value.isSaving || future.isEmpty()) return
        history.add(EditState(steps, _uiState.value.elements))
        val next = future.removeAt(future.lastIndex); steps = next.steps
        _uiState.value = _uiState.value.copy(elements = next.elements, canUndo = true, canRedo = future.isNotEmpty())
        refreshPreview()
    }
    fun clearAll() { if (!_uiState.value.isSaving) { checkpoint(); steps = emptyList(); _uiState.value = _uiState.value.copy(elements = emptyList()); refreshPreview() } }

    fun saveAnnotatedImage(
        context: Context,
        viewWidth: Float,
        viewHeight: Float
    ) {
        val media = _uiState.value.mediaItem ?: return
        if (_uiState.value.isSaving || viewWidth <= 0 || viewHeight <= 0) return
        val recipe = steps + if (_uiState.value.elements.isEmpty()) emptyList() else listOf(EditStep.Draw(_uiState.value.elements, viewWidth, viewHeight))

        _uiState.value = _uiState.value.copy(isSaving = true)

        viewModelScope.launch(Dispatchers.IO) {
            com.sitecam.app.core.media.MediaOperationCoordinator.withExclusive {
            var originalBitmap: Bitmap? = null
            var annotatedBitmap: Bitmap? = null
            var savedUri: Uri? = null
            try {
                check(appContainer.database.mediaItemDao().getMediaItemById(media.id) != null) { "媒体已删除" }
                val profile = appContainer.settingsDataStore.photoQualityProfile.first()
                val gallery = appContainer.settingsDataStore.saveToSystemGallery.first()
                val sourceSize = _uiState.value.mediaItem?.let { it.width to it.height } ?: (0 to 0)
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
                        vectorDataJson = if (legacyProduct) "" else EditRecipe.encode(recipe)
                    )
                )
                // Ownership is transferred to Room at this point.  The new
                // product must never be deleted by a later cancellation or
                // exception while cleaning up the old product/emitting UI.
                savedUri = null

                val oldCleanupWarning = oldAnnotation?.let {
                    appContainer.mediaStoreManager.deleteMediaUriDetailed(Uri.parse(it.annotatedContentUri)) == MediaDeleteOutcome.FAILED
                } ?: false
                _saveCompleted.emit(AnnotationSaveResult(saveResult.uri, oldCleanupWarning))
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
                    contentRect = contentRect.rect
                )
                val bubbleTopLeft = map(layout.bubbleRect.topLeft)
                val bubbleBottomRight = map(layout.bubbleRect.bottomRight)
                val baseline = map(layout.baseline)
                val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = element.color.toArgb()
                    textSize = TextNoteGeometry.TEXT_SIZE * avgScale
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                }
                // Background bubble for high contrast
                val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = android.graphics.Color.parseColor("#CC121212")
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
                        android.graphics.Color.argb(245, 24, 24, 24)
                    } else {
                        android.graphics.Color.argb(230, 72, 72, 72)
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
