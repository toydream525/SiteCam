package com.sitecam.app.feature.permissions

import android.Manifest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30, 34])
class PermissionAccessTest {
    @Test fun actualPermissionReadAcceptsCoarseAndRechecksRevocation() {
        val context = RuntimeEnvironment.getApplication()
        val application = shadowOf(context)
        application.denyPermissions(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        assertFalse(PermissionAccess.read(context).allGranted)
        application.grantPermissions(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO, Manifest.permission.ACCESS_COARSE_LOCATION)
        assertTrue(PermissionAccess.read(context).allGranted)
        assertTrue(PermissionAccess.read(context).permissionsToRequest().isEmpty())
        application.denyPermissions(Manifest.permission.ACCESS_COARSE_LOCATION)
        application.grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        assertTrue("Fine alone must satisfy vendor permission behavior", PermissionAccess.read(context).allGranted)
        application.denyPermissions(Manifest.permission.CAMERA)
        assertEquals(listOf(Manifest.permission.CAMERA), PermissionAccess.read(context).permissionsToRequest())
    }
}
