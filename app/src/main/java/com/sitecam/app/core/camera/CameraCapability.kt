package com.sitecam.app.core.camera

import androidx.camera.core.CameraInfo
import androidx.camera.core.ZoomState

data class CameraCapability(
    val minZoomRatio: Float = 1.0f,
    val maxZoomRatio: Float = 5.0f,
    val zoomPillPresets: List<Float> = listOf(1.0f, 2.0f),
    val hasFlashUnit: Boolean = false,
    val isFrontCameraAvailable: Boolean = true,
    val isBackCameraAvailable: Boolean = true
) {
    companion object {
        fun fromZoomState(
            zoomState: ZoomState?,
            hasFlash: Boolean = false,
            hasFront: Boolean = true,
            hasBack: Boolean = true
        ): CameraCapability {
            val minZoom = (zoomState?.minZoomRatio ?: 1.0f).coerceAtLeast(0.4f)
            val maxZoom = (zoomState?.maxZoomRatio ?: 5.0f).coerceAtMost(30.0f)

            val candidatePills = mutableListOf<Float>()
            if (minZoom <= 0.65f) {
                candidatePills.add(if (minZoom <= 0.55f) 0.5f else 0.6f)
            }
            candidatePills.add(1.0f)
            if (maxZoom >= 1.9f) candidatePills.add(2.0f)
            if (maxZoom >= 2.9f) candidatePills.add(3.0f)
            if (maxZoom >= 4.9f) candidatePills.add(5.0f)
            if (maxZoom >= 9.9f) candidatePills.add(10.0f)

            val availablePills = candidatePills.filter { it in minZoom..maxZoom }.distinct()
            val finalPills = if (availablePills.isEmpty()) listOf(1.0f) else availablePills

            return CameraCapability(
                minZoomRatio = minZoom,
                maxZoomRatio = maxZoom,
                zoomPillPresets = finalPills,
                hasFlashUnit = hasFlash,
                isFrontCameraAvailable = hasFront,
                isBackCameraAvailable = hasBack
            )
        }

        fun fromCameraInfo(
            cameraInfo: CameraInfo?,
            hasFront: Boolean = true,
            hasBack: Boolean = true
        ): CameraCapability {
            return fromZoomState(
                zoomState = cameraInfo?.zoomState?.value,
                hasFlash = cameraInfo?.hasFlashUnit() ?: false,
                hasFront = hasFront,
                hasBack = hasBack
            )
        }
    }
}
