package com.sitecam.app.feature.annotation

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import com.sitecam.app.feature.annotation.model.AnnotationElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EditableTextRecipeTest {
    private fun note(x: Float, y: Float, text: String = "裂缝") = AnnotationElement.TextNote(
        position = Offset(x, y), text = text, color = Color.Red, strokeWidth = 7f
    )

    private fun map(point: Offset, target: TextEditTarget): Offset {
        val values = floatArrayOf(point.x, point.y)
        target.toView.mapPoints(values)
        return Offset(values[0], values[1])
    }

    private fun mappedRect(rect: Rect, target: TextEditTarget): Rect {
        val points = floatArrayOf(rect.left, rect.top, rect.right, rect.bottom)
        target.toView.mapPoints(points)
        return Rect(points[0], points[1], points[2], points[3])
    }

    @Test
    fun saveAndReopenKeepsTextPositionSizeAndLayerOrder() {
        val bottom = note(40f, 70f)
        val top = note(42f, 72f, "复核")
        val recipe = listOf(
            EditStep.Draw(listOf(bottom, top), 100f, 100f),
            EditStep.Transform("rotate"),
            EditStep.Draw(listOf(note(50f, 60f, "后续标注")), 100f, 100f)
        )
        val reopened = EditRecipe.decode(EditRecipe.encode(recipe))
        val targets = EditableTextRecipe.targets(reopened, emptyList(), 100, 100, 400f, 400f)

        assertEquals(listOf(0, 0, 2), targets.map { it.stepIndex })
        assertEquals(listOf(0, 1, 0), targets.map { it.elementIndex })
        val originalTargets = EditableTextRecipe.targets(recipe, emptyList(), 100, 100, 400f, 400f)
        assertEquals(originalTargets.map { it.note.position }, targets.map { it.note.position })
        assertEquals(originalTargets.map { mappedRect(TextNoteGeometry.layout(it.note.position, it.note.text, it.contentRect).bubbleRect, it) },
            targets.map { mappedRect(TextNoteGeometry.layout(it.note.position, it.note.text, it.contentRect).bubbleRect, it) })
        assertEquals(listOf(7f, 7f, 7f), targets.map { it.note.strokeWidth })
        assertEquals(listOf(Color.Red, Color.Red, Color.Red), targets.map { it.note.color })
    }

    @Test
    fun rotatedMirroredAndCroppedTextRemainsSelectableAtItsRenderedPoint() {
        val text = note(500f, 500f)
        val recipe = listOf(
            EditStep.Draw(listOf(text), 1000f, 1000f),
            EditStep.Transform("rotate"),
            EditStep.Transform("horizontal"),
            EditStep.Transform("crop", .1f, .2f, .9f, .8f)
        )
        val target = EditableTextRecipe.targets(recipe, emptyList(), 1000, 1000, 360f, 240f).single()
        val layout = TextNoteGeometry.layout(target.note.position, target.note.text, target.contentRect)
        assertTrue(target.contains(map(layout.bubbleRect.center, target)))
        val drag = Offset(18f, -12f)
        val moved = target.moved(drag)
        val sourceStep = recipe[target.stepIndex!!] as EditStep.Draw
        val changedElements = sourceStep.elements.toMutableList().apply { this[target.elementIndex] = moved }
        val changedRecipe = recipe.toMutableList().apply { this[target.stepIndex!!] = sourceStep.copy(elements = changedElements) }
        val movedTarget = EditableTextRecipe.targets(changedRecipe, emptyList(), 1000, 1000, 360f, 240f).single()
        val beforeView = map(layout.baseline, target)
        val movedLayout = TextNoteGeometry.layout(movedTarget.note.position, movedTarget.note.text, movedTarget.contentRect)
        val afterView = map(movedLayout.baseline, movedTarget)
        assertEquals(drag.x, afterView.x - beforeView.x, 1f)
        assertEquals(drag.y, afterView.y - beforeView.y, 1f)
        assertEquals(target.note.text, moved.text)
        assertEquals(target.note.color, moved.color)
        assertEquals(target.note.strokeWidth, moved.strokeWidth)
        assertEquals(text.position, target.note.position)
    }

    @Test
    fun dragAtImageEdgeIsConstrainedByRenderedBubbleBounds() {
        val recipe = listOf(EditStep.Draw(listOf(note(15f, 60f, "边界")), 100f, 100f))
        val target = EditableTextRecipe.targets(recipe, emptyList(), 100, 100, 400f, 400f).single()
        val before = TextNoteGeometry.layout(target.note.position, target.note.text, target.contentRect)
        val moved = target.moved(Offset(10_000f, 10_000f))
        val after = TextNoteGeometry.layout(moved.position, moved.text, target.contentRect)
        val renderedAfter = mappedRect(after.bubbleRect, target)

        assertTrue(after.baseline.x > before.baseline.x)
        assertTrue(after.baseline.y > before.baseline.y)
        assertTrue(renderedAfter.left >= target.imageRect.left - 1f)
        assertTrue(renderedAfter.top >= target.imageRect.top - 1f)
        assertTrue(renderedAfter.right <= target.imageRect.right + 1f)
        assertTrue(renderedAfter.bottom <= target.imageRect.bottom + 1f)
    }

    @Test
    fun hitTestingUsesTopmostTextAndWindowResizeKeepsImageRelativePosition() {
        val lower = note(45f, 70f, "下层")
        val upper = note(47f, 70f, "上层")
        val recipe = listOf(EditStep.Draw(listOf(lower, upper), 100f, 100f))
        val narrow = EditableTextRecipe.targets(recipe, emptyList(), 100, 100, 300f, 500f)
        val wide = EditableTextRecipe.targets(recipe, emptyList(), 100, 100, 500f, 300f)
        val narrowTop = narrow.asReversed().first {
            it.contains(map(TextNoteGeometry.layout(it.note.position, it.note.text, it.contentRect).bubbleRect.center, it))
        }
        val wideTop = wide.asReversed().first { it.note.text == narrowTop.note.text }
        assertEquals("上层", narrowTop.note.text)
        val n = TextNoteGeometry.layout(narrowTop.note.position, narrowTop.note.text, narrowTop.contentRect).baseline
        val w = TextNoteGeometry.layout(wideTop.note.position, wideTop.note.text, wideTop.contentRect).baseline
        assertEquals((n.x - narrowTop.contentRect.left) / narrowTop.contentRect.width,
            (w.x - wideTop.contentRect.left) / wideTop.contentRect.width, .001f)
        assertEquals((n.y - narrowTop.contentRect.top) / narrowTop.contentRect.height,
            (w.y - wideTop.contentRect.top) / wideTop.contentRect.height, .001f)
    }

    @Test
    fun textBeyondCropEdgeIsNotMovedBackIntoTheImage() {
        val croppedAway = note(18f, 55f, "被裁切")
        val recipe = listOf(EditStep.Draw(listOf(croppedAway), 100f, 100f), EditStep.Transform("crop", .5f, 0f, 1f, 1f))
        val target = EditableTextRecipe.targets(recipe, emptyList(), 100, 100, 400f, 400f).single()
        val projected = map(target.note.position, target)

        assertTrue("a note outside the crop must stay outside the image", projected.x < 0f)
        assertEquals(croppedAway.position, target.note.position)
    }
}
