package com.sitecam.app

import android.app.Application
import com.sitecam.app.core.di.AppContainer

class SiteCamApplication : Application() {

    lateinit var appContainer: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        appContainer = AppContainer(this)
    }
}
