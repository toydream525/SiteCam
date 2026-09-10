package com.sitecam.app.feature.icon

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.AdaptiveIconDrawable
import java.io.File
import android.content.Intent
import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** On-device regression for alias declarations differing from component overrides. */
@RunWith(AndroidJUnit4::class)
class AppIconPickerPlatformTest {
    @Test fun selectingEachIconLeavesExactlyOneRealLauncherEntry() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val manager = context.packageManager
        val previous = AppIconChoice.entries.associateWith { manager.getComponentEnabledSetting(it.component(context)) }
        val output = File(context.cacheDir, "icon-audit").apply { mkdirs() }
        val picker = AppIconPicker(context)
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(context.packageName)
        try {
            for (choice in AppIconChoice.entries) {
                assertEquals(choice, picker.select(choice))
                val icon = manager.getActivityIcon(choice.component(context))
                assertTrue("${choice.name} must load its adaptive icon resource", icon is AdaptiveIconDrawable)
                for (size in listOf(192, 48)) {
                    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
                    try {
                        icon.setBounds(0, 0, size, size)
                        icon.draw(Canvas(bitmap))
                        assertEquals(size, bitmap.width)
                        assertEquals(size, bitmap.height)
                        val pixels = IntArray(size * size)
                        bitmap.getPixels(pixels, 0, size, 0, 0, size, size)
                        assertTrue("${choice.name} at $size pixels must render visible pixels", pixels.any { Color.alpha(it) > 0 })
                        File(output, "${choice.name}-$size.png").outputStream().use {
                            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
                        }
                    } finally { bitmap.recycle() }
                }
                assertEquals(setOf(choice), picker.enabledChoices())
                assertEquals(choice, AppIconPicker(context).selectedChoice())
                assertEquals(setOf(choice.component(context).className), manager.queryIntentActivities(intent, 0).map { it.activityInfo.name }.toSet())
                if (choice != AppIconChoice.D) {
                    assertEquals(PackageManager.COMPONENT_ENABLED_STATE_ENABLED, manager.getComponentEnabledSetting(choice.component(context)))
                }
            }
        } finally {
            // Enable the former entry before restoring defaults/disabled aliases.
            previous.filterValues { it == PackageManager.COMPONENT_ENABLED_STATE_ENABLED }.keys.forEach {
                manager.setComponentEnabledSetting(it.component(context), PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
            }
            previous.forEach { (choice, state) -> manager.setComponentEnabledSetting(choice.component(context), state, PackageManager.DONT_KILL_APP) }
        }
    }
}
