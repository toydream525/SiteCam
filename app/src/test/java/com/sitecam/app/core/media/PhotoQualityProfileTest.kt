package com.sitecam.app.core.media

import org.junit.Assert.assertEquals
import org.junit.Test

class PhotoQualityProfileTest {
    @Test fun profilesPreserveAspectRatioAndNeverEnlargeSmallPhotos() {
        assertEquals(1920 to 1440, PhotoQualityProfile.STANDARD.targetSize(4000, 3000))
        assertEquals(1440 to 1920, PhotoQualityProfile.STANDARD.targetSize(3000, 4000))
        PhotoQualityProfile.entries.forEach { assertEquals(320 to 240, it.targetSize(320, 240)) }
        assertEquals(4032 to 3024, PhotoQualityProfile.ORIGINAL.targetSize(4032, 3024))
        assertEquals(65, PhotoQualityProfile.SMALL.jpegQuality)
        assertEquals(75, PhotoQualityProfile.STANDARD.jpegQuality)
        assertEquals(85, PhotoQualityProfile.CLEAR.jpegQuality)
        assertEquals(95, PhotoQualityProfile.ORIGINAL.jpegQuality)
        assertEquals(1280 to 960, PhotoQualityProfile.SMALL.targetSize(4032, 3024))
        assertEquals(2560 to 1920, PhotoQualityProfile.CLEAR.targetSize(4032, 3024))
    }
}
