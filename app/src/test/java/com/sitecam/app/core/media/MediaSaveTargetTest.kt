package com.sitecam.app.core.media

import org.junit.Assert.*
import org.junit.Test

class MediaSaveTargetTest {
    @Test fun privateDefaultNeedsNoLegacyPermission() {
        assertEquals(MediaSaveTarget.APP_PRIVATE, resolveMediaSaveTarget(false, 26, false))
        assertEquals(MediaSaveTarget.APP_PRIVATE, resolveMediaSaveTarget(false, 36, false))
    }
    @Test fun publicRequestNeverFallsBackOnDeniedPermission() {
        try { resolveMediaSaveTarget(true, 28, false); fail("Public save without permission must fail") }
        catch (_: IllegalStateException) { }
        assertEquals(MediaSaveTarget.SYSTEM_GALLERY, resolveMediaSaveTarget(true, 28, true))
        assertEquals(MediaSaveTarget.SYSTEM_GALLERY, resolveMediaSaveTarget(true, 29, false))
    }
}
