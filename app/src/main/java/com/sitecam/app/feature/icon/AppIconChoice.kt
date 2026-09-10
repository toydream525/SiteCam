package com.sitecam.app.feature.icon

import android.content.ComponentName
import android.content.Context

/** The four launcher aliases exposed in the manifest. */
enum class AppIconChoice(
    val label: String,
    val description: String,
    private val aliasSimpleName: String
) {
    A("A · 蓝图镜头", "深蓝白取景框与工程线条", "MainActivityIconA"),
    B("B · 工程印记", "石墨底色与琥珀记录卡", "MainActivityIconB"),
    C("C · 现场坐标", "青绿定位相机与现场坐标", "MainActivityIconC"),
    D("D · 工程现场", "工程黄安全帽与深蓝工业相机", "MainActivityIconD");

    fun component(context: Context): ComponentName =
        ComponentName(context.packageName, "${context.packageName}.$aliasSimpleName")
}

object AppIconChoiceResolver {
    /**
     * Resolves the system-reported enabled aliases to one choice. A broken
     * or partially-applied state falls back to D for display; AppIconPicker
     * validates and repairs state during an attempted switch.
     */
    fun resolve(enabled: Set<AppIconChoice>): AppIconChoice =
        AppIconChoice.entries.firstOrNull { it in enabled } ?: AppIconChoice.D
}
