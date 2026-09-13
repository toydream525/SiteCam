package com.sitecam.app.core.camera

import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.ZoomState
import kotlin.math.tan

enum class CameraLensRole {
    WIDE,
    MAIN,
    TELE,
    UNKNOWN
}

/** Whether the current CameraX use-case combination has actually been bound. */
enum class CameraBindingVerification {
    UNVERIFIED,
    VERIFIED
}

/** Public camera information discovered from CameraX/Camera2 metadata. */
data class CameraLensCapability(
    val cameraId: String,
    val lensFacing: Int,
    val role: CameraLensRole = CameraLensRole.UNKNOWN,
    val isLogicalCamera: Boolean = false,
    val physicalMemberIds: Set<String> = emptySet(),
    val horizontalFovDegrees: Float? = null,
    /** Optical view relative to the main-camera reference, when measurable. */
    val equivalentZoomRatio: Float? = null,
    val hardwarePresent: Boolean = true,
    val appAccessible: Boolean = true,
    /** The public selector is accepted as a candidate by CameraX; see verification below. */
    val photoBindable: Boolean = false,
    val videoBindable: Boolean = false,
    val photoBindingVerification: CameraBindingVerification = CameraBindingVerification.UNVERIFIED,
    val videoBindingVerification: CameraBindingVerification = CameraBindingVerification.UNVERIFIED,
    val isActive: Boolean = false
)

/**
 * Lens classification deliberately uses field of view, which accounts for
 * each sensor's physical size. Comparing the raw millimetre focal lengths of
 * different sensors would mislabel wide and tele cameras.
 */
object CameraLensRoleClassifier {
    fun roleForHorizontalFov(horizontalFovDegrees: Float?): CameraLensRole {
        val fov = horizontalFovDegrees ?: return CameraLensRole.UNKNOWN
        return when {
            fov >= 75f -> CameraLensRole.WIDE
            fov <= 40f -> CameraLensRole.TELE
            else -> CameraLensRole.MAIN
        }
    }

    fun equivalentZoomRatio(referenceFovDegrees: Float?, lensFovDegrees: Float?): Float? {
        if (referenceFovDegrees == null || lensFovDegrees == null ||
            referenceFovDegrees <= 0f || lensFovDegrees <= 0f
        ) return null
        val reference = tan(Math.toRadians(referenceFovDegrees.toDouble() / 2.0))
        val lens = tan(Math.toRadians(lensFovDegrees.toDouble() / 2.0))
        if (!reference.isFinite() || !lens.isFinite() || lens <= 0.0) return null
        return (reference / lens).toFloat().takeIf { it.isFinite() && it > 0f }
    }
}

data class CameraCapability(
    val minZoomRatio: Float = 1.0f,
    val maxZoomRatio: Float = 5.0f,
    val zoomPillPresets: List<Float> = listOf(1.0f, 2.0f),
    val hasFlashUnit: Boolean = false,
    val isFrontCameraAvailable: Boolean = true,
    val isBackCameraAvailable: Boolean = true,
    /** CameraX devices visible to this app, including logical groups. */
    val publicLenses: List<CameraLensCapability> = emptyList(),
    /** Camera2 devices reported by the system but not independently exposed to this app. */
    val hardwareOnlyLenses: List<CameraLensCapability> = emptyList(),
    val activeCameraId: String? = null,
    /**
     * The active session's native 1x expressed against the rear main-camera
     * reference.  A separately exposed tele camera can therefore start at
     * e.g. 3.2x in the UI while CameraX still receives a native 1.0 request.
     */
    val activeLensEquivalentZoomRatio: Float = 1.0f
) {
    companion object {
        fun fromZoomState(
            zoomState: ZoomState?,
            hasFlash: Boolean = false,
            hasFront: Boolean = true,
            hasBack: Boolean = true,
            activeLensEquivalentZoomRatio: Float = 1.0f
        ): CameraCapability {
            val sessionMinZoom = (zoomState?.minZoomRatio ?: 1.0f).coerceAtLeast(0.4f)
            val sessionMaxZoom = (zoomState?.maxZoomRatio ?: 5.0f).coerceAtMost(30.0f)
                .coerceAtLeast(sessionMinZoom)
            val referenceRatio = CameraZoomMapping.sanitizeEquivalentRatio(activeLensEquivalentZoomRatio)
            // All controls expose one main-camera coordinate system.  The
            // values passed back through CameraManager are mapped to the
            // native session range before CameraX is called.
            val minZoom = sessionMinZoom * referenceRatio
            val maxZoom = sessionMaxZoom * referenceRatio

            val candidatePills = mutableListOf<Float>()
            // The first control must represent the camera's actual lower
            // bound.  This matters for 0.54x wide cameras and for a tele
            // session whose native 1x is, for example, 3.2x main-equivalent.
            candidatePills.add(minZoom)
            candidatePills.add(1.0f)
            if (maxZoom >= 1.9f) candidatePills.add(2.0f)
            if (maxZoom >= 2.9f) candidatePills.add(3.0f)
            if (maxZoom >= 4.9f) candidatePills.add(5.0f)
            if (maxZoom >= 9.9f) candidatePills.add(10.0f)

            val availablePills = candidatePills
                .filter { it in minZoom..maxZoom }
                .distinct()
                .sorted()
            val finalPills = if (availablePills.isEmpty()) listOf(minZoom) else availablePills

            return CameraCapability(
                minZoomRatio = minZoom,
                maxZoomRatio = maxZoom,
                zoomPillPresets = finalPills,
                hasFlashUnit = hasFlash,
                isFrontCameraAvailable = hasFront,
                isBackCameraAvailable = hasBack,
                activeLensEquivalentZoomRatio = referenceRatio
            )
        }

        fun fromCameraInfo(
            cameraInfo: CameraInfo?,
            hasFront: Boolean = true,
            hasBack: Boolean = true,
            activeLensEquivalentZoomRatio: Float = 1.0f
        ): CameraCapability {
            return fromZoomState(
                zoomState = cameraInfo?.zoomState?.value,
                hasFlash = cameraInfo?.hasFlashUnit() ?: false,
                hasFront = hasFront,
                hasBack = hasBack,
                activeLensEquivalentZoomRatio = activeLensEquivalentZoomRatio
            )
        }
    }
}

/** Maps between CameraX's per-session zoom and the UI's main-camera scale. */
object CameraZoomMapping {
    fun sanitizeEquivalentRatio(ratio: Float): Float =
        ratio.takeIf { it.isFinite() && it > 0f } ?: 1.0f

    fun displayRatioFromSession(sessionRatio: Float, equivalentRatio: Float): Float {
        val session = sessionRatio.takeIf { it.isFinite() && it > 0f } ?: 1.0f
        return session * sanitizeEquivalentRatio(equivalentRatio)
    }

    fun sessionRatioFromDisplay(displayRatio: Float, equivalentRatio: Float): Float {
        val display = displayRatio.takeIf { it.isFinite() && it > 0f } ?: 1.0f
        return display / sanitizeEquivalentRatio(equivalentRatio)
    }

    /**
     * Logical groups already expose one system-managed zoom coordinate. Only
     * an independently selectable rear group gets an optical main-camera
     * offset; a logical wide/main group must stay at its native 1x baseline.
     */
    fun referenceRatioForActiveLens(lens: CameraLensCapability?): Float =
        if (lens?.lensFacing == CameraSelector.LENS_FACING_BACK && !lens.isLogicalCamera) {
            sanitizeEquivalentRatio(lens.equivalentZoomRatio ?: 1.0f)
        } else {
            1.0f
        }
}
