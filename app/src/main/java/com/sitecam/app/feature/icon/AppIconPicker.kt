package com.sitecam.app.feature.icon

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

class AppIconSwitchException(
    message: String,
    cause: Throwable? = null
) : IllegalStateException(message, cause)

/**
 * Switches launcher aliases without storing a second preference copy.
 * PackageManager's effective component state is the source of truth, so a
 * reboot or upgrade keeps the icon the user actually selected.
 */
class AppIconPicker(private val context: Context) {

    private val packageManager: PackageManager = context.packageManager

    fun enabledChoices(): Set<AppIconChoice> = AppIconChoice.entries
        .filter(::isEnabled)
        .toSet()

    fun selectedChoice(): AppIconChoice =
        AppIconChoiceResolver.resolve(enabledChoices())

    /**
     * Enables the requested alias and disables the other aliases. The
     * target is enabled first on API 26-32, while API 33+ updates all three
     * aliases atomically. Every mutation uses DONT_KILL_APP.
     */
    @Synchronized
    fun select(choice: AppIconChoice): AppIconChoice {
        val previous = selectedChoice()
        val previousEnabled = enabledChoices()
        if (choice in previousEnabled && previousEnabled.size == 1) return choice

        try {
            applyChoice(choice)
            val readBack = selectedChoice()
            val readBackEnabled = enabledChoices()
            check(readBack == choice && readBackEnabled == setOf(choice)) {
                "桌面图标状态未完成切换"
            }
            return readBack
        } catch (error: Throwable) {
            val restored = runCatching { restore(previous) }.isSuccess
            if (!restored) {
                runCatching { enableOnly(previous) }
            }
            if (enabledChoices().isEmpty()) {
                runCatching { enableOnly(AppIconChoice.D) }
            }
            throw AppIconSwitchException("桌面图标切换失败，已尝试恢复原选择", error)
        }
    }

    private fun isEnabled(choice: AppIconChoice): Boolean =
        runCatching {
            val component = choice.component(context)
            when (packageManager.getComponentEnabledSetting(component)) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED -> false
                PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> {
                    // ActivityInfo.enabled is the manifest default, not the runtime
                    // override. Include disabled aliases when reading that default.
                    packageManager.getActivityInfo(component, PackageManager.MATCH_DISABLED_COMPONENTS).enabled
                }
                else -> false
            }
        }.getOrDefault(false)

    private fun applyChoice(choice: AppIconChoice) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val settings = AppIconChoice.entries.map { candidate ->
                PackageManager.ComponentEnabledSetting(
                    candidate.component(context),
                    if (candidate == choice) {
                        PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                    } else {
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                    },
                    PackageManager.DONT_KILL_APP
                )
            }
            packageManager.setComponentEnabledSettings(settings)
        } else {
            // Keep one launcher entry alive while changing the selection.
            packageManager.setComponentEnabledSetting(
                choice.component(context),
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )
            AppIconChoice.entries
                .filterNot { it == choice }
                .forEach { candidate ->
                    packageManager.setComponentEnabledSetting(
                        candidate.component(context),
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        PackageManager.DONT_KILL_APP
                    )
                }
        }
    }

    private fun restore(choice: AppIconChoice) {
        applyChoice(choice)
        check(enabledChoices() == setOf(choice)) { "桌面图标原选择恢复失败" }
    }

    private fun enableOnly(choice: AppIconChoice) {
        applyChoice(choice)
        check(enabledChoices() == setOf(choice)) { "没有可用的桌面图标入口" }
    }
}
