package com.sitecam.app.feature.camera.components

import com.sitecam.app.core.camera.CameraBindingVerification
import com.sitecam.app.core.camera.CameraLensCapability
import com.sitecam.app.core.camera.CameraLensRole
import com.sitecam.app.feature.camera.CaptureMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraTopBarLensTest {

    @Test
    fun knownPhotoOnlyLensIsExcludedFromVideoMenu() {
        val photoOnly = CameraLensCapability(
            cameraId = "photo-only",
            lensFacing = androidx.camera.core.CameraSelector.LENS_FACING_BACK,
            role = CameraLensRole.MAIN,
            appAccessible = true,
            photoBindable = true,
            videoBindingVerification = CameraBindingVerification.VERIFIED,
            videoBindable = false
        )

        assertTrue(isLensSelectableInMode(photoOnly, CaptureMode.PHOTO))
        assertFalse(isLensSelectableInMode(photoOnly, CaptureMode.VIDEO))
    }

    @Test
    fun lensDetailsUseUserTermsAndDistinguishVideoAvailability() {
        val base = CameraLensCapability(
            cameraId = "lens",
            lensFacing = androidx.camera.core.CameraSelector.LENS_FACING_BACK,
            role = CameraLensRole.TELE,
            isLogicalCamera = true,
            equivalentZoomRatio = 3.2f,
            appAccessible = true,
            photoBindable = true
        )

        val unavailable = lensDetail(
            base.copy(videoBindingVerification = CameraBindingVerification.VERIFIED, videoBindable = false),
            CaptureMode.VIDEO
        )
        val unknown = lensDetail(base, CaptureMode.VIDEO)
        val available = lensDetail(
            base.copy(videoBindingVerification = CameraBindingVerification.VERIFIED, videoBindable = true),
            CaptureMode.VIDEO
        )

        assertTrue(unavailable.contains("录像不可用"))
        assertTrue(unknown.contains("录像待确认"))
        assertTrue(available.contains("可录像"))
        assertTrue(available.contains("约3.2×"))
        assertFalse(available.contains("CameraX"))
        assertFalse(available.contains("公开逻辑组"))
        assertFalse(available.contains("选择后验证拍照"))
    }
}
