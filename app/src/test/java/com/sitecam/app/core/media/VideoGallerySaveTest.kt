package com.sitecam.app.core.media

import android.content.ContentProvider
import android.content.ContentValues
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.database.MatrixCursor
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import com.sitecam.app.core.watermark.model.WatermarkData
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver
import org.robolectric.shadows.ShadowMediaMetadataRetriever

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class VideoGallerySaveTest {
    private class GalleryProvider(private val directory: File) : ContentProvider() {
        var path = ""
        var name = ""
        var published = false
        val output = File(directory, "gallery-output")
        override fun onCreate() = true
        override fun insert(uri: Uri, values: ContentValues?): Uri {
            val columns = requireNotNull(values)
            path = columns.getAsString(MediaStore.MediaColumns.RELATIVE_PATH)
            name = columns.getAsString(MediaStore.MediaColumns.DISPLAY_NAME)
            val expected = if (uri == MediaStore.Video.Media.EXTERNAL_CONTENT_URI) "Movies/" else "Pictures/"
            require(path.startsWith(expected)) { "Primary directory not allowed for $uri: $path" }
            assertEquals(1, columns.getAsInteger(MediaStore.MediaColumns.IS_PENDING))
            return Uri.withAppendedPath(uri, "1")
        }
        override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor = ParcelFileDescriptor.open(
            output, ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_READ_WRITE or ParcelFileDescriptor.MODE_TRUNCATE)
        override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<out String>?): Int {
            published = values?.getAsInteger(MediaStore.MediaColumns.IS_PENDING) == 0
            return 1
        }
        override fun query(uri: Uri, projection: Array<out String>?, selection: String?, args: Array<out String>?, sort: String?): Cursor =
            MatrixCursor(arrayOf(MediaStore.MediaColumns.DISPLAY_NAME)).apply { addRow(arrayOf(name)) }
        override fun getType(uri: Uri) = if (uri.path.orEmpty().contains("video")) "video/mp4" else "image/jpeg"
        override fun delete(uri: Uri, selection: String?, args: Array<out String>?) = 1
    }

    @Test fun android10VideoPublishesInMoviesAndPhotosStayInPictures() = runBlocking<Unit> {
        val context = RuntimeEnvironment.getApplication()
        val directory = File(context.cacheDir, "video-gallery-regression").apply { mkdirs() }
        val provider = GalleryProvider(directory)
        provider.attachInfo(context, ProviderInfo().apply { authority = "media"; exported = true })
        ShadowContentResolver.registerProviderInternal("media", provider)
        val source = File(directory, "recording.mp4")
        val videoBytes = byteArrayOf(1, 2, 3, 4, 5)
        source.writeBytes(videoBytes)
        ShadowMediaMetadataRetriever.addMetadata(source.absolutePath, MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH, "1280")
        ShadowMediaMetadataRetriever.addMetadata(source.absolutePath, MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT, "720")
        ShadowMediaMetadataRetriever.addMetadata(source.absolutePath, MediaMetadataRetriever.METADATA_KEY_DURATION, "5000")
        val manager = MediaStoreManager(context)
        val bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
        try {
            val result = manager.saveVideoToMediaStore(source, "现场.mp4", "测试工程", 1700000000000, true)
            assertEquals("Movies/SiteCam/测试工程", provider.path)
            assertEquals(provider.path, result.filePath)
            assertTrue(provider.published)
            assertArrayEquals(videoBytes, provider.output.readBytes())
            assertTrue(source.exists())

            provider.published = false
            manager.savePhotoToMediaStore(bitmap, "现场.jpg", "测试工程", WatermarkData(), saveToSystemGallery = true)
            assertEquals("Pictures/SiteCam/测试工程", provider.path)
            assertTrue(provider.published)
        } finally {
            bitmap.recycle()
            directory.deleteRecursively()
        }
    }
}
