package com.sitecam.app.feature.gallery

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ImportedPhotoSourceTest {
    @get:Rule val temp = TemporaryFolder()

    @Test
    fun parsesExifOriginalTimeStrictlyAndHonorsOffsetWhenPresent() {
        val oldZone = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
            assertEquals(1_767_268_800_000L, ImportedPhotoSource.parseOriginalTime("2026:01:01 12:00:00"))
            assertEquals(1_767_240_000_000L, ImportedPhotoSource.parseOriginalTime("2026:01:01 12:00:00", "+08:00"))
            assertNull(ImportedPhotoSource.parseOriginalTime(null))
            assertNull(ImportedPhotoSource.parseOriginalTime("2026:02:30 12:00:00"))
            assertNull(ImportedPhotoSource.parseOriginalTime("2026:01:01 12:00:00 trailing"))
        } finally {
            TimeZone.setDefault(oldZone)
        }
    }

    @Test
    fun orientationMatrixMapsAllEightExifOrientationsToExpectedBounds() {
        val width = 40f
        val height = 20f
        val corners = floatArrayOf(0f, 0f, width, 0f, 0f, height, width, height)
        val expected = mapOf(
            ExifInterface.ORIENTATION_NORMAL to floatArrayOf(0f,0f, 40f,0f, 0f,20f, 40f,20f),
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL to floatArrayOf(40f,0f, 0f,0f, 40f,20f, 0f,20f),
            ExifInterface.ORIENTATION_ROTATE_180 to floatArrayOf(40f,20f, 0f,20f, 40f,0f, 0f,0f),
            ExifInterface.ORIENTATION_FLIP_VERTICAL to floatArrayOf(0f,20f, 40f,20f, 0f,0f, 40f,0f),
            ExifInterface.ORIENTATION_TRANSPOSE to floatArrayOf(0f,0f, 0f,40f, 20f,0f, 20f,40f),
            ExifInterface.ORIENTATION_ROTATE_90 to floatArrayOf(20f,0f, 20f,40f, 0f,0f, 0f,40f),
            ExifInterface.ORIENTATION_TRANSVERSE to floatArrayOf(20f,40f, 20f,0f, 0f,40f, 0f,0f),
            ExifInterface.ORIENTATION_ROTATE_270 to floatArrayOf(0f,40f, 0f,0f, 20f,40f, 20f,0f)
        )

        expected.forEach { (orientation, points) ->
            val actual = corners.copyOf()
            ImportedPhotoSource.orientationMatrix(orientation).mapPoints(actual)
            val minX = (0 until actual.size step 2).minOf { actual[it] }
            val minY = (1 until actual.size step 2).minOf { actual[it] }
            for (i in actual.indices step 2) { actual[i] -= minX; actual[i + 1] -= minY }
            points.indices.forEach { index -> assertEquals("orientation=$orientation point=$index", points[index], actual[index], .001f) }
        }
    }

    @Test
    fun readsOriginalExifAndDecodesDownsampledUprightPixelsWithoutChangingSource() {
        val context = RuntimeEnvironment.getApplication() as Context
        val file = temp.newFile("portrait-source.jpg")
        val bitmap = Bitmap.createBitmap(40, 20, Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(android.graphics.Color.BLUE)
            file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it)) }
        } finally {
            bitmap.recycle()
        }
        ExifInterface(file).apply {
            setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, "2026:01:01 12:00:00")
            setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
            saveAttributes()
        }
        val sourceLength = file.length()
        val uri = Uri.fromFile(file)
        val metadata = ImportedPhotoSource.metadata(context, uri)

        assertEquals(ExifInterface.ORIENTATION_ROTATE_90, metadata.orientation)
        assertNotNull(metadata.captureTimestamp)
        val decoded = ImportedPhotoSource.decode(context, uri, maxLongEdge = 10, orientation = metadata.orientation)
        try {
            assertEquals(5, decoded.width)
            assertEquals(10, decoded.height)
            assertEquals(sourceLength, file.length())
            assertTrue(file.exists())
        } finally {
            decoded.recycle()
        }
    }
}
