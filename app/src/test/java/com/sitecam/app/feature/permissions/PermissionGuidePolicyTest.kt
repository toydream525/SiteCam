package com.sitecam.app.feature.permissions

import android.Manifest
import com.sitecam.app.feature.onboarding.OnboardingState
import org.junit.Assert.*
import org.junit.Test

class PermissionGuidePolicyTest {
    @Test fun waitsForPersistentStateThenShowsFeatureGuideBeforePermissions() {
        assertEquals(StartupGate.LOADING, resolveStartupGate(null, CapturePermissions()))
        assertEquals(StartupGate.LOADING, resolveStartupGate(OnboardingState(), null))
        assertEquals(StartupGate.FEATURE_GUIDE, resolveStartupGate(OnboardingState(), CapturePermissions()))
        assertEquals(StartupGate.PERMISSION_GUIDE, resolveStartupGate(OnboardingState(hasDismissedGuide = true), CapturePermissions()))
    }
    @Test fun fullyGrantedPermissionsSkipGuideIncludingFineOnlyVendorBehavior() {
        val fineOnly = CapturePermissions(camera = true, fineLocation = true, microphone = true)
        assertTrue(fineOnly.allGranted)
        assertTrue(fineOnly.permissionsToRequest().isEmpty())
        assertEquals(StartupGate.APP, resolveStartupGate(OnboardingState(hasDismissedGuide = true), fineOnly))
        assertEquals(StartupGate.FEATURE_GUIDE, resolveStartupGate(OnboardingState(), fineOnly))
    }
    @Test fun approximateLocationIsEnoughAndOnlyMissingCameraAndAudioAreRequested() {
        val approximate = CapturePermissions(coarseLocation = true)
        assertEquals(listOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO), approximate.permissionsToRequest())
        assertTrue(approximate.copy(camera = true, microphone = true).allGranted)
    }
    @Test fun initialRequestContainsOnlyForegroundCapturePermissionsAndLocationPair() {
        assertEquals(setOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), CapturePermissions().permissionsToRequest().toSet())
        assertEquals(listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
            CapturePermissions(camera = true, microphone = true).permissionsToRequest())
    }
    @Test fun skippedOrDeniedGuideDoesNotReappearOnRestartOrTutorialReplay() {
        val handled = OnboardingState(hasDismissedGuide = true, hasHandledPermissionGuide = true,
            requestedPermissions = CapturePermissions().permissionsToRequest().toSet())
        assertEquals(StartupGate.APP, resolveStartupGate(handled, CapturePermissions()))
        // Replaying feature pages does not reset either persistent completion marker.
        assertEquals(StartupGate.APP, resolveStartupGate(handled.copy(hasDismissedGuide = true), CapturePermissions(camera = true)))
    }
    @Test fun permanentlyDeniedItemsGoToSettingsInsteadOfBeingRequestedAgain() {
        val blocked = CapturePermissions(cameraNeedsSettings = true, locationNeedsSettings = true)
        assertEquals(listOf(Manifest.permission.RECORD_AUDIO), blocked.permissionsToRequest())
        assertTrue(blocked.copy(microphoneNeedsSettings = true).permissionsToRequest().isEmpty())
    }
    @Test fun completedRequestCanShowActualResultsUntilUserContinues() {
        val handled = OnboardingState(hasDismissedGuide = true, hasHandledPermissionGuide = true)
        assertEquals(StartupGate.PERMISSION_GUIDE, resolveStartupGate(handled, CapturePermissions(), keepPermissionGuideOpen = true))
        assertEquals(StartupGate.APP, resolveStartupGate(handled, CapturePermissions(), keepPermissionGuideOpen = false))
    }
}
