package com.sitecam.app.feature.projects

import com.sitecam.app.core.database.entity.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class ProjectBrowserTest {
    @Test fun namesUseChineseOrderAndIgnoreLatinCaseWithStableId() {
        val projects = listOf(ProjectEntity(id=5, name="张"), ProjectEntity(id=2, name="阿"),
            ProjectEntity(id=7, name="Alpha"), ProjectEntity(id=3, name="alpha"))
        assertEquals(listOf(3L,7L,2L,5L), sortedProjects(projects, emptyMap(), ProjectSort.NAME, true).map { it.id })
    }
    @Test fun emptyRoutesLastInBothDirectionsAndEqualRoutesKeepIdOrder() {
        val projects = listOf(ProjectEntity(id=3,name="三",routeName=""), ProjectEntity(id=2,name="二",routeName="B"), ProjectEntity(id=1,name="一",routeName="b"))
        for(ascending in listOf(true,false)) assertEquals(listOf(1L,2L,3L), sortedProjects(projects, emptyMap(), ProjectSort.ROUTE, ascending).map { it.id })
    }
    @Test fun captureStatisticsUseCaptureTimeAcrossYearsNotEditTime() {
        val a = Instant.parse("2025-12-31T12:00:00Z").toEpochMilli()
        val b = Instant.parse("2026-01-02T12:00:00Z").toEpochMilli()
        val media = listOf(MediaItemEntity(id=1,projectId=1,contentUri="",fileName="a",captureTimestamp=b,createdAt=999),
            MediaItemEntity(id=2,projectId=1,contentUri="",fileName="b",mediaType="VIDEO",captureTimestamp=a,isIssue=true))
        val stats = projectStatistics(media).getValue(1)
        assertEquals(a,stats.firstCapture); assertEquals(b,stats.lastCapture)
        assertEquals(1,stats.photos); assertEquals(1,stats.videos); assertEquals(1,stats.issues)
        assertTrue(stats.dateLabel().contains("2025-12-31")); assertTrue(stats.dateLabel().contains("2026-01-02"))
        assertEquals("尚未拍摄",ProjectStatistics().dateLabel())
    }
    @Test fun latestCaptureSortNeverUsesProjectUpdateDate() {
        val projects = listOf(ProjectEntity(id=1,name="空",updatedAt=999),ProjectEntity(id=2,name="有",updatedAt=0))
        assertEquals(listOf(2L,1L),sortedProjects(projects,mapOf(2L to ProjectStatistics(lastCapture=1)),ProjectSort.LAST_CAPTURE,false).map { it.id })
    }

    @Test fun descendingCreationSortPutsNewestIdFirstWhenTimestampsTie() {
        val timestamp = 1_000L
        val projects = listOf(
            ProjectEntity(id = 1, name = "旧", createdAt = timestamp),
            ProjectEntity(id = 2, name = "新", createdAt = timestamp)
        )
        assertEquals(listOf(2L, 1L), sortedProjects(projects, emptyMap(), ProjectSort.CREATED, false).map { it.id })
    }

    @Test fun projectUndoRestoresOnlyChangedFieldAndRejectsLaterMutation() {
        val before = ProjectEntity(id = 9, name = "现场", isArchived = false, isCaptureLocked = false)
        val afterLock = before.copy(isCaptureLocked = true)
        val undo = ProjectMutationUndo(
            projectId = before.id,
            lockedBefore = false,
            lockedAfter = true,
            message = "拍摄已锁定：现场"
        )

        val restored = undo.restore(afterLock, updatedAt = 20L)
        assertNotNull(restored)
        assertFalse(restored!!.isCaptureLocked)
        assertFalse(restored.isArchived)
        assertEquals(20L, restored.updatedAt)

        val independentlyArchived = afterLock.copy(isArchived = true)
        assertNotNull(undo.restore(independentlyArchived, updatedAt = 21L))
        val changedAgain = afterLock.copy(isCaptureLocked = false)
        assertNull(undo.restore(changedAgain, updatedAt = 22L))
    }
}
