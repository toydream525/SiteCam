package com.sitecam.app.core.watermark

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.media3.common.util.UnstableApi
import com.sitecam.app.core.media.VideoWatermarkTranscoder
import com.sitecam.app.core.watermark.engine.WatermarkLayoutEngine
import com.sitecam.app.core.watermark.engine.watermarkPaint
import com.sitecam.app.core.watermark.model.*
import com.sitecam.app.core.watermark.renderer.WatermarkBitmapRenderer
import com.sitecam.app.core.watermark.renderer.WatermarkCanvasPainter
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@UnstableApi
class UnifiedWatermarkRenderingTest {
    private val sample = WatermarkData(projectName="示例工程 · 东区道路改造",categoryName="市政道路",
        captureTimestamp=1700000000000,latitude=31.230416,longitude=121.473701,
        addressText="示例市示例区建设路 88 号东侧施工区域",userName="示例记录员",
        customFields=listOf(WatermarkFieldItem("part","施工部位","道路基层 · 第三区段"),
            WatermarkFieldItem("note","现场备注","现场影像留档")))

    private fun assertComplete(data: WatermarkData, width: Int, height: Int) {
        val layout=WatermarkLayoutEngine.calculateLayout(width.toFloat(),height.toFloat(),data)
        assertTrue(layout.cardRect.left>=0); assertTrue(layout.cardRect.top>=-.01f)
        assertTrue(layout.cardRect.right<=width+.01f); assertTrue(layout.cardRect.bottom<=height+.01f)
        for(line in layout.lines) {
            val vp=watermarkPaint(line.textSize,line.textColor,line.isBold)
            val lp=watermarkPaint(line.textSize,line.labelColor,true)
            assertTrue("top ${data.styleType}",line.y+minOf(vp.fontMetrics.top,lp.fontMetrics.top)>=layout.cardRect.top-.1f)
            assertTrue("bottom ${data.styleType}",line.y+maxOf(vp.fontMetrics.bottom,lp.fontMetrics.bottom)<=layout.cardRect.bottom+.1f)
            assertTrue("right ${data.styleType}",line.x+lp.measureText(line.label)+vp.measureText(line.value)<=layout.cardRect.right+.1f)
        }
        val expected=BuiltInWatermarkFieldKeys.all.filter { it in data.enabledSystemFields }
            .mapNotNull { key -> data.builtInValue(key)?.takeIf { it.isNotBlank() }?.let { key to it } } +
            data.customFields.filter { it.isEnabled && it.value.isNotBlank() }.map { it.key to it.value }
        for((key,value) in expected) assertEquals("all text $key ${data.styleType}",value,
            layout.lines.filter { it.fieldKey==key && it.contentRole=="value" }.joinToString(""){it.value})
        for(field in data.customFields.filter { it.isEnabled && it.value.isNotBlank() }) {
            val actual=layout.lines.filter { it.fieldKey==field.key }.joinToString("") {
                if(it.contentRole=="label") it.value else it.label.removeSuffix(": ")
            }
            assertEquals("full custom label",field.label,actual)
        }
    }

    @Test fun completeFieldsAndRealFontBoundsAcrossAvailableStyles() {
        val long=sample.copy(fontSizeScale=2.2f,addressText="示例施工区域长地址。".repeat(50),
            customFields=(0..11).map { WatermarkFieldItem("extra$it","完整自定义字段$it".repeat(4),"记录内容$it。".repeat(35)) })
        for(style in WatermarkStyleCatalog.styles) for((w,h) in listOf(1280 to 960,960 to 1280,1920 to 1080,1080 to 1920,2560 to 1920,1920 to 2560,4000 to 3000,3000 to 4000,4032 to 3024,3024 to 4032,240 to 180)) {
            assertComplete(sample.copy(styleType=style.id),w,h)
            assertComplete(long.copy(styleType=style.id),w,h)
        }
    }

    @Test fun photoAndVideoUseIdenticalPixelsAndWriteReviewImages() = runBlocking {
        val dir=File(System.getProperty("java.io.tmpdir"),"sitecam-watermark-previews").apply { mkdirs() }
        for(style in WatermarkStyleCatalog.styles) for(long in listOf(false,true)) {
            val data=sample.copy(styleType=style.id,fontSizeScale=if(long)2.2f else 1f,
                addressText=if(long)sample.addressText.repeat(12) else sample.addressText,
                customFields=if(long)(0..7).map { WatermarkFieldItem("extra$it","自定义记录$it","示例现场完整内容。".repeat(12)) } else sample.customFields)
            val preview=Bitmap.createBitmap(1280,960,Bitmap.Config.ARGB_8888)
            WatermarkCanvasPainter.draw(Canvas(preview),WatermarkLayoutEngine.calculateLayout(1280f,960f,data))
            val photo=WatermarkBitmapRenderer.renderWatermarkOnBitmap(Bitmap.createBitmap(1280,960,Bitmap.Config.ARGB_8888),data)
            val video=VideoWatermarkTranscoder.renderDisplayOverlay(1280,960,0,data)
            assertTrue("photo ${style.id}",preview.sameAs(photo))
            assertTrue("video ${style.id}",preview.sameAs(video))
            File(dir,"${style.id}-${if(long)"long" else "normal"}.png").outputStream().use { preview.compress(Bitmap.CompressFormat.PNG,100,it) }
            preview.recycle();photo.recycle();video.recycle()
        }
    }

    @Test fun rotatedVideoOverlayUsesPresentationGeometry() {
        for(rotation in listOf(90,270)) for(style in WatermarkStyleCatalog.styles) {
            val data=sample.copy(styleType=style.id)
            val video=VideoWatermarkTranscoder.renderDisplayOverlay(1920,1080,rotation,data)
            assertEquals(1080,video.width);assertEquals(1920,video.height)
            val expected=Bitmap.createBitmap(1080,1920,Bitmap.Config.ARGB_8888)
            WatermarkCanvasPainter.draw(Canvas(expected),WatermarkLayoutEngine.calculateLayout(1080f,1920f,data))
            assertTrue(expected.sameAs(video));expected.recycle();video.recycle()
        }
    }

    @Test fun retiredSitePhotoSnapshotPreservesDataAndRendersAsMinimal() {
        val original=sample.copy(styleType="SITE_PHOTO",fontSizeScale=1.4f,opacity=.7f,position="TOP_RIGHT")
        val restored=requireNotNull(WatermarkSnapshotCodec.decode(WatermarkSnapshotCodec.encode(original)))
        assertEquals(original,restored)
        assertFalse(WatermarkStyleCatalog.styles.any { it.id=="SITE_PHOTO" })
        assertEquals("MINIMAL",WatermarkStyleCatalog.resolve(restored.styleType).id)
        val a=Bitmap.createBitmap(1280,960,Bitmap.Config.ARGB_8888)
        val b=Bitmap.createBitmap(1280,960,Bitmap.Config.ARGB_8888)
        WatermarkCanvasPainter.draw(Canvas(a),WatermarkLayoutEngine.calculateLayout(1280f,960f,restored))
        WatermarkCanvasPainter.draw(Canvas(b),WatermarkLayoutEngine.calculateLayout(1280f,960f,restored.copy(styleType="MINIMAL")))
        assertTrue(a.sameAs(b));assertEquals(original,restored)
        a.recycle();b.recycle()
    }

    @Test fun unknownSnapshotStyleFallsBackToClassicWithoutChangingData() {
        val unknown=sample.copy(styleType="OLDER_UNKNOWN_STYLE")
        val a=Bitmap.createBitmap(1280,960,Bitmap.Config.ARGB_8888)
        val b=Bitmap.createBitmap(1280,960,Bitmap.Config.ARGB_8888)
        WatermarkCanvasPainter.draw(Canvas(a),WatermarkLayoutEngine.calculateLayout(1280f,960f,unknown))
        WatermarkCanvasPainter.draw(Canvas(b),WatermarkLayoutEngine.calculateLayout(1280f,960f,sample))
        assertTrue(a.sameAs(b));assertEquals("OLDER_UNKNOWN_STYLE",unknown.styleType)
        a.recycle();b.recycle()
    }
}
