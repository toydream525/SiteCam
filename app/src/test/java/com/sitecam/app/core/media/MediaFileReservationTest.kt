package com.sitecam.app.core.media

import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class MediaFileReservationTest {
    @get:Rule val temporary = TemporaryFolder()
    @Test fun sameNameNeverOverwritesExistingPhoto() {
        val directory = temporary.newFolder()
        File(directory, "photo.jpg").writeText("historical photo")
        val reserved = createUniqueMediaFile(directory, "photo.jpg")
        assertEquals("photo (1).jpg", reserved.name)
        reserved.writeText("new photo")
        assertEquals("historical photo", File(directory, "photo.jpg").readText())
        assertEquals("photo (2).jpg", createUniqueMediaFile(directory, "photo.jpg").name)
    }
}
