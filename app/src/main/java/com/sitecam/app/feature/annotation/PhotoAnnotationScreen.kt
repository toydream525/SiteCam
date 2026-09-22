package com.sitecam.app.feature.annotation

import android.graphics.Paint
import android.graphics.Typeface
import android.widget.Toast
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.FormatShapes
import androidx.compose.material.icons.filled.Grain
import androidx.compose.material.icons.filled.NorthEast
import androidx.compose.material.icons.filled.PanoramaFishEye
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.activity.compose.BackHandler
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.sitecam.app.feature.annotation.model.AnnotationElement
import com.sitecam.app.feature.annotation.model.AnnotationTool
import com.sitecam.app.ui.theme.DarkBackground
import com.sitecam.app.ui.theme.DarkCard
import com.sitecam.app.ui.theme.EngineeringYellow
import com.sitecam.app.ui.theme.ErrorRed
import com.sitecam.app.ui.theme.SuccessGreen
import com.sitecam.app.ui.theme.TextPrimaryDark
import com.sitecam.app.ui.theme.TextSecondaryDark
import com.sitecam.app.ui.theme.WarningYellow
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoAnnotationScreen(
    viewModel: PhotoAnnotationViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    var dragStart by remember { mutableStateOf<Offset?>(null) }
    var dragCurrent by remember { mutableStateOf<Offset?>(null) }
    var freehandPoints by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var showTextInputDialog by remember { mutableStateOf(false) }
    var pendingTextPosition by remember { mutableStateOf(Offset.Zero) }
    var showUnsavedBackConfirm by remember { mutableStateOf(false) }

    var showCrop by remember { mutableStateOf(false) }
    var cropLeft by remember { mutableStateOf(0f) }
    var cropTop by remember { mutableStateOf(0f) }
    var cropRight by remember { mutableStateOf(1f) }
    var cropBottom by remember { mutableStateOf(1f) }
    val imageContentRect = remember(uiState.imageWidth, uiState.imageHeight, canvasSize) {
        if (uiState.imageWidth > 0 && uiState.imageHeight > 0 && canvasSize.width > 0 && canvasSize.height > 0) {
            ImageContentRect.forFit(
                imageWidth = uiState.imageWidth,
                imageHeight = uiState.imageHeight,
                viewWidth = canvasSize.width.toFloat(),
                viewHeight = canvasSize.height.toFloat()
            )
        } else null
    }

    fun requestBack() {
        if (uiState.isSaving) return
        if (uiState.canUndo || uiState.elements.isNotEmpty()) showUnsavedBackConfirm = true else onNavigateBack()
    }

    BackHandler(enabled = true, onBack = ::requestBack)

    LaunchedEffect(viewModel) {
        viewModel.saveCompleted.collect { result ->
            Toast.makeText(
                context,
                if (result.oldProductCleanupWarning) "标注图已保存，但旧成品清理失败" else "标注图已生成并保存！",
                Toast.LENGTH_LONG
            ).show()
            onNavigateBack()
        }
    }
    LaunchedEffect(viewModel) {
        viewModel.saveFailed.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

    if (showCrop) {
        AlertDialog(onDismissRequest = { showCrop = false }, title = { Text("自由裁剪") }, text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("在预览上拖出保留范围，或用下方滑块微调。确认后可撤销。")
                Box(Modifier.fillMaxWidth().height(220.dp).pointerInput(uiState.imageWidth, uiState.imageHeight) {
                    var start = Offset.Zero
                    val rect = ImageContentRect.forFit(uiState.imageWidth.coerceAtLeast(1), uiState.imageHeight.coerceAtLeast(1), size.width.toFloat(), size.height.toFloat())
                    detectDragGestures(onDragStart = { start = it }, onDrag = { change, _ ->
                        change.consume()
                        val a = Offset(((start.x - rect.rect.left) / rect.width).coerceIn(0f, 1f), ((start.y - rect.rect.top) / rect.height).coerceIn(0f, 1f))
                        val b = Offset(((change.position.x - rect.rect.left) / rect.width).coerceIn(0f, 1f), ((change.position.y - rect.rect.top) / rect.height).coerceIn(0f, 1f))
                        if (kotlin.math.abs(a.x - b.x) >= 0.05f && kotlin.math.abs(a.y - b.y) >= 0.05f) {
                            cropLeft = minOf(a.x,b.x); cropRight = maxOf(a.x,b.x); cropTop = minOf(a.y,b.y); cropBottom = maxOf(a.y,b.y)
                        }
                    })
                }) {
                    AsyncImage(uiState.previewBitmap ?: uiState.mediaItem?.contentUri, "裁剪预览", Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                    Canvas(Modifier.fillMaxSize()) {
                        val rect = ImageContentRect.forFit(uiState.imageWidth.coerceAtLeast(1), uiState.imageHeight.coerceAtLeast(1), size.width, size.height)
                        drawRect(Color.Yellow, topLeft = Offset(rect.rect.left + rect.width * cropLeft, rect.rect.top + rect.height * cropTop), size = androidx.compose.ui.geometry.Size(rect.width * (cropRight-cropLeft), rect.height * (cropBottom-cropTop)), style = Stroke(2.dp.toPx()))
                    }
                }
                Text("左边"); androidx.compose.material3.Slider(cropLeft, { cropLeft = it.coerceAtMost(cropRight - 0.05f) }, valueRange = 0f..0.95f)
                Text("右边"); androidx.compose.material3.Slider(cropRight, { cropRight = it.coerceAtLeast(cropLeft + 0.05f) }, valueRange = 0.05f..1f)
                Text("上边"); androidx.compose.material3.Slider(cropTop, { cropTop = it.coerceAtMost(cropBottom - 0.05f) }, valueRange = 0f..0.95f)
                Text("下边"); androidx.compose.material3.Slider(cropBottom, { cropBottom = it.coerceAtLeast(cropTop + 0.05f) }, valueRange = 0.05f..1f)
            }
        }, confirmButton = { TextButton(onClick = {
            viewModel.applyTransform("crop", canvasSize.width.toFloat(), canvasSize.height.toFloat(), Rect(cropLeft, cropTop, cropRight, cropBottom)); showCrop = false
        }) { Text("确认裁剪") } }, dismissButton = { TextButton(onClick = { showCrop = false }) { Text("取消") } })
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = DarkBackground,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = { Text("现场隐患照片标注", color = TextPrimaryDark, fontWeight = FontWeight.Bold, fontSize = 17.sp) },
                navigationIcon = {
                    IconButton(onClick = ::requestBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = Color.White)
                    }
                },
                actions = {
                    TextButton(onClick = { viewModel.redo() }, enabled = uiState.canRedo && !uiState.isSaving) { Text("重做") }
                    IconButton(onClick = { viewModel.undo() }, enabled = uiState.canUndo && !uiState.isSaving) {
                        Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "撤销", tint = Color.White)
                    }
                    IconButton(
                        onClick = {
                            viewModel.saveAnnotatedImage(
                                context = context,
                                viewWidth = canvasSize.width.toFloat(),
                                viewHeight = canvasSize.height.toFloat()
                            )
                        },
                        enabled = !uiState.isSaving
                    ) {
                        if (uiState.isSaving) {
                            CircularProgressIndicator(color = EngineeringYellow, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Check, contentDescription = "保存标注", tint = EngineeringYellow)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBackground)
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                listOf("horizontal" to "水平翻转", "vertical" to "垂直翻转", "rotate" to "旋转90°").forEach { (kind, label) ->
                    TextButton(enabled = !uiState.isSaving, onClick = { viewModel.applyTransform(kind, canvasSize.width.toFloat(), canvasSize.height.toFloat()) }) { Text(label, fontSize = 12.sp) }
                }
                TextButton(enabled = !uiState.isSaving, onClick = { cropLeft = 0f; cropTop = 0f; cropRight = 1f; cropBottom = 1f; showCrop = true }) { Text("裁剪") }
            }
            Text("翻转、旋转或裁剪会连同照片里已有的水印一起变化。原图始终保留。", color = TextSecondaryDark, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 12.dp))
            // Interactive Drawing Canvas Area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color.Black)
                    .onSizeChanged { canvasSize = it; viewModel.viewportChanged(it.width.toFloat(), it.height.toFloat()) }
                    .pointerInput(uiState.selectedTool, uiState.selectedColor, uiState.selectedStrokeWidth, uiState.isSaving, canvasSize, uiState.imageWidth, uiState.imageHeight) {
                        if (uiState.isSaving) return@pointerInput
                        if (uiState.selectedTool == AnnotationTool.TEXT) {
                            detectTapGestures { offset ->
                                if (imageContentRect == null || imageContentRect.contains(offset)) {
                                    pendingTextPosition = offset
                                    showTextInputDialog = true
                                }
                            }
                        } else detectDragGestures(
                            onDragStart = { offset ->
                                if (imageContentRect != null && !imageContentRect.contains(offset)) {
                                    dragStart = null
                                    dragCurrent = null
                                    freehandPoints = emptyList()
                                    return@detectDragGestures
                                }
                                dragStart = offset
                                dragCurrent = offset
                                if (uiState.selectedTool == AnnotationTool.FREEHAND || uiState.selectedTool == AnnotationTool.MOSAIC) {
                                    freehandPoints = listOf(offset)
                                } else if (uiState.selectedTool == AnnotationTool.TEXT) {
                                    pendingTextPosition = offset
                                    showTextInputDialog = true
                                }
                            },
                            onDrag = { change, _ ->
                                if (dragStart == null) return@detectDragGestures
                                dragCurrent = change.position
                                if (uiState.selectedTool == AnnotationTool.FREEHAND || uiState.selectedTool == AnnotationTool.MOSAIC) {
                                    freehandPoints = freehandPoints + change.position
                                }
                            },
                            onDragEnd = {
                                val start = dragStart
                                val current = dragCurrent
                                if (start != null && current != null) {
                                    when (uiState.selectedTool) {
                                        AnnotationTool.ARROW -> {
                                            viewModel.addElement(
                                                AnnotationElement.Arrow(
                                                    start = start,
                                                    end = current,
                                                    color = uiState.selectedColor,
                                                    strokeWidth = uiState.selectedStrokeWidth
                                                )
                                            )
                                        }
                                        AnnotationTool.RECTANGLE -> {
                                            viewModel.addElement(
                                                AnnotationElement.Rectangle(
                                                    topLeft = Offset(minOf(start.x, current.x), minOf(start.y, current.y)),
                                                    bottomRight = Offset(maxOf(start.x, current.x), maxOf(start.y, current.y)),
                                                    color = uiState.selectedColor,
                                                    strokeWidth = uiState.selectedStrokeWidth
                                                )
                                            )
                                        }
                                        AnnotationTool.CIRCLE -> {
                                            val dx = current.x - start.x
                                            val dy = current.y - start.y
                                            val radius = kotlin.math.sqrt((dx * dx + dy * dy).toDouble()).toFloat() / 2f
                                            val center = Offset((start.x + current.x) / 2f, (start.y + current.y) / 2f)
                                            viewModel.addElement(
                                                AnnotationElement.Circle(
                                                    center = center,
                                                    radius = radius,
                                                    color = uiState.selectedColor,
                                                    strokeWidth = uiState.selectedStrokeWidth
                                                )
                                            )
                                        }
                                        AnnotationTool.FREEHAND -> {
                                            if (freehandPoints.isNotEmpty()) {
                                                viewModel.addElement(
                                                    AnnotationElement.Freehand(
                                                        points = freehandPoints,
                                                        color = uiState.selectedColor,
                                                        strokeWidth = uiState.selectedStrokeWidth
                                                    )
                                                )
                                            }
                                        }
                                        AnnotationTool.MOSAIC -> {
                                            if (freehandPoints.isNotEmpty()) {
                                                viewModel.addElement(
                                                    AnnotationElement.Mosaic(
                                                        points = freehandPoints,
                                                        strokeWidth = 36f
                                                    )
                                                )
                                            }
                                        }
                                        AnnotationTool.TEXT -> {}
                                    }
                                }
                                dragStart = null
                                dragCurrent = null
                                freehandPoints = emptyList()
                            }
                        )
                    }
            ) {
                // Background Photo Image
                uiState.mediaItem?.let { item ->
                    AsyncImage(
                        model = uiState.previewBitmap ?: item.contentUri,
                        contentDescription = "底图",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                }

                // Rendered Annotations + Live Dragging
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val drawAnnotations: DrawScope.() -> Unit = {
                        for (element in uiState.elements) {
                            drawAnnotationElement(element, imageContentRect?.rect ?: Rect(0f, 0f, size.width, size.height))
                        }

                        // Live Drag Preview
                        val start = dragStart
                        val current = dragCurrent
                        if (start != null && current != null) {
                            when (uiState.selectedTool) {
                            AnnotationTool.ARROW -> {
                                drawArrow(start, current, uiState.selectedColor, uiState.selectedStrokeWidth)
                            }
                            AnnotationTool.RECTANGLE -> {
                                val left = minOf(start.x, current.x)
                                val top = minOf(start.y, current.y)
                                val right = maxOf(start.x, current.x)
                                val bottom = maxOf(start.y, current.y)
                                drawRect(
                                    color = uiState.selectedColor,
                                    topLeft = Offset(left, top),
                                    size = Size(right - left, bottom - top),
                                    style = Stroke(width = uiState.selectedStrokeWidth)
                                )
                            }
                            AnnotationTool.CIRCLE -> {
                                val dx = current.x - start.x
                                val dy = current.y - start.y
                                val radius = kotlin.math.sqrt((dx * dx + dy * dy).toDouble()).toFloat() / 2f
                                val center = Offset((start.x + current.x) / 2f, (start.y + current.y) / 2f)
                                drawCircle(
                                    color = uiState.selectedColor,
                                    center = center,
                                    radius = radius,
                                    style = Stroke(width = uiState.selectedStrokeWidth)
                                )
                            }
                            AnnotationTool.FREEHAND -> {
                                if (freehandPoints.size > 1) {
                                    val path = Path().apply {
                                        moveTo(freehandPoints[0].x, freehandPoints[0].y)
                                        for (i in 1 until freehandPoints.size) {
                                            lineTo(freehandPoints[i].x, freehandPoints[i].y)
                                        }
                                    }
                                    drawPath(path, uiState.selectedColor, style = Stroke(width = uiState.selectedStrokeWidth))
                                }
                            }
                                AnnotationTool.MOSAIC -> drawMosaicPreview(freehandPoints, 36f)
                            AnnotationTool.TEXT -> {}
                            }
                        }
                    }
                    if (imageContentRect != null) {
                        clipRect(
                            left = imageContentRect.left,
                            top = imageContentRect.top,
                            right = imageContentRect.right,
                            bottom = imageContentRect.bottom,
                            block = drawAnnotations
                        )
                    } else {
                        drawAnnotations()
                    }
                }
            }

            // Bottom Tool Palette
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                colors = CardDefaults.cardColors(containerColor = DarkCard)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    // Tool Selection Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        listOf(
                            AnnotationTool.ARROW to (Icons.Default.NorthEast to "箭头"),
                            AnnotationTool.RECTANGLE to (Icons.Default.CropSquare to "矩形"),
                            AnnotationTool.CIRCLE to (Icons.Default.PanoramaFishEye to "圆圈"),
                            AnnotationTool.FREEHAND to (Icons.Default.Brush to "画笔"),
                            AnnotationTool.TEXT to (Icons.Default.FormatShapes to "文字"),
                            AnnotationTool.MOSAIC to (Icons.Default.Grain to "脱敏遮盖")
                        ).forEach { (tool, iconLabel) ->
                            val isSelected = uiState.selectedTool == tool
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { viewModel.selectTool(tool) }
                                    .padding(horizontal = 8.dp, vertical = 8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(if (isSelected) EngineeringYellow else Color.Black.copy(alpha = 0.4f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = iconLabel.first,
                                        contentDescription = iconLabel.second,
                                        tint = if (isSelected) Color.Black else Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Text(
                                    text = iconLabel.second,
                                    color = if (isSelected) EngineeringYellow else TextSecondaryDark,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Color Palette Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        listOf(
                            ErrorRed to "红色",
                            EngineeringYellow to "黄色",
                            WarningYellow to "橙色",
                            SuccessGreen to "绿色",
                            Color.White to "白色"
                        ).forEach { (color, _) ->
                            val isSelected = uiState.selectedColor == color
                            Box(
                                modifier = Modifier
                                    .padding(horizontal = 8.dp)
                                    .size(30.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .clickable { viewModel.selectColor(color) },
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .clip(CircleShape)
                                            .background(if (color == Color.White) Color.Black else Color.White)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Text Note Dialog
    if (showTextInputDialog) {
        var noteText by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showTextInputDialog = false },
            containerColor = DarkCard,
            title = { Text("添加现场标注说明", color = TextPrimaryDark, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    label = { Text("批注内容（如：裂缝长约50cm）") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = EngineeringYellow),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (noteText.isNotBlank()) {
                            viewModel.addElement(
                                AnnotationElement.TextNote(
                                    position = pendingTextPosition,
                                    text = noteText.trim(),
                                    color = uiState.selectedColor,
                                    strokeWidth = uiState.selectedStrokeWidth
                                )
                            )
                            showTextInputDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EngineeringYellow)
                ) {
                    Text("添加", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showTextInputDialog = false }) {
                    Text("取消", color = TextSecondaryDark)
                }
            }
        )
    }

    if (showUnsavedBackConfirm) {
        AlertDialog(
            onDismissRequest = { showUnsavedBackConfirm = false },
            containerColor = DarkCard,
            title = { Text("放弃未保存标注？", color = TextPrimaryDark, fontWeight = FontWeight.Bold) },
            text = { Text("当前标注尚未生成成品图，返回后会丢失。", color = TextSecondaryDark) },
            confirmButton = {
                TextButton(onClick = { showUnsavedBackConfirm = false; onNavigateBack() }) {
                    Text("放弃并返回", color = ErrorRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { showUnsavedBackConfirm = false }) {
                    Text("继续编辑", color = EngineeringYellow)
                }
            }
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawAnnotationElement(
    element: AnnotationElement,
    imageRect: Rect
) {
    when (element) {
        is AnnotationElement.Arrow -> {
            drawArrow(element.start, element.end, element.color, element.strokeWidth)
        }
        is AnnotationElement.Rectangle -> {
            drawRect(
                color = element.color,
                topLeft = element.topLeft,
                size = Size(element.bottomRight.x - element.topLeft.x, element.bottomRight.y - element.topLeft.y),
                style = Stroke(width = element.strokeWidth)
            )
        }
        is AnnotationElement.Circle -> {
            drawCircle(
                color = element.color,
                center = element.center,
                radius = element.radius,
                style = Stroke(width = element.strokeWidth)
            )
        }
        is AnnotationElement.Freehand -> {
            if (element.points.size > 1) {
                val path = Path().apply {
                    moveTo(element.points[0].x, element.points[0].y)
                    for (i in 1 until element.points.size) {
                        lineTo(element.points[i].x, element.points[i].y)
                    }
                }
                drawPath(path, element.color, style = Stroke(width = element.strokeWidth))
            }
        }
        is AnnotationElement.TextNote -> {
            val layout = TextNoteGeometry.layout(
                position = element.position,
                text = element.text,
                contentRect = imageRect
            )
            drawRoundRect(
                color = Color(0xCC121212),
                topLeft = layout.bubbleRect.topLeft,
                size = layout.bubbleRect.size,
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f, 8f)
            )
            drawIntoCanvas { canvas ->
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = element.color.toArgb()
                    textSize = TextNoteGeometry.TEXT_SIZE
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                }
                canvas.nativeCanvas.drawText(
                    layout.text,
                    layout.baseline.x,
                    layout.baseline.y,
                    paint
                )
            }
        }
        is AnnotationElement.Mosaic -> {
            drawMosaicPreview(element.points, element.strokeWidth)
        }
    }
}

private fun DrawScope.drawMosaicPreview(points: List<Offset>, strokeWidth: Float) {
    if (points.isEmpty()) return
    val cellSize = (strokeWidth / 3f).coerceIn(4f, 96f)
    val half = (strokeWidth / 2f).coerceAtLeast(cellSize)
    val samples = MosaicGeometry.samplePolyline(points, (cellSize / 2f).coerceAtLeast(1f))
    for (point in samples) {
        val minX = kotlin.math.floor((point.x - half) / cellSize).toInt()
        val maxX = kotlin.math.floor((point.x + half) / cellSize).toInt()
        val minY = kotlin.math.floor((point.y - half) / cellSize).toInt()
        val maxY = kotlin.math.floor((point.y + half) / cellSize).toInt()
        for (x in minX..maxX) {
            for (y in minY..maxY) {
                drawRect(
                    color = if ((x + y) and 1 == 0) Color(0xF5181818) else Color(0xE6484848),
                    topLeft = Offset(x * cellSize, y * cellSize),
                    size = Size(cellSize, cellSize)
                )
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawArrow(
    start: Offset,
    end: Offset,
    color: Color,
    strokeWidth: Float
) {
    drawLine(color, start, end, strokeWidth = strokeWidth)

    val angle = atan2((end.y - start.y).toDouble(), (end.x - start.x).toDouble())
    val headLength = 28f
    val headAngle = Math.PI / 6

    val x1 = (end.x - headLength * cos(angle - headAngle)).toFloat()
    val y1 = (end.y - headLength * sin(angle - headAngle)).toFloat()
    val x2 = (end.x - headLength * cos(angle + headAngle)).toFloat()
    val y2 = (end.y - headLength * sin(angle + headAngle)).toFloat()

    val path = Path().apply {
        moveTo(end.x, end.y)
        lineTo(x1, y1)
        moveTo(end.x, end.y)
        lineTo(x2, y2)
    }
    drawPath(path, color, style = Stroke(width = strokeWidth))
}
