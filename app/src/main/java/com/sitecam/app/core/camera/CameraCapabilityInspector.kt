package com.sitecam.app.core.camera

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.os.Build
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import kotlin.math.atan

data class CameraCapabilityInspection(
    val publicLenses: List<CameraLensCapability> = emptyList(),
    val hardwareOnlyLenses: List<CameraLensCapability> = emptyList(),
    val activeCameraId: String? = null
)

/**
 * Reads the CameraX public inventory and its Camera2 metadata together.
 * Camera2 IDs that are not in CameraX's public inventory remain evidence of
 * hardware presence only and are never offered as selectable lenses.
 */
object CameraCapabilityInspector {

    /**
     * Choose the best public camera group for an implicit facing request.
     * Logical groups come first because CameraX can manage their members and
     * mode-specific switching as one supported session.  An inactive video
     * result is UNVERIFIED rather than false, so a candidate is still tried
     * and the real Recorder bind decides whether video is available.
     */
    fun preferredPublicLens(
        lenses: List<CameraLensCapability>,
        lensFacing: Int,
        includeVideo: Boolean
    ): CameraLensCapability? = lenses
        .asSequence()
        .filter {
            it.lensFacing == lensFacing &&
                it.appAccessible &&
                it.photoBindable &&
                (!includeVideo || it.videoBindingVerification != CameraBindingVerification.VERIFIED || it.videoBindable)
        }
        .sortedWith(
            compareBy<CameraLensCapability>(
                { if (it.isLogicalCamera) 0 else 1 },
                { when (it.role) {
                    CameraLensRole.MAIN -> 0
                    CameraLensRole.WIDE -> 1
                    CameraLensRole.TELE -> 2
                    CameraLensRole.UNKNOWN -> 3
                } },
                { it.equivalentZoomRatio ?: Float.MAX_VALUE },
                { it.cameraId }
            )
        )
        .firstOrNull()

    /** Resolve the preferred public group without inventing a camera ID. */
    fun preferredPublicCameraId(
        context: Context,
        provider: ProcessCameraProvider,
        lensFacing: Int,
        includeVideo: Boolean
    ): String? = runCatching {
        preferredPublicLens(
            lenses = inspect(context = context, provider = provider, activeInfo = null).publicLenses,
            lensFacing = lensFacing,
            includeVideo = includeVideo
        )?.cameraId
    }.getOrNull()

    fun selectorForCameraId(cameraId: String): CameraSelector =
        CameraSelector.Builder()
            .addCameraFilter { infos -> infos.filter { cameraIdOf(it) == cameraId } }
            .build()

    fun cameraIdOf(cameraInfo: CameraInfo?): String? = cameraInfo?.let {
        runCatching { Camera2CameraInfo.from(it).cameraId }.getOrNull()
    }

    fun inspect(
        context: Context,
        provider: ProcessCameraProvider,
        activeInfo: CameraInfo?,
        activeVideoBindable: Boolean = false,
        activeVideoBindingAttempted: Boolean = false
    ): CameraCapabilityInspection {
        val available = provider.availableCameraInfos
        val directIds = available.mapNotNull(::cameraIdOf).toSet()
        val activeId = cameraIdOf(activeInfo)
        val public = available.mapNotNull { info ->
            val id = cameraIdOf(info) ?: return@mapNotNull null
            capabilityFor(
                info = info,
                cameraId = id,
                provider = provider,
                active = id == activeId,
                videoBindable = if (id == activeId) activeVideoBindable else false,
                videoBindingAttempted = if (id == activeId) activeVideoBindingAttempted else false,
                appAccessible = true
            )
        }

        val systemManager = context.getSystemService(Context.CAMERA_SERVICE)
            as? android.hardware.camera2.CameraManager
        val systemCameraIds = runCatching { systemManager?.cameraIdList?.toSet().orEmpty() }
            .getOrDefault(emptySet())
        val systemCharacteristics = readCharacteristics(systemManager, systemCameraIds)
        val exposedPhysicalIds = public
            .flatMap { it.physicalMemberIds }
            .filter { it !in directIds }
            .toSet()
        // A logical camera may expose physical IDs through its public
        // characteristics while omitting them from cameraIdList.  Try the
        // same public CameraManager read for those IDs so newer Android
        // versions can contribute role/FOV evidence; a failure remains an
        // UNKNOWN, non-selectable hardware fact.
        val exposedPhysicalCharacteristics = readCharacteristics(systemManager, exposedPhysicalIds)
        val allSystemCharacteristics = systemCharacteristics + exposedPhysicalCharacteristics
        val hardwareOnly = buildList {
            allSystemCharacteristics
                .filterKeys { it !in directIds }
                .forEach { (id, characteristics) ->
                    add(
                        capabilityFromCharacteristics(
                            cameraId = id,
                            characteristics = characteristics,
                            isLogicalCamera = false,
                            physicalMemberIds = emptySet(),
                            hardwarePresent = true,
                            appAccessible = false,
                            photoBindable = false,
                            videoBindable = false,
                            isActive = false
                        )
                    )
                }
            // A logical camera's physical IDs are often intentionally absent
            // from CameraManager.cameraIdList. Keep that public metadata as
            // hardware evidence even when the vendor exposes no separate
            // characteristics for the member; it remains non-selectable.
            exposedPhysicalIds
                .filter { id -> allSystemCharacteristics[id] == null }
                .forEach { id ->
                    add(
                        CameraLensCapability(
                            cameraId = id,
                            lensFacing = CameraSelector.LENS_FACING_BACK,
                            role = CameraLensRole.UNKNOWN,
                            hardwarePresent = true,
                            appAccessible = false,
                            photoBindable = false,
                            videoBindable = false
                        )
                    )
                }
        }.distinctBy { it.cameraId }
        // Prefer a measurable public main-camera FOV as reference, then any
        // measurable rear group. This uses sensor geometry rather than raw
        // focal millimetres so unlike sensors are compared consistently.
        val referenceFov = public
            .filter { it.lensFacing == CameraSelector.LENS_FACING_BACK && it.role == CameraLensRole.MAIN }
            .mapNotNull { it.horizontalFovDegrees }
            .firstOrNull()
            ?: public.filter { it.lensFacing == CameraSelector.LENS_FACING_BACK }
                .mapNotNull { it.horizontalFovDegrees }
                .firstOrNull()
        val fovNormalizedPublic = public.map { lens ->
            lens.copy(
                equivalentZoomRatio = CameraLensRoleClassifier.equivalentZoomRatio(
                    referenceFov,
                    lens.horizontalFovDegrees
                ),
                isActive = lens.cameraId == activeId
            )
        }
        return CameraCapabilityInspection(
            publicLenses = fovNormalizedPublic,
            hardwareOnlyLenses = hardwareOnly,
            activeCameraId = activeId
        )
    }

    private fun readCharacteristics(
        manager: android.hardware.camera2.CameraManager?,
        cameraIds: Set<String>
    ): Map<String, CameraCharacteristics> = cameraIds.mapNotNull { id ->
        runCatching { manager?.getCameraCharacteristics(id) }
            .getOrNull()
            ?.let { id to it }
    }.toMap()

    private fun capabilityFor(
        info: CameraInfo,
        cameraId: String,
        provider: ProcessCameraProvider,
        active: Boolean,
        videoBindable: Boolean,
        videoBindingAttempted: Boolean,
        appAccessible: Boolean
    ): CameraLensCapability? {
        val publicSelectorCandidate = isPublicSelectorCandidate(provider, cameraId)
        val characteristics = runCatching {
            Camera2CameraInfo.extractCameraCharacteristics(info)
        }.getOrNull()
        if (characteristics == null) {
            val facing = runCatching { info.lensFacing }.getOrDefault(CameraSelector.LENS_FACING_UNKNOWN)
            return CameraLensCapability(
                cameraId = cameraId,
                lensFacing = facing,
                hardwarePresent = true,
                appAccessible = appAccessible,
                photoBindable = publicSelectorCandidate,
                videoBindable = active && videoBindable,
                photoBindingVerification = if (active) CameraBindingVerification.VERIFIED else CameraBindingVerification.UNVERIFIED,
                videoBindingVerification = if (active && videoBindingAttempted) CameraBindingVerification.VERIFIED else CameraBindingVerification.UNVERIFIED,
                isActive = active
            )
        }
        val physicalIds = if (Build.VERSION.SDK_INT >= 28) {
            characteristics.physicalCameraIds
        } else {
            emptySet()
        }
        val logical = physicalIds.isNotEmpty() ||
            if (Build.VERSION.SDK_INT >= 28) {
                characteristics.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
                    ?.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA) == true
            } else false
        return capabilityFromCharacteristics(
            cameraId = cameraId,
            characteristics = characteristics,
            isLogicalCamera = logical,
            physicalMemberIds = physicalIds,
            hardwarePresent = true,
            appAccessible = appAccessible,
            photoBindable = publicSelectorCandidate,
            videoBindable = active && videoBindable,
            photoBindingVerification = if (active) CameraBindingVerification.VERIFIED else CameraBindingVerification.UNVERIFIED,
            videoBindingVerification = if (active && videoBindingAttempted) CameraBindingVerification.VERIFIED else CameraBindingVerification.UNVERIFIED,
            isActive = active,
            fallbackLensFacing = runCatching { info.lensFacing }
                .getOrDefault(CameraSelector.LENS_FACING_UNKNOWN)
        )
    }

    /*
     * The selector check only establishes a public candidate. The active
     * camera's verification fields are set after bindToLifecycle succeeds.
     */
    private fun isPublicSelectorCandidate(provider: ProcessCameraProvider, cameraId: String): Boolean = runCatching {
        provider.hasCamera(selectorForCameraId(cameraId))
    }.getOrDefault(false)

    /*
     * Kept below as a single metadata mapper so system-only physical members
     * and public CameraX groups share the same evidence fields.
     */
    private fun capabilityFromCharacteristics(
        cameraId: String,
        characteristics: CameraCharacteristics,
        isLogicalCamera: Boolean,
        physicalMemberIds: Set<String>,
        hardwarePresent: Boolean,
        appAccessible: Boolean,
        photoBindable: Boolean,
        videoBindable: Boolean,
        photoBindingVerification: CameraBindingVerification = CameraBindingVerification.UNVERIFIED,
        videoBindingVerification: CameraBindingVerification = CameraBindingVerification.UNVERIFIED,
        isActive: Boolean,
        fallbackLensFacing: Int = CameraSelector.LENS_FACING_UNKNOWN
    ): CameraLensCapability {
        val facing = characteristics.get(CameraCharacteristics.LENS_FACING) ?: fallbackLensFacing
        val fov = horizontalFovDegrees(characteristics)
        return CameraLensCapability(
            cameraId = cameraId,
            lensFacing = facing,
            role = CameraLensRoleClassifier.roleForHorizontalFov(fov),
            isLogicalCamera = isLogicalCamera,
            physicalMemberIds = physicalMemberIds,
            horizontalFovDegrees = fov,
            hardwarePresent = hardwarePresent,
            appAccessible = appAccessible,
            photoBindable = photoBindable,
            videoBindable = videoBindable,
            photoBindingVerification = photoBindingVerification,
            videoBindingVerification = videoBindingVerification,
            isActive = isActive
        )
    }

    private fun horizontalFovDegrees(characteristics: CameraCharacteristics): Float? {
        val sensorWidth = characteristics.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)?.width ?: return null
        val focalLength = characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
            ?.firstOrNull() ?: return null
        if (sensorWidth <= 0f || focalLength <= 0f) return null
        return Math.toDegrees(2.0 * atan(sensorWidth / (2.0 * focalLength))).toFloat()
    }

}
