package com.sitecam.app.core.camera

import android.annotation.SuppressLint
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.os.Build
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import android.util.Size
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.util.Rational
import android.util.Log
import android.view.Surface
import android.view.View
import androidx.camera.core.Camera
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.AspectRatio
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.ViewPort
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoRecordEvent
import androidx.camera.video.VideoCapture
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.ceil
import kotlin.math.floor

class CameraManager(private val context: Context) {

    enum class VideoRecordingState {
        IDLE,
        STARTING,
        RECORDING,
        FINALIZING
    }

    private val captureExecutor: Executor = Dispatchers.IO.asExecutor()

    private var cameraProvider: ProcessCameraProvider? = null
    // Assigned on the main thread by binding, read from capture coroutines and
    // from CameraX callbacks, so the references must be safely published.
    @Volatile
    private var camera: Camera? = null
    @Volatile
    private var imageCapture: ImageCapture? = null
    @Volatile
    private var videoCapture: VideoCapture<Recorder>? = null
    @Volatile
    private var activeRecording: Recording? = null
    @Volatile
    private var activeRecordingToken: Long? = null
    private var nextRecordingToken = 0L
    private var unbindWhenIdle = false
    private var preview: Preview? = null
    private var previewViewRef: PreviewView? = null
    private var lifecycleOwnerRef: LifecycleOwner? = null
    private val unavailableZoomLenses = mutableSetOf<String>()
    private val unavailableVideoZoomLenses = mutableSetOf<String>()
    private var zoomRequiresVideo = false
    private var pendingDisplayZoom: Float? = null
    private var zoomRequestGeneration = 0L
    private var exposureCompensationEv = 0.0
    @Volatile private var snapshotEnabled = true
    @Volatile private var snapshotShutterSpeed = 250
    @Volatile private var exposurePrioritySupported = false
    @Volatile private var exposurePriorityActive = false
    @Volatile private var exposurePriorityPausedUntil = 0L

    fun previewIsMirrored(): Boolean = currentLensFacing == CameraSelector.LENS_FACING_FRONT

    @SuppressLint("UnsafeOptInUsageError")
    suspend fun configureSnapshot(enabled: Boolean, shutterSpeed: Int) = withContext(Dispatchers.Main) {
        val rebind = snapshotEnabled != enabled
        snapshotEnabled = enabled
        snapshotShutterSpeed = SnapshotPolicy.shutterSpeed(shutterSpeed)
        exposurePriorityPausedUntil = 0L
        if (rebind && _videoRecordingState.value == VideoRecordingState.IDLE) {
            val owner = lifecycleOwnerRef
            val view = previewViewRef
            if (owner != null && view != null && camera != null) {
                val zoom = _currentZoomRatio.value
                bindCameraUseCases(owner, view, currentLensFacing, currentCameraId, zoomRequiresVideo)
                setZoomRatio(zoom, requireVideo = zoomRequiresVideo)
            }
        }
        applySnapshotControls(false)
    }

    /** Keep AE and its ISO control active; never force a dark manual exposure. */
    @SuppressLint("UnsafeOptInUsageError")
    private fun applySnapshotControls(shortExposure: Boolean) {
        val active = camera ?: return
        val options = CaptureRequestOptions.Builder()
        if (snapshotEnabled && !zoomRequiresVideo) {
            val info = Camera2CameraInfo.from(active.cameraInfo)
            val ranges = info.getCameraCharacteristic(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES).orEmpty()
            ranges.takeIf { shortExposure }.orEmpty().filter { it.lower >= 30 && it.upper <= 60 }.minByOrNull { it.lower + it.upper }?.let {
                options.setCaptureRequestOption(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, it)
            }
            if (Build.VERSION.SDK_INT >= 36 && exposurePrioritySupported) {
                options.setCaptureRequestOption(CaptureRequest.CONTROL_AE_PRIORITY_MODE,
                    if (shortExposure) CaptureRequest.CONTROL_AE_PRIORITY_MODE_SENSOR_EXPOSURE_TIME_PRIORITY else CaptureRequest.CONTROL_AE_PRIORITY_MODE_OFF)
                if (shortExposure) {
                    val range = info.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)
                    val requested = SnapshotPolicy.exposureNanos(snapshotShutterSpeed)
                    options.setCaptureRequestOption(CaptureRequest.SENSOR_EXPOSURE_TIME, range?.clamp(requested) ?: requested)
                }
            }
        }
        exposurePriorityActive = shortExposure
        val future = Camera2CameraControl.from(active.cameraControl).setCaptureRequestOptions(options.build())
        future.addListener({ runCatching { future.get() }.onFailure {
            Log.w("CameraManager", "Snapshot controls unavailable", it)
            if (camera === active) {
                exposurePriorityActive = false
                exposurePrioritySupported = false
                Camera2CameraControl.from(active.cameraControl).clearCaptureRequestOptions()
            }
        } }, ContextCompat.getMainExecutor(context))
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun updateExposurePriority(result: TotalCaptureResult) {
        if (!snapshotEnabled || zoomRequiresVideo || !exposurePrioritySupported || Build.VERSION.SDK_INT < 36) return
        val active = camera ?: return
        val iso = result.get(CaptureResult.SENSOR_SENSITIVITY) ?: return
        val exposure = result.get(CaptureResult.SENSOR_EXPOSURE_TIME) ?: return
        val maxIso = Camera2CameraInfo.from(active.cameraInfo)
            .getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)?.upper ?: return
        val now = android.os.SystemClock.elapsedRealtime()
        if (exposurePriorityActive && iso >= maxIso * .90) {
            exposurePriorityPausedUntil = now + 2000L
            ContextCompat.getMainExecutor(context).execute { if (camera === active) applySnapshotControls(false) }
        } else if (!exposurePriorityActive && now >= exposurePriorityPausedUntil &&
            exposure.toDouble() * iso / SnapshotPolicy.exposureNanos(snapshotShutterSpeed) < maxIso * .75) {
            exposurePriorityPausedUntil = now + 1000L
            ContextCompat.getMainExecutor(context).execute { if (camera === active) applySnapshotControls(true) }
        }
    }

    private val _cameraCapability = MutableStateFlow(CameraCapability())
    val cameraCapability: StateFlow<CameraCapability> = _cameraCapability.asStateFlow()

    private val _currentZoomRatio = MutableStateFlow(1.0f)
    val currentZoomRatio: StateFlow<Float> = _currentZoomRatio.asStateFlow()

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _videoRecordingState = MutableStateFlow(VideoRecordingState.IDLE)
    val videoRecordingState: StateFlow<VideoRecordingState> = _videoRecordingState.asStateFlow()

    private val _isCameraReady = MutableStateFlow(false)
    val isCameraReady: StateFlow<Boolean> = _isCameraReady.asStateFlow()

    private val _cameraError = MutableStateFlow<String?>(null)
    val cameraError: StateFlow<String?> = _cameraError.asStateFlow()

    private var currentLensFacing = CameraSelector.LENS_FACING_BACK
    private var currentCameraId: String? = null
    private var lensInspection = CameraCapabilityInspection()
    private var currentFlashMode = "AUTO" // AUTO, ON, OFF, TORCH
    private var activeZoomObserver: Observer<androidx.camera.core.ZoomState>? = null
    private var activeZoomInfo: CameraInfo? = null
    private var physicalStateObserver: Observer<androidx.camera.core.CameraState>? = null
    private var physicalStateInfo: CameraInfo? = null
    @Volatile private var cameraBindingGeneration = 0L
    private var targetRotation: Int = Surface.ROTATION_0
    // Preview, still capture and video must all follow the real display
    // rotation. Forcing Preview to ROTATION_0 made a physical phone keep a
    // portrait camera buffer after the UI had already entered landscape.
    private var previewTargetRotation: Int = Surface.ROTATION_0

    suspend fun initializeCamera(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        lensFacing: Int = currentLensFacing,
        flashMode: String = "AUTO",
        displayRotation: Int = Surface.ROTATION_0
    ) = withContext(Dispatchers.Main) {
        try {
            if (previewViewRef === previewView && _isCameraReady.value && lensFacing == currentLensFacing) {
                updateTargetRotation(displayRotation)
                return@withContext
            }
            val previousZoom = _currentZoomRatio.value
            previewViewRef = previewView
            lifecycleOwnerRef = lifecycleOwner
            currentFlashMode = flashMode
            targetRotation = safeCameraTargetRotation(displayRotation)
            previewTargetRotation = previewTargetRotationForCameraStream(targetRotation)

            val provider = getCameraProvider()
            cameraProvider = provider

            val requestedLens = if (hasCamera(provider, lensFacing)) {
                lensFacing
            } else {
                val fallback = oppositeLensFacing(lensFacing)
                if (hasCamera(provider, fallback)) fallback else lensFacing
            }
            if (bindCameraUseCases(lifecycleOwner, previewView, requestedLens)) {
                // previousZoom is already expressed in the main-camera
                // display scale.  setZoomRatio maps it to the newly bound
                // session (including an independent tele camera's native
                // 1x), instead of passing a display value to CameraX as if
                // it were the new session's native ratio.
                setZoomRatio(previousZoom)
            }
        } catch (cancelled: CancellationException) {
            // A resized/disposed preview cancels its LaunchedEffect normally.
            throw cancelled
        } catch (error: Exception) {
            _isCameraReady.value = false
            reportCameraError("相机启动失败，请检查相机权限或重试")
            Log.e("CameraManager", "Camera initialization failed", error)
        }
    }

    private suspend fun getCameraProvider(): ProcessCameraProvider = suspendCancellableCoroutine { continuation ->
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            try {
                if (continuation.isActive) continuation.resume(future.get())
            } catch (e: Exception) {
                if (continuation.isActive) continuation.resumeWithException(e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun bindCameraUseCases(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        requestedLensFacing: Int = currentLensFacing,
        requestedCameraId: String? = null,
        requireVideo: Boolean = false
    ): Boolean {
        val provider = cameraProvider ?: return false
        val previousLensFacing = currentLensFacing
        val previousCameraId = currentCameraId
        val previousZoom = _currentZoomRatio.value
        val previousCameraInfo = camera?.cameraInfo
        val previousCameraState = previousCameraInfo?.cameraState?.value
        val defaultSelector = CameraSelector.Builder().requireLensFacing(requestedLensFacing).build()
        // An explicit camera ID is a user selection and therefore remains the
        // only candidate.  For the ordinary front/back path, inspect the
        // public CameraX inventory first so a suitable logical group gets the
        // first real Preview + ImageCapture + Video bind attempt.  The facing
        // selector remains the compatibility fallback for devices whose
        // metadata is incomplete or whose preferred group cannot bind.
        val candidates = buildList<Pair<String?, CameraSelector>> {
            if (requestedCameraId != null) {
                add(requestedCameraId to selectorForSelection(requestedCameraId))
            } else {
                val preferredCameraId = CameraCapabilityInspector.preferredPublicCameraId(
                    context = context,
                    provider = provider,
                    lensFacing = requestedLensFacing,
                    includeVideo = true
                )
                if (preferredCameraId != null) {
                    add(preferredCameraId to CameraCapabilityInspector.selectorForCameraId(preferredCameraId))
                }
                add(null to defaultSelector)
            }
        }

        val availableCandidates = candidates.filter { (_, selector) -> hasCamera(provider, selector) }
        if (availableCandidates.isEmpty()) {
            reportCameraError("当前设备没有可用的${lensName(requestedLensFacing)}摄像头")
            return false
        }

        var lastBindingError: Exception? = null
        // Try every public candidate with the complete capture combination
        // before accepting a still-photo-only fallback.  A preferred logical
        // group must not hide a later public group that can record video.
        for ((candidateCameraId, selector) in availableCandidates) {
            try {
                bindUseCases(
                    provider = provider,
                    lifecycleOwner = lifecycleOwner,
                    previewView = previewView,
                    cameraSelector = selector,
                    includeVideo = true
                )
                currentLensFacing = requestedLensFacing
                currentCameraId = selector.physicalCameraId ?: CameraCapabilityInspector.cameraIdOf(camera?.cameraInfo) ?: candidateCameraId
                _isCameraReady.value = true
                _cameraError.value = null
                watchPhysicalBinding(lifecycleOwner, previewView, previousCameraId, previousLensFacing,
                    previousZoom, requireVideo, previousCameraInfo, previousCameraState)
                return true
            } catch (videoBindingError: Exception) {
                lastBindingError = videoBindingError
                Log.w("CameraManager", "Preview/photo/video binding failed for candidate", videoBindingError)
            }
        }

        // Some vendor Camera2 implementations cannot bind all three use
        // cases at once. Keep preview and still capture usable only after all
        // complete candidates have had a chance to bind.
        for ((candidateCameraId, selector) in if (requireVideo) emptyList() else availableCandidates) {
            try {
                bindUseCases(
                    provider = provider,
                    lifecycleOwner = lifecycleOwner,
                    previewView = previewView,
                    cameraSelector = selector,
                    includeVideo = false,
                    videoBindingAttempted = true
                )
                currentLensFacing = requestedLensFacing
                currentCameraId = selector.physicalCameraId ?: CameraCapabilityInspector.cameraIdOf(camera?.cameraInfo) ?: candidateCameraId
                _isCameraReady.value = true
                _cameraError.value = "当前设备不支持同时录像，已启用拍照模式"
                watchPhysicalBinding(lifecycleOwner, previewView, previousCameraId, previousLensFacing,
                    previousZoom, requireVideo, previousCameraInfo, previousCameraState)
                return true
            } catch (bindingError: Exception) {
                lastBindingError = bindingError
                Log.e("CameraManager", "Camera use case binding failed for candidate", bindingError)
            }
        }
        lastBindingError?.let { Log.e("CameraManager", "All camera candidates failed to bind", it) }

        // A lens switch is transactional: restore the lens that was already
        // working before reporting failure to the UI.  If that previous
        // session was still-photo-only, retain it with the same fallback.
        val previousSelector = previousCameraId?.let(::selectorForSelection)
            ?: CameraSelector.Builder().requireLensFacing(previousLensFacing).build()
        if ((requestedCameraId != previousCameraId || requestedLensFacing != previousLensFacing) &&
            hasCamera(provider, previousSelector)
        ) {
            runCatching {
                bindUseCases(
                    provider = provider,
                    lifecycleOwner = lifecycleOwner,
                    previewView = previewView,
                    cameraSelector = previousSelector,
                    includeVideo = true
                )
            }.recoverCatching {
                bindUseCases(
                    provider = provider,
                    lifecycleOwner = lifecycleOwner,
                    previewView = previewView,
                    cameraSelector = previousSelector,
                    includeVideo = false,
                    videoBindingAttempted = true
                )
            }.onSuccess {
                currentLensFacing = previousLensFacing
                currentCameraId = previousCameraId
                _isCameraReady.value = true
            }.onFailure { restoreError ->
                Log.e("CameraManager", "Failed to restore previous camera", restoreError)
                _isCameraReady.value = false
            }
        } else {
            _isCameraReady.value = false
        }
        reportCameraError("相机启动失败，请检查相机权限或重试")
        return false
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun bindUseCases(
        provider: ProcessCameraProvider,
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        cameraSelector: CameraSelector,
        includeVideo: Boolean,
        videoBindingAttempted: Boolean = includeVideo
    ) {
        val previewBuilder = Preview.Builder()
            .setTargetAspectRatio(AspectRatio.RATIO_4_3)
            .setTargetRotation(previewTargetRotation)
        CameraDiagnostics.clearSession()
        val diagnosticGeneration = cameraBindingGeneration + 1L
        Camera2Interop.Extender(previewBuilder).setSessionCaptureCallback(object : CameraCaptureSession.CaptureCallback() {
            override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
                if (cameraBindingGeneration != diagnosticGeneration) return
                updateExposurePriority(result)
                CameraDiagnostics.updateSession(
                    cameraSelector.physicalCameraId ?: CameraCapabilityInspector.cameraIdOf(camera?.cameraInfo),
                    if (Build.VERSION.SDK_INT >= 29) result.get(CaptureResult.LOGICAL_MULTI_CAMERA_ACTIVE_PHYSICAL_ID) else null,
                    if (Build.VERSION.SDK_INT >= 30) request.get(CaptureRequest.CONTROL_ZOOM_RATIO) else null,
                    if (Build.VERSION.SDK_INT >= 30) result.get(CaptureResult.CONTROL_ZOOM_RATIO) else null,
                    result.get(CaptureResult.LENS_FOCAL_LENGTH))
            }
        })
        val newPreview = previewBuilder.build()
            .also { it.surfaceProvider = previewView.surfaceProvider }
        val imageBuilder = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .setFlashMode(mapImageCaptureFlashMode(currentFlashMode))
            .setTargetRotation(targetRotation)
        if (snapshotEnabled) imageBuilder.setResolutionSelector(ResolutionSelector.Builder()
            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
            .setResolutionStrategy(ResolutionStrategy(Size(2560, 1920), ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER)).build())
        else imageBuilder.setTargetAspectRatio(AspectRatio.RATIO_4_3)
        val newImageCapture = imageBuilder.build()
        val newVideoCapture = if (includeVideo) {
            val recorder = Recorder.Builder()
                .setQualitySelector(QualitySelector.from(Quality.HD))
                .build()
            VideoCapture.Builder(recorder)
                .setTargetRotation(previewTargetRotation)
                .build()
        } else {
            null
        }

        activeZoomObserver?.let { observer -> activeZoomInfo?.zoomState?.removeObserver(observer) }
        activeZoomObserver = null
        activeZoomInfo = null
        physicalStateObserver?.let { physicalStateInfo?.cameraState?.removeObserver(it) }
        physicalStateObserver = null
        physicalStateInfo = null
        cameraBindingGeneration += 1L
        camera?.cameraInfo?.exposureState?.let { exposure ->
            exposureCompensationEv = exposure.exposureCompensationIndex * exposure.exposureCompensationStep.toDouble()
        }
        provider.unbindAll()
        // Do not leave references to use cases that were just unbound. A
        // failed vendor bind must never make a later start attempt write to a
        // stale VideoCapture instance.
        camera = null
        preview = null
        imageCapture = null
        videoCapture = null

        val useCaseGroupBuilder = UseCaseGroup.Builder()
            .addUseCase(newPreview)
            .addUseCase(newImageCapture)
        newVideoCapture?.let(useCaseGroupBuilder::addUseCase)
        if (previewView.width > 0 && previewView.height > 0) {
            useCaseGroupBuilder.setViewPort(
                ViewPort.Builder(
                    Rational(previewView.width, previewView.height),
                    previewTargetRotation
                )
                    .setScaleType(ViewPort.FILL_CENTER)
                    .setLayoutDirection(
                        if (previewView.layoutDirection == View.LAYOUT_DIRECTION_RTL) {
                            View.LAYOUT_DIRECTION_RTL
                        } else {
                            View.LAYOUT_DIRECTION_LTR
                        }
                    )
                    .build()
            )
        }
        val boundCamera = provider.bindToLifecycle(
            lifecycleOwner,
            cameraSelector,
            useCaseGroupBuilder.build()
        )

        preview = newPreview
        imageCapture = newImageCapture
        videoCapture = newVideoCapture
        camera = boundCamera
        exposurePriorityActive = false
        exposurePrioritySupported = Build.VERSION.SDK_INT >= 36 && runCatching {
            Camera2CameraInfo.from(boundCamera.cameraInfo)
                .getCameraCharacteristic(CameraCharacteristics.CONTROL_AE_AVAILABLE_PRIORITY_MODES)
                ?.contains(CaptureRequest.CONTROL_AE_PRIORITY_MODE_SENSOR_EXPOSURE_TIME_PRIORITY) == true
        }.getOrDefault(false)
        applySnapshotControls(false)
        boundCamera.cameraControl.enableTorch(currentFlashMode.uppercase() == "TORCH")
        boundCamera.cameraInfo.exposureState.let { exposure ->
            val step = exposure.exposureCompensationStep.toDouble()
            if (exposure.isExposureCompensationSupported && step > 0.0) {
                val index = kotlin.math.round(exposureCompensationEv / step).toInt()
                    .coerceIn(exposure.exposureCompensationRange.lower, exposure.exposureCompensationRange.upper)
                boundCamera.cameraControl.setExposureCompensationIndex(index)
            }
        }

        val cameraInfo = boundCamera.cameraInfo
        val hasFront = hasCamera(provider, CameraSelector.LENS_FACING_FRONT)
        val hasBack = hasCamera(provider, CameraSelector.LENS_FACING_BACK)
        lensInspection = CameraCapabilityInspector.inspect(
            context = context,
            provider = provider,
            activeInfo = cameraInfo,
            activeVideoBindable = includeVideo && newVideoCapture != null,
            activeVideoBindingAttempted = videoBindingAttempted,
            activeSelectionId = cameraSelector.physicalCameraId
        )
        val activeEquivalentRatio = activeLensEquivalentZoomRatio()
        _cameraCapability.value = capabilityFromZoomState(cameraInfo, hasFront, hasBack)
        _currentZoomRatio.value = pendingDisplayZoom ?: CameraZoomMapping.displayRatioFromSession(
            cameraInfo.zoomState.value?.zoomRatio ?: 1.0f,
            activeEquivalentRatio
        )
        val zoomObserver = Observer<androidx.camera.core.ZoomState> { zoomState ->
            _currentZoomRatio.value = pendingDisplayZoom ?: CameraZoomMapping.displayRatioFromSession(
                zoomState.zoomRatio,
                activeEquivalentRatio
            )
            _cameraCapability.value = capabilityFromZoomState(
                cameraInfo = cameraInfo,
                hasFront = hasFront,
                hasBack = hasBack,
                zoomState = zoomState
            )
        }
        activeZoomObserver = zoomObserver
        activeZoomInfo = cameraInfo
        cameraInfo.zoomState.observe(lifecycleOwner, zoomObserver)
    }

    private fun hasCamera(provider: ProcessCameraProvider, lensFacing: Int): Boolean =
        hasCamera(provider, CameraSelector.Builder().requireLensFacing(lensFacing).build())

    private fun hasCamera(provider: ProcessCameraProvider, selector: CameraSelector): Boolean =
        runCatching { provider.hasCamera(selector) }.getOrDefault(false)

    private fun selectorForSelection(cameraId: String): CameraSelector =
        lensInspection.publicLenses.firstOrNull { it.cameraId == cameraId }
            ?.let(CameraCapabilityInspector::selectorForLens)
            ?: CameraCapabilityInspector.selectorForCameraId(cameraId)

    private fun watchPhysicalBinding(lifecycleOwner: LifecycleOwner, previewView: PreviewView,
        previousId: String?, previousFacing: Int, previousZoom: Float, requireVideo: Boolean,
        previousCameraInfo: CameraInfo?, previousCameraState: androidx.camera.core.CameraState?) {
        // Any public session can fail asynchronously, not only a physical
        // member bound through its logical parent: a device that exposes an
        // independent ultra-wide or tele camera needs the same recovery.
        val selected = lensInspection.publicLenses.firstOrNull { it.isActive } ?: return
        val bound = camera ?: return
        val previous = lensInspection.publicLenses.firstOrNull { it.cameraId == previousId }
        val fallbackId = previous?.parentLogicalCameraId ?: previousId ?: selected.parentLogicalCameraId
        val generation = cameraBindingGeneration
        var firstObservation = true
        val observer = Observer<androidx.camera.core.CameraState> { state ->
            // Ignore only the identical old state replayed by the same
            // logical camera. A new first ERROR may already belong to this
            // binding and must still trigger recovery.
            val staleReplay = firstObservation && bound.cameraInfo === previousCameraInfo &&
                state === previousCameraState
            firstObservation = false
            // Never fall back to a camera this session already recorded as
            // unusable: that bounds recovery to one attempt per camera instead
            // of letting two failing sessions swap forever.
            val canRecover = fallbackId != null && fallbackId != selected.cameraId &&
                fallbackId !in unavailableZoomLenses && fallbackId !in unavailableVideoZoomLenses
            if (!staleReplay && canRecover && state.error != null &&
                generation == cameraBindingGeneration &&
                camera === bound && _videoRecordingState.value == VideoRecordingState.IDLE) {
                if (requireVideo) unavailableVideoZoomLenses.add(selected.cameraId) else unavailableZoomLenses.add(selected.cameraId)
                // Camera2 session configuration can fail asynchronously after
                // bindToLifecycle returns; restore the last public session.
                // Rebuilding a session from inside the state dispatch that
                // reported the error re-enters CameraX, so defer it.
                val fallbackZoom = pendingDisplayZoom ?: previousZoom
                pendingDisplayZoom = fallbackZoom
                ContextCompat.getMainExecutor(context).execute {
                    if (generation == cameraBindingGeneration && camera === bound) {
                        bindCameraUseCases(lifecycleOwner, previewView, previousFacing, fallbackId, requireVideo)
                        applySessionZoom(fallbackZoom)
                        reportCameraError("当前镜头暂不可用，已恢复原镜头")
                    }
                }
            } else if (!staleReplay && state.error != null && generation == cameraBindingGeneration &&
                camera === bound && _videoRecordingState.value == VideoRecordingState.IDLE) {
                // Nothing known-good to fall back to. Surface the failure
                // instead of leaving a frozen preview behind an apparently
                // usable shutter.
                reportCameraError("相机会话异常，请重新进入相机")
            }
        }
        physicalStateObserver = observer
        physicalStateInfo = bound.cameraInfo
        bound.cameraInfo.cameraState.observe(lifecycleOwner, observer)
    }

    private fun capabilityFromZoomState(
        cameraInfo: CameraInfo,
        hasFront: Boolean,
        hasBack: Boolean,
        zoomState: androidx.camera.core.ZoomState? = cameraInfo.zoomState.value
    ): CameraCapability {
        val base = CameraCapability.fromZoomState(
            zoomState = zoomState,
            hasFlash = cameraInfo.hasFlashUnit(),
            hasFront = hasFront,
            hasBack = hasBack,
            activeLensEquivalentZoomRatio = activeLensEquivalentZoomRatio()
        )
        val active = lensInspection.publicLenses.firstOrNull { it.isActive }
        val currentRange = active?.takeIf { it.parentLogicalCameraId != null }?.let(NativeZoomPolicy::displayRange)
        val lenses = if (active == null) emptyList() else NativeZoomPolicy.usableLenses(
            zoomLensCandidates(zoomRequiresVideo), active)
        val available = if (_videoRecordingState.value != VideoRecordingState.IDLE) listOfNotNull(active) else lenses
        val ranges = available.map(NativeZoomPolicy::displayRange)
        val presets = NativeZoomPolicy.presets(available)
        // The display range must stay a valid interval: device metadata can put
        // the floor above the capped ceiling, and Float.coerceIn(min, max)
        // throws when min > max.
        val displayFloor = minOf(
            currentRange?.start ?: base.minZoomRatio,
            ranges.minOfOrNull { it.start } ?: base.minZoomRatio
        ).coerceIn(0.01f, 30f)
        val displayCeiling = maxOf(
            base.maxZoomRatio,
            ranges.maxOfOrNull { it.endInclusive } ?: base.maxZoomRatio
        ).coerceIn(displayFloor, 30f)
        return base.copy(
            minZoomRatio = displayFloor,
            maxZoomRatio = displayCeiling,
            zoomPillPresets = presets.filter { it <= 30f }.ifEmpty { base.zoomPillPresets },
            publicLenses = lensInspection.publicLenses.map { lens ->
                when (lens.cameraId) {
                    in unavailableZoomLenses -> lens.copy(photoBindable = false, videoBindable = false,
                        photoBindingVerification = CameraBindingVerification.VERIFIED,
                        videoBindingVerification = CameraBindingVerification.VERIFIED)
                    in unavailableVideoZoomLenses -> lens.copy(videoBindable = false,
                        videoBindingVerification = CameraBindingVerification.VERIFIED)
                    else -> lens
                }
            },
            hardwareOnlyLenses = lensInspection.hardwareOnlyLenses,
            activeCameraId = lensInspection.activeCameraId,
            activeLensEquivalentZoomRatio = activeLensEquivalentZoomRatio()
        )
    }

    private fun updateRecordingState(state: VideoRecordingState) {
        _videoRecordingState.value = state
        refreshZoomCapability()
    }

    fun setZoomCaptureMode(video: Boolean) {
        zoomRequiresVideo = video
        applySnapshotControls(false)
        refreshZoomCapability()
    }

    private fun refreshZoomCapability() {
        camera?.cameraInfo?.let { info ->
            _cameraCapability.value = capabilityFromZoomState(info,
                _cameraCapability.value.isFrontCameraAvailable, _cameraCapability.value.isBackCameraAvailable)
        }
    }

    private fun zoomLensCandidates(requireVideo: Boolean): List<CameraLensCapability> =
        lensInspection.publicLenses.filter { it.cameraId !in unavailableZoomLenses &&
            (!requireVideo || (it.cameraId !in unavailableVideoZoomLenses &&
                (it.videoBindingVerification != CameraBindingVerification.VERIFIED || it.videoBindable))) }

    private fun activeLensEquivalentZoomRatio(): Float {
        val active = lensInspection.publicLenses.firstOrNull { it.cameraId == lensInspection.activeCameraId }
        return CameraZoomMapping.referenceRatioForActiveLens(active)
    }

    private fun oppositeLensFacing(lensFacing: Int): Int =
        if (lensFacing == CameraSelector.LENS_FACING_BACK) {
            CameraSelector.LENS_FACING_FRONT
        } else {
            CameraSelector.LENS_FACING_BACK
        }

    private fun lensName(lensFacing: Int): String =
        if (lensFacing == CameraSelector.LENS_FACING_FRONT) "前置" else "后置"

    private fun reportCameraError(message: String) {
        _cameraError.value = message
        Log.e("CameraManager", message)
    }

    /**
     * Clears an already delivered failure so a later identical failure is
     * surfaced again instead of being conflated away by StateFlow.
     */
    fun clearCameraError() {
        _cameraError.value = null
    }

    fun switchLens(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView
    ): Int {
        if (_videoRecordingState.value != VideoRecordingState.IDLE) return currentLensFacing
        val provider = cameraProvider ?: return currentLensFacing
        val requestedLens = oppositeLensFacing(currentLensFacing)
        if (!hasCamera(provider, requestedLens)) {
            reportCameraError("当前设备没有可用的${lensName(requestedLens)}摄像头")
            return currentLensFacing
        }
        val previousDisplayZoom = _currentZoomRatio.value
        bindCameraUseCases(lifecycleOwner, previewView, requestedLens, requireVideo = zoomRequiresVideo)
        // Both a successful front/back switch and a failed switch restored to
        // the previous selector must keep the display-scale zoom coherent.
        applySessionZoom(previousDisplayZoom)
        return currentLensFacing
    }

    /** Bind one CameraX-public camera group selected from real metadata. */
    fun switchToPublicLens(
        lens: CameraLensCapability,
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView
    ): Boolean {
        if (_videoRecordingState.value != VideoRecordingState.IDLE || !lens.appAccessible || !lens.photoBindable) {
            return false
        }
        val requireVideo = zoomRequiresVideo
        if (lens.cameraId in unavailableZoomLenses ||
            (requireVideo && lens.cameraId in unavailableVideoZoomLenses)) {
            reportCameraError("当前模式暂不可使用此镜头")
            return false
        }
        val provider = cameraProvider ?: return false
        val selector = CameraCapabilityInspector.selectorForLens(lens)
        if (!hasCamera(provider, selector)) {
            reportCameraError("当前镜头暂不可绑定")
            return false
        }
        if (currentCameraId == lens.cameraId) return true
        val previousDisplayZoom = _currentZoomRatio.value
        val switched = bindCameraUseCases(
            lifecycleOwner = lifecycleOwner,
            previewView = previewView,
            requestedLensFacing = lens.lensFacing,
            requestedCameraId = lens.cameraId,
            requireVideo = requireVideo
        )
        if (!switched) {
            if (requireVideo) unavailableVideoZoomLenses.add(lens.cameraId)
            else unavailableZoomLenses.add(lens.cameraId)
            refreshZoomCapability()
        }
        // Preserve the user's main-camera reference where the new session
        // allows it; native 1x on an independent tele naturally clamps to its
        // displayed equivalent lower bound. bindCameraUseCases restores the
        // previous selector after a failed switch, so the same call also
        // restores that selector's displayed ratio.
        applySessionZoom(previousDisplayZoom)
        return switched
    }

    /**
     * Display rotation changes are not a layout concern, but they must be
     * propagated to CameraX so the preview and newly started recordings are
     * upright.  CameraX exposes mutable targetRotation on all three use cases,
     * so this avoids unbinding a recording in progress.
     */
    fun updateTargetRotation(displayRotation: Int) {
        val normalized = safeCameraTargetRotation(displayRotation)
        if (targetRotation == normalized) return
        targetRotation = normalized
        previewTargetRotation = previewTargetRotationForCameraStream(normalized)
        preview?.targetRotation = previewTargetRotation
        imageCapture?.targetRotation = normalized
        videoCapture?.targetRotation = previewTargetRotation
    }

    /** The rotation last applied to all active CameraX use cases. */
    fun currentTargetRotation(): Int = targetRotation

    /** Keep the camera stream aligned with the current display on real devices. */
    private fun previewTargetRotationForCameraStream(displayRotation: Int): Int =
        safeCameraTargetRotation(displayRotation)

    fun setFlashMode(flashMode: String) {
        currentFlashMode = flashMode.uppercase()
        val isTorch = currentFlashMode == "TORCH"
        camera?.cameraControl?.enableTorch(isTorch)
        // TORCH must also clear the still-capture flash mode: a continuous
        // torch and an AE flash request are mutually exclusive on Camera2, and
        // some HALs reject the capture request outright.
        imageCapture?.flashMode = mapImageCaptureFlashMode(currentFlashMode)
    }

    fun setTorchEnabled(enabled: Boolean) {
        if (enabled) {
            currentFlashMode = "TORCH"
        } else if (currentFlashMode == "TORCH") {
            currentFlashMode = "OFF"
        }
        camera?.cameraControl?.enableTorch(enabled)
        imageCapture?.flashMode = mapImageCaptureFlashMode(currentFlashMode)
    }

    /** CameraX may clear hardware torch state while its lifecycle is stopped. */
    fun restoreFlashMode() {
        val torchEnabled = currentFlashMode == "TORCH"
        camera?.cameraControl?.enableTorch(torchEnabled)
        imageCapture?.flashMode = mapImageCaptureFlashMode(currentFlashMode)
    }

    fun setZoomRatio(ratio: Float, exactNativeStop: Boolean = false, requireVideo: Boolean = false) {
        // Never hand coerceIn an empty range: these bounds come from device
        // metadata and must be ordered and finite before use.
        val min = _cameraCapability.value.minZoomRatio
        val max = _cameraCapability.value.maxZoomRatio
        val floor = minOf(min, max).takeIf { it.isFinite() && it > 0f } ?: 0.01f
        val ceiling = maxOf(min, max).takeIf { it.isFinite() }?.coerceAtLeast(floor) ?: floor
        val displayRatio = ratio.takeIf { it.isFinite() && it > 0f } ?: floor
        val clampedDisplayRatio = displayRatio.coerceIn(floor, ceiling)
        val active = lensInspection.publicLenses.firstOrNull { it.isActive }
        val lifecycleOwner = lifecycleOwnerRef
        val previewView = previewViewRef
        if (_videoRecordingState.value == VideoRecordingState.IDLE && active != null &&
            lifecycleOwner != null && previewView != null) {
            val target = NativeZoomPolicy.targetLens(
                zoomLensCandidates(requireVideo),
                active, clampedDisplayRatio, exactNativeStop)
            // Prefer the logical session for native stops in its real range.
            // Its normalized preset requests the intended ratio without forcing
            // an unverified physical session on vendor HALs.
            val isMemberOfActiveLogical = active.isLogicalCamera && target.parentLogicalCameraId == active.cameraId
            val coveredByActiveLogical = active.isLogicalCamera && clampedDisplayRatio in NativeZoomPolicy.displayRange(active)
            if (target.cameraId != active.cameraId && !isMemberOfActiveLogical && !coveredByActiveLogical) {
                pendingDisplayZoom = clampedDisplayRatio
                if (!bindCameraUseCases(lifecycleOwner, previewView, target.lensFacing, target.cameraId, requireVideo)) {
                    if (requireVideo) unavailableVideoZoomLenses.add(target.cameraId)
                    else unavailableZoomLenses.add(target.cameraId)
                    refreshZoomCapability()
                }
            }
        }
        applySessionZoom(clampedDisplayRatio)
    }

    private fun applySessionZoom(displayRatio: Float) {
        val activeCamera = camera ?: run { pendingDisplayZoom = null; return }
        val zoomState = activeCamera.cameraInfo.zoomState.value ?: run { pendingDisplayZoom = null; return }
        val activeLens = lensInspection.publicLenses.firstOrNull { it.isActive }
        val currentRange = activeLens?.takeIf { it.parentLogicalCameraId != null }?.let(NativeZoomPolicy::displayRange)
        val rangeFloor = currentRange?.let { minOf(it.start, it.endInclusive) }
        val rangeCeiling = currentRange?.let { maxOf(it.start, it.endInclusive) }
        val sessionFloor = zoomState.minZoomRatio.takeIf { it.isFinite() && it > 0f } ?: 0.01f
        val sessionCeiling = zoomState.maxZoomRatio.takeIf { it.isFinite() }
            ?.coerceAtLeast(sessionFloor) ?: sessionFloor
        val sessionRatio = CameraZoomMapping.sessionRatioFromDisplay(
            if (rangeFloor != null && rangeCeiling != null) {
                displayRatio.coerceIn(rangeFloor, rangeCeiling)
            } else displayRatio,
            _cameraCapability.value.activeLensEquivalentZoomRatio
        ).coerceIn(sessionFloor, sessionCeiling)
        val display = CameraZoomMapping.displayRatioFromSession(sessionRatio, activeLensEquivalentZoomRatio())
        pendingDisplayZoom = display
        _currentZoomRatio.value = display
        val generation = ++zoomRequestGeneration
        val result = activeCamera.cameraControl.setZoomRatio(sessionRatio)
        result.addListener({
            if (generation == zoomRequestGeneration && camera === activeCamera) {
                pendingDisplayZoom = null
                _currentZoomRatio.value = CameraZoomMapping.displayRatioFromSession(
                    activeCamera.cameraInfo.zoomState.value?.zoomRatio ?: sessionRatio, activeLensEquivalentZoomRatio())
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun setLinearZoom(linearRatio: Float) {
        camera?.cameraControl?.setLinearZoom(linearRatio.coerceIn(0f, 1f))
    }

    fun currentPreviewAspectRatio(): Float? {
        val previewView = previewViewRef ?: return null
        val width = previewView.width
        val height = previewView.height
        return if (width > 0 && height > 0) width.toFloat() / height.toFloat() else null
    }

    private var focusRequestGeneration = 0L

    /** Coordinates are pixels in the current PreviewView, including its actual display transform. */
    fun focusOnPoint(x: Float, y: Float, onResult: (Boolean) -> Unit = {}): Boolean {
        val previewView = previewViewRef ?: return false
        val activeCamera = camera ?: return false
        if (previewView.width <= 0 || previewView.height <= 0 ||
            previewView.previewStreamState.value != PreviewView.StreamState.STREAMING ||
            !x.isFinite() || !y.isFinite() || x < 0 || y < 0 ||
            x > previewView.width || y > previewView.height
        ) return false
        val generation = ++focusRequestGeneration
        val point = previewView.meteringPointFactory.createPoint(x, y)
        val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE)
            .setAutoCancelDuration(3000L, java.util.concurrent.TimeUnit.MILLISECONDS)
            .build()
        try {
            if (!activeCamera.cameraInfo.isFocusMeteringSupported(action)) return false
            val result = activeCamera.cameraControl.startFocusAndMetering(action)
            result.addListener({
                if (generation == focusRequestGeneration && camera === activeCamera && previewViewRef === previewView) {
                    onResult(runCatching { result.get().isFocusSuccessful }.getOrDefault(false))
                }
            }, ContextCompat.getMainExecutor(context))
            return true
        } catch (_: Exception) { return false }
    }

    suspend fun capturePhoto(targetRotation: Int): Pair<Bitmap, Int> = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { continuation ->
            // Take the use case, set its rotation and start the capture on the
            // main thread — the same thread that binds and unbinds use cases —
            // so the instance cannot be detached between the check and the call.
            val capture = imageCapture ?: run {
                continuation.resumeWithException(IllegalStateException("ImageCapture is not initialized"))
                return@suspendCancellableCoroutine
            }

            capture.targetRotation = safeCameraTargetRotation(targetRotation)

            capture.takePicture(
                captureExecutor,
                object : ImageCapture.OnImageCapturedCallback() {
                    override fun onCaptureSuccess(image: ImageProxy) {
                        try {
                            val rotationDegrees = image.imageInfo.rotationDegrees
                            val bitmap = imageProxyToBitmap(image, Rect(image.cropRect))
                            if (continuation.isActive) continuation.resume(Pair(bitmap, rotationDegrees))
                            else bitmap.recycle()
                        } catch (error: Throwable) {
                            // An OutOfMemoryError on a full-resolution frame must
                            // still settle the awaiting coroutine, otherwise the
                            // caller stays suspended and the shutter never recovers.
                            if (continuation.isActive) continuation.resumeWithException(error)
                        } finally {
                            image.close()
                        }
                    }

                    override fun onError(exception: ImageCaptureException) {
                        if (continuation.isActive) continuation.resumeWithException(exception)
                    }
                }
            )
        }
    }

    @SuppressLint("MissingPermission")
    fun startVideoRecording(
        outputFile: File,
        withAudio: Boolean,
        onEvent: (Long, VideoRecordEvent) -> Unit
    ): Long {
        check(_videoRecordingState.value == VideoRecordingState.IDLE) {
            "录像仍在${_videoRecordingState.value}状态，不能开始新录像"
        }
        val vc = videoCapture ?: throw IllegalStateException("当前设备不支持录像")
        val token = ++nextRecordingToken
        activeRecordingToken = token
        updateRecordingState(VideoRecordingState.STARTING)
        _isRecording.value = false
        val outputOptions = FileOutputOptions.Builder(outputFile).build()
        try {
            val pendingRecording = vc.output.prepareRecording(context, outputOptions).apply {
                if (withAudio) withAudioEnabled()
            }
            val recording = pendingRecording.start(ContextCompat.getMainExecutor(context)) { event ->
                if (activeRecordingToken != token) return@start
                when (event) {
                    is VideoRecordEvent.Start -> {
                        if (_videoRecordingState.value == VideoRecordingState.FINALIZING) {
                            // Stop may be requested before CameraX delivers
                            // Start. Stop the just-created recording as soon
                            // as it becomes available and never expose a
                            // second logical recording to the UI.
                            activeRecording?.stop()
                        } else {
                            updateRecordingState(VideoRecordingState.RECORDING)
                            _isRecording.value = true
                        }
                    }
                    is VideoRecordEvent.Finalize -> {
                        updateRecordingState(VideoRecordingState.IDLE)
                        _isRecording.value = false
                        activeRecording = null
                        activeRecordingToken = null
                    }
                }
                onEvent(token, event)
                if (event is VideoRecordEvent.Finalize && unbindWhenIdle) {
                    unbindWhenIdle = false
                    unbindCamera()
                }
            }
            activeRecording = recording
            // A stop can arrive in the short STARTING window before CameraX
            // returns the Recording object. Honour that request now rather
            // than leaving the state machine stuck in FINALIZING.
            if (_videoRecordingState.value == VideoRecordingState.FINALIZING) {
                recording.stop()
            }
            return token
        } catch (error: Exception) {
            if (activeRecordingToken == token) {
                activeRecording = null
                activeRecordingToken = null
                updateRecordingState(VideoRecordingState.IDLE)
                _isRecording.value = false
            }
            outputFile.delete()
            throw error
        }
    }

    fun stopVideoRecording(): Boolean {
        if (_videoRecordingState.value != VideoRecordingState.RECORDING &&
            _videoRecordingState.value != VideoRecordingState.STARTING
        ) return false
        updateRecordingState(VideoRecordingState.FINALIZING)
        activeRecording?.stop()
        // Keep the token and recording until CameraX emits Finalize. This
        // prevents a second start from racing the old callback.
        return true
    }

    fun pauseVideoRecording() {
        activeRecording?.pause()
    }

    fun resumeVideoRecording() {
        activeRecording?.resume()
    }

    private fun imageProxyToBitmap(image: ImageProxy, cropRect: Rect): Bitmap {
        val buffer: ByteBuffer = image.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ?: throw IllegalStateException("Failed to decode ImageProxy byte stream to Bitmap")

        if (cropRect.left <= 0 && cropRect.top <= 0 &&
            cropRect.right >= image.width && cropRect.bottom >= image.height
        ) {
            return bitmap
        }

        val scaleX = bitmap.width.toFloat() / image.width.coerceAtLeast(1)
        val scaleY = bitmap.height.toFloat() / image.height.coerceAtLeast(1)
        val left = floor(cropRect.left * scaleX).toInt().coerceIn(0, bitmap.width - 1)
        val top = floor(cropRect.top * scaleY).toInt().coerceIn(0, bitmap.height - 1)
        val right = ceil(cropRect.right * scaleX).toInt().coerceIn(left + 1, bitmap.width)
        val bottom = ceil(cropRect.bottom * scaleY).toInt().coerceIn(top + 1, bitmap.height)
        val cropped = Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
        if (cropped != bitmap) bitmap.recycle()
        return cropped
    }

    private fun mapImageCaptureFlashMode(mode: String): Int {
        return when (mode.uppercase()) {
            "ON" -> ImageCapture.FLASH_MODE_ON
            "OFF" -> ImageCapture.FLASH_MODE_OFF
            "TORCH" -> ImageCapture.FLASH_MODE_OFF // Torch handles continuous illumination
            else -> ImageCapture.FLASH_MODE_AUTO
        }
    }

    fun release() {
        unbindCamera()
    }

    /** Stops camera resources when the screen leaves, even if its ViewModel is
     * retained on the navigation back stack. A recording is finalized first. */
    fun unbindCamera() {
        if (_videoRecordingState.value != VideoRecordingState.IDLE) {
            unbindWhenIdle = true
            stopVideoRecording()
            return
        }
        unbindWhenIdle = false
        activeZoomObserver?.let { observer -> activeZoomInfo?.zoomState?.removeObserver(observer) }
        activeZoomObserver = null
        activeZoomInfo = null
        physicalStateObserver?.let { physicalStateInfo?.cameraState?.removeObserver(it) }
        physicalStateObserver = null
        physicalStateInfo = null
        camera?.cameraControl?.enableTorch(false)
        cameraProvider?.unbindAll()
        camera = null
        preview = null
        imageCapture = null
        videoCapture = null
        currentCameraId = null
        CameraDiagnostics.clearSession()
        lensInspection = CameraCapabilityInspection()
        unavailableZoomLenses.clear()
        unavailableVideoZoomLenses.clear()
        cameraBindingGeneration += 1L
        pendingDisplayZoom = null
        zoomRequestGeneration++
        lifecycleOwnerRef = null
        // This manager lives in the application container, so keeping the
        // PreviewView would pin the whole Activity view tree for the process.
        previewViewRef = null
        _isCameraReady.value = false
    }
}
