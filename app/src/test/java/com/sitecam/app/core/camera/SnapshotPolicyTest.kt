package com.sitecam.app.core.camera

import com.sitecam.app.core.media.PhotoQualityProfile
import org.junit.Assert.*
import org.junit.Test

class SnapshotPolicyTest {
    @Test fun burstAlwaysUsesSnapshotEvenWhenSingleShotSnapshotIsOff() {
        assertTrue(SnapshotPolicy.enabled(false, true))
        assertTrue(SnapshotPolicy.enabled(true, true))
        assertTrue(SnapshotPolicy.enabled(true, false))
        assertFalse(SnapshotPolicy.enabled(false, false))
    }
    @Test fun snapshotKeepsTextResolutionAndAvoidsHeavyCompression() {
        for (quality in PhotoQualityProfile.entries) {
            val snapshot = SnapshotPolicy.profile(true, quality)
            assertTrue(snapshot.maxLongEdge!! >= 1920)
            assertTrue(snapshot.jpegQuality >= 85)
            assertSame(quality, SnapshotPolicy.profile(false, quality))
        }
    }
}
