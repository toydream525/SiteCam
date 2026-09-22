package com.sitecam.app.core.camera

import android.annotation.SuppressLint
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
import androidx.camera.core.MeteringPointFactory
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
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
    private var camera: Camera? = null
    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var activeRecording: Recording? = null
    private var activeRecordingToken: Long? = null
    private var nextRecordingToken = 0L
    private var unbindWhenIdle = false
    private var preview: Preview? = null
    private var previewViewRef: PreviewView? = null

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
        requestedCameraId: String? = null
    ): Boolean {
        val provider = cameraProvider ?: return false
        val previousLensFacing = currentLensFacing
        val previousCameraId = currentCameraId
        val defaultSelector = CameraSelector.Builder().requireLensFacing(requestedLensFacing).build()
        // An explicit camera ID is a user selection and therefore remains the
        // only candidate.  For the ordinary front/back path, inspect the
        // public CameraX inventory first so a suitable logical group gets the
        // first real Preview + ImageCapture + Video bind attempt.  The facing
        // selector remains the compatibility fallback for devices whose
        // metadata is incomplete or whose preferred group cannot bind.
        val candidates = buildList<Pair<String?, CameraSelector>> {
            if (requestedCameraId != null) {
                add(requestedCameraId to CameraCapabilityInspector.selectorForCameraId(requestedCameraId))
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
                currentCameraId = CameraCapabilityInspector.cameraIdOf(camera?.cameraInfo) ?: candidateCameraId
                _isCameraReady.value = true
                _cameraError.value = null
                return true
            } catch (videoBindingError: Exception) {
                lastBindingError = videoBindingError
                Log.w("CameraManager", "Preview/photo/video binding failed for candidate", videoBindingError)
            }
        }

        // Some vendor Camera2 implementations cannot bind all three use
        // cases at once. Keep preview and still capture usable only after all
        // complete candidates have had a chance to bind.
        for ((candidateCameraId, selector) in availableCandidates) {
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
                currentCameraId = CameraCapabilityInspector.cameraIdOf(camera?.cameraInfo) ?: candidateCameraId
                _isCameraReady.value = true
                _cameraError.value = "当前设备不支持同时录像，已启用拍照模式"
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
        val previousSelector = previousCameraId?.let(CameraCapabilityInspector::selectorForCameraId)
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

    private fun bindUseCases(
        provider: ProcessCameraProvider,
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        cameraSelector: CameraSelector,
        includeVideo: Boolean,
        videoBindingAttempted: Boolean = includeVideo
    ) {
        val newPreview = Preview.Builder()
            .setTargetAspectRatio(AspectRatio.RATIO_4_3)
            .setTargetRotation(previewTargetRotation)
            .build()
            .also { it.surfaceProvider = previewView.surfaceProvider }
        val newImageCapture = ImageCapture.Builder()
            .setTargetAspectRatio(AspectRatio.RATIO_4_3)
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .setFlashMode(mapImageCaptureFlashMode(currentFlashMode))
            .setTargetRotation(targetRotation)
            .build()
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
        boundCamera.cameraControl.enableTorch(currentFlashMode.uppercase() == "TORCH")

        val cameraInfo = boundCamera.cameraInfo
        val hasFront = hasCamera(provider, CameraSelector.LENS_FACING_FRONT)
        val hasBack = hasCamera(provider, CameraSelector.LENS_FACING_BACK)
        lensInspection = CameraCapabilityInspector.inspect(
            context = context,
            provider = provider,
            activeInfo = cameraInfo,
            activeVideoBindable = includeVideo && newVideoCapture != null,
            activeVideoBindingAttempted = videoBindingAttempted
        )
        val activeEquivalentRatio = activeLensEquivalentZoomRatio()
        _cameraCapability.value = capabilityFromZoomState(cameraInfo, hasFront, hasBack)
        _currentZoomRatio.value = CameraZoomMapping.displayRatioFromSession(
            cameraInfo.zoomState.value?.zoomRatio ?: 1.0f,
            activeEquivalentRatio
        )
        val zoomObserver = Observer<androidx.camera.core.ZoomState> { zoomState ->
            _currentZoomRatio.value = CameraZoomMapping.displayRatioFromSession(
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

    private fun capabilityFromZoomState(
        cameraInfo: CameraInfo,
        hasFront: Boolean,
        hasBack: Boolean,
        zoomState: androidx.camera.core.ZoomState? = cameraInfo.zoomState.value
    ): CameraCapability = CameraCapability.fromZoomState(
        zoomState = zoomState,
        hasFlash = cameraInfo.hasFlashUnit(),
        hasFront = hasFront,
        hasBack = hasBack,
        activeLensEquivalentZoomRatio = activeLensEquivalentZoomRatio()
    ).copy(
        publicLenses = lensInspection.publicLenses,
        hardwareOnlyLenses = lensInspection.hardwareOnlyLenses,
        activeCameraId = lensInspection.activeCameraId,
        activeLensEquivalentZoomRatio = activeLensEquivalentZoomRatio()
    )

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
        bindCameraUseCases(lifecycleOwner, previewView, requestedLens)
        // Both a successful front/back switch and a failed switch restored to
        // the previous selector must keep the display-scale zoom coherent.
        setZoomRatio(previousDisplayZoom)
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
        val provider = cameraProvider ?: return false
        val selector = CameraCapabilityInspector.selectorForCameraId(lens.cameraId)
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
            requestedCameraId = lens.cameraId
        )
        // Preserve the user's main-camera reference where the new session
        // allows it; native 1x on an independent tele naturally clamps to its
        // displayed equivalent lower bound. bindCameraUseCases restores the
        // previous selector after a failed switch, so the same call also
        // restores that selector's displayed ratio.
        setZoomRatio(previousDisplayZoom)
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

        if (!isTorch) {
            val mapped = mapImageCaptureFlashMode(currentFlashMode)
            imageCapture?.flashMode = mapped
        }
    }

    fun setTorchEnabled(enabled: Boolean) {
        if (enabled) {
            currentFlashMode = "TORCH"
        } else if (currentFlashMode == "TORCH") {
            currentFlashMode = "OFF"
        }
        camera?.cameraControl?.enableTorch(enabled)
    }

    /** CameraX may clear hardware torch state while its lifecycle is stopped. */
    fun restoreFlashMode() {
        val torchEnabled = currentFlashMode == "TORCH"
        camera?.cameraControl?.enableTorch(torchEnabled)
        if (!torchEnabled) {
            imageCapture?.flashMode = mapImageCaptureFlashMode(currentFlashMode)
        }
    }

    fun setZoomRatio(ratio: Float) {
        val min = _cameraCapability.value.minZoomRatio
        val max = _cameraCapability.value.maxZoomRatio
        val displayRatio = ratio.takeIf { it.isFinite() && it > 0f } ?: min
        val clampedDisplayRatio = displayRatio.coerceIn(min, max)
        val sessionRatio = CameraZoomMapping.sessionRatioFromDisplay(
            clampedDisplayRatio,
            _cameraCapability.value.activeLensEquivalentZoomRatio
        )
        camera?.cameraControl?.setZoomRatio(sessionRatio)
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

    fun focusOnPoint(x: Float, y: Float, width: Float, height: Float) {
        if (width <= 0 || height <= 0) return
        val factory: MeteringPointFactory = SurfaceOrientedMeteringPointFactory(width, height)
        val point = factory.createPoint(x, y)
        val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE)
            .setAutoCancelDuration(3000L, java.util.concurrent.TimeUnit.MILLISECONDS)
            .build()
        try {
            camera?.cameraControl?.startFocusAndMetering(action)
        } catch (_: Exception) {}
    }

    suspend fun capturePhoto(targetRotation: Int): Pair<Bitmap, Int> = suspendCancellableCoroutine { continuation ->
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
                    } catch (error: Exception) {
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
        _videoRecordingState.value = VideoRecordingState.STARTING
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
                            _videoRecordingState.value = VideoRecordingState.RECORDING
                            _isRecording.value = true
                        }
                    }
                    is VideoRecordEvent.Finalize -> {
                        _videoRecordingState.value = VideoRecordingState.IDLE
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
                _videoRecordingState.value = VideoRecordingState.IDLE
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
        _videoRecordingState.value = VideoRecordingState.FINALIZING
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
        camera?.cameraControl?.enableTorch(false)
        cameraProvider?.unbindAll()
        camera = null
        preview = null
        imageCapture = null
        videoCapture = null
        currentCameraId = null
        lensInspection = CameraCapabilityInspection()
        _isCameraReady.value = false
    }
}
