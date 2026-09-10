package com.sitecam.app.core.media

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Serializes export snapshots with destructive media operations across screens. */
object MediaOperationCoordinator {
    private val mutex = Mutex()
    suspend fun <T> withExclusive(action: suspend () -> T): T = mutex.withLock { action() }
}
