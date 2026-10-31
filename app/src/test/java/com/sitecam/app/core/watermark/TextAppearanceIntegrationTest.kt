package com.sitecam.app.core.watermark

import android.graphics.Canvas
import android.graphics.Bitmap
import android.graphics.Typeface
import android.net.Uri
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.sitecam.app.core.text.FontLibrary
import com.sitecam.app.core.watermark.engine.WatermarkLayoutEngine
import com.sitecam.app.core.watermark.model.WatermarkData
import com.sitecam.app.core.watermark.model.WatermarkSnapshotCodec
import com.sitecam.app.core.watermark.renderer.WatermarkCanvasPainter
import com.sitecam.app.feature.annotation.EditRecipe
import com.sitecam.app.feature.annotation.EditStep
import com.sitecam.app.feature.annotation.model.AnnotationElement
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TextAppearanceIntegrationTest {
    @Test fun importedFontSurvivesSourceRemovalAndAppearanceSurvivesSnapshotsAndRecipes() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val systemFont = listOfNotNull(System.getenv("SITECAM_TEST_FONT"),
            "/System/Library/Fonts/Supplemental/Arial.ttf", "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", "C:/Windows/Fonts/arial.ttf")
            .map(::File).first { it.isFile }
        val source = File(context.cacheDir, "custom-font.ttf")
        systemFont.copyTo(source)
        FontLibrary.initialize(context)
        val imported = FontLibrary.importFont(context, Uri.fromFile(source))
        try {
            assertTrue(source.delete())
            val retained = File(context.filesDir, "fonts/${imported.id}")
            assertTrue(retained.isFile)
            assertNotNull(Typeface.createFromFile(retained))
            val original = WatermarkData(projectName = "工程 AB123", styleType = "WHITEBOARD", fontId = imported.id, textColor = "#1262AE")
            val restored = requireNotNull(WatermarkSnapshotCodec.decode(WatermarkSnapshotCodec.encode(original)))
            assertEquals(original.fontId, restored.fontId); assertEquals(original.textColor, restored.textColor)
            val layout = WatermarkLayoutEngine.calculateLayout(800f, 1000f, restored)
            assertEquals(imported.id, layout.fontId)
            assertTrue(layout.lines.isNotEmpty())
            assertTrue(layout.lines.all { it.textColor == android.graphics.Color.parseColor("#1262AE") })
            val bitmap = Bitmap.createBitmap(800, 1000, Bitmap.Config.ARGB_8888)
            try { WatermarkCanvasPainter.draw(Canvas(bitmap), layout); assertTrue(layout.cardRect.height() > 0) } finally { bitmap.recycle() }
            val note = AnnotationElement.TextNote(Offset(50f, 100f), "复查 AB123", Color.Red, 10f, imported.id, true)
            val recipe = EditRecipe.decode(EditRecipe.encode(listOf(EditStep.Draw(listOf(note), 600f, 400f))))
            assertEquals(note, (recipe.single() as EditStep.Draw).elements.single())
        } finally { source.delete(); File(context.filesDir, "fonts/${imported.id}").delete() }
    }

    @Test fun invalidFontDoesNotLeaveAnImportedOrPartialFile() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        FontLibrary.initialize(context)
        val before = FontLibrary.choices().map { it.id }.toSet()
        val source = File(context.cacheDir, "invalid.ttf").apply { writeText("not a font") }
        try {
            assertTrue(runCatching { FontLibrary.importFont(context, Uri.fromFile(source)) }.isFailure)
            assertEquals(before, FontLibrary.choices().map { it.id }.toSet())
            assertFalse(File(context.filesDir, "fonts").listFiles().orEmpty().any { it.name.endsWith(".pending") })
        } finally { source.delete() }
    }
}
