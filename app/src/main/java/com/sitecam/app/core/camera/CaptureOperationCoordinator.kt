package com.sitecam.app.core.camera

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class CaptureInProgressException : IllegalStateException("此工程正在拍摄或保存，请完成后再锁定")

/** Saving protects the project while allowing the camera to accept another capture. */
class CaptureOperationCoordinator {
    private val mutex = Mutex()
    private val active = mutableSetOf<Long>()
    private val backgroundSaves = mutableMapOf<Long, Int>()

    suspend fun tryBegin(projectId: Long, latestUnlocked: suspend () -> Boolean): Boolean = mutex.withLock {
        if (projectId in active || !latestUnlocked()) false else { active.add(projectId); true }
    }
    suspend fun finish(projectId: Long) { kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) { mutex.withLock { active.remove(projectId) } } }
    suspend fun moveToBackground(projectId: Long) = mutex.withLock {
        check(active.remove(projectId)) { "没有正在拍摄的录像" }
        backgroundSaves[projectId] = (backgroundSaves[projectId] ?: 0) + 1
    }
    suspend fun finishBackground(projectId: Long) = kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
        mutex.withLock {
            val remaining = (backgroundSaves[projectId] ?: 0) - 1
            if (remaining > 0) backgroundSaves[projectId] = remaining else backgroundSaves.remove(projectId)
        }
    }
    suspend fun <T> withProjectIdle(projectId: Long, action: suspend () -> T): T = mutex.withLock {
        if (projectId in active || projectId in backgroundSaves) throw CaptureInProgressException()
        action()
    }

    /** Guard global project selection while any capture is still being saved. */
    suspend fun <T> withAllProjectsIdle(action: suspend () -> T): T = mutex.withLock {
        if (active.isNotEmpty() || backgroundSaves.isNotEmpty()) throw CaptureInProgressException()
        action()
    }
}
