package com.sitecam.app.core.media

import android.content.pm.ProviderInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.sitecam.app.core.watermark.model.WatermarkData
import com.sitecam.app.core.watermark.renderer.WatermarkBitmapRenderer
import com.sitecam.app.feature.annotation.PhotoAnnotationViewModelIntegrationTest.ImageProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowContentResolver
import java.io.ByteArrayOutputStream
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PhotoQualitySaveTest {
    @Test fun fourProfilesSaveRealJpegsAtExpectedDimensionsAndQuality() = runBlocking<Unit> {
        val context=RuntimeEnvironment.getApplication()
        val directory=File(context.cacheDir,"four-quality-save").apply { mkdirs() }
        val provider=ImageProvider(directory)
        provider.attachInfo(context,ProviderInfo().apply { authority="media"; exported=true; grantUriPermissions=true })
        ShadowContentResolver.registerProviderInternal("media",provider)
        val cases=listOf(Triple(PhotoQualityProfile.SMALL,1280 to 960,65),
            Triple(PhotoQualityProfile.STANDARD,1920 to 1440,75),
            Triple(PhotoQualityProfile.CLEAR,2560 to 1920,85),
            Triple(PhotoQualityProfile.ORIGINAL,4032 to 3024,95))
        for((profile,dimensions,quality) in cases) {
            val data=WatermarkData(projectName="画质测试",captureTimestamp=1700000000000)
            val rendered=WatermarkBitmapRenderer.renderWatermarkOnBitmap(
                Bitmap.createBitmap(4032,3024,Bitmap.Config.ARGB_8888),data,qualityProfile=profile)
            val result=MediaStoreManager(context).savePhotoToMediaStore(rendered,"${profile.name}.jpg","画质测试",data,
                quality=profile.jpegQuality,saveToSystemGallery=true)
            assertEquals(dimensions.first,result.width);assertEquals(dimensions.second,result.height)
            val bytes=context.contentResolver.openInputStream(result.uri)!!.use { it.readBytes() }
            val bounds=BitmapFactory.Options().apply { inJustDecodeBounds=true }
            BitmapFactory.decodeByteArray(bytes,0,bytes.size,bounds)
            assertEquals(dimensions.first,bounds.outWidth);assertEquals(dimensions.second,bounds.outHeight)
            val reference=ByteArrayOutputStream().also { assertTrue(rendered.compress(Bitmap.CompressFormat.JPEG,quality,it)) }.toByteArray()
            // EXIF differs, but JPEG quantization tables must match the independently specified quality.
            assertArrayEquals(tables(reference),tables(bytes))
            rendered.recycle()
        }
        directory.deleteRecursively()
    }

    private fun tables(jpeg: ByteArray): ByteArray {
        val result=ByteArrayOutputStream()
        var offset=2
        while(offset+3<jpeg.size) {
            require((jpeg[offset].toInt() and 255)==255)
            val marker=jpeg[offset+1].toInt() and 255
            if(marker==0xda || marker==0xd9) break
            val length=((jpeg[offset+2].toInt() and 255) shl 8) or (jpeg[offset+3].toInt() and 255)
            if(marker==0xdb) result.write(jpeg,offset+4,length-2)
            offset+=length+2
        }
        return result.toByteArray().also { assertTrue("real JPEG quantization tables",it.isNotEmpty()) }
    }
}
