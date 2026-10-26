package com.sitecam.app.core.camera

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Bound memory while overlapping the camera with watermark rendering and disk IO. */
suspend fun <T> captureAndSaveFrames(
    multiple: Boolean,
    shouldCapture: () -> Boolean,
    capture: suspend () -> T,
    save: suspend (T) -> Unit,
    discard: (T) -> Unit,
    intervalMs: Long = SnapshotPolicy.burstIntervalMs
): Int {
    val frames = Channel<T>(1, onUndeliveredElement = discard)
    var saved = 0
    try {
        coroutineScope {
            var failure: Exception? = null
            launch {
                try {
                    do {
                        if (!shouldCapture()) break
                        frames.send(capture())
                        if (multiple) delay(intervalMs)
                    } while (multiple)
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (error: Exception) { failure = error }
                finally { frames.close() }
            }
            for (frame in frames) { save(frame); saved++ }
            failure?.let { throw it }
        }
    } finally { frames.cancel() }
    return saved
}
