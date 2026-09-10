package com.sitecam.app.feature.permissions

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.content.ContextCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sitecam.app.MainActivity
import com.sitecam.app.feature.onboarding.OnboardingPreferences
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Run on an emulator with CAMERA denied; this test never changes device permissions. */
@RunWith(AndroidJUnit4::class)
class HandledPermissionGuidePlatformTest {
    @Test fun handledDenialEntersCameraManagementWithoutAnotherSystemPermissionRequest() {
        assumeTrue("Emulator-only fixture", Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish" || Build.FINGERPRINT.startsWith("generic"))
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        assumeTrue("Prepare emulator with camera permission denied", ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)
        runBlocking {
            val preferences = OnboardingPreferences(context)
            preferences.markGuideDismissed()
            preferences.markPermissionsRequested(listOf(Manifest.permission.CAMERA))
            preferences.markPermissionGuideHandled()
        }
        ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use {
            val deadline = SystemClock.uptimeMillis() + 10_000
            var cameraManagementVisible = false
            while (SystemClock.uptimeMillis() < deadline && !cameraManagementVisible) {
                instrumentation.waitForIdleSync()
                val root = instrumentation.uiAutomation.rootInActiveWindow
                cameraManagementVisible = root?.hasText("管理工程") == true &&
                    (root.hasText("开启相机权限") || root.hasText("去系统设置开启相机"))
                root?.recycle()
                if (!cameraManagementVisible) SystemClock.sleep(100)
            }
            assertTrue("Camera denial must reach the app's explicit permission and management buttons, not a repeated system permission dialog", cameraManagementVisible)
            SystemClock.sleep(500)
            instrumentation.waitForIdleSync()
            val root = instrumentation.uiAutomation.rootInActiveWindow
            try { assertTrue("No delayed automatic permission request after camera mounts", root?.hasText("管理工程") == true) }
            finally { root?.recycle() }
        }
    }

    private fun AccessibilityNodeInfo.hasText(expected: String): Boolean {
        if (text?.toString() == expected) return true
        for (index in 0 until childCount) {
            val child = getChild(index) ?: continue
            try { if (child.hasText(expected)) return true } finally { child.recycle() }
        }
        return false
    }
}
