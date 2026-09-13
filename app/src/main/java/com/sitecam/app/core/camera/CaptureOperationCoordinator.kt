package com.sitecam.app.core.camera

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class CaptureInProgressException : IllegalStateException("此工程正在拍摄或保存，请完成后再锁定")

/** A capture reservation lasts through recording finalization and persistence. */
class CaptureOperationCoordinator {
    private val mutex = Mutex()
    private val active = mutableSetOf<Long>()

    suspend fun tryBegin(projectId: Long, latestUnlocked: suspend () -> Boolean): Boolean = mutex.withLock {
        if (projectId in active || !latestUnlocked()) false else { active.add(projectId); true }
    }
    suspend fun finish(projectId: Long) { kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) { mutex.withLock { active.remove(projectId) } } }
    suspend fun <T> withProjectIdle(projectId: Long, action: suspend () -> T): T = mutex.withLock {
        if (projectId in active) throw CaptureInProgressException()
        action()
    }

    /** Guard global project selection while any capture is still being saved. */
    suspend fun <T> withAllProjectsIdle(action: suspend () -> T): T = mutex.withLock {
        if (active.isNotEmpty()) throw CaptureInProgressException()
        action()
    }
}
