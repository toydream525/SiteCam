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
        lensFacing: Int = CameraSelector.LENS_FACING_BACK,
        flashMode: String = "AUTO",
        displayRotation: Int = Surface.ROTATION_0
    ) = withContext(Dispatchers.Main) {
        try {
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
            bindCameraUseCases(lifecycleOwner, previewView, requestedLens)
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
        requestedLensFacing: Int = currentLensFacing
    ): Boolean {
        val provider = cameraProvider ?: return false
        val previousLensFacing = currentLensFacing
        if (!hasCamera(provider, requestedLensFacing)) {
            reportCameraError("当前设备没有可用的${lensName(requestedLensFacing)}摄像头")
            return false
        }

        try {
            bindUseCases(
                provider = provider,
                lifecycleOwner = lifecycleOwner,
                previewView = previewView,
                lensFacing = requestedLensFacing,
                includeVideo = true
            )
            currentLensFacing = requestedLensFacing
            _isCameraReady.value = true
            _cameraError.value = null
            return true
        } catch (videoBindingError: Exception) {
            Log.w("CameraManager", "Preview/photo/video binding failed; retrying without video", videoBindingError)
            try {
                // Some vendor Camera2 implementations cannot bind all three
                // use cases at once. Keep preview and still capture usable and
                // expose a clear warning instead of leaving a black preview.
                bindUseCases(
                    provider = provider,
                    lifecycleOwner = lifecycleOwner,
                    previewView = previewView,
                    lensFacing = requestedLensFacing,
                    includeVideo = false
                )
                currentLensFacing = requestedLensFacing
                _isCameraReady.value = true
                _cameraError.value = "当前设备不支持同时录像，已启用拍照模式"
                return true
            } catch (bindingError: Exception) {
                Log.e("CameraManager", "Camera use case binding failed", bindingError)
                // A lens switch is transactional: restore the lens that was
                // already working before reporting failure to the UI.
                if (requestedLensFacing != previousLensFacing && hasCamera(provider, previousLensFacing)) {
                    runCatching {
                        bindUseCases(
                            provider = provider,
                            lifecycleOwner = lifecycleOwner,
                            previewView = previewView,
                            lensFacing = previousLensFacing,
                            includeVideo = true
                        )
                        currentLensFacing = previousLensFacing
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
        }
    }

    private fun bindUseCases(
        provider: ProcessCameraProvider,
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        lensFacing: Int,
        includeVideo: Boolean
    ) {
        val cameraSelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
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
        _cameraCapability.value = CameraCapability.fromCameraInfo(cameraInfo, hasFront, hasBack)
        val zoomObserver = Observer<androidx.camera.core.ZoomState> { zoomState ->
            _currentZoomRatio.value = zoomState.zoomRatio
            _cameraCapability.value = CameraCapability.fromZoomState(
                zoomState = zoomState,
                hasFlash = cameraInfo.hasFlashUnit(),
                hasFront = hasFront,
                hasBack = hasBack
            )
        }
        activeZoomObserver = zoomObserver
        activeZoomInfo = cameraInfo
        cameraInfo.zoomState.observe(lifecycleOwner, zoomObserver)
    }

    private fun hasCamera(provider: ProcessCameraProvider, lensFacing: Int): Boolean =
        runCatching {
            provider.hasCamera(
                CameraSelector.Builder().requireLensFacing(lensFacing).build()
            )
        }.getOrDefault(false)

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
        bindCameraUseCases(lifecycleOwner, previewView, requestedLens)
        return currentLensFacing
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
        val clampedRatio = ratio.coerceIn(min, max)
        camera?.cameraControl?.setZoomRatio(clampedRatio)
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
        _isCameraReady.value = false
    }
}
