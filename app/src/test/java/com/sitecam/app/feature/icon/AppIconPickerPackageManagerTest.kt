package com.sitecam.app.feature.icon

import com.sitecam.app.normalizeLauncherIconsOnStartup
import android.content.ComponentName
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import org.robolectric.Shadows.shadowOf
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import android.content.Intent
import android.content.pm.PackageManager
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Installs declarations from the real source manifest into Robolectric's package registry.
 * Resource loading is disabled for this project, so the runner does not auto-install aliases.
 * PackageManager lookup, overrides and intent resolution remain real shadow implementations.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 34])
class AppIconPickerPackageManagerTest {
    @Test fun manifestDefaultsAndRuntimeOverridesKeepExactlyOneLauncherThroughAllChoices() {
        val context = RuntimeEnvironment.getApplication()
        val manager = context.packageManager
        installManifestAliases(context)
        AppIconChoice.entries.forEach { manager.setComponentEnabledSetting(it.component(context), PackageManager.COMPONENT_ENABLED_STATE_DEFAULT, PackageManager.DONT_KILL_APP) }
        val picker = AppIconPicker(context)
        assertEquals(setOf(AppIconChoice.D), picker.enabledChoices())
        normalizeLauncherIconsOnStartup(context)
        assertEquals(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT,
            manager.getComponentEnabledSetting(AppIconChoice.D.component(context)))
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(context.packageName)
        for (choice in AppIconChoice.entries + AppIconChoice.D) {
            assertEquals(choice, picker.select(choice))
            assertEquals(setOf(choice), picker.enabledChoices())
            // A new picker must read the system override, without remembered UI state.
            assertEquals(choice, AppIconPicker(context).selectedChoice())
            val aliases = manager.queryIntentActivities(intent, 0).map { it.activityInfo.name }.toSet()
            assertEquals(setOf(choice.component(context).className), aliases)
        }
    }

    @Test fun upgradeWithOldExplicitBAndNewDefaultDRetainsOnlyBOnStartup() {
        val context = RuntimeEnvironment.getApplication()
        val manager = context.packageManager
        installManifestAliases(context)
        AppIconChoice.entries.forEach {
            manager.setComponentEnabledSetting(it.component(context), PackageManager.COMPONENT_ENABLED_STATE_DEFAULT, PackageManager.DONT_KILL_APP)
        }
        // The previous installation explicitly enabled B; the new D alias has
        // no runtime override and therefore follows its new manifest default.
        manager.setComponentEnabledSetting(AppIconChoice.B.component(context), PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
        assertEquals(setOf(AppIconChoice.B, AppIconChoice.D), AppIconPicker(context).enabledChoices())
        normalizeLauncherIconsOnStartup(context)
        assertEquals(setOf(AppIconChoice.B), AppIconPicker(context).enabledChoices())
        assertEquals(AppIconChoice.B, AppIconPicker(context).selectedChoice())
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(context.packageName)
        assertEquals(setOf(AppIconChoice.B.component(context).className),
            manager.queryIntentActivities(intent, 0).map { it.activityInfo.name }.toSet())
        // Repeated app starts leave the restored user choice unchanged.
        normalizeLauncherIconsOnStartup(context)
        assertEquals(setOf(AppIconChoice.B), AppIconPicker(context).enabledChoices())
    }

    private fun installManifestAliases(context: android.content.Context) {
        val manifest = listOf(File("src/main/AndroidManifest.xml"), File("app/src/main/AndroidManifest.xml"))
            .firstOrNull { it.isFile } ?: error("Application source manifest not found")
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        val document = factory.newDocumentBuilder().parse(manifest)
        val androidNamespace = "http://schemas.android.com/apk/res/android"
        fun Element.attribute(name: String) = getAttributeNS(androidNamespace, name)
        fun className(value: String): String {
            val expanded = value.replace("\${applicationId}", context.packageName)
            return if (expanded.startsWith(".")) context.packageName + expanded else expanded
        }
        val manager = shadowOf(context.packageManager)
        val aliases = document.getElementsByTagName("activity-alias")
        assertEquals("Expected one launcher declaration per icon choice", AppIconChoice.entries.size, aliases.length)
        for (index in 0 until aliases.length) {
            val alias = aliases.item(index) as Element
            val component = ComponentName(context.packageName, className(alias.attribute("name")))
            manager.addActivityIfNotPresent(ComponentName(context.packageName, className(alias.attribute("targetActivity"))))
            manager.addOrUpdateActivity(ActivityInfo().apply {
                packageName = context.packageName
                name = component.className
                targetActivity = className(alias.attribute("targetActivity"))
                enabled = alias.attribute("enabled") != "false"
                exported = alias.attribute("exported") == "true"
                applicationInfo = ApplicationInfo(context.applicationInfo).apply { enabled = true }
            })
            manager.clearIntentFilterForActivity(component)
            val filters = alias.getElementsByTagName("intent-filter")
            for (filterIndex in 0 until filters.length) {
                val declaration = filters.item(filterIndex) as Element
                val filter = IntentFilter()
                val actions = declaration.getElementsByTagName("action")
                for (actionIndex in 0 until actions.length) filter.addAction((actions.item(actionIndex) as Element).attribute("name"))
                val categories = declaration.getElementsByTagName("category")
                for (categoryIndex in 0 until categories.length) filter.addCategory((categories.item(categoryIndex) as Element).attribute("name"))
                manager.addIntentFilterForActivity(component, filter)
            }
        }
    }

}
