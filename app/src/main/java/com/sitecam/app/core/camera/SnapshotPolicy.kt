package com.sitecam.app.core.camera

import com.sitecam.app.core.media.PhotoQualityProfile

/** One policy for a quick single shot and every frame of a burst. */
object SnapshotPolicy {
    val shutterSpeeds = listOf(125, 250, 500, 1000)
    const val defaultShutterSpeed = 250
    const val burstIntervalMs = 120L

    fun shutterSpeed(value: Int) = value.takeIf { it in shutterSpeeds } ?: defaultShutterSpeed
    fun enabled(singleShotEnabled: Boolean, burstEnabled: Boolean) = singleShotEnabled || burstEnabled
    fun exposureNanos(value: Int) = 1_000_000_000L / shutterSpeed(value)

    // Four megapixels retain small lettering without decoding a 50MP frame.
    fun profile(quick: Boolean, selected: PhotoQualityProfile) =
        if (quick) PhotoQualityProfile.CLEAR else selected
}
