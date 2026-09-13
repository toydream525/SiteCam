package com.sitecam.app.core.camera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import io.mockk.every
import io.mockk.mockk
import androidx.camera.core.ZoomState

class CameraCapabilityTest {

    @Test
    fun testDefaultCameraCapability() {
        val capability = CameraCapability()
        assertEquals(1.0f, capability.minZoomRatio, 0.001f)
        assertEquals(5.0f, capability.maxZoomRatio, 0.001f)
        assertTrue(capability.zoomPillPresets.isNotEmpty())
    }

    @Test
    fun testCameraCapabilityPillPresets() {
        val capability = CameraCapability(
            minZoomRatio = 0.5f,
            maxZoomRatio = 10.0f,
            zoomPillPresets = listOf(0.5f, 1.0f, 2.0f, 5.0f, 10.0f)
        )
        assertEquals(5, capability.zoomPillPresets.size)
        assertTrue(capability.zoomPillPresets.contains(0.5f))
        assertTrue(capability.zoomPillPresets.contains(1.0f))
    }

    @Test
    fun fromZoomStateKeepsActualWideLowerBound() {
        val state = mockk<ZoomState>()
        every { state.minZoomRatio } returns 0.54f
        every { state.maxZoomRatio } returns 8.0f

        val capability = CameraCapability.fromZoomState(state)

        assertEquals(0.54f, capability.minZoomRatio, 0.0001f)
        assertTrue("the wide pill must remain bindable", capability.zoomPillPresets.contains(0.54f))
    }

    @Test
    fun independentTeleSessionUsesMainReferenceLabelsAndNativeMapping() {
        val state = mockk<ZoomState>()
        every { state.minZoomRatio } returns 1.0f
        every { state.maxZoomRatio } returns 4.0f

        val capability = CameraCapability.fromZoomState(
            zoomState = state,
            activeLensEquivalentZoomRatio = 3.2f
        )

        assertEquals(3.2f, capability.minZoomRatio, 0.0001f)
        assertTrue("tele native 1x must be visible as its main reference", capability.zoomPillPresets.contains(3.2f))
        assertTrue("the tele session must not expose a false main 1x", !capability.zoomPillPresets.contains(1.0f))
        assertEquals(1.0f, CameraZoomMapping.sessionRatioFromDisplay(3.2f, 3.2f), 0.0001f)
        assertEquals(1.5625f, CameraZoomMapping.sessionRatioFromDisplay(5.0f, 3.2f), 0.0001f)
        assertEquals(5.0f, CameraZoomMapping.displayRatioFromSession(1.5625f, 3.2f), 0.0001f)
    }

    @Test
    fun invalidEquivalentRatioFallsBackToOne() {
        assertEquals(1.0f, CameraZoomMapping.sanitizeEquivalentRatio(Float.NaN), 0.0001f)
        assertEquals(1.0f, CameraZoomMapping.sanitizeEquivalentRatio(Float.POSITIVE_INFINITY), 0.0001f)
        assertEquals(1.0f, CameraZoomMapping.sanitizeEquivalentRatio(0f), 0.0001f)
    }

    @Test
    fun logicalGroupKeepsNativeOneXWhileIndependentTeleUsesItsReference() {
        val logicalWideMain = CameraLensCapability(
            cameraId = "0",
            lensFacing = androidx.camera.core.CameraSelector.LENS_FACING_BACK,
            isLogicalCamera = true,
            equivalentZoomRatio = 3.2f
        )
        val independentTele = logicalWideMain.copy(
            cameraId = "4",
            isLogicalCamera = false,
            role = CameraLensRole.TELE
        )

        assertEquals(1.0f, CameraZoomMapping.referenceRatioForActiveLens(logicalWideMain), 0.0001f)
        assertEquals(3.2f, CameraZoomMapping.referenceRatioForActiveLens(independentTele), 0.0001f)
    }

    @Test
    fun lensRoleUsesFieldOfViewAndEquivalentRatioUsesSensorAwareGeometry() {
        assertEquals(CameraLensRole.WIDE, CameraLensRoleClassifier.roleForHorizontalFov(80f))
        assertEquals(CameraLensRole.MAIN, CameraLensRoleClassifier.roleForHorizontalFov(55f))
        assertEquals(CameraLensRole.TELE, CameraLensRoleClassifier.roleForHorizontalFov(30f))
        assertEquals(CameraLensRole.UNKNOWN, CameraLensRoleClassifier.roleForHorizontalFov(null))

        val ratio = CameraLensRoleClassifier.equivalentZoomRatio(80f, 40f)
        assertTrue("a narrower FOV must produce a greater-than-one main-camera equivalent", ratio!! > 1f)
        assertEquals(1f, CameraLensRoleClassifier.equivalentZoomRatio(55f, 55f)!!, 0.0001f)
    }

    @Test
    fun publicLensStateSeparatesHardwarePresenceFromBindableSelection() {
        val hardwareOnly = CameraLensCapability(
            cameraId = "4",
            lensFacing = androidx.camera.core.CameraSelector.LENS_FACING_BACK,
            role = CameraLensRole.TELE,
            hardwarePresent = true,
            appAccessible = false,
            photoBindable = false,
            videoBindable = false
        )
        assertTrue(hardwareOnly.hardwarePresent)
        assertTrue(!hardwareOnly.appAccessible)
        assertTrue(!hardwareOnly.photoBindable)
        assertEquals(CameraBindingVerification.UNVERIFIED, hardwareOnly.photoBindingVerification)
    }

    @Test
    fun preferredPublicLensChoosesSuitableLogicalGroupBeforeIndependentCamera() {
        val logicalMain = CameraLensCapability(
            cameraId = "logical-main",
            lensFacing = androidx.camera.core.CameraSelector.LENS_FACING_BACK,
            role = CameraLensRole.MAIN,
            isLogicalCamera = true,
            appAccessible = true,
            photoBindable = true
        )
        val independentTele = CameraLensCapability(
            cameraId = "tele",
            lensFacing = androidx.camera.core.CameraSelector.LENS_FACING_BACK,
            role = CameraLensRole.TELE,
            equivalentZoomRatio = 3.2f,
            appAccessible = true,
            photoBindable = true
        )

        assertEquals(
            logicalMain,
            CameraCapabilityInspector.preferredPublicLens(
                lenses = listOf(independentTele, logicalMain),
                lensFacing = androidx.camera.core.CameraSelector.LENS_FACING_BACK,
                includeVideo = true
            )
        )
    }

    @Test
    fun preferredPublicLensSkipsKnownPhotoOnlyCameraForVideo() {
        val photoOnlyLogical = CameraLensCapability(
            cameraId = "logical-photo",
            lensFacing = androidx.camera.core.CameraSelector.LENS_FACING_BACK,
            role = CameraLensRole.MAIN,
            isLogicalCamera = true,
            appAccessible = true,
            photoBindable = true,
            videoBindable = false,
            videoBindingVerification = CameraBindingVerification.VERIFIED
        )
        val videoCandidate = CameraLensCapability(
            cameraId = "video-candidate",
            lensFacing = androidx.camera.core.CameraSelector.LENS_FACING_BACK,
            role = CameraLensRole.TELE,
            appAccessible = true,
            photoBindable = true
        )

        assertEquals(
            videoCandidate,
            CameraCapabilityInspector.preferredPublicLens(
                lenses = listOf(photoOnlyLogical, videoCandidate),
                lensFacing = androidx.camera.core.CameraSelector.LENS_FACING_BACK,
                includeVideo = true
            )
        )
    }
}
