@file:android.annotation.SuppressLint("UnsafeOptInUsageError")

package com.sitecam.app.feature.camera

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.camera.video.VideoRecordEvent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.sitecam.app.core.camera.CameraCapability
import com.sitecam.app.core.camera.CameraManager
import com.sitecam.app.core.camera.OrientationManager
import com.sitecam.app.core.camera.mayRetainRecordedFrames
import com.sitecam.app.core.database.entity.MediaItemEntity
import com.sitecam.app.core.database.entity.ProjectEntity
import com.sitecam.app.core.database.entity.WatermarkFieldEntity
import com.sitecam.app.core.database.entity.WatermarkTemplateEntity
import com.sitecam.app.core.database.AppDatabase
import com.sitecam.app.core.di.AppContainer
import com.sitecam.app.core.location.LocationFreshness
import com.sitecam.app.core.location.SiteLocation
import com.sitecam.app.core.media.NamingEngine
import com.sitecam.app.core.media.PendingVideoStore
import com.sitecam.app.core.media.MediaOperationCoordinator
import com.sitecam.app.core.watermark.model.WatermarkData
import com.sitecam.app.core.watermark.model.WatermarkSnapshotCodec
import com.sitecam.app.core.watermark.model.resolveWatermarkFields
import com.sitecam.app.core.watermark.renderer.WatermarkBitmapRenderer
import com.sitecam.app.core.camera.SnapshotPolicy
import com.sitecam.app.core.camera.captureAndSaveFrames
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import java.io.File

enum class CaptureMode {
    PHOTO, VIDEO
}

sealed interface CameraUiEvent {
    /** Emitted only after a PHOTO row and its media have been saved. */
    data class PhotoSaved(val mediaId: Long, val uri: Uri, val showToast: Boolean = true) : CameraUiEvent
    data class VideoSaved(val mediaId: Long, val uri: Uri) : CameraUiEvent
    data class QuickIssuePrompt(val mediaId: Long, val uri: Uri) : CameraUiEvent
    data class ShowToast(val message: String) : CameraUiEvent
    data class ProjectSwitched(val projectId: Long, val projectName: String) : CameraUiEvent
}

data class CameraUiState(
    val currentProject: ProjectEntity? = null,
    val activeTemplate: WatermarkTemplateEntity? = null,
    val watermarkFields: List<WatermarkFieldEntity> = emptyList(),
    val watermarkData: WatermarkData = WatermarkData(),
    val currentLocation: SiteLocation? = null,
    val currentAddress: String = "",
    val addressRefreshState: String = "IDLE", // IDLE, REFRESHING, FAILED_*
    val cameraCapability: CameraCapability = CameraCapability(),
    val currentZoomRatio: Float = 1.0f,
    val flashMode: String = "AUTO",
    val cameraControlStyle: String = "NORMAL",
    val captureOrientation: com.sitecam.app.core.camera.CaptureOrientation = com.sitecam.app.core.camera.CaptureOrientation.AUTO,
    val isOrientationLocked: Boolean = false,
    val orientationDegrees: Int = 0,
    val isQuickIssueMode: Boolean = false,
    val isCapturing: Boolean = false,
    val burstEnabled: Boolean = false,
    val isBurstCapturing: Boolean = false,
    val latestThumbnailUri: String? = null,
    val latestMediaType: String? = null,
    val captureMode: CaptureMode = CaptureMode.PHOTO,
    val isRecordingVideo: Boolean = false,
    val recordingDurationSeconds: Int = 0,
    val shutterSoundEnabled: Boolean = true
)

private data class LocatedAddress(
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long,
    val text: String,
    val resolvedAt: Long
) {
    fun matches(location: SiteLocation?, nowMs: Long = System.currentTimeMillis()): Boolean {
        val anchor = SiteLocation(latitude, longitude, timestamp = timestamp)
        return nearbyLocation(anchor, location ?: return false, nowMs)
    }
}

class CameraViewModel(
    private val appContainer: AppContainer
) : ViewModel() {

    val cameraManager: CameraManager = appContainer.cameraManager
    val orientationManager: OrientationManager = appContainer.orientationManager

    private val _uiEvents = MutableSharedFlow<CameraUiEvent>()
    val uiEvents: SharedFlow<CameraUiEvent> = _uiEvents.asSharedFlow()

    private val _currentProject = MutableStateFlow<ProjectEntity?>(null)
    private val _projectPickerProjects = MutableStateFlow<List<ProjectEntity>>(emptyList())
    val projectPickerProjects: StateFlow<List<ProjectEntity>> = _projectPickerProjects.asStateFlow()
    private val _isProjectSwitching = MutableStateFlow(false)
    val isProjectSwitching: StateFlow<Boolean> = _isProjectSwitching.asStateFlow()
    private val _activeTemplate = MutableStateFlow<WatermarkTemplateEntity?>(null)
    private val _watermarkFields = MutableStateFlow<List<WatermarkFieldEntity>>(emptyList())
    private val _automaticAddress = MutableStateFlow<LocatedAddress?>(null)
    private val _addressRefreshState = MutableStateFlow("IDLE")
    private var locationAddressGeneration = 0L
    private var lastAutomaticAddressAttempt: SiteLocation? = null
    private var lastAutomaticAddressAttemptAt: Long = 0L
    private var manualAddressGeneration = 0L
    /** Keeps automatic geocoding from finishing a request during a manual retry. */
    private var manualAddressActive = false
    private val _isCapturing = MutableStateFlow(false)
    private val _burstEnabled = MutableStateFlow(false)
    private val _isBurstCapturing = MutableStateFlow(false)
    private var photoSessionJob: Job? = null
    private val _latestThumbnailUri = MutableStateFlow<String?>(null)
    private val _latestMediaType = MutableStateFlow<String?>(null)
    private val _captureMode = MutableStateFlow(CaptureMode.PHOTO)
    private val _isRecordingVideo = MutableStateFlow(false)
    private val _recordingDurationSeconds = MutableStateFlow(0)
    // Keep the interaction-critical toggle local.  DataStore remains the
    // persistence layer, but a capture must not wait for its Flow to round
    // trip before the new mode takes effect.
    private val _quickIssueMode = MutableStateFlow(false)
    private var quickIssueModeOverrideVersion = 0L
    // Same reason as the quick-issue mode: a rapid double tap must not compute
    // the same next step from a value DataStore has not written back yet.
    private val _flashModeOverride = MutableStateFlow<String?>(null)
    private var flashModeOverrideVersion = 0L
    private var recordingTimerJob: Job? = null
    private data class VideoCaptureSession(
        val token: Long,
        val timestamp: Long,
        val project: ProjectEntity,
        val location: CaptureLocation,
        val watermark: WatermarkData,
        val quickIssueMode: Boolean,
        val saveToSystemGallery: Boolean,
        val tempFile: File,
        val pendingVideo: PendingVideoStore.Record
    )

    // Written from the capture coroutine, read from CameraX callback threads.
    @Volatile
    private var activeVideoSession: VideoCaptureSession? = null
    private val pendingVideoStore by lazy { PendingVideoStore(appContainer.appContext, appContainer.database.mediaItemDao()) }
    private var recoveryJob: Job? = null
    private val _weather = MutableStateFlow<com.sitecam.app.core.weather.SystemWeatherReading?>(null)
    private var weatherJob: Job? = null
    private val systemWeatherReader = com.sitecam.app.core.weather.SystemWeatherReader(appContainer.appContext)
    private val _clockTick = MutableStateFlow(System.currentTimeMillis())

    private data class CaptureLocation(
        val latitude: Double?,
        val longitude: Double?,
        val altitude: Double?,
        val accuracy: Float?,
        val timestamp: Long?,
        val address: String,
        val status: String
    )

    private fun freshLocation(state: CameraUiState, now: Long = System.currentTimeMillis()): CaptureLocation {
        val location = state.currentLocation
        val fresh = LocationFreshness.isFresh(location, now)
        val address = resolveWatermarkAddress(
            manualAddress = state.currentProject?.address,
            automaticAddress = state.currentAddress,
            locationIsFresh = fresh
        )
        return if (fresh && location != null) {
            CaptureLocation(
                latitude = location.latitude,
                longitude = location.longitude,
                altitude = location.altitude,
                accuracy = location.accuracy,
                timestamp = location.timestamp,
                address = address,
                status = "FRESH"
            )
        } else {
            CaptureLocation(null, null, null, null, null, address, "UNAVAILABLE")
        }
    }

    @Suppress("UNCHECKED_CAST")
    val uiState: StateFlow<CameraUiState> = combine(
        listOf(
            _currentProject,
            _activeTemplate,
            _watermarkFields,
            appContainer.locationTracker.currentLocation,
            _automaticAddress,
            _addressRefreshState,
            cameraManager.cameraCapability,
            cameraManager.currentZoomRatio,
            combine(appContainer.settingsDataStore.flashMode, _flashModeOverride) { persisted, local -> local ?: persisted },
            appContainer.settingsDataStore.captureOrientation,
            orientationManager.orientationDegrees,
            _quickIssueMode,
            _isCapturing,
            _latestThumbnailUri,
            _latestMediaType,
            _captureMode,
            _isRecordingVideo,
            _recordingDurationSeconds,
            appContainer.settingsDataStore.shutterSoundEnabled,
            _clockTick,
            appContainer.settingsDataStore.cameraControlStyle,
            _burstEnabled,
            _isBurstCapturing,
            _weather
        ) as List<Flow<Any?>>
    ) { array ->
        val project = array[0] as? ProjectEntity
        val template = array[1] as? WatermarkTemplateEntity
        val fields = (array[2] as? List<WatermarkFieldEntity>) ?: emptyList()
        val location = array[3] as? SiteLocation
        val locatedAddress = array[4] as? LocatedAddress
        val addressRefreshState = (array[5] as? String) ?: "IDLE"
        val capability = (array[6] as? CameraCapability) ?: CameraCapability()
        val zoomRatio = (array[7] as? Float) ?: 1.0f

        val flash = (array[8] as? String) ?: "AUTO"
        val orientationMode = (array[9] as? com.sitecam.app.core.camera.CaptureOrientation) ?: com.sitecam.app.core.camera.CaptureOrientation.AUTO
        val orientationLocked = orientationMode != com.sitecam.app.core.camera.CaptureOrientation.AUTO
        val orientationDeg = (array[10] as? Int) ?: 0
        val quickIssue = (array[11] as? Boolean) ?: false
        val capturing = (array[12] as? Boolean) ?: false
        val latestThumb = array[13] as? String
        val latestMediaType = array[14] as? String
        val mode = (array[15] as? CaptureMode) ?: CaptureMode.PHOTO

        val isRecording = (array[16] as? Boolean) ?: false
        val duration = (array[17] as? Int) ?: 0
        val shutterSoundEnabled = (array[18] as? Boolean) ?: true
        val clockTick = (array[19] as? Long) ?: System.currentTimeMillis()

        val resolvedFields = resolveWatermarkFields(fields)
        val locationStatus = if (location != null && LocationFreshness.isFresh(location, clockTick)) "FRESH" else "UNAVAILABLE"
        val locationIsFresh = locationStatus == "FRESH"
        val automaticAddress = locatedAddress
            ?.takeIf { it.matches(location) && locationIsFresh }
            ?.text
            .orEmpty()
        val watermarkAddress = resolveWatermarkAddress(
            manualAddress = project?.address,
            automaticAddress = automaticAddress,
            locationIsFresh = locationIsFresh
        )

        val watermarkData = WatermarkData(
            projectName = project?.name ?: "请选择工程包",
            categoryName = project?.categoryName ?: "",
            captureTimestamp = clockTick,
            latitude = location?.latitude?.takeIf { locationIsFresh },
            longitude = location?.longitude?.takeIf { locationIsFresh },
            altitude = location?.altitude?.takeIf { locationIsFresh },
            locationStatus = locationStatus,
            addressText = watermarkAddress,
            userName = resolvedFields.userName,
            weatherText = (array[23] as? com.sitecam.app.core.weather.SystemWeatherReading)?.takeIf { it.matches(location, clockTick) }?.text.orEmpty(),
            enabledSystemFields = resolvedFields.enabledSystemFields,
            systemValueOverrides = resolvedFields.systemValueOverrides,
            customFields = resolvedFields.customFields,
            fieldLabels = resolvedFields.fieldLabels,
            fieldOrder = resolvedFields.fieldOrder,
            styleType = template?.styleType ?: "CLASSIC",
            fontSizeScale = template?.fontSizeScale ?: 1.0f,
            widthRatio = template?.widthRatio ?: 0.6f,
            opacity = template?.opacity ?: 0.85f,
            marginDp = template?.marginDp ?: 16,
            position = template?.position ?: "BOTTOM_LEFT"
        )

        CameraUiState(
            currentProject = project,
            activeTemplate = template,
            watermarkFields = fields,
            watermarkData = watermarkData,
            currentLocation = location,
            currentAddress = automaticAddress,
            addressRefreshState = addressRefreshState,
            cameraCapability = capability,
            currentZoomRatio = zoomRatio,
            flashMode = flash,
            captureOrientation = orientationMode,
            isOrientationLocked = orientationLocked,
            orientationDegrees = orientationDeg,
            isQuickIssueMode = quickIssue,
            isCapturing = capturing,
            latestThumbnailUri = latestThumb,
            latestMediaType = latestMediaType,
            captureMode = mode,
            isRecordingVideo = isRecording,
            recordingDurationSeconds = duration,
            shutterSoundEnabled = shutterSoundEnabled,
            cameraControlStyle = (array[20] as? String) ?: "NORMAL",
            burstEnabled = array[21] as? Boolean ?: false,
            isBurstCapturing = array[22] as? Boolean ?: false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = CameraUiState()
    )

    init {
        cameraManager.setZoomCaptureMode(_captureMode.value == CaptureMode.VIDEO)
        viewModelScope.launch {
            combine(appContainer.settingsDataStore.snapshotEnabled,
                appContainer.settingsDataStore.snapshotShutterSpeed, _burstEnabled, _captureMode) { enabled, speed, burst, mode ->
                Triple(SnapshotPolicy.enabled(enabled, burst) && mode == CaptureMode.PHOTO, speed, mode)
            }.collect { (enabled, speed, _) ->
                if (!_isCapturing.value) cameraManager.configureSnapshot(enabled, speed)
            }
        }
        loadInitialData()
        recoverPendingVideos()
        viewModelScope.launch {
            // A failed watermark write is otherwise only visible in logcat.
            appContainer.watermarkTemplateMutations.saveError.collect { error ->
                if (error != null) {
                    _uiEvents.emit(CameraUiEvent.ShowToast("水印设置保存失败，请重试"))
                }
            }
        }
        viewModelScope.launch {
            appContainer.settingsDataStore.captureOrientation.collect { mode ->
                orientationManager.restoreLockedState(mode.degrees != null, mode.degrees ?: 0)
            }
        }
        viewModelScope.launch {
            while (isActive) {
                _clockTick.value = System.currentTimeMillis()
                delay(1000L)
            }
        }
        viewModelScope.launch {
            val persisted = appContainer.settingsDataStore.quickIssueMode.first()
            if (quickIssueModeOverrideVersion == 0L) {
                _quickIssueMode.value = persisted
            }
        }
        viewModelScope.launch {
            val persisted = appContainer.settingsDataStore.flashMode.first()
            if (flashModeOverrideVersion == 0L) {
                _flashModeOverride.value = persisted
            }
        }
        observeLocationAndAddress()
        observeLatestMedia()
    }

    private fun loadInitialData() {
        viewModelScope.launch {
            AppDatabase.ensureDefaultData(appContainer.database)
            // Keep the camera selection live after returning from the
            // management page.  The old nested collect never returned after
            // the first project list emission, so a later DataStore selection
            // could leave the camera showing the previous project.
            combine(
                combine(
                    appContainer.settingsDataStore.selectedProjectId,
                    appContainer.settingsDataStore.projectSelectionCleared
                ) { id, cleared -> id to cleared },
                appContainer.database.projectDao().getAllProjects()
            ) { (savedProjectId, cleared), projects ->
                if (cleared || savedProjectId == null) {
                    null
                } else {
                    // An empty or deleted selection is a real state. Never
                    // silently replace it with the first active project.
                    projects.firstOrNull { it.id == savedProjectId }
                }
            }.collect { project ->
                _currentProject.value = project
            }
        }

        viewModelScope.launch {
            AppDatabase.ensureDefaultData(appContainer.database)
            appContainer.settingsDataStore.activeTemplateId.collectLatest { templateId ->
                val dao = appContainer.database.watermarkDao()
                val template = dao.getTemplateById(templateId) ?: dao.getDefaultTemplate()
                _activeTemplate.value = template
                if (template == null) {
                    _watermarkFields.value = emptyList()
                } else {
                    // Older databases only seeded the default template.  Make
                    // every template editable before collecting its live flow.
                    dao.ensureBuiltInFields(
                        template.id,
                        com.sitecam.app.core.watermark.model.builtInWatermarkFieldsForTemplate(template.id)
                    )
                    combine(
                        dao.observeTemplate(template.id),
                        dao.getFieldsForTemplate(template.id)
                    ) { latestTemplate, fields -> latestTemplate to fields }.collect { (latestTemplate, fields) ->
                        _activeTemplate.value = latestTemplate
                        _watermarkFields.value = fields
                    }
                }
            }
        }
    }

    private fun observeLocationAndAddress() {
        viewModelScope.launch {
            appContainer.locationTracker.currentLocation
                // Let an in-flight lookup finish, then process the latest
                // coordinates. A moving GPS fix should not repeatedly cancel
                // a slow system geocoder request; old-coordinate results are
                // rejected below before they can be displayed or captured.
                .collect { loc ->
                if (loc != null) {
                    val generation = ++locationAddressGeneration
                    // Do not even start an automatic lookup while the retry
                    // action owns the location/address transaction.  Checking
                    // only the generation at launch still allows an already
                    // running automatic lookup to reset REFRESHING when it
                    // completes.
                    if (manualAddressActive) return@collect
                    // A manual refresh owns the current request generation.
                    // Capture it before the potentially slow geocoder call so
                    // an automatic result that started earlier cannot overwrite
                    // the manual result after it finishes.
                    val manualGenerationAtStart = manualAddressGeneration
                    val retainedAddress = _automaticAddress.value
                    val now = System.currentTimeMillis()
                    if (retainedAddress != null && retainedAddress.text.isNotBlank() && retainedAddress.matches(loc, now) && now - retainedAddress.resolvedAt < 60_000L) {
                        _addressRefreshState.value = "IDLE"
                        return@collect
                    }
                    val lastAttempt = lastAutomaticAddressAttempt
                    if (lastAttempt != null && now - lastAutomaticAddressAttemptAt < 60_000L && nearbyLocation(lastAttempt, loc, now)) return@collect
                    lastAutomaticAddressAttempt = loc
                    lastAutomaticAddressAttemptAt = now
                    val address = lookupAutomaticAddress(
                        reverseGeocode = {
                            appContainer.reverseGeocoder.getAddressText(loc.latitude, loc.longitude)
                        }
                    )
                    val current = appContainer.locationTracker.currentLocation.value
                    if (canCommitAutomaticAddress(
                            manualGenerationAtStart = manualGenerationAtStart,
                            currentManualGeneration = manualAddressGeneration,
                            manualAddressActive = manualAddressActive,
                            locationGenerationAtStart = generation,
                            currentLocationGeneration = locationAddressGeneration,
                            sameLocation = nearbyLocation(current, loc, System.currentTimeMillis())
                        )
                    ) {
                        if (address.isNotBlank()) {
                            _automaticAddress.value = LocatedAddress(loc.latitude, loc.longitude, loc.timestamp, address, System.currentTimeMillis())
                        } else if (_automaticAddress.value?.matches(loc) != true) {
                            _automaticAddress.value = null
                        }
                        _addressRefreshState.value = if (address.isBlank()) "FAILED_ADDRESS" else "IDLE"
                    }
                } else {
                    // Never retain an address after permission is revoked or
                    // tracking is stopped; it could otherwise be burned into
                    // a later, unrelated capture.
                    _automaticAddress.value = null
                    lastAutomaticAddressAttempt = null
                    lastAutomaticAddressAttemptAt = 0L
                    if (_addressRefreshState.value != "REFRESHING") _addressRefreshState.value = "IDLE"
                }
            }
        }
    }

    /** Retry reverse geocoding from the latest fix, bypassing the normal cache. */
    fun isSystemLocationEnabled(): Boolean = appContainer.locationTracker.isSystemLocationEnabled()

    fun refreshAddress() {
        if (_addressRefreshState.value == "REFRESHING" || manualAddressActive) return
        val generation = ++manualAddressGeneration
        manualAddressActive = true
        _addressRefreshState.value = "REFRESHING"
        viewModelScope.launch {
            try {
                val result = performAddressRefresh(
                    source = object : AddressRefreshSource {
                        override val currentLocation = appContainer.locationTracker.currentLocation
                        override fun hasLocationPermission(): Boolean =
                            appContainer.locationTracker.hasLocationPermission()
                        override fun isSystemLocationEnabled(): Boolean =
                            appContainer.locationTracker.isSystemLocationEnabled()
                        override fun clearLocation() = appContainer.locationTracker.clearLocation()
                        override fun requestFreshLocation() = appContainer.locationTracker.refreshLocation()
                    },
                    reverseGeocode = { location ->
                        appContainer.reverseGeocoder.getAddressText(
                            location.latitude,
                            location.longitude,
                            forceRefresh = true
                        )
                    }
                )
                if (generation != manualAddressGeneration) return@launch
                when (result) {
                    is AddressRefreshResult.Success -> {
                        val current = appContainer.locationTracker.currentLocation.value
                        when {
                            !appContainer.locationTracker.hasLocationPermission() -> {
                                _addressRefreshState.value = "FAILED_PERMISSION"
                            }
                            !nearbyLocation(current, result.location, System.currentTimeMillis()) -> {
                                _addressRefreshState.value = "FAILED_LOCATION"
                            }
                            else -> {
                                _automaticAddress.value = LocatedAddress(
                                    result.location.latitude,
                                    result.location.longitude,
                                    result.location.timestamp,
                                    result.address,
                                    System.currentTimeMillis()
                                )
                                _addressRefreshState.value = "IDLE"
                            }
                        }
                    }
                    is AddressRefreshResult.Failure -> {
                        _addressRefreshState.value = when (result.reason) {
                            AddressRefreshFailure.PERMISSION -> "FAILED_PERMISSION"
                            AddressRefreshFailure.LOCATION_DISABLED -> "FAILED_LOCATION_DISABLED"
                            AddressRefreshFailure.LOCATION -> "FAILED_LOCATION"
                            AddressRefreshFailure.ADDRESS -> "FAILED_ADDRESS"
                        }
                    }
                }
            } catch (_: Exception) {
                if (generation == manualAddressGeneration) _addressRefreshState.value = "FAILED_ADDRESS"
            } finally {
                if (generation == manualAddressGeneration) manualAddressActive = false
            }
        }
    }

    private fun observeLatestMedia() {
        viewModelScope.launch {
            appContainer.database.mediaItemDao().getLatestMediaItem().collect { latest ->
                _latestThumbnailUri.value = latest?.contentUri
                _latestMediaType.value = latest?.mediaType
            }
        }
    }

    fun setCaptureMode(mode: CaptureMode) {
        if (_isRecordingVideo.value || _isCapturing.value) return
        _captureMode.value = mode
        cameraManager.setZoomCaptureMode(mode == CaptureMode.VIDEO)
    }

    fun startForegroundServices(locationEnabled: Boolean = true) {
        if (locationEnabled) appContainer.locationTracker.startLocationUpdates()
        else appContainer.locationTracker.stopLocationUpdates()
        orientationManager.startListening()
        if (weatherJob?.isActive != true) weatherJob = viewModelScope.launch {
            while (isActive) {
                _weather.value = systemWeatherReader.read()
                delay(120_000L)
            }
        }
    }

    fun updateLocationPermission(granted: Boolean) {
        if (granted) appContainer.locationTracker.startLocationUpdates()
        else appContainer.locationTracker.stopLocationUpdates()
    }

    fun stopForegroundServices() {
        stopBurst()
        weatherJob?.cancel()
        weatherJob = null
        appContainer.locationTracker.stopLocationUpdates()
        orientationManager.stopListening()
    }

    fun toggleFlashMode() {
        val current = _flashModeOverride.value ?: uiState.value.flashMode
        val next = when (current.uppercase()) {
            "AUTO" -> "ON"
            "ON" -> "OFF"
            "TORCH" -> "OFF"
            else -> "AUTO"
        }
        applyFlashMode(next)
    }

    fun toggleTorchMode() {
        val current = _flashModeOverride.value ?: uiState.value.flashMode
        val next = if (current.equals("TORCH", ignoreCase = true)) "OFF" else "TORCH"
        applyFlashMode(next)
    }

    /** Advance the local value first so a double tap cannot skip a step. */
    private fun applyFlashMode(next: String) {
        flashModeOverrideVersion += 1L
        _flashModeOverride.value = next
        viewModelScope.launch {
            appContainer.settingsDataStore.setFlashMode(next)
            cameraManager.setFlashMode(next)
        }
    }

    fun setCaptureOrientation(mode: com.sitecam.app.core.camera.CaptureOrientation) {
        if (_isCapturing.value || _isRecordingVideo.value) return
        orientationManager.restoreLockedState(mode.degrees != null, mode.degrees ?: 0)
        viewModelScope.launch { appContainer.settingsDataStore.setCaptureOrientation(mode) }
    }
    fun toggleOrientationLock() {
        val modes = com.sitecam.app.core.camera.CaptureOrientation.entries
        setCaptureOrientation(modes[(uiState.value.captureOrientation.ordinal + 1) % modes.size])
    }

    fun toggleQuickIssueMode() {
        val next = !_quickIssueMode.value
        quickIssueModeOverrideVersion += 1L
        _quickIssueMode.value = next
        viewModelScope.launch {
            appContainer.settingsDataStore.setQuickIssueMode(next)
        }
    }

    fun setZoomRatio(ratio: Float) {
        if (_isCapturing.value) return
        cameraManager.setZoomRatio(ratio, requireVideo = _captureMode.value == CaptureMode.VIDEO)
    }

    fun selectNativeZoomRatio(ratio: Float) {
        if (_isCapturing.value) return
        cameraManager.setZoomRatio(ratio, exactNativeStop = true, requireVideo = _captureMode.value == CaptureMode.VIDEO)
    }

    /** Refresh the compact camera chooser from the latest persisted rows. */
    fun refreshProjectPicker() {
        viewModelScope.launch {
            runCatching {
                val selectedId = appContainer.settingsDataStore.selectedProjectId.first()
                val projects = appContainer.database.projectDao().getAllProjects().first()
                val byId = projects.associateBy { it.id }
                val current = selectedId?.let(byId::get)
                val recent = appContainer.database.projectDao().getProjectsByLatestCapture()
                    .filter { it.id != current?.id }
                _projectPickerProjects.value = buildList {
                    current?.let(::add)
                    addAll(recent)
                }
            }.onFailure {
                _uiEvents.emit(CameraUiEvent.ShowToast("读取工程失败：${it.message ?: "请重试"}"))
            }
        }
    }

    /** Persist a camera quick-switch only after the latest row is re-read. */
    fun selectProjectFromCamera(projectId: Long) {
        if (_isProjectSwitching.value || _isCapturing.value || _isRecordingVideo.value) {
            viewModelScope.launch {
                _uiEvents.emit(CameraUiEvent.ShowToast("正在拍摄或保存，暂时不能切换工程"))
            }
            return
        }
        _isProjectSwitching.value = true
        viewModelScope.launch {
            try {
                val project = appContainer.captureOperationCoordinator.withAllProjectsIdle {
                    val selected = appContainer.database.projectDao().getProjectById(projectId)
                        ?: error("工程已删除")
                    appContainer.settingsDataStore.setSelectedProjectIdAndRecordRecent(selected.id)
                    // Keep the in-memory selection inside the same global
                    // idle window as the durable write. A shutter reservation
                    // cannot begin between the read and the selection commit.
                    _currentProject.value = selected
                    selected
                }
                _uiEvents.emit(CameraUiEvent.ProjectSwitched(project.id, project.name))
            } catch (error: Exception) {
                _uiEvents.emit(CameraUiEvent.ShowToast("切换工程失败：${error.message ?: "请重试"}"))
            } finally {
                _isProjectSwitching.value = false
            }
        }
    }

    /** Create from the compact chooser and return to the camera on success. */
    fun createProjectFromCamera(name: String, routeName: String = "") {
        val cleanName = name.trim()
        if (cleanName.isBlank() || _isProjectSwitching.value) return
        _isProjectSwitching.value = true
        viewModelScope.launch {
            try {
                val project = appContainer.captureOperationCoordinator.withAllProjectsIdle {
                    val id = appContainer.database.projectDao().insertProject(
                        ProjectEntity(
                            name = cleanName,
                            routeName = routeName.trim(),
                            categoryName = "建筑"
                        )
                    )
                    val created = appContainer.database.projectDao().getProjectById(id)
                        ?: error("工程创建后读取失败")
                    appContainer.settingsDataStore.setSelectedProjectIdAndRecordRecent(id)
                    _currentProject.value = created
                    created
                }
                _uiEvents.emit(CameraUiEvent.ProjectSwitched(project.id, project.name))
            } catch (error: Exception) {
                _uiEvents.emit(CameraUiEvent.ShowToast("工程创建失败：${error.message ?: "请重试"}"))
            } finally {
                _isProjectSwitching.value = false
            }
        }
    }

    fun unlockCurrentProject() = updateCurrentProjectFlags(locked = false)

    fun restoreCurrentProject() = updateCurrentProjectFlags(archived = false)

    private fun updateCurrentProjectFlags(locked: Boolean? = null, archived: Boolean? = null) {
        if (_isCapturing.value || _isRecordingVideo.value) {
            viewModelScope.launch {
                _uiEvents.emit(CameraUiEvent.ShowToast("正在拍摄或保存，暂时不能修改工程状态"))
            }
            return
        }
        val projectId = _currentProject.value?.id ?: return
        viewModelScope.launch {
            try {
                val updated = appContainer.captureOperationCoordinator.withProjectIdle(projectId) {
                    val current = appContainer.database.projectDao().getProjectById(projectId)
                        ?: error("工程已删除")
                    val next = current.copy(
                        isCaptureLocked = locked ?: current.isCaptureLocked,
                        isArchived = archived ?: current.isArchived,
                        updatedAt = System.currentTimeMillis()
                    )
                    appContainer.database.projectDao().updateProject(next)
                    next
                }
                _currentProject.value = updated
                val action = when {
                    archived == false -> "工程已恢复"
                    locked == false -> "已解锁拍摄"
                    else -> "工程状态已更新"
                }
                _uiEvents.emit(CameraUiEvent.ShowToast(action))
            } catch (error: Exception) {
                _uiEvents.emit(CameraUiEvent.ShowToast("工程状态未修改：${error.message ?: "请重试"}"))
            }
        }
    }

    // --- Direct Watermark Quick Update Functions ---

    fun updateWatermarkFieldValue(fieldId: Long, newValue: String) {
        val field = _watermarkFields.value.find { it.id == fieldId } ?: return
        val updated = field.copy(defaultValue = newValue)
        // Optimistic local update makes the preview react on every keystroke.
        _watermarkFields.value = _watermarkFields.value.map { if (it.id == fieldId) updated else it }
        appContainer.watermarkTemplateMutations.fieldValue(fieldId, newValue)
    }

    fun toggleWatermarkFieldEnabled(fieldId: Long, enabled: Boolean) {
        if (_watermarkFields.value.none { it.id == fieldId }) return
        _watermarkFields.value = _watermarkFields.value.map { if (it.id == fieldId) it.copy(isEnabled = enabled) else it }
        appContainer.watermarkTemplateMutations.fieldEnabled(fieldId, enabled)
    }

    fun updateTemplateStyle(styleType: String) {
        val id = _activeTemplate.value?.id ?: return
        appContainer.watermarkTemplateMutations.style(id, styleType)
    }

    fun updateTemplateFontSize(scale: Float) {
        val id = _activeTemplate.value?.id ?: return
        appContainer.watermarkTemplateMutations.fontSize(id, scale)
    }

    fun updateTemplateWidth(ratio: Float) {
        val id = _activeTemplate.value?.id ?: return
        appContainer.watermarkTemplateMutations.width(id, ratio)
    }

    fun updateTemplateOpacity(opacity: Float) {
        val id = _activeTemplate.value?.id ?: return
        appContainer.watermarkTemplateMutations.opacity(id, opacity)
    }

    fun addCustomFieldDirect(label: String, defaultValue: String) {
        val template = _activeTemplate.value ?: return
        // Route through the shared serial queue: it derives displayOrder from
        // the database inside its consumer, so two quick additions cannot end
        // up with the same order (the field list itself arrives via the flow).
        appContainer.watermarkTemplateMutations.addField(
            WatermarkFieldEntity(
                templateId = template.id,
                fieldKey = "CUSTOM_${System.currentTimeMillis()}",
                label = label.trim(),
                defaultValue = defaultValue.trim(),
                isEnabled = true,
                displayOrder = 0
            )
        )
    }

    fun toggleBurstMode() {
        if (_isCapturing.value || _captureMode.value != CaptureMode.PHOTO) return
        _burstEnabled.value = !_burstEnabled.value
    }

    fun stopBurst() { _isBurstCapturing.value = false }

    fun handleShutterAction(context: Context, withAudio: Boolean = false) {
        if (_isBurstCapturing.value) { stopBurst(); return }
        if (_isProjectSwitching.value) {
            viewModelScope.launch { _uiEvents.emit(CameraUiEvent.ShowToast("工程切换中，请稍候")) }
            return
        }
        if (_captureMode.value == CaptureMode.VIDEO) {
            when (cameraManager.videoRecordingState.value) {
                CameraManager.VideoRecordingState.RECORDING,
                CameraManager.VideoRecordingState.STARTING -> stopVideoRecording()
                CameraManager.VideoRecordingState.FINALIZING -> viewModelScope.launch {
                    _uiEvents.emit(CameraUiEvent.ShowToast("录像正在保存，请稍候"))
                }
                CameraManager.VideoRecordingState.IDLE -> if (!_isCapturing.value) {
                val audioAllowed = withAudio &&
                    androidx.core.content.ContextCompat.checkSelfPermission(
                        context,
                        android.Manifest.permission.RECORD_AUDIO
                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                if (withAudio && !audioAllowed) {
                    viewModelScope.launch { _uiEvents.emit(CameraUiEvent.ShowToast("未授权录音，已切换为无声录像")) }
                }
                // Snapshot on the main thread: the session must use the project
                // and watermark visible when the shutter was pressed.
                startVideoRecording(context, audioAllowed, uiState.value)
                }
            }
        } else {
            capturePhoto()
        }
    }

    private fun startVideoRecording(context: Context, withAudio: Boolean, state: CameraUiState) {
        viewModelScope.launch {
            try { startVideoRecordingReserved(context, withAudio, state) }
            catch (error: Exception) {
                _isCapturing.value = false
                state.currentProject?.let { appContainer.captureOperationCoordinator.finish(it.id) }
                _uiEvents.emit(CameraUiEvent.ShowToast(error.message ?: "录像启动失败"))
            }
        }
    }

    private suspend fun startVideoRecordingReserved(context: Context, withAudio: Boolean, state: CameraUiState) {
        if (_isProjectSwitching.value) {
            _uiEvents.emit(CameraUiEvent.ShowToast("工程切换中，请稍候"))
            return
        }
        if (_isCapturing.value || cameraManager.videoRecordingState.value != CameraManager.VideoRecordingState.IDLE) {
            viewModelScope.launch { _uiEvents.emit(CameraUiEvent.ShowToast("上一段录像仍在保存，请稍候")) }
            return
        }
        val project = state.currentProject
        if (project == null) {
            _uiEvents.emit(CameraUiEvent.ShowToast("请先选择工程包"))
            return
        }
        if (!appContainer.captureOperationCoordinator.tryBegin(project.id) {
                appContainer.database.projectDao().getProjectById(project.id)?.let { !it.isCaptureLocked && !it.isArchived } == true
            }) {
            _uiEvents.emit(CameraUiEvent.ShowToast("此工程已锁定或正在拍摄，请解锁或切换工程"))
            return
        }
        _isCapturing.value = true
        val saveToGallery = appContainer.settingsDataStore.saveToSystemGallery.first()
        val quickIssueModeAtCapture = _quickIssueMode.value
        val captureTimestamp = System.currentTimeMillis()
        val captureLocation = freshLocation(state, captureTimestamp)
        val captureWatermark = state.watermarkData.copy(
            captureTimestamp = captureTimestamp,
            captureTimeZoneId = java.util.TimeZone.getDefault().id,
            saveToSystemGallery = saveToGallery,
            latitude = captureLocation.latitude,
            longitude = captureLocation.longitude,
            altitude = captureLocation.altitude,
            addressText = captureLocation.address,
            locationStatus = captureLocation.status
        )
        val pendingVideo = withContext(appContainer.dispatchers.io) {
            pendingVideoStore.prepare(MediaItemEntity(
                projectId = project.id, mediaType = "VIDEO", contentUri = "",
                fileName = NamingEngine.generateFileName(project.name, project.categoryName, captureTimestamp, extension = "mp4"),
                captureTimestamp = captureTimestamp,
                latitude = captureLocation.latitude, longitude = captureLocation.longitude, altitude = captureLocation.altitude,
                locationAccuracy = captureLocation.accuracy, locationTimestamp = captureLocation.timestamp,
                locationStatus = captureLocation.status, addressText = captureLocation.address,
                watermarkSnapshotJson = WatermarkSnapshotCodec.encode(captureWatermark)
            ), quickIssueModeAtCapture)
        }
        val tempFile = pendingVideo.file

        _recordingDurationSeconds.value = 0
        _isRecordingVideo.value = true
        _isCapturing.value = false
        val pendingSession = VideoCaptureSession(
            token = 0L,
            timestamp = captureTimestamp,
            project = project,
            location = captureLocation,
            watermark = captureWatermark,
            quickIssueMode = quickIssueModeAtCapture,
            saveToSystemGallery = saveToGallery,
            tempFile = tempFile,
            pendingVideo = pendingVideo
        )
        activeVideoSession = pendingSession
        try {
            val token = cameraManager.startVideoRecording(
                outputFile = tempFile,
                withAudio = withAudio
            ) { eventToken, event ->
                val session = activeVideoSession
                    ?.takeIf { it.token == 0L || it.token == eventToken }
                    ?.let { if (it.token == 0L) it.copy(token = eventToken) else it }
                if (session == null) {
                    // Its durable capture manifest belongs to recovery if this ViewModel lost the session.
                    return@startVideoRecording
                }
                activeVideoSession = session
                if (event is VideoRecordEvent.Finalize) {
                    _isRecordingVideo.value = false
                    recordingTimerJob?.cancel()
                    if (mayRetainRecordedFrames(event.error) && session.tempFile.length() > 0L) {
                        _isCapturing.value = true
                        viewModelScope.launch {
                            _uiEvents.emit(CameraUiEvent.ShowToast("录像已结束，正在烧录工程水印…"))
                        }
                        viewModelScope.launch(appContainer.dispatchers.io) {
                            finalizeVideoSession(context, session)
                        }
                    } else {
                        pendingVideoStore.discard(session.pendingVideo)
                        viewModelScope.launch { appContainer.captureOperationCoordinator.finish(session.project.id) }
                        activeVideoSession = null
                        viewModelScope.launch {
                            _uiEvents.emit(CameraUiEvent.ShowToast("录像中断: ${event.cause?.message ?: "未知错误"}"))
                        }
                    }
                }
            }
            if (activeVideoSession?.token == 0L) {
                activeVideoSession = pendingSession.copy(token = token)
            }
            recordingTimerJob?.cancel()
            recordingTimerJob = viewModelScope.launch {
                while (_isRecordingVideo.value) {
                    delay(1000L)
                    if (_isRecordingVideo.value) _recordingDurationSeconds.value += 1
                }
            }
        } catch (error: Exception) {
            appContainer.captureOperationCoordinator.finish(project.id)
            activeVideoSession = null
            _isRecordingVideo.value = false
            recordingTimerJob?.cancel()
            pendingVideoStore.discard(pendingVideo)
            viewModelScope.launch {
                _uiEvents.emit(CameraUiEvent.ShowToast("录像启动失败: ${error.message ?: "未知错误"}"))
            }
        }
    }

    private fun stopVideoRecording() {
        if (cameraManager.stopVideoRecording()) {
            // The UI remains busy until CameraX sends Finalize and the
            // watermark/save pipeline completes.
            recordingTimerJob?.cancel()
        }
    }

    fun stopRecordingForBackground() = stopVideoRecording()

    /** Replay only durable recordings that have not yet been accepted by Room. */
    fun recoverPendingVideos() {
        if (recoveryJob?.isActive == true) return
        recoveryJob = viewModelScope.launch(appContainer.dispatchers.io) {
            MediaOperationCoordinator.withExclusive {
                var recovered = 0
                for (record in pendingVideoStore.pending()) {
                    if (record.file == activeVideoSession?.tempFile) continue
                    try {
                        val metadata = appContainer.mediaStoreManager.inspectRecordedVideo(record.file)
                        val restored = pendingVideoStore.publish(record, metadata)
                        recovered++
                        if (record.quickIssueMode) _uiEvents.emit(CameraUiEvent.QuickIssuePrompt(restored.id, Uri.parse(restored.contentUri)))
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { /* Keep the source and frozen manifest for the next resume. */ }
                }
                if (recovered > 0) _uiEvents.emit(CameraUiEvent.ShowToast("已恢复 $recovered 段录像，可在工程相册重试水印"))
            }
        }
    }

    private suspend fun finalizeVideoSession(context: Context, session: VideoCaptureSession) {
        try {
            MediaOperationCoordinator.withExclusive {
                val burnedFile = File(
                    context.cacheDir,
                    "temp_burned_${session.timestamp}_${System.nanoTime()}.mp4"
                )
                var sourceFile = session.tempFile
                var burnedIn = false
                var original: MediaItemEntity? = null
                var uncommittedUri: Uri? = null
                try {
                    val metadata = appContainer.mediaStoreManager.inspectRecordedVideo(session.tempFile)
                    val retained = pendingVideoStore.publish(session.pendingVideo, metadata)
                    original = retained
                    try {
                        appContainer.videoWatermarkTranscoder.transcode(
                            inputFile = session.tempFile,
                            outputFile = burnedFile,
                            watermarkData = session.watermark
                        )
                        sourceFile = burnedFile
                        burnedIn = true
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { /* The indexed original remains the retry target. */ }

                    val fileName = session.pendingVideo.media.fileName
                    val saveResult = appContainer.mediaStoreManager.saveVideoToMediaStore(
                        tempVideoFile = sourceFile,
                        fileName = fileName,
                        projectName = session.project.name,
                        timestamp = session.timestamp,
                        saveToSystemGallery = session.saveToSystemGallery
                    )
                    uncommittedUri = saveResult.uri
                    withContext(NonCancellable) {
                        appContainer.database.withTransaction {
                            val current = checkNotNull(appContainer.database.mediaItemDao().getMediaItemById(retained.id)) { "原录像记录已变化" }
                            appContainer.database.mediaItemDao().updateMediaItem(current.copy(
                                contentUri = saveResult.uri.toString(), filePath = saveResult.filePath, fileName = saveResult.fileName,
                                width = saveResult.width, height = saveResult.height, duration = saveResult.duration,
                                orientation = saveResult.rotation,
                                processingStatus = if (burnedIn) "READY" else "FAILED_VIDEO_WATERMARK_RETRY"
                            ))
                        }
                        uncommittedUri = null
                        pendingVideoStore.discard(session.pendingVideo)
                    }
                    _latestThumbnailUri.value = saveResult.uri.toString()
                    _latestMediaType.value = "VIDEO"
                    if (!burnedIn) _uiEvents.emit(CameraUiEvent.ShowToast("视频水印处理失败，已保留原片，可在工程相册重试"))
                    if (session.quickIssueMode) {
                        _uiEvents.emit(CameraUiEvent.QuickIssuePrompt(retained.id, saveResult.uri))
                    } else {
                        _uiEvents.emit(CameraUiEvent.VideoSaved(retained.id, saveResult.uri))
                    }
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (error: Exception) {
                    _uiEvents.emit(CameraUiEvent.ShowToast(if (original != null)
                        "保存视频失败，原片已保留，可在工程相册重试"
                    else "录像已保留，稍后打开相机可恢复"))
                } finally {
                    withContext(NonCancellable) {
                        // Cleanup failures must not replace the error caught above.
                        runCatching { uncommittedUri?.let { appContainer.mediaStoreManager.deleteMediaUri(it) } }
                        runCatching { burnedFile.delete() }
                    }
                }
            }
        } finally {
            // Project operations acquire capture before media; release media before finishing the reservation.
            appContainer.captureOperationCoordinator.finish(session.project.id)
            if (activeVideoSession?.token == session.token) activeVideoSession = null
            _isCapturing.value = false
        }
    }

    private data class PhotoFrame(val bitmap: Bitmap, val rotation: Int, val surfaceRotation: Int,
        val timestamp: Long, val location: CaptureLocation, val watermark: WatermarkData, val mirrored: Boolean)

    fun capturePhoto() {
        if (_isProjectSwitching.value || _isCapturing.value || cameraManager.videoRecordingState.value != CameraManager.VideoRecordingState.IDLE) return
        val state = uiState.value
        val project = state.currentProject
        if (project == null) {
            viewModelScope.launch { _uiEvents.emit(CameraUiEvent.ShowToast("请先选择工程包")) }
            return
        }
        val burst = _burstEnabled.value
        val quickIssue = _quickIssueMode.value
        _isCapturing.value = true
        _isBurstCapturing.value = burst
        photoSessionJob = viewModelScope.launch(appContainer.dispatchers.io) {
            var reserved = false
            var saved = 0
            var lastSaved: Pair<Long, Uri>? = null
            try {
                reserved = appContainer.captureOperationCoordinator.tryBegin(project.id) {
                    appContainer.database.projectDao().getProjectById(project.id)?.let { !it.isCaptureLocked && !it.isArchived } == true
                }
                check(reserved) { "此工程已锁定或正在拍摄，请解锁或切换工程" }
                val quick = SnapshotPolicy.enabled(appContainer.settingsDataStore.snapshotEnabled.first(), burst)
                cameraManager.configureSnapshot(quick, appContainer.settingsDataStore.snapshotShutterSpeed.first())
                val profile = SnapshotPolicy.profile(quick, appContainer.settingsDataStore.photoQualityProfile.first())
                val gallery = appContainer.settingsDataStore.saveToSystemGallery.first()
                val pattern = appContainer.settingsDataStore.namingPattern.first()
                saved = captureAndSaveFrames(
                    multiple = burst,
                    shouldCapture = { (!burst || _isBurstCapturing.value) && cameraManager.isCameraReady.value },
                    capture = {
                        val timestamp = System.currentTimeMillis()
                        val current = uiState.value
                        val location = freshLocation(current, timestamp)
                        val watermark = current.watermarkData.copy(captureTimestamp = timestamp,
                            captureTimeZoneId = java.util.TimeZone.getDefault().id,
                            latitude = location.latitude, longitude = location.longitude,
                            altitude = location.altitude, addressText = location.address, locationStatus = location.status)
                        val rotation = cameraManager.currentTargetRotation()
                        val capture = cameraManager.capturePhoto(rotation)
                        PhotoFrame(capture.first, capture.second, rotation, timestamp, location, watermark, cameraManager.previewIsMirrored())
                    },
                    save = { frame -> lastSaved = savePhotoFrame(frame, project, profile, gallery, pattern, quickIssue, burst) },
                    discard = { frame -> if (!frame.bitmap.isRecycled) frame.bitmap.recycle() }
                )
                if (burst && saved > 0) _uiEvents.emit(CameraUiEvent.ShowToast("连拍已保存 $saved 张"))
                if (quickIssue && lastSaved != null) _uiEvents.emit(CameraUiEvent.QuickIssuePrompt(lastSaved!!.first, lastSaved!!.second))
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (outOfMemory: OutOfMemoryError) { _uiEvents.emit(CameraUiEvent.ShowToast("内存不足，已停止拍摄")) }
            catch (error: Exception) { _uiEvents.emit(CameraUiEvent.ShowToast("拍摄失败: ${error.message ?: "未知错误"}")) }
            finally {
                _isBurstCapturing.value = false
                if (reserved) appContainer.captureOperationCoordinator.finish(project.id)
                _isCapturing.value = false
            }
        }
    }

    private suspend fun savePhotoFrame(frame: PhotoFrame, project: ProjectEntity,
        profile: com.sitecam.app.core.media.PhotoQualityProfile, saveToGallery: Boolean,
        namingPattern: String, quickIssueModeAtCapture: Boolean, burst: Boolean): Pair<Long, Uri> {
        val capturedBitmap = frame.bitmap
        val rotationDegrees = frame.rotation
        val surfaceRotation = frame.surfaceRotation
        val captureTimestamp = frame.timestamp
        val captureLocation = frame.location
        val watermarkData = frame.watermark
        var watermarkedBitmap: Bitmap? = null
        try {
                val renderedBitmap = WatermarkBitmapRenderer.renderWatermarkOnBitmap(
                    sourceBitmap = capturedBitmap,
                    watermarkData = watermarkData,
                    rotationDegrees = rotationDegrees,
                    targetAspectRatio = if (surfaceRotation == android.view.Surface.ROTATION_90 || surfaceRotation == android.view.Surface.ROTATION_270) 4f / 3f else 3f / 4f,
                    qualityProfile = profile,
                    mirrorHorizontally = frame.mirrored
                )
                watermarkedBitmap = renderedBitmap

                val pattern = namingPattern
                val fileName = NamingEngine.generateFileName(
                    projectName = project.name,
                    categoryName = project.categoryName,
                    timestamp = captureTimestamp,
                    pattern = pattern
                )

                val quality = profile.jpegQuality
                val saveResult = appContainer.mediaStoreManager.savePhotoToMediaStore(
                    bitmap = renderedBitmap,
                    fileName = fileName,
                    projectName = project.name,
                    watermarkData = watermarkData,
                    quality = quality,
                    orientation = 0,
                    saveToSystemGallery = saveToGallery
                )

                val mediaEntity = MediaItemEntity(
                    projectId = project.id,
                    mediaType = "PHOTO",
                    contentUri = saveResult.uri.toString(),
                    filePath = saveResult.filePath,
                    fileName = saveResult.fileName,
                    width = saveResult.width,
                    height = saveResult.height,
                    captureTimestamp = captureTimestamp,
                    latitude = watermarkData.latitude,
                    longitude = watermarkData.longitude,
                    altitude = watermarkData.altitude,
                    locationAccuracy = captureLocation.accuracy,
                    locationTimestamp = captureLocation.timestamp,
                    locationStatus = captureLocation.status,
                    addressText = watermarkData.addressText,
                    orientation = 0,
                    // The quick-issue dialog is the commit point.
                    isIssue = false,
                    processingStatus = "READY",
                    watermarkSnapshotJson = WatermarkSnapshotCodec.encode(watermarkData)
                )

                val mediaId = try {
                    appContainer.database.mediaItemDao().insertMediaItem(mediaEntity)
                } catch (dbError: Exception) {
                    appContainer.mediaStoreManager.deleteMediaUri(saveResult.uri)
                    throw dbError
                }

                _latestThumbnailUri.value = saveResult.uri.toString()
                _latestMediaType.value = "PHOTO"

                // This event is intentionally separate from video completion;
                // the camera UI uses it to animate only a successfully saved
                // photo, including photos that open the quick-issue prompt.
                _uiEvents.emit(
                    CameraUiEvent.PhotoSaved(
                        mediaId = mediaId,
                        uri = saveResult.uri,
                        showToast = !quickIssueModeAtCapture && !burst
                    )
                )

                return mediaId to saveResult.uri

        } finally {
            watermarkedBitmap?.takeIf { !it.isRecycled }?.recycle()
            if (capturedBitmap != watermarkedBitmap && !capturedBitmap.isRecycled) capturedBitmap.recycle()
        }
    }

    override fun onCleared() {
        stopBurst()
        super.onCleared()
        activeVideoSession?.let { session ->
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch { appContainer.captureOperationCoordinator.finish(session.project.id) }
        }
        cameraManager.release()
    }

    companion object {
        fun provideFactory(appContainer: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return CameraViewModel(appContainer) as T
                }
            }
    }
}
