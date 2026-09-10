@file:android.annotation.SuppressLint("UnsafeOptInUsageError")

package com.sitecam.app.core.watermark

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sitecam.app.core.media.VideoWatermarkTranscoder
import com.sitecam.app.core.watermark.engine.WatermarkLayoutEngine
import com.sitecam.app.core.watermark.engine.watermarkPaint
import com.sitecam.app.core.watermark.model.WatermarkData
import com.sitecam.app.core.watermark.model.WatermarkFieldItem
import com.sitecam.app.core.watermark.model.WatermarkStyleCatalog
import com.sitecam.app.core.watermark.renderer.WatermarkBitmapRenderer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Synthetic render fixtures only: no camera, database, preferences, or shared media writes. */
@RunWith(AndroidJUnit4::class)
class WatermarkPlatformRenderAuditTest {
    @Test fun availableStylesUseRealAndroidFontsPreserveLastRowAndMatchVideoPixels(): Unit = runBlocking {
        assumeTrue("Emulator-only fixture", Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish" || Build.FINGERPRINT.startsWith("generic"))
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val output = File(context.cacheDir, "watermark-audit").apply { mkdirs() }
        assertTrue("Android image must provide Chinese glyphs", watermarkPaint(30f).hasGlyph("工"))
        val width = 1080
        val height = 1440
        for (style in WatermarkStyleCatalog.styles) {
            for (longContent in listOf(false, true)) {
                val suffix = if (longContent) "long" else "normal"
                val note = if (longContent) "末行验收观察内容完整保留，不自动填写验收结论；设备编号与施工情况由现场人员记录。".repeat(3)
                    else "末行现场备注完整保留"
                val data = WatermarkData(
                    projectName = if (longContent) "长春市朝阳区配电改造工程第三施工标段地下综合管廊设备安装现场长名称".repeat(3) else "长春配电改造工程",
                    categoryName = "电力工程",
                    captureTimestamp = 1788822300000L,
                    addressText = if (longContent) "吉林省长春市朝阳区施工现场第三作业区地下二层配电室北侧设备基础位置及相邻通道".repeat(3) else "吉林省长春市朝阳区施工现场",
                    latitude = 43.886842,
                    longitude = 125.324501,
                    userName = "现场记录员",
                    customFields = (if (longContent) (1..5).map {
                        WatermarkFieldItem("CUSTOM_$it", "施工观察记录第${it}项", "设备安装位置、隐蔽施工过程及现场检查记录需完整保留。".repeat(2))
                    } else emptyList()) + WatermarkFieldItem("CUSTOM_LAST", "末行备注", note),
                    styleType = style.id,
                    fontSizeScale = if (longContent) 2.2f else 1f,
                    opacity = .85f
                )
                val layout = WatermarkLayoutEngine.calculateLayout(width.toFloat(), height.toFloat(), data)
                val card = layout.cardRect
                assertTrue("${style.id}/$suffix card inside image", card.left >= 0 && card.top >= 0 && card.right <= width + .1f && card.bottom <= height + .1f)
                for (line in layout.lines) {
                    val metrics = watermarkPaint(line.textSize, line.textColor, line.isBold).fontMetrics
                    assertTrue("${style.id}/$suffix line top", line.y + metrics.top >= card.top - .1f)
                    assertTrue("${style.id}/$suffix line bottom: ${line.value}", line.y + metrics.bottom <= card.bottom + .1f)
                    val labelWidth = watermarkPaint(line.textSize, line.labelColor, true).measureText(line.label)
                    val valueWidth = watermarkPaint(line.textSize, line.textColor, line.isBold).measureText(line.value)
                    assertTrue("${style.id}/$suffix line right", line.x + labelWidth + valueWidth <= card.right + .1f)
                }
                assertEquals("${style.id}/$suffix complete last field", note,
                    layout.lines.filter { it.fieldKey == "CUSTOM_LAST" && it.contentRole == "value" }.joinToString("") { it.value })
                val lastLine = layout.lines.last { it.fieldKey == "CUSTOM_LAST" && it.contentRole == "value" }
                val lastBottom = lastLine.y + watermarkPaint(lastLine.textSize).fontMetrics.bottom
                assertTrue("${style.id}/$suffix last line retains bottom padding", lastBottom < card.bottom)

                // Both production paths receive an identical transparent frame, avoiding
                // compositing-rounding differences from a synthetic photo background.
                val photo = WatermarkBitmapRenderer.renderWatermarkOnBitmap(
                    Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888), data)
                val overlay = VideoWatermarkTranscoder.renderDisplayOverlay(width, height, 0, data)
                try {
                    assertTrue("${style.id}/$suffix photo and video overlay pixels", photo.sameAs(overlay))
                    val review = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    try {
                        val canvas = Canvas(review)
                        val background = Paint().apply {
                            shader = LinearGradient(0f, 0f, width.toFloat(), height.toFloat(),
                                Color.rgb(91, 109, 123), Color.rgb(179, 182, 168), Shader.TileMode.CLAMP)
                        }
                        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), background)
                        canvas.drawBitmap(photo, 0f, 0f, null)
                        File(output, "${style.id}_$suffix.png").outputStream().use {
                            assertTrue(review.compress(Bitmap.CompressFormat.PNG, 100, it))
                        }
                    } finally { review.recycle() }
                } finally {
                    photo.recycle()
                    overlay.recycle()
                }
            }
        }
    }
}
