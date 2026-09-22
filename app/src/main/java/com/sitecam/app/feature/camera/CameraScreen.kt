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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.offset
import com.sitecam.app.core.layout.cameraGeometry
import com.sitecam.app.core.layout.rememberScreenEnvironment
import androidx.window.layout.FoldingFeature
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.draw.clip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import android.view.Surface
import android.view.MotionEvent
import com.sitecam.app.core.camera.isLandscapeWindow
import com.sitecam.app.core.camera.resolveCameraTargetRotation
import com.sitecam.app.core.watermark.engine.WatermarkLayoutEngine
import com.sitecam.app.core.watermark.renderer.WatermarkPreviewCanvas
import com.sitecam.app.feature.camera.components.CameraBottomBar
import com.sitecam.app.feature.camera.components.CameraLensSelector
import com.sitecam.app.feature.camera.components.CameraTopBar
import com.sitecam.app.feature.camera.components.FocusRing
import com.sitecam.app.feature.camera.components.QuickWatermarkEditSheet
import com.sitecam.app.ui.theme.DarkBackground
import com.sitecam.app.ui.theme.EngineeringYellow
import com.sitecam.app.ui.theme.Letterbox

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
    val lifecycleState by lifecycleOwner.lifecycle.currentStateAsState()
    val uiState by viewModel.uiState.collectAsState()
    val permissionPreferences = remember(context) { OnboardingPreferences(context) }
    val permissionPreferenceState by permissionPreferences.state.collectAsState(initial = null)
    val permissionScope = rememberCoroutineScope()
    var requestedHere by remember { mutableStateOf(emptySet<String>()) }
    val requestedPermissions = permissionPreferenceState?.requestedPermissions.orEmpty() + requestedHere
    // Session-only record of the permissions that already look "permanently denied" and have been
    // retried once through the system dialog. See PermissionAccess.blockedPermissions: rationale
    // == false alone is not proof of a permanent denial, so settings is only opened after a retry.
    var blockedRetryIssued by remember { mutableStateOf(emptySet<String>()) }

    var showQuickWatermarkSheet by remember { mutableStateOf(false) }
    var showProjectPicker by remember { mutableStateOf(false) }
    var showCreateProject by remember { mutableStateOf(false) }
    var newProjectName by remember { mutableStateOf("") }
    var addressRefreshNeedsFallback by remember { mutableStateOf(false) }
    var showAddressMoreMenu by remember { mutableStateOf(false) }
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

    // A "needs settings" permission still gets one in-session system dialog retry, so the controls
    // only promise the settings page once that retry has actually happened.
    val cameraBlockedRetryAvailable = cameraNeedsSettings && Manifest.permission.CAMERA !in blockedRetryIssued
    val locationBlockedRetryAvailable = locationNeedsSettings &&
        Manifest.permission.ACCESS_FINE_LOCATION !in blockedRetryIssued
    val audioBlockedRetryAvailable = audioNeedsSettings && Manifest.permission.RECORD_AUDIO !in blockedRetryIssued

    val projectCanCapture = uiState.currentProject?.let { !it.isCaptureLocked && !it.isArchived } == true
    val isProjectSwitching by viewModel.isProjectSwitching.collectAsState()
    val isCaptureBusy = uiState.isCapturing || uiState.isRecordingVideo || isProjectSwitching
    val orientationContext = LocalContext.current
    val orientationActivity = remember(orientationContext) {
        var candidate: android.content.Context = orientationContext
        while (candidate is android.content.ContextWrapper && candidate !is android.app.Activity) candidate = candidate.baseContext
        candidate as? android.app.Activity
    }
    DisposableEffect(orientationActivity) {
        // Camera orientation is an Activity-wide request. Once this destination
        // leaves the foreground, release it so settings/help and other screens
        // follow the device's normal orientation policy.
        onDispose {
            orientationActivity?.requestedOrientation =
                android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }
    LaunchedEffect(uiState.captureOrientation, uiState.orientationDegrees, isCaptureBusy, lifecycleState) {
        if (isCaptureBusy) return@LaunchedEffect
        if (lifecycleState != Lifecycle.State.RESUMED) {
            // NavHost keeps the outgoing destination composed during its
            // transition. Do not let its sensor updates keep controlling the
            // global window while another destination is becoming visible.
            orientationActivity?.requestedOrientation =
                android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            return@LaunchedEffect
        }
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

    val isCameraBusy = isCaptureBusy || !cameraReady
    val addressNeedsRefresh = hasLocationPermission && uiState.currentAddress.isBlank()
    val pickerProjects by viewModel.projectPickerProjects.collectAsState()
    LaunchedEffect(showProjectPicker) {
        if (showProjectPicker) viewModel.refreshProjectPicker()
    }
    LaunchedEffect(addressNeedsRefresh) {
        if (!addressNeedsRefresh) {
            addressRefreshNeedsFallback = false
            showAddressMoreMenu = false
        }
    }

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

    /**
     * Returns true when a permission classified as "needs settings" must first be retried through
     * the system dialog. The persisted "requested" mark is written by the launcher result callbacks
     * below, never before the dialog has actually run.
     */
    fun shouldRetryBlocked(permissions: List<String>): Boolean {
        if (permissions.isEmpty() || permissions.any { it in blockedRetryIssued }) return false
        blockedRetryIssued = blockedRetryIssued + permissions
        return true
    }

    /**
     * Launches a request and drops the local retry mark when the launch itself fails, so a dialog
     * that never appeared is never treated as an already-used retry.
     */
    fun launchPermissionRequest(permissions: List<String>, launch: (Array<String>) -> Unit) {
        if (permissions.isEmpty()) return
        permissionScope.launch {
            try {
                launch(permissions.toTypedArray())
            } catch (error: Exception) {
                blockedRetryIssued = blockedRetryIssued - permissions.toSet()
                Toast.makeText(context, "无法打开权限申请，请稍后重试", Toast.LENGTH_LONG).show()
            }
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        // The system dialog returned, so this is a genuine "asked" fact. Writing it any earlier
        // would turn a failed launch into a fake permanent denial.
        permissionScope.launch { permissionPreferences.markPermissionsRequested(listOf(Manifest.permission.CAMERA)) }
        refreshPermissions()
        if (!hasCameraPermission) Toast.makeText(context,
            if (cameraNeedsSettings) "相机未开启，请到系统设置允许；仍可管理已有资料" else "相机未开启，仍可管理已有资料", Toast.LENGTH_LONG).show()
    }
    val audioPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        permissionScope.launch { permissionPreferences.markPermissionsRequested(listOf(Manifest.permission.RECORD_AUDIO)) }
        refreshPermissions()
        Toast.makeText(context, if (hasAudioPermission) "麦克风已开启，下次录像将包含声音" else "麦克风未开启，仍可录制无声视频", Toast.LENGTH_LONG).show()
    }
    val locationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        permissionScope.launch {
            permissionPreferences.markPermissionsRequested(
                listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
        }
        refreshPermissions()
        if (hasLocationPermission) viewModel.startForegroundServices(locationEnabled = true)
        else Toast.makeText(context, "定位未开启，照片仍可拍摄但不含现场定位", Toast.LENGTH_LONG).show()
    }

    fun requestCameraPermission() {
        val actual = PermissionAccess.read(context, requestedPermissions)
        if (actual.camera) { refreshPermissions(); return }
        val camera = listOf(Manifest.permission.CAMERA)
        if (actual.cameraNeedsSettings && !shouldRetryBlocked(camera)) {
            openPermissionSettings(); return
        }
        requestedHere = requestedHere + camera
        launchPermissionRequest(camera) { cameraPermissionLauncher.launch(it.first()) }
    }
    fun requestOptionalPermission(forLocation: Boolean) {
        val actual = PermissionAccess.read(context, requestedPermissions)
        if (forLocation && !actual.location) {
            val permissions = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            if (actual.locationNeedsSettings && !shouldRetryBlocked(permissions)) { openPermissionSettings(); return }
            requestedHere = requestedHere + permissions
            launchPermissionRequest(permissions) { locationPermissionLauncher.launch(it) }
        } else if (!forLocation && !actual.microphone) {
            val microphone = listOf(Manifest.permission.RECORD_AUDIO)
            if (actual.microphoneNeedsSettings && !shouldRetryBlocked(microphone)) {
                openPermissionSettings(); return
            }
            requestedHere = requestedHere + microphone
            launchPermissionRequest(microphone) { audioPermissionLauncher.launch(it.first()) }
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
                is CameraUiEvent.ProjectSwitched -> {
                    showProjectPicker = false
                    showCreateProject = false
                    newProjectName = ""
                    Toast.makeText(context, "已切换到${event.projectName}", Toast.LENGTH_SHORT).show()
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
                    }) { Text(optionalPermissionActionLabel("位置", locationNeedsSettings, locationBlockedRetryAvailable)) }
                    if (!hasAudioPermission) androidx.compose.material3.TextButton(onClick = {
                        showOptionalPermissions = false; requestOptionalPermission(forLocation = false)
                    }) { Text(optionalPermissionActionLabel("麦克风", audioNeedsSettings, audioBlockedRetryAvailable)) }
                }
            },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { showOptionalPermissions = false }) { Text("暂时不用") } }
        )
    }

    if (!hasCameraPermission) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                // Same Letterbox black as the granted viewfinder: granting the camera must not
                // flip this route from #121212 to pure black.
                .background(Letterbox),
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
                        text = if (cameraNeedsSettings && !cameraBlockedRetryAvailable) "去系统设置开启相机" else "开启相机权限",
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
            // Keep `background` before `windowInsetsPadding`: the letterbox colour has to cover
            // the safe-drawing inset area too, otherwise the #121212 window background shows as
            // lighter bars along the notch and gesture edges.
            .background(Letterbox)
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
        val smallCoverNeedsSecondaryAddressAction = screenEnvironment.profile.smallCover &&
            addressNeedsRefresh && addressRefreshNeedsFallback &&
            smallCoverAddressFallbackSlot(maxHeight.value) == null
        val previewFrameModifier = Modifier.offset(geometry.previewX.dp, geometry.previewY.dp)
            .width(geometry.previewWidth.dp).height(geometry.previewHeight.dp)

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
                .pointerInput(smallCoverNeedsSecondaryAddressAction) {
                    detectTapGestures(
                        onLongPress = {
                            if (smallCoverNeedsSecondaryAddressAction) showAddressMoreMenu = true
                        },
                        onTap = { offset ->
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
                    )
                },
            update = { previewView ->
                previewViewRef = previewView
            }
        )

        // The cover display has no full top shelf. Keep the current project
        // name and the same one-tap chooser entry on the preview itself;
        // this overlay does not change the camera frame or control geometry.
        if (screenEnvironment.profile.smallCover) {
            Box(modifier = previewFrameModifier.zIndex(1f)) {
                TextButton(
                    onClick = { showProjectPicker = true },
                    enabled = !isCaptureBusy,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(4.dp)
                        // Width follows the preview; height only has a 48dp touch-target floor so
                        // the 11sp/3-line project name keeps its room at large font scales.
                        .widthIn(max = (geometry.previewWidth - 8f).coerceAtLeast(48f).dp)
                        .heightIn(min = 48.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = uiState.currentProject?.let { project ->
                            buildString {
                                append(project.name)
                                if (project.isCaptureLocked) append(" · 已锁定")
                                if (project.isArchived) append(" · 已归档")
                            }
                        } ?: "请选择工程包",
                        color = Color.White,
                        fontSize = 11.sp,
                        lineHeight = 14.sp,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                // The cover has no full top shelf, but independently exposed
                // rear groups must remain reachable there as well.  Keep the
                // selector in the preview's top-right corner, separate from
                // the project chooser at top-left and the narrow control dock.
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(2.dp)
                ) {
                    CameraLensSelector(
                        publicLenses = uiState.cameraCapability.publicLenses,
                        activeCameraId = uiState.cameraCapability.activeCameraId,
                        enabled = !isCaptureBusy,
                        onLensSelected = { lens ->
                            val preview = previewViewRef
                            if (preview == null || !viewModel.cameraManager.switchToPublicLens(lens, lifecycleOwner, preview)) {
                                Toast.makeText(context, "当前镜头暂不可绑定", Toast.LENGTH_SHORT).show()
                            }
                        },
                        compact = true,
                        captureMode = uiState.captureMode
                    )
                }
            }
        }

        if (!isLandscape) {
            // An opaque shelf is intentional here: MIUI keeps top actions out
            // of the live image, which makes the fixed frame visually obvious.
            // Same Letterbox black as the root, so the route has one colour.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(portraitTopBarHeight)
                    .align(Alignment.TopCenter)
                    .background(Letterbox)
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height((maxHeight - portraitPreviewBottom).coerceAtLeast(0.dp))
                    .align(Alignment.BottomCenter)
                    .background(Letterbox)
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

        // Address retry stays in the live framing area, but chooses an edge
        // that does not cover the current watermark card. The preview frame
        // is already separated from shelves, safe insets, and the shutter
        // dock by CameraGeometry, so the control cannot steal capture space.
        if (addressNeedsRefresh && watermarkOverlaySize.width > 0 && watermarkOverlaySize.height > 0) {
            Box(modifier = previewFrameModifier.zIndex(2f)) {
                AddressRefreshPill(
                    state = uiState.addressRefreshState,
                    watermarkRect = WatermarkLayoutEngine.calculateLayout(
                        canvasWidth = watermarkOverlaySize.width.toFloat(),
                        canvasHeight = watermarkOverlaySize.height.toFloat(),
                        data = uiState.watermarkData
                    ).cardRect,
                    enabled = !isCaptureBusy && uiState.addressRefreshState != "REFRESHING",
                    onClick = viewModel::refreshAddress,
                    onNoSafePlacement = { noSafePlacement ->
                        if (addressRefreshNeedsFallback != noSafePlacement) {
                            addressRefreshNeedsFallback = noSafePlacement
                        }
                    }
                )
            }
        }

        // 3. Focus Ring
        FocusRing(
            position = focusPosition,
            onAnimationEnd = { focusPosition = null }
        )

        if (!projectCanCapture) {
            val blockedProject = uiState.currentProject
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Letterbox.copy(alpha = 0.82f))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "${blockedProject?.name ?: "当前工程不可用"}：禁止拍摄",
                    color = EngineeringYellow
                )
                Text(
                    when {
                        blockedProject?.isArchived == true && blockedProject.isCaptureLocked -> "请先恢复工程，再解锁拍摄"
                        blockedProject?.isArchived == true -> "请恢复工程后再拍摄"
                        blockedProject?.isCaptureLocked == true -> "请解锁拍摄后再继续"
                        else -> "请选择可拍摄工程"
                    },
                    color = Color.White.copy(alpha = .82f),
                    fontSize = 13.sp
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (blockedProject?.isArchived == true) {
                        TextButton(
                            onClick = viewModel::restoreCurrentProject,
                            enabled = !isCaptureBusy
                        ) { Text("恢复工程") }
                    }
                    if (blockedProject?.isCaptureLocked == true) {
                        TextButton(
                            onClick = viewModel::unlockCurrentProject,
                            enabled = !isCaptureBusy
                        ) { Text("解锁拍摄") }
                    }
                    if (blockedProject == null || (!blockedProject.isArchived && !blockedProject.isCaptureLocked)) {
                        TextButton(onClick = { showProjectPicker = true }, enabled = !isCaptureBusy) {
                            Text("选择工程")
                        }
                    }
                }
            }
        }
        // 4. Top Controls Bar
        if (!screenEnvironment.profile.smallCover) CameraTopBar(
            projectName = uiState.currentProject?.let { project ->
                buildString {
                    append(project.name)
                    if (project.isCaptureLocked) append(" · 已锁定")
                    if (project.isArchived) append(" · 已归档")
                }
            } ?: "请选择工程包",
            orientationLabel = uiState.captureOrientation.label,
            onOrientationSelected = viewModel::setCaptureOrientation,
            flashMode = uiState.flashMode,
            isQuickIssueMode = uiState.isQuickIssueMode,
            onProjectClick = { showProjectPicker = true },
            onFlashToggle = { viewModel.toggleFlashMode() },
            onFlashLongPress = { viewModel.toggleTorchMode() },
            onQuickIssueToggle = { viewModel.toggleQuickIssueMode() },
            onSettingsClick = onNavigateToSettings,
            isBusy = isCaptureBusy,
            isLandscape = isLandscape,
            publicLenses = uiState.cameraCapability.publicLenses,
            activeCameraId = uiState.cameraCapability.activeCameraId,
            captureMode = uiState.captureMode,
            onLensSelected = { lens ->
                val preview = previewViewRef
                if (preview == null || !viewModel.cameraManager.switchToPublicLens(lens, lifecycleOwner, preview)) {
                    Toast.makeText(context, "当前镜头暂不可绑定", Toast.LENGTH_SHORT).show()
                }
            },
            addressRefreshState = if (addressNeedsRefresh && addressRefreshNeedsFallback && !screenEnvironment.profile.smallCover) {
                uiState.addressRefreshState
            } else null,
            onAddressRefreshClick = if (addressNeedsRefresh && addressRefreshNeedsFallback && !screenEnvironment.profile.smallCover) {
                viewModel::refreshAddress
            } else null,
            modifier = Modifier.offset(geometry.toolbarX.dp, 0.dp)
                .width(geometry.toolbarWidth.dp).height(geometry.toolbarHeight.dp)
        )

        if (!screenEnvironment.profile.smallCover &&
            (!hasLocationPermission || (uiState.captureMode == CaptureMode.VIDEO && !hasAudioPermission))) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(
                        start = if (isLandscape) landscapeToolBarWidth + 12.dp else 12.dp,
                        top = if (isLandscape) 12.dp else portraitTopBarHeight + 12.dp,
                        end = 12.dp
                    )
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(18.dp))
                    .background(Letterbox.copy(alpha = 0.78f))
                    .clickable(enabled = !isCaptureBusy && permissionPreferenceState != null && uiState.addressRefreshState != "REFRESHING") {
                        showOptionalPermissions = true
                    }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = when {
                        !hasLocationPermission -> Icons.Default.LocationOn
                        else -> Icons.Default.MicOff
                    },
                    contentDescription = null,
                    tint = if (!hasLocationPermission) EngineeringYellow else Color.White,
                    modifier = Modifier.size(17.dp)
                )
                Text(
                    text = when {
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
                addressRefreshFallbackState = if (addressNeedsRefresh && addressRefreshNeedsFallback && screenEnvironment.profile.smallCover) {
                    uiState.addressRefreshState
                } else null,
                onAddressRefreshFallbackClick = if (addressNeedsRefresh && addressRefreshNeedsFallback && screenEnvironment.profile.smallCover) {
                    viewModel::refreshAddress
                } else null,
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

        if (showAddressMoreMenu && smallCoverNeedsSecondaryAddressAction) {
            Box(modifier = previewFrameModifier) {
                androidx.compose.material3.DropdownMenu(
                    expanded = true,
                    onDismissRequest = { showAddressMoreMenu = false }
                ) {
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text("刷新地址") },
                        onClick = {
                            showAddressMoreMenu = false
                            viewModel.refreshAddress()
                        }
                    )
                }
            }
        }

        if (showProjectPicker) {
            CameraProjectPicker(
                currentProject = uiState.currentProject,
                projects = pickerProjects,
                isSwitching = isProjectSwitching,
                isLandscape = isLandscape,
                wideLayout = screenEnvironment.profile.large,
                onDismiss = { if (!isProjectSwitching) showProjectPicker = false },
                onSelect = viewModel::selectProjectFromCamera,
                onOpenAll = {
                    showProjectPicker = false
                    onNavigateToProjects()
                },
                onCreate = {
                    showProjectPicker = false
                    newProjectName = ""
                    showCreateProject = true
                },
                onCurrent = { showProjectPicker = false }
            )
        }
        if (showCreateProject) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { if (!isProjectSwitching) showCreateProject = false },
                title = { Text("新建工程包") },
                text = {
                    Column {
                        Text("创建后会立即作为当前拍摄工程。", color = Color.White.copy(alpha = .72f))
                        OutlinedTextField(
                            value = newProjectName,
                            onValueChange = { newProjectName = it },
                            label = { Text("工程名称") },
                            singleLine = true,
                            enabled = !isProjectSwitching
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.createProjectFromCamera(newProjectName)
                        },
                        enabled = newProjectName.isNotBlank() && !isProjectSwitching
                    ) { Text(if (isProjectSwitching) "创建中…" else "创建并使用") }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showCreateProject = false },
                        enabled = !isProjectSwitching
                    ) { Text("取消") }
                }
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

@Composable
private fun AddressRefreshPill(
    state: String,
    watermarkRect: android.graphics.RectF,
    enabled: Boolean,
    onClick: () -> Unit,
    onNoSafePlacement: (Boolean) -> Unit
) {
    val density = LocalDensity.current
    val marginPx = with(density) { 12.dp.toPx() }
    val label = when (state) {
        "REFRESHING" -> "地址刷新中…"
        "FAILED_PERMISSION" -> "定位未开启 · 重试"
        "FAILED_LOCATION" -> "定位不可用 · 重试"
        "FAILED_ADDRESS" -> "地址服务失败 · 重试"
        "FAILED" -> "地址不可用 · 重试"
        else -> "刷新地址"
    }
    val minChipWidth = with(density) { 132.dp.toPx() }
    val minChipHeight = with(density) { 40.dp.toPx() }
    val gapPx = with(density) { 8.dp.toPx() }

    // Measure the real row, including wrapping at the current font scale,
    // before selecting a corner.  A fixed 176x40 assumption made large text
    // both truncate and reserve the wrong protected area.
    SubcomposeLayout(modifier = Modifier.fillMaxSize()) { constraints ->
        val maxChipWidth = (constraints.maxWidth - (marginPx * 2f).toInt()).coerceAtLeast(1)
        val maxChipHeight = (constraints.maxHeight - (marginPx * 2f).toInt()).coerceAtLeast(1)
        val minWidth = minChipWidth.toInt().coerceAtMost(maxChipWidth)
        val minHeight = minChipHeight.toInt().coerceAtMost(maxChipHeight)
        val measured = subcompose("address-refresh-pill") {
            Row(
                modifier = Modifier
                    .widthIn(
                        min = with(density) { minWidth.toDp() },
                        max = with(density) { maxChipWidth.toDp() }
                    )
                    .heightIn(
                        min = with(density) { minHeight.toDp() },
                        max = with(density) { maxChipHeight.toDp() }
                    )
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(20.dp))
                    .background(Letterbox.copy(alpha = if (enabled) .82f else .64f))
                    .clickable(enabled = enabled, onClick = onClick)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = label,
                    tint = EngineeringYellow,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = label,
                    color = Color.White,
                    fontSize = 12.sp,
                    softWrap = true,
                    modifier = Modifier.padding(start = 6.dp)
                )
            }
        }.single().measure(
            Constraints(
                minWidth = 0,
                maxWidth = maxChipWidth,
                minHeight = 0,
                maxHeight = maxChipHeight
            )
        )
        val corner = chooseAddressRefreshCorner(
            canvasWidth = constraints.maxWidth.toFloat(),
            canvasHeight = constraints.maxHeight.toFloat(),
            chipWidth = measured.width.toFloat(),
            chipHeight = measured.height.toFloat(),
            watermarkRect = watermarkRect,
            margin = marginPx,
            gap = gapPx
        )
        val bounds = addressRefreshRectForCorner(
            corner = corner,
            canvasWidth = constraints.maxWidth.toFloat(),
            canvasHeight = constraints.maxHeight.toFloat(),
            chipWidth = measured.width.toFloat(),
            chipHeight = measured.height.toFloat(),
            margin = marginPx
        )
        val x: Int
        val y: Int
        if (bounds != null) {
            x = bounds.left.toInt()
            y = bounds.top.toInt()
        } else {
            // No candidate clears the watermark. The retry is rendered in the
            // profile's measured safe control shelf by CameraTopBar or
            // CameraBottomBar; do not draw a negative/out-of-window child here.
            x = ((constraints.maxWidth - measured.width) / 2f).toInt()
            y = constraints.maxHeight
        }
        layout(constraints.maxWidth, constraints.maxHeight) {
            // Placement is the first point at which the measured decision is
            // committed. The callback is guarded by the caller and therefore
            // only requests a recomposition when the safe-shelf state changes.
            onNoSafePlacement(corner == AddressRefreshCorner.OUTSIDE_PREVIEW)
            if (bounds != null) measured.placeRelative(x, y)
        }
    }
}

@Composable
private fun CameraProjectPicker(
    currentProject: com.sitecam.app.core.database.entity.ProjectEntity?,
    projects: List<com.sitecam.app.core.database.entity.ProjectEntity>,
    isSwitching: Boolean,
    isLandscape: Boolean,
    wideLayout: Boolean,
    onDismiss: () -> Unit,
    onSelect: (Long) -> Unit,
    onOpenAll: () -> Unit,
    onCreate: () -> Unit,
    onCurrent: () -> Unit
) {
    val recent = projects.filter { it.id != currentProject?.id && !it.isArchived }
    if (!isLandscape && !wideLayout) {
        androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss) {
            CameraProjectPickerContent(
                currentProject = currentProject,
                recent = recent,
                isSwitching = isSwitching,
                onSelect = onSelect,
                onOpenAll = onOpenAll,
                onCreate = onCreate,
                onCurrent = onCurrent,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
    } else {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("切换工程包") },
            text = {
                CameraProjectPickerContent(
                    currentProject = currentProject,
                    recent = recent,
                    isSwitching = isSwitching,
                    onSelect = onSelect,
                    onOpenAll = onOpenAll,
                    onCreate = onCreate,
                    onCurrent = onCurrent
                )
            },
            confirmButton = {
                TextButton(onClick = onDismiss, enabled = !isSwitching) { Text("关闭") }
            }
        )
    }
}

@Composable
private fun CameraProjectPickerContent(
    currentProject: com.sitecam.app.core.database.entity.ProjectEntity?,
    recent: List<com.sitecam.app.core.database.entity.ProjectEntity>,
    isSwitching: Boolean,
    onSelect: (Long) -> Unit,
    onOpenAll: () -> Unit,
    onCreate: () -> Unit,
    onCurrent: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.heightIn(max = 480.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            TextButton(onClick = onOpenAll, enabled = !isSwitching, modifier = Modifier.weight(1f)) {
                Text("全部工程包", maxLines = 1)
            }
            TextButton(onClick = onCreate, enabled = !isSwitching, modifier = Modifier.weight(1f)) {
                Text("新建工程", maxLines = 1)
            }
        }
        if (isSwitching) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Text("切换中…", color = EngineeringYellow, fontSize = 13.sp)
        }
        LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
            item {
                Text("当前工程", color = EngineeringYellow, fontWeight = FontWeight.Bold)
                if (currentProject != null) {
                    ProjectPickerRow(
                        project = currentProject,
                        current = true,
                        enabled = !isSwitching,
                        onClick = onCurrent
                    )
                } else {
                    Text("尚未选择工程包", color = Color.White.copy(alpha = .72f), modifier = Modifier.padding(vertical = 10.dp))
                }
                Text("最近拍摄", color = EngineeringYellow, fontWeight = FontWeight.Bold)
                Text("按最近拍照或录像时间排序，不含已归档工程", color = Color.White.copy(alpha = .72f), fontSize = 12.sp)
                if (recent.isEmpty()) {
                    Text("暂无其他有拍摄记录的工程", color = Color.White.copy(alpha = .72f), modifier = Modifier.padding(vertical = 10.dp))
                }
            }
            items(recent, key = { it.id }) { project ->
                ProjectPickerRow(
                    project = project,
                    current = false,
                    enabled = !isSwitching,
                    onClick = { onSelect(project.id) }
                )
            }
        }
    }
}

@Composable
private fun ProjectPickerRow(
    project: com.sitecam.app.core.database.entity.ProjectEntity,
    current: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 4.dp)
    ) {
        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
            Text(
                text = project.name,
                color = if (current) EngineeringYellow else Color.White,
                fontWeight = if (current) FontWeight.Bold else FontWeight.Normal,
                maxLines = 2
            )
            Text(
                text = buildString {
                    if (project.routeName.isNotBlank()) append("线路 · ${project.routeName} · ")
                    append(if (project.isArchived) "已归档" else if (project.isCaptureLocked) "已锁定拍摄" else "可拍摄")
                },
                color = Color.White.copy(alpha = .65f),
                fontSize = 12.sp,
                maxLines = 2
            )
        }
        Text(
            text = if (current) "返回相机" else "切换",
            color = if (current) EngineeringYellow else Color.White.copy(alpha = .82f),
            fontSize = 12.sp
        )
    }
}

/**
 * Label for the optional-permission entry points. The system settings page is only promised once
 * the in-session system dialog retry has been used up.
 */
private fun optionalPermissionActionLabel(name: String, needsSettings: Boolean, retryAvailable: Boolean): String =
    if (needsSettings && !retryAvailable) "去设置开启$name" else "开启${name}权限"
