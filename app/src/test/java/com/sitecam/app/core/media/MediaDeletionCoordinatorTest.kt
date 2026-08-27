package com.sitecam.app.core.media

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaDeletionCoordinatorTest {
    @Test
    fun doesNotRestoreAnnotationAfterPhysicalProductWasDeleted() {
        assertFalse(
            canRestoreAnnotationPointerSafely(
                annotationWasRemoved = true,
                physicalDeleteOutcome = MediaDeleteOutcome.DELETED
            )
        )
        assertFalse(
            canRestoreAnnotationPointerSafely(
                annotationWasRemoved = true,
                physicalDeleteOutcome = MediaDeleteOutcome.ALREADY_MISSING
            )
        )
    }

    @Test
    fun onlyRestoresWhenPhysicalDeleteWasNotAttempted() {
        assertTrue(
            canRestoreAnnotationPointerSafely(
                annotationWasRemoved = true,
                physicalDeleteOutcome = null
            )
        )
        assertFalse(
            canRestoreAnnotationPointerSafely(
                annotationWasRemoved = false,
                physicalDeleteOutcome = null
            )
        )
    }
}
