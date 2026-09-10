@file:android.annotation.SuppressLint("UnsafeOptInUsageError")

package com.sitecam.app.core.di

import android.content.Context
import com.sitecam.app.core.camera.CameraManager
import com.sitecam.app.core.camera.OrientationManager
import com.sitecam.app.core.common.AppDispatchers
import com.sitecam.app.core.database.AppDatabase
import com.sitecam.app.core.export.ProjectExportEngine
import com.sitecam.app.core.location.LocationTracker
import com.sitecam.app.core.location.ReverseGeocoder
import com.sitecam.app.core.media.MediaStoreManager
import com.sitecam.app.core.media.MediaDeletionCoordinator
import com.sitecam.app.core.media.VideoWatermarkTranscoder
import com.sitecam.app.core.preferences.AppSettingsDataStore

class AppContainer(val context: Context) {

    val appContext: Context = context.applicationContext

    val dispatchers: AppDispatchers by lazy {
        AppDispatchers()
    }

    val database: AppDatabase by lazy {
        AppDatabase.getInstance(appContext)
    }

    val watermarkTemplateMutations by lazy {
        com.sitecam.app.core.database.repository.WatermarkTemplateMutations(database.watermarkDao())
    }

    val settingsDataStore: AppSettingsDataStore by lazy {
        AppSettingsDataStore(appContext)
    }

    val mediaStoreManager: MediaStoreManager by lazy {
        MediaStoreManager(appContext)
    }

    val mediaDeletionCoordinator: MediaDeletionCoordinator by lazy {
        MediaDeletionCoordinator(database, mediaStoreManager)
    }

    val videoWatermarkTranscoder: VideoWatermarkTranscoder by lazy {
        VideoWatermarkTranscoder(appContext)
    }

    val locationTracker: LocationTracker by lazy {
        LocationTracker(appContext)
    }

    val reverseGeocoder: ReverseGeocoder by lazy {
        ReverseGeocoder(appContext)
    }

    val orientationManager: OrientationManager by lazy {
        OrientationManager(appContext)
    }

    val captureOperationCoordinator = com.sitecam.app.core.camera.CaptureOperationCoordinator()

    val cameraManager: CameraManager by lazy {
        CameraManager(appContext)
    }

    val exportEngine: ProjectExportEngine by lazy {
        ProjectExportEngine(appContext, database)
    }
}
