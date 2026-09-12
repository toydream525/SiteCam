package com.sitecam.app.feature.camera

import com.sitecam.app.feature.permissions.PermissionAccess
import com.sitecam.app.feature.onboarding.OnboardingPreferences
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
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
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import com.sitecam.app.core.layout.cameraGeometry
import com.sitecam.app.core.layout.rememberScreenEnvironment
import androidx.window.layout.FoldingFeature
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
import androidx.compose.ui.platform.LocalView
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
    val localView = LocalView.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsState()
    val permissionPreferences = remember(context) { OnboardingPreferences(context) }
    val permissionPreferenceState by permissionPreferences.state.collectAsState(initial = null)
    val permissionScope = rememberCoroutineScope()
    var requestedHere by remember { mutableStateOf(emptySet<String>()) }
    val requestedPermissions = permissionPreferenceState?.requestedPermissions.orEmpty() + requestedHere

    var showQuickWatermarkSheet by remember { mutableStateOf(false) }
    var shutterFlashToken by remember { mutableStateOf(0L) }
    var photoSaveAnimationToken by remember { mutableStateOf(0L) }
    val previewFlashAlpha = remember { Animatable(0f) }

    LaunchedEffect(shutterFlashToken) {
        if (shutterFlashToken == 0L) return@LaunchedEffect
        previewFlashAlpha.snapTo(0.28f)
        previewFlashAlpha.animateTo(0f, tween(durationMillis = 100))
    }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    var cameraNeedsSettings by remember { mutableStateOf(false) }
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
    var audioNeedsSettings by remember { mutableStateOf(false) }
    var showOptionalPermissions by remember { mutableStateOf(false) }
    var locationNeedsSettings by remember { mutableStateOf(false) }

    val projectCanCapture = uiState.currentProject?.let { !it.isCaptureLocked && !it.isArchived } == true
    val isCaptureBusy = uiState.isCapturing || uiState.isRecordingVideo
    val orientationContext = LocalContext.current
    val orientationActivity = remember(orientationContext) {
        var candidate: android.content.Context = orientationContext
        while (candidate is android.content.ContextWrapper && candidate !is android.app.Activity) candidate = candidate.baseContext
        candidate as? android.app.Activity
    }
    DisposableEffect(orientationActivity) {
        val previous = orientationActivity?.requestedOrientation
        onDispose { if (previous != null) orientationActivity?.requestedOrientation = previous }
    }
    LaunchedEffect(uiState.captureOrientation, uiState.orientationDegrees, isCaptureBusy) {
        if (!isCaptureBusy) {
            val requestedOrientation = if (uiState.captureOrientation == com.sitecam.app.core.camera.CaptureOrientation.AUTO) {
                // Drive AUTO through the three accepted directions explicitly.
                // Some vendor implementations treat SENSOR as FULL_SENSOR;
                // updating the activity lock from the filtered sensor value
                // prevents a reverse-portrait window from ever being shown.
                when (com.sitecam.app.core.camera.allowedSensorDegrees(uiState.orientationDegrees)) {
                    90 -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
                    270 -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                    else -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                }
            } else {
                uiState.captureOrientation.requestedOrientation
            }
            orientationActivity?.requestedOrientation = requestedOrientation
        }
    }

    val isCameraBusy = isCaptureBusy || !cameraReady

    fun handleShutterPressed() {
        // CameraBottomBar filters busy/locked taps before invoking this
        // callback. The token therefore represents an accepted photo tap and
        // remains independent from the optional shutter sound setting.
        if (uiState.captureMode == CaptureMode.PHOTO) {
            shutterFlashToken += 1L
        }
        if (uiState.captureMode == CaptureMode.VIDEO && !uiState.isRecordingVideo) {
            if (hasAudioPermission) {
                viewModel.handleShutterAction(context, withAudio = true)
            } else {
                Toast.makeText(context, "本次录制无声视频，可点无录音提示开启麦克风", Toast.LENGTH_SHORT).show()
                viewModel.handleShutterAction(context, withAudio = false)
            }
        } else {
            viewModel.handleShutterAction(context, withAudio = hasAudioPermission)
        }
    }

    fun refreshPermissions() {
        val actual = PermissionAccess.read(context, requestedPermissions)
        hasCameraPermission = actual.camera
        hasAudioPermission = actual.microphone
        hasLocationPermission = actual.location
        cameraNeedsSettings = actual.cameraNeedsSettings
        locationNeedsSettings = actual.locationNeedsSettings
        audioNeedsSettings = actual.microphoneNeedsSettings
        viewModel.updateLocationPermission(hasLocationPermission)
        if (!hasCameraPermission) viewModel.cameraManager.unbindCamera()
    }

    fun openPermissionSettings() {
        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        refreshPermissions()
        if (!hasCameraPermission) Toast.makeText(context,
            if (cameraNeedsSettings) "相机未开启，请到系统设置允许；仍可管理已有资料" else "相机未开启，仍可管理已有资料", Toast.LENGTH_LONG).show()
    }
    val audioPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        refreshPermissions()
        Toast.makeText(context, if (hasAudioPermission) "麦克风已开启，下次录像将包含声音" else "麦克风未开启，仍可录制无声视频", Toast.LENGTH_LONG).show()
    }
    val locationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        refreshPermissions()
        if (hasLocationPermission) viewModel.startForegroundServices(locationEnabled = true)
        else Toast.makeText(context, "定位未开启，照片仍可拍摄但不含现场定位", Toast.LENGTH_LONG).show()
    }

    fun requestCameraPermission() {
        val actual = PermissionAccess.read(context, requestedPermissions)
        if (actual.camera) { refreshPermissions(); return }
        if (actual.cameraNeedsSettings) { openPermissionSettings(); return }
        requestedHere = requestedHere + Manifest.permission.CAMERA
        permissionScope.launch {
            permissionPreferences.markPermissionsRequested(listOf(Manifest.permission.CAMERA))
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }
    fun requestOptionalPermission(forLocation: Boolean) {
        val actual = PermissionAccess.read(context, requestedPermissions)
        if (forLocation && !actual.location) {
            if (actual.locationNeedsSettings) { openPermissionSettings(); return }
            val permissions = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            requestedHere = requestedHere + permissions
            permissionScope.launch { permissionPreferences.markPermissionsRequested(permissions); locationPermissionLauncher.launch(permissions.toTypedArray()) }
        } else if (!forLocation && !actual.microphone) {
            if (actual.microphoneNeedsSettings) { openPermissionSettings(); return }
            requestedHere = requestedHere + Manifest.permission.RECORD_AUDIO
            permissionScope.launch { permissionPreferences.markPermissionsRequested(listOf(Manifest.permission.RECORD_AUDIO)); audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }
        }
    }

    // Mounting or returning from the guide/settings only reads permission state.
    // System requests are reserved for the explicit missing-permission controls.
    LaunchedEffect(requestedPermissions) { refreshPermissions() }

    LaunchedEffect(hasCameraPermission, hasLocationPermission) {
        if (hasCameraPermission) {
            viewModel.startForegroundServices(locationEnabled = hasLocationPermission)
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
                is CameraUiEvent.PhotoSaved -> {
                    photoSaveAnimationToken += 1L
                    if (event.showToast) {
                        Toast.makeText(context, "媒体已保存", Toast.LENGTH_SHORT).show()
                    }
                }
                is CameraUiEvent.VideoSaved -> {
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

    if (showOptionalPermissions) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showOptionalPermissions = false },
            title = { Text("补充可选权限") },
            text = {
                androidx.compose.foundation.layout.Column {
                    Text("不授权也能继续使用：照片不含定位，视频不含声音。")
                    if (!hasLocationPermission) androidx.compose.material3.TextButton(onClick = {
                        showOptionalPermissions = false; requestOptionalPermission(forLocation = true)
                    }) { Text(if (locationNeedsSettings) "去设置开启位置" else "开启位置权限") }
                    if (!hasAudioPermission) androidx.compose.material3.TextButton(onClick = {
                        showOptionalPermissions = false; requestOptionalPermission(forLocation = false)
                    }) { Text(if (audioNeedsSettings) "去设置开启麦克风" else "开启麦克风权限") }
                }
            },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { showOptionalPermissions = false }) { Text("暂时不用") } }
        )
    }

    if (!hasCameraPermission) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(DarkBackground),
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.foundation.layout.Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Button(
                    onClick = ::requestCameraPermission,
                    enabled = permissionPreferenceState != null,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = EngineeringYellow,
                        contentColor = DarkBackground
                    )
                ) {
                    Text(
                        text = if (cameraNeedsSettings) "去系统设置开启相机" else "开启相机权限",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                androidx.compose.material3.TextButton(onClick = onNavigateToProjects) { Text("管理工程") }
                androidx.compose.material3.TextButton(onClick = { onNavigateToGallery(null) }) { Text("浏览相册") }
                androidx.compose.material3.TextButton(onClick = onNavigateToSettings) { Text("设置与使用教程") }
            }
        }
        return
    }

    var focusPosition by remember { mutableStateOf<Offset?>(null) }
    var previewViewRef by remember { mutableStateOf<PreviewView?>(null) }
    var previewViewSize by remember { mutableStateOf(IntSize.Zero) }

    val screenEnvironment = rememberScreenEnvironment()
    val screenDensity = LocalDensity.current
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        // Layout follows only the currently available window. Sensor
        // orientation is consumed by capture rotation/watermark metadata and
        // must never force a narrow portrait window into landscape controls.
        val crease = screenEnvironment.fold?.takeIf { it.state == FoldingFeature.State.HALF_OPENED && it.orientation == FoldingFeature.Orientation.HORIZONTAL }
        val verticalCrease = screenEnvironment.fold?.takeIf { it.isSeparating && it.orientation == FoldingFeature.Orientation.VERTICAL }
        val insetLeft = WindowInsets.safeDrawing.getLeft(screenDensity, androidx.compose.ui.unit.LayoutDirection.Ltr)
        val insetTop = WindowInsets.safeDrawing.getTop(screenDensity)
        val geometry = cameraGeometry(maxWidth.value, maxHeight.value, screenDensity.density,
            screenEnvironment.profile.smallCover,
            crease?.let { (it.bounds.top - insetTop) / screenDensity.density } ?: -1f,
            crease?.let { (it.bounds.bottom - insetTop) / screenDensity.density } ?: -1f,
            verticalCrease?.let { (it.bounds.left - insetLeft) / screenDensity.density } ?: -1f,
            verticalCrease?.let { (it.bounds.right - insetLeft) / screenDensity.density } ?: -1f)
        val isLandscape = geometry.side
        val portraitTopBarHeight = geometry.toolbarHeight.dp
        val portraitPreviewBottom = (geometry.previewY + geometry.previewHeight).dp
        val landscapeToolBarWidth = geometry.toolbarWidth.dp
        val landscapeControlWidth = geometry.controlsWidth.dp
        val previewFrameModifier = Modifier.offset(geometry.previewX.dp, geometry.previewY.dp)
            .width(geometry.previewWidth.dp).height(geometry.previewHeight.dp)

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
                        focusPosition = offset + with(screenDensity) { Offset(geometry.previewX.dp.toPx(), geometry.previewY.dp.toPx()) }
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
            if (isCaptureBusy) return@LaunchedEffect
            val targetRotation = if (uiState.captureOrientation == com.sitecam.app.core.camera.CaptureOrientation.AUTO) {
                resolveCameraTargetRotation(
                    orientationDegrees = uiState.orientationDegrees,
                    windowIsLandscape = isLandscape,
                    displayRotation = localView.display?.rotation ?: Surface.ROTATION_0
                )
            } else {
                uiState.captureOrientation.targetRotation(uiState.orientationDegrees)
            }
            viewModel.cameraManager.initializeCamera(
                lifecycleOwner = lifecycleOwner,
                previewView = preview,
                flashMode = uiState.flashMode,
                displayRotation = targetRotation
            )
            viewModel.cameraManager.updateTargetRotation(targetRotation)
        }

        LaunchedEffect(uiState.captureOrientation, uiState.orientationDegrees, previewViewRef, isCaptureBusy, isLandscape, localView.display?.rotation) {
            if (!isCaptureBusy) {
                previewViewRef?.let {
                    val targetRotation = if (uiState.captureOrientation == com.sitecam.app.core.camera.CaptureOrientation.AUTO) {
                        resolveCameraTargetRotation(
                            orientationDegrees = uiState.orientationDegrees,
                            windowIsLandscape = isLandscape,
                            displayRotation = localView.display?.rotation ?: Surface.ROTATION_0
                        )
                    } else {
                        uiState.captureOrientation.targetRotation(uiState.orientationDegrees)
                    }
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
                    if (screenEnvironment.profile.smallCover || isCameraBusy || watermarkOverlaySize.width <= 0 || watermarkOverlaySize.height <= 0) {
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

        if (previewFlashAlpha.value > 0f) {
            Box(
                modifier = previewFrameModifier
                    .background(Color.White.copy(alpha = previewFlashAlpha.value))
            )
        }

        // 3. Focus Ring
        FocusRing(
            position = focusPosition,
            onAnimationEnd = { focusPosition = null }
        )

        if (!projectCanCapture) {
            Text("${uiState.currentProject?.name ?: "当前工程不可用"}：禁止拍摄，请解锁或切换工程", color = EngineeringYellow,
                modifier = Modifier.align(Alignment.Center).background(Color.Black.copy(alpha = 0.75f)).padding(12.dp))
        }
        // 4. Top Controls Bar
        if (!screenEnvironment.profile.smallCover) CameraTopBar(
            projectName = (uiState.currentProject?.name ?: "默认工程项目") + if (uiState.currentProject?.isCaptureLocked == true) " · 已锁定" else "",
            orientationLabel = uiState.captureOrientation.label,
            onOrientationSelected = viewModel::setCaptureOrientation,
            flashMode = uiState.flashMode,
            isQuickIssueMode = uiState.isQuickIssueMode,
            onProjectClick = onNavigateToProjects,
            onFlashToggle = { viewModel.toggleFlashMode() },
            onFlashLongPress = { viewModel.toggleTorchMode() },
            onQuickIssueToggle = { viewModel.toggleQuickIssueMode() },
            onSettingsClick = onNavigateToSettings,
            isBusy = isCaptureBusy,
            isLandscape = isLandscape,
            modifier = Modifier.offset(geometry.toolbarX.dp, 0.dp)
                .width(geometry.toolbarWidth.dp).height(geometry.toolbarHeight.dp)
        )

        if (!screenEnvironment.profile.smallCover && (!hasLocationPermission || (uiState.captureMode == CaptureMode.VIDEO && !hasAudioPermission))) {
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
                    .clickable(enabled = !isCaptureBusy && permissionPreferenceState != null) { showOptionalPermissions = true }
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
                    text = when {
                        uiState.currentProject?.isArchived == true -> "工程已归档"
                        uiState.currentProject?.isCaptureLocked == true -> "工程已锁定"
                        !hasLocationPermission -> if (locationNeedsSettings) "无定位 · 去设置" else "无定位 · 可拍摄"
                        else -> if (audioNeedsSettings) "无录音 · 去设置" else "无录音 · 点此开启"
                    },
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
                captureAllowed = projectCanCapture,
                captureMode = uiState.captureMode,
                isRecordingVideo = uiState.isRecordingVideo,
                recordingDurationSeconds = uiState.recordingDurationSeconds,
                zoomPresets = uiState.cameraCapability.zoomPillPresets.filter { it < 9.5f },
                currentZoomRatio = uiState.currentZoomRatio,
                onZoomSelected = { ratio -> viewModel.setZoomRatio(ratio) },
                isLandscape = true,
                landscapeBarWidth = landscapeControlWidth,
                compactGroup = screenEnvironment.profile.large,
                smallCover = screenEnvironment.profile.smallCover,
                onModeChange = { mode -> viewModel.setCaptureMode(mode) },
                onShutterClick = ::handleShutterPressed,
                onGalleryClick = { onNavigateToGallery(uiState.currentProject?.id) },
                onFlipCameraClick = {
                    val preview = previewViewRef
                    if (preview != null) {
                        viewModel.cameraManager.switchLens(lifecycleOwner, preview)
                    }
                },
                modifier = Modifier.offset(geometry.controlsX.dp, geometry.controlsY.dp)
                    .height(geometry.controlsHeight.dp),
                isBusy = isCameraBusy,
                latestMediaType = uiState.latestMediaType,
                shutterSoundEnabled = uiState.shutterSoundEnabled,
                thumbnailBounceToken = photoSaveAnimationToken,
            )
        } else {
            CameraBottomBar(
                latestThumbnailUri = uiState.latestThumbnailUri,
                isCapturing = uiState.isCapturing,
                captureAllowed = projectCanCapture,
                captureMode = uiState.captureMode,
                isRecordingVideo = uiState.isRecordingVideo,
                recordingDurationSeconds = uiState.recordingDurationSeconds,
                zoomPresets = uiState.cameraCapability.zoomPillPresets.filter { it < 9.5f },
                currentZoomRatio = uiState.currentZoomRatio,
                onZoomSelected = { ratio -> viewModel.setZoomRatio(ratio) },
                isLandscape = false,
                onModeChange = { mode -> viewModel.setCaptureMode(mode) },
                onShutterClick = ::handleShutterPressed,
                onGalleryClick = { onNavigateToGallery(uiState.currentProject?.id) },
                onFlipCameraClick = {
                    val preview = previewViewRef
                    if (preview != null) {
                        viewModel.cameraManager.switchLens(lifecycleOwner, preview)
                    }
                },
                modifier = Modifier.offset(geometry.controlsX.dp, geometry.controlsY.dp)
                    .width(geometry.controlsWidth.dp).height(geometry.controlsHeight.dp),
                isBusy = isCameraBusy,
                latestMediaType = uiState.latestMediaType,
                shutterSoundEnabled = uiState.shutterSoundEnabled,
                thumbnailBounceToken = photoSaveAnimationToken,
            )
        }

        // 6. Quick Watermark Edit Bottom Sheet
        if (showQuickWatermarkSheet) {
            QuickWatermarkEditSheet(
                activeTemplate = uiState.activeTemplate,
                fields = uiState.watermarkFields,
                previewData = uiState.watermarkData,
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
