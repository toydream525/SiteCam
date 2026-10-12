package com.sitecam.app.core.watermark

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.media3.common.util.UnstableApi
import com.sitecam.app.core.media.VideoWatermarkTranscoder
import com.sitecam.app.core.watermark.renderer.WatermarkBitmapRenderer
import androidx.room.Room
import com.sitecam.app.core.database.AppDatabase
import com.sitecam.app.core.database.entity.WatermarkTemplateEntity
import com.sitecam.app.core.watermark.engine.WatermarkLayoutEngine
import com.sitecam.app.core.watermark.engine.watermarkPaint
import com.sitecam.app.core.watermark.model.*
import com.sitecam.app.core.watermark.renderer.WatermarkCanvasPainter
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@UnstableApi
class WatermarkWidthTest {
    private fun sample(style: String, ratio: Float, long: Boolean, font: Float) = WatermarkData(
        projectName = "工程甲", addressText = if (long) "江苏省南京市建邺区江东中路道路施工现场".repeat(3) else "一区",
        enabledSystemFields = setOf(BuiltInWatermarkFieldKeys.PROJECT_NAME, BuiltInWatermarkFieldKeys.ADDRESS),
        customFields = listOf(WatermarkFieldItem("CUSTOM_NOTE", "现场备注", if (long) "钢筋绑扎及模板检查".repeat(3) else "合格")),
        fieldOrder = listOf(BuiltInWatermarkFieldKeys.PROJECT_NAME, BuiltInWatermarkFieldKeys.ADDRESS, "CUSTOM_NOTE"),
        styleType = style, widthRatio = ratio, fontSizeScale = font, captureTimestamp = 1700000000000
    )

    @Test fun allStylesWrapWithinRequestedWidthAndKeepContent() {
        for (style in WatermarkStyleCatalog.styles) for (ratio in listOf(.3f, .6f, .9f))
            for ((w, h) in listOf(1080 to 1440, 1440 to 1080, 1600 to 2560, 2560 to 1600, 240 to 180))
                for (font in listOf(.6f, 1f, 2f)) for (long in listOf(false, true)) {
                    val data = sample(style.id, ratio, long, font)
                    val result = WatermarkLayoutEngine.calculateLayout(w.toFloat(), h.toFloat(), data)
                    val context = "${style.id} $ratio $w/$h font=$font long=$long"
                    assertTrue(context, result.cardRect.width() <= minOf(w, h) * ratio + 1)
                    assertTrue(context, result.cardRect.left >= -1 && result.cardRect.right <= w + 1 && result.cardRect.bottom <= h + 1)
                    // The existing readability floor clips extremely tall stamps on tiny canvases.
                    if (minOf(w, h) >= 1080) assertTrue(context, result.cardRect.top >= -1)
                    for (line in result.lines) {
                        val paint = watermarkPaint(line.textSize, bold = line.isBold)
                        assertTrue("text $context ${line.fieldKey}", line.x >= result.cardRect.left - 1 && line.x + paint.measureText(line.value) <= result.cardRect.right + 1)
                    }
                    val address = result.lines.filter { it.fieldKey == BuiltInWatermarkFieldKeys.ADDRESS && it.contentRole == "value" }.joinToString("") { it.value }
                    assertEquals(context, data.addressText, address)
                    val custom = result.lines.filter { it.fieldKey == "CUSTOM_NOTE" && it.contentRole == "value" }.joinToString("") { it.value }
                    assertEquals(context, data.customFields.single().value, custom)
                }
    }

    @Test fun widthChangesWrappingWithoutChangingFontAndShortContentStaysCompact() {
        val data = sample("CLASSIC", .3f, true, 1f)
        val narrow = WatermarkLayoutEngine.calculateLayout(1080f, 1920f, data)
        val wide = WatermarkLayoutEngine.calculateLayout(1080f, 1920f, data.copy(widthRatio = .9f))
        assertTrue(narrow.cardRect.width() < wide.cardRect.width())
        assertTrue(narrow.lines.size > wide.lines.size)
        assertEquals(wide.lines.first().textSize, narrow.lines.first().textSize, .01f)
        val short = WatermarkLayoutEngine.calculateLayout(1080f, 1920f, sample("CLASSIC", .6f, false, 1f))
        assertTrue(short.cardRect.width() < 1080f * .6f)
    }

    @Test fun snapshotRoundTripKeepsNewWidthAndMissingWidthKeepsHistoricalLimit() {
        for (ratio in listOf(.3f, .6f, .9f)) {
            val data = sample("CLASSIC", ratio, true, 1f)
            assertEquals(data, WatermarkSnapshotCodec.decode(WatermarkSnapshotCodec.encode(data)))
        }
        for (style in WatermarkStyleCatalog.styles) {
            val old = sample(style.id, .6f, true, 1f).copy(widthRatio = null)
            val json = WatermarkSnapshotCodec.encode(old)
            assertFalse(json.contains("widthRatio"))
            val decoded = WatermarkSnapshotCodec.decode(json)!!
            assertNull(decoded.widthRatio)
            assertEquals(WatermarkLayoutEngine.calculateLayout(1080f, 1440f, old), WatermarkLayoutEngine.calculateLayout(1080f, 1440f, decoded))
            val v3 = WatermarkSnapshotCodec.decode("""{"version":3,"styleType":"${style.id}"}""")!!
            assertNull(v3.widthRatio)
        }
    }

    @Test fun previewPhotoAndVideoUseIdenticalPixelsForEachWidthAndOrientation() = runBlocking {
        for (style in WatermarkStyleCatalog.styles) for (ratio in listOf(.3f, .6f, .9f))
            for ((w, h) in listOf(1080 to 1440, 1440 to 1080)) {
                val data = sample(style.id, ratio, true, 1f)
                val preview = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                val photo = WatermarkBitmapRenderer.renderWatermarkOnBitmap(Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888), data)
                val video = VideoWatermarkTranscoder.renderDisplayOverlay(w, h, 0, data)
                try {
                    WatermarkCanvasPainter.draw(Canvas(preview), WatermarkLayoutEngine.calculateLayout(w.toFloat(), h.toFloat(), data))
                    assertTrue("photo ${style.id} $ratio $w/$h", preview.sameAs(photo))
                    assertTrue("video ${style.id} $ratio $w/$h", preview.sameAs(video))
                } finally { preview.recycle(); photo.recycle(); video.recycle() }
            }
    }

    @Test fun databaseMigrationDefaultsOldTemplatesOnceAndKeepsIndependentWidthsAfterReopen() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val name = "watermark-width-migration-test.db"
        context.deleteDatabase(name)
        fun open() = Room.databaseBuilder(context, AppDatabase::class.java, name).allowMainThreadQueries().addMigrations(AppDatabase.MIGRATION_5_6).build()
        var db = open()
        try {
            val first = db.watermarkDao().insertTemplate(WatermarkTemplateEntity(name = "甲", widthRatio = .3f))
            val second = db.watermarkDao().insertTemplate(WatermarkTemplateEntity(name = "乙", widthRatio = .9f))
            val sql = db.openHelper.writableDatabase
            sql.execSQL("CREATE TABLE old_templates (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, styleType TEXT NOT NULL, fontSizeScale REAL NOT NULL, opacity REAL NOT NULL, marginDp INTEGER NOT NULL, position TEXT NOT NULL, isDefault INTEGER NOT NULL)")
            sql.execSQL("INSERT INTO old_templates SELECT id,name,styleType,fontSizeScale,opacity,marginDp,position,isDefault FROM watermark_templates")
            sql.execSQL("DROP TABLE watermark_templates")
            sql.execSQL("ALTER TABLE old_templates RENAME TO watermark_templates")
            sql.execSQL("DROP TABLE room_master_table")
            sql.version = 5
            db.close()
            db = open()
            assertEquals(.6f, db.watermarkDao().getTemplateById(first)!!.widthRatio, 0f)
            assertEquals(.6f, db.watermarkDao().getTemplateById(second)!!.widthRatio, 0f)
            db.watermarkDao().updateTemplateWidth(first, .35f)
            db.watermarkDao().updateTemplateWidth(second, .85f)
            db.close()
            db = open()
            assertEquals(.35f, db.watermarkDao().getTemplateById(first)!!.widthRatio, 0f)
            assertEquals(.85f, db.watermarkDao().getTemplateById(second)!!.widthRatio, 0f)
            val newId = db.watermarkDao().insertTemplate(WatermarkTemplateEntity(name = "新模板"))
            assertEquals(.6f, db.watermarkDao().getTemplateById(newId)!!.widthRatio, 0f)
        } finally { db.close(); context.deleteDatabase(name) }
    }
}
