package com.sitecam.app.feature.camera

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.draw.clip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import android.view.Surface
import android.view.MotionEvent
import com.sitecam.app.core.camera.isLandscapeWindow
import com.sitecam.app.core.camera.resolveCameraTargetRotation
import com.sitecam.app.core.watermark.engine.WatermarkLayoutEngine
import com.sitecam.app.core.watermark.renderer.WatermarkPreviewCanvas
import com.sitecam.app.feature.camera.components.CameraBottomBar
import com.sitecam.app.feature.camera.components.CameraTopBar
import com.sitecam.app.feature.camera.components.FocusRing
import com.sitecam.app.feature.camera.components.QuickWatermarkEditSheet
import com.sitecam.app.ui.theme.DarkBackground
import com.sitecam.app.ui.theme.EngineeringYellow

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun CameraScreen(
    viewModel: CameraViewModel,
    onNavigateToProjects: () -> Unit,
    onNavigateToGallery: (Long?) -> Unit,
    onNavigateToSettings: () -> Unit,
    onQuickIssuePrompt: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsState()

    var showQuickWatermarkSheet by remember { mutableStateOf(false) }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    var cameraPermissionRequested by remember { mutableStateOf(false) }
    var cameraNeedsSettings by remember { mutableStateOf(false) }
    val storagePermissionRequired = Build.VERSION.SDK_INT <= Build.VERSION_CODES.P
    var hasStoragePermission by remember {
        mutableStateOf(
            !storagePermissionRequired || ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    val cameraReady by viewModel.cameraManager.isCameraReady.collectAsState()
    val cameraError by viewModel.cameraManager.cameraError.collectAsState()

    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        )
    }
    var pendingVideoStart by remember { mutableStateOf(false) }
    var locationPermissionRequested by remember { mutableStateOf(false) }
    var locationNeedsSettings by remember { mutableStateOf(false) }

    val isCaptureBusy = uiState.isCapturing || uiState.isRecordingVideo
    val isCameraBusy = isCaptureBusy || !cameraReady

    fun refreshPermissions() {
        hasCameraPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        hasAudioPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        hasLocationPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (hasLocationPermission) locationNeedsSettings = false
        if (hasCameraPermission) cameraNeedsSettings = false
        hasStoragePermission = !storagePermissionRequired || ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
        viewModel.updateLocationPermission(hasLocationPermission)
        if (!hasCameraPermission) viewModel.cameraManager.unbindCamera()
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { permissions ->
        cameraPermissionRequested = true
        hasCameraPermission = permissions
        if (!permissions) {
            val activity = context as? ComponentActivity
            cameraNeedsSettings = activity != null &&
                !androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale(
                    activity,
                    Manifest.permission.CAMERA
                )
            Toast.makeText(
                context,
                if (cameraNeedsSettings) "相机权限已永久拒绝，请到系统设置开启" else "未授予相机权限，无法拍摄",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    val storagePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasStoragePermission = granted
        if (!granted) {
            Toast.makeText(context, "未授予存储权限，将保存到应用专属相册目录", Toast.LENGTH_LONG).show()
        }
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasAudioPermission = granted
        if (pendingVideoStart) {
            pendingVideoStart = false
            if (granted) {
                viewModel.handleShutterAction(context, withAudio = true)
            } else {
                Toast.makeText(context, "未授予录音权限，将录制无声视频", Toast.LENGTH_LONG).show()
                viewModel.handleShutterAction(context, withAudio = false)
            }
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasLocationPermission = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (hasLocationPermission) {
            locationNeedsSettings = false
            viewModel.startForegroundServices(locationEnabled = true)
        } else {
            val activity = context as? ComponentActivity
            locationNeedsSettings = locationPermissionRequested && activity != null &&
                !androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale(
                    activity,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) && !androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale(
                    activity,
                    Manifest.permission.ACCESS_FINE_LOCATION
                )
            Toast.makeText(context, "未授予定位权限，照片仍可拍摄但不含现场定位", Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(Unit) {
        refreshPermissions()
        if (!hasCameraPermission) {
            cameraPermissionRequested = true
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        } else {
            viewModel.startForegroundServices(locationEnabled = hasLocationPermission)
        }
    }

    LaunchedEffect(hasCameraPermission, hasLocationPermission) {
        if (hasCameraPermission) {
            viewModel.startForegroundServices(locationEnabled = hasLocationPermission)
        }
    }

    LaunchedEffect(hasCameraPermission, hasStoragePermission) {
        if (hasCameraPermission && storagePermissionRequired && !hasStoragePermission) {
            storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }

    LaunchedEffect(cameraError) {
        cameraError?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshPermissions()
                (context as? ComponentActivity)?.window?.decorView?.postDelayed(
                    { viewModel.cameraManager.restoreFlashMode() },
                    180L
                )
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Do not let a system back gesture abandon an in-progress recording or
    // watermark transcode.  When idle, NavHost owns the normal back behavior.
    BackHandler(enabled = isCaptureBusy) {
        Toast.makeText(
            context,
            if (uiState.isRecordingVideo) "请先结束录像" else "正在保存，请稍候",
            Toast.LENGTH_SHORT
        ).show()
    }

    DisposableEffect(lifecycleOwner) {
        onDispose {
            viewModel.stopForegroundServices()
            viewModel.cameraManager.unbindCamera()
        }
    }

    // Observe UI Events (Captured, Toast, Quick Issue)
    LaunchedEffect(viewModel) {
        viewModel.uiEvents.collect { event ->
            when (event) {
                is CameraUiEvent.PhotoCaptured -> {
                    Toast.makeText(context, "媒体已保存", Toast.LENGTH_SHORT).show()
                }
                is CameraUiEvent.QuickIssuePrompt -> {
                    onQuickIssuePrompt(event.mediaId)
                }
                is CameraUiEvent.ShowToast -> {
                    Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    if (!hasCameraPermission) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(DarkBackground),
            contentAlignment = Alignment.Center
        ) {
            Box(modifier = Modifier.padding(24.dp)) {
                Button(
                    onClick = {
                        if (cameraNeedsSettings) {
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.parse("package:${context.packageName}")
                                )
                            )
                        } else {
                            cameraPermissionRequested = true
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = EngineeringYellow,
                        contentColor = DarkBackground
                    )
                ) {
                    Text(
                        text = "授予相机权限",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        return
    }

    var focusPosition by remember { mutableStateOf<Offset?>(null) }
    var previewViewRef by remember { mutableStateOf<PreviewView?>(null) }
    var previewViewSize by remember { mutableStateOf(IntSize.Zero) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Layout follows only the currently available window. Sensor
        // orientation is consumed by capture rotation/watermark metadata and
        // must never force a narrow portrait window into landscape controls.
        val isLandscape = isLandscapeWindow(maxWidth.value, maxHeight.value)
        // Measured from the connected 1200x2670 @ 480 dpi Xiaomi camera:
        // tool shelf 307px, 3:4 preview 1600px, zoom centered on its lower edge.
        val portraitTopBarHeight = 102.dp
        val minPortraitDockHeight = 150.dp
        val portraitFrameWidth = minOf(
            maxWidth,
            (maxHeight - portraitTopBarHeight - minPortraitDockHeight).coerceAtLeast(1.dp) * (3f / 4f)
        )
        val portraitPreviewHeight = (portraitFrameWidth * (4f / 3f)).coerceAtLeast(1.dp)
        val portraitPreviewBottom = portraitTopBarHeight + portraitPreviewHeight
        val landscapeToolBarWidth = 102.dp
        val minLandscapeDockWidth = 160.dp
        val landscapeAvailableWidth = (maxWidth - landscapeToolBarWidth).coerceAtLeast(1.dp)
        val requestedLandscapePreviewWidth = maxHeight * (4f / 3f)
        val landscapePreviewWidth = requestedLandscapePreviewWidth.coerceAtMost(
            (landscapeAvailableWidth - minLandscapeDockWidth).coerceAtLeast(1.dp)
        )
        val landscapePreviewEnd = landscapeToolBarWidth + landscapePreviewWidth
        val landscapeControlWidth = (maxWidth - landscapePreviewEnd).coerceAtLeast(1.dp)
        val portraitPreviewTopPx = with(LocalDensity.current) { portraitTopBarHeight.toPx() }
        val landscapePreviewStartPx = with(LocalDensity.current) { landscapeToolBarWidth.toPx() }
        val previewFrameModifier = if (isLandscape) {
            Modifier
                .align(Alignment.CenterStart)
                .padding(start = landscapeToolBarWidth)
                .width(landscapePreviewWidth)
                .height(maxHeight)
        } else {
            Modifier
                .align(Alignment.TopCenter)
                .padding(top = portraitTopBarHeight)
                .width(portraitFrameWidth)
                .height(portraitPreviewHeight)
        }

        // Match the system-camera hierarchy: system bars stay hidden while the
        // dedicated black tool/control areas frame a non-full-screen preview.
        val activity = context as? ComponentActivity
        DisposableEffect(isLandscape, activity) {
            val controller = activity?.let {
                WindowCompat.getInsetsController(it.window, it.window.decorView)
            }
            controller?.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller?.hide(WindowInsetsCompat.Type.systemBars())
            onDispose {
                controller?.show(WindowInsetsCompat.Type.systemBars())
            }
        }

        // 1. CameraX Preview View with Pinch-to-zoom & Tap-to-focus
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    // SurfaceView performance mode can leave the camera
                    // buffer's sensor transform unapplied when an API 16+
                    // display rotates. TextureView/COMPATIBLE keeps
                    // PreviewView's rotation matrix active for the same
                    // CameraX targetRotation used by capture.
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    previewViewRef = this
                }
            },
            modifier = previewFrameModifier
                .clipToBounds()
                .onSizeChanged { previewViewSize = it }
                .pointerInput(Unit) {
                    detectTransformGestures { _, _, zoom, _ ->
                        val newRatio = (uiState.currentZoomRatio * zoom).coerceIn(
                            uiState.cameraCapability.minZoomRatio,
                            uiState.cameraCapability.maxZoomRatio
                        )
                        viewModel.setZoomRatio(newRatio)
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        focusPosition = if (isLandscape) {
                            offset + Offset(landscapePreviewStartPx, 0f)
                        } else {
                            offset + Offset(0f, portraitPreviewTopPx)
                        }
                        val preview = previewViewRef
                        if (preview != null && preview.width > 0 && preview.height > 0) {
                            viewModel.cameraManager.focusOnPoint(
                                offset.x,
                                offset.y,
                                preview.width.toFloat(),
                                preview.height.toFloat()
                            )
                        }
                    }
                },
            update = { previewView ->
                previewViewRef = previewView
            }
        )

        if (!isLandscape) {
            // An opaque shelf is intentional here: MIUI keeps top actions out
            // of the live image, which makes the fixed frame visually obvious.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(portraitTopBarHeight)
                    .align(Alignment.TopCenter)
                    .background(Color.Black)
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height((maxHeight - portraitPreviewBottom).coerceAtLeast(0.dp))
                    .align(Alignment.BottomCenter)
                    .background(Color.Black)
            )
        }

        // Initialize Camera lifecycle binding once PreviewView is ready
        LaunchedEffect(previewViewRef, previewViewSize) {
            val preview = previewViewRef ?: return@LaunchedEffect
            if (previewViewSize.width <= 0 || previewViewSize.height <= 0) return@LaunchedEffect
            if (uiState.isRecordingVideo) return@LaunchedEffect
            val targetRotation = resolveCameraTargetRotation(
                orientationDegrees = uiState.orientationDegrees,
                windowIsLandscape = isLandscape,
                displayRotation = preview.display?.rotation ?: Surface.ROTATION_0
            )
            viewModel.cameraManager.initializeCamera(
                lifecycleOwner = lifecycleOwner,
                previewView = preview,
                flashMode = uiState.flashMode,
                displayRotation = targetRotation
            )
            viewModel.cameraManager.updateTargetRotation(targetRotation)
        }

        LaunchedEffect(uiState.orientationDegrees, previewViewRef, uiState.isRecordingVideo, isLandscape) {
            if (!uiState.isRecordingVideo) {
                previewViewRef?.let { preview ->
                    val targetRotation = resolveCameraTargetRotation(
                        orientationDegrees = uiState.orientationDegrees,
                        windowIsLandscape = isLandscape,
                        displayRotation = preview.display?.rotation ?: Surface.ROTATION_0
                    )
                    viewModel.cameraManager.updateTargetRotation(
                        targetRotation
                    )
                }
            }
        }

        // 2. Realtime watermark overlay. Only taps that begin inside the actual
        // watermark card are intercepted to open the editor. Everything outside
        // the card still falls through to PreviewView for focus and zoom.
        var watermarkOverlaySize by remember { mutableStateOf(IntSize.Zero) }
        var watermarkTapActive by remember { mutableStateOf(false) }
        Box(
            modifier = previewFrameModifier
                .onSizeChanged { watermarkOverlaySize = it }
                .pointerInteropFilter { event ->
                    if (isCameraBusy || watermarkOverlaySize.width <= 0 || watermarkOverlaySize.height <= 0) {
                        false
                    } else {
                        val layout = WatermarkLayoutEngine.calculateLayout(
                            canvasWidth = watermarkOverlaySize.width.toFloat(),
                            canvasHeight = watermarkOverlaySize.height.toFloat(),
                            data = uiState.watermarkData
                        )
                        val inside = layout.cardRect.contains(event.x, event.y)
                        when (event.actionMasked) {
                            MotionEvent.ACTION_DOWN -> {
                                watermarkTapActive = inside
                                inside
                            }
                            MotionEvent.ACTION_UP -> {
                                val handled = watermarkTapActive
                                if (handled && inside) showQuickWatermarkSheet = true
                                watermarkTapActive = false
                                handled
                            }
                            MotionEvent.ACTION_CANCEL -> {
                                val handled = watermarkTapActive
                                watermarkTapActive = false
                                handled
                            }
                            else -> watermarkTapActive
                        }
                    }
                }
        ) {
            WatermarkPreviewCanvas(
                watermarkData = uiState.watermarkData,
                modifier = Modifier.fillMaxSize()
            )
        }

        // 3. Focus Ring
        FocusRing(
            position = focusPosition,
            onAnimationEnd = { focusPosition = null }
        )

        // 4. Top Controls Bar
        CameraTopBar(
            projectName = uiState.currentProject?.name ?: "默认工程项目",
            flashMode = uiState.flashMode,
            isQuickIssueMode = uiState.isQuickIssueMode,
            onProjectClick = onNavigateToProjects,
            onFlashToggle = { viewModel.toggleFlashMode() },
            onFlashLongPress = { viewModel.toggleTorchMode() },
            onQuickIssueToggle = { viewModel.toggleQuickIssueMode() },
            onSettingsClick = onNavigateToSettings,
            isBusy = isCameraBusy,
            isLandscape = isLandscape,
            modifier = if (isLandscape) {
                Modifier
                    .align(Alignment.CenterStart)
                    .width(landscapeToolBarWidth)
                    .fillMaxHeight()
            } else {
                Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(portraitTopBarHeight)
            }
        )

        if (!hasLocationPermission || (uiState.captureMode == CaptureMode.VIDEO && !hasAudioPermission)) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(
                        start = if (isLandscape) landscapeToolBarWidth + 12.dp else 12.dp,
                        top = if (isLandscape) 12.dp else portraitTopBarHeight + 12.dp,
                        end = 12.dp
                    )
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(18.dp))
                    .background(Color.Black.copy(alpha = 0.78f))
                    .clickable(enabled = !hasLocationPermission && !isCameraBusy) {
                        if (!hasLocationPermission) {
                            if (locationNeedsSettings) {
                                context.startActivity(
                                    Intent(
                                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                        Uri.parse("package:${context.packageName}")
                                    )
                                )
                            } else {
                                locationPermissionRequested = true
                                locationPermissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                    )
                                )
                            }
                        }
                    }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (!hasLocationPermission) Icons.Default.LocationOn else Icons.Default.MicOff,
                    contentDescription = null,
                    tint = if (!hasLocationPermission) EngineeringYellow else Color.White,
                    modifier = Modifier.size(17.dp)
                )
                Text(
                    text = if (!hasLocationPermission) {
                        if (locationNeedsSettings) "无定位 · 去设置" else "无定位 · 可拍摄"
                    } else "无录音 · 录像无声",
                    color = Color.White,
                    fontSize = 11.sp,
                    maxLines = 1
                )
            }
        }

        // 5. Unified camera control dock. Zoom, mode and shutter controls now
        // share one sequential layout and cannot drift into each other on
        // different aspect ratios, font scales or navigation configurations.
        if (isLandscape) {
            CameraBottomBar(
                latestThumbnailUri = uiState.latestThumbnailUri,
                isCapturing = uiState.isCapturing,
                captureMode = uiState.captureMode,
                isRecordingVideo = uiState.isRecordingVideo,
                recordingDurationSeconds = uiState.recordingDurationSeconds,
                zoomPresets = uiState.cameraCapability.zoomPillPresets.filter { it < 9.5f },
                currentZoomRatio = uiState.currentZoomRatio,
                onZoomSelected = { ratio -> viewModel.setZoomRatio(ratio) },
                isLandscape = true,
                landscapeBarWidth = landscapeControlWidth,
                onModeChange = { mode -> viewModel.setCaptureMode(mode) },
                onShutterClick = {
                    if (uiState.captureMode == CaptureMode.VIDEO && !uiState.isRecordingVideo) {
                        if (hasAudioPermission) {
                            viewModel.handleShutterAction(context, withAudio = true)
                        } else {
                            pendingVideoStart = true
                            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    } else {
                        viewModel.handleShutterAction(context, withAudio = hasAudioPermission)
                    }
                },
                onGalleryClick = { onNavigateToGallery(uiState.currentProject?.id) },
                onFlipCameraClick = {
                    val preview = previewViewRef
                    if (preview != null) {
                        viewModel.cameraManager.switchLens(lifecycleOwner, preview)
                    }
                },
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .windowInsetsPadding(
                        WindowInsets.safeDrawing.only(
                            WindowInsetsSides.Top + WindowInsetsSides.Bottom + WindowInsetsSides.End
                        )
                    ),
                isBusy = isCameraBusy,
                latestMediaType = uiState.latestMediaType
            )
        } else {
            CameraBottomBar(
                latestThumbnailUri = uiState.latestThumbnailUri,
                isCapturing = uiState.isCapturing,
                captureMode = uiState.captureMode,
                isRecordingVideo = uiState.isRecordingVideo,
                recordingDurationSeconds = uiState.recordingDurationSeconds,
                zoomPresets = uiState.cameraCapability.zoomPillPresets.filter { it < 9.5f },
                currentZoomRatio = uiState.currentZoomRatio,
                onZoomSelected = { ratio -> viewModel.setZoomRatio(ratio) },
                isLandscape = false,
                onModeChange = { mode -> viewModel.setCaptureMode(mode) },
                onShutterClick = {
                    if (uiState.captureMode == CaptureMode.VIDEO && !uiState.isRecordingVideo) {
                        if (hasAudioPermission) {
                            viewModel.handleShutterAction(context, withAudio = true)
                        } else {
                            pendingVideoStart = true
                            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    } else {
                        viewModel.handleShutterAction(context, withAudio = hasAudioPermission)
                    }
                },
                onGalleryClick = { onNavigateToGallery(uiState.currentProject?.id) },
                onFlipCameraClick = {
                    val preview = previewViewRef
                    if (preview != null) {
                        viewModel.cameraManager.switchLens(lifecycleOwner, preview)
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height((maxHeight - portraitPreviewBottom).coerceAtLeast(0.dp))
                    .windowInsetsPadding(
                        WindowInsets.safeDrawing.only(
                            WindowInsetsSides.Bottom + WindowInsetsSides.Start + WindowInsetsSides.End
                        )
                    ),
                isBusy = isCameraBusy,
                latestMediaType = uiState.latestMediaType
            )
        }

        // 6. Quick Watermark Edit Bottom Sheet
        if (showQuickWatermarkSheet) {
            QuickWatermarkEditSheet(
                activeTemplate = uiState.activeTemplate,
                fields = uiState.watermarkFields,
                onDismissRequest = { showQuickWatermarkSheet = false },
                onFieldValueChange = { fieldId, newVal ->
                    viewModel.updateWatermarkFieldValue(fieldId, newVal)
                },
                onFieldToggle = { fieldId, enabled ->
                    viewModel.toggleWatermarkFieldEnabled(fieldId, enabled)
                },
                onStyleChange = { style ->
                    viewModel.updateTemplateStyle(style)
                },
                onScaleChange = { scale ->
                    viewModel.updateTemplateFontSize(scale)
                },
                onOpacityChange = { opacity ->
                    viewModel.updateTemplateOpacity(opacity)
                },
                onAddField = { label, defaultVal ->
                    viewModel.addCustomFieldDirect(label, defaultVal)
                }
            )
        }
    }
}
