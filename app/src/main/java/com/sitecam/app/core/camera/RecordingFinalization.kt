@file:android.annotation.SuppressLint("UnsafeOptInUsageError")

package com.sitecam.app.core.camera

import androidx.camera.video.VideoRecordEvent

/** CameraX may finish with an error while retaining all frames recorded before interruption. */
internal fun mayRetainRecordedFrames(error: Int): Boolean = when (error) {
    VideoRecordEvent.Finalize.ERROR_NONE,
    VideoRecordEvent.Finalize.ERROR_SOURCE_INACTIVE,
    VideoRecordEvent.Finalize.ERROR_FILE_SIZE_LIMIT_REACHED,
    VideoRecordEvent.Finalize.ERROR_DURATION_LIMIT_REACHED,
    VideoRecordEvent.Finalize.ERROR_INSUFFICIENT_STORAGE -> true
    else -> false
}
