package com.sitecam.app

import android.app.Application
import android.content.Context
import android.util.Log
import com.sitecam.app.feature.icon.AppIconPicker
import com.sitecam.app.core.di.AppContainer

class SiteCamApplication : Application() {

    lateinit var appContainer: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        appContainer = AppContainer(this)
        normalizeLauncherIconsOnStartup(this)
    }
}

/** Keep a previously selected alias when an upgrade introduces a new default icon. */
internal fun normalizeLauncherIconsOnStartup(context: Context) {
    try {
        val picker = AppIconPicker(context)
        if (picker.enabledChoices().size != 1) {
            picker.select(picker.selectedChoice())
        }
    } catch (error: Exception) {
        Log.w("SiteCamApplication", "Unable to normalize launcher icon state", error)
    }
}
