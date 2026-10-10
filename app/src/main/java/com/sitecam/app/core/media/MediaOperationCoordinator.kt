package com.sitecam.app.core.media

import com.sitecam.app.core.database.AppDatabase
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Serializes export snapshots with destructive media operations across screens. */
object MediaOperationCoordinator {
    private val mutex = Mutex()
    suspend fun <T> withExclusive(action: suspend () -> T): T = mutex.withLock { action() }

    suspend fun moveMedia(
        database: AppDatabase,
        expectedProjects: Map<Long, Long>,
        targetProjectId: Long
    ) = withExclusive {
        check(database.projectDao().getProjectById(targetProjectId) != null) { "目标工程已删除，请刷新列表" }
        for ((id, expectedProject) in expectedProjects) {
            val current = database.mediaItemDao().getMediaItemById(id)
            check(current != null && current.projectId == expectedProject) { "选中的资料已变化，请刷新后重试" }
        }
        database.mediaItemDao().moveMediaToProject(expectedProjects.keys.toList(), targetProjectId)
    }
}
