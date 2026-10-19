package com.sitecam.app.feature.annotation

import android.graphics.Matrix
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.sitecam.app.feature.annotation.model.AnnotationElement

/** Reference into an existing vector recipe; editing never flattens or reorders its layers. */
data class TextEditTarget(val stepIndex: Int?, val elementIndex: Int, val note: AnnotationElement.TextNote,
    val contentRect: Rect, val toView: Matrix) {
    var imageRect: Rect = contentRect
    private fun map(point: Offset, matrix: Matrix): Offset {
        val values = floatArrayOf(point.x, point.y); matrix.mapPoints(values); return Offset(values[0], values[1])
    }
    fun contains(point: Offset): Boolean {
        if (!imageRect.contains(point)) return false
        val inverse = Matrix(); if (!toView.invert(inverse)) return false
        return TextNoteGeometry.layout(note.position, note.text, contentRect).bubbleRect.contains(map(point, inverse))
    }
    fun moved(delta: Offset): AnnotationElement.TextNote {
        val inverse = Matrix(); if (!toView.invert(inverse)) return note
        val box = TextNoteGeometry.layout(note.position, note.text, contentRect).bubbleRect
        val bounds = android.graphics.RectF(box.left, box.top, box.right, box.bottom)
        toView.mapRect(bounds)
        val dx = delta.x.coerceIn(imageRect.left - bounds.left,
            (imageRect.right - bounds.right).coerceAtLeast(imageRect.left - bounds.left))
        val dy = delta.y.coerceIn(imageRect.top - bounds.top,
            (imageRect.bottom - bounds.bottom).coerceAtLeast(imageRect.top - bounds.top))
        val localDelta = map(Offset(dx, dy), inverse) - map(Offset.Zero, inverse)
        val baseline = TextNoteGeometry.layout(note.position, note.text, contentRect).baseline
        val layout = TextNoteGeometry.layout(baseline + localDelta, note.text, contentRect)
        return note.copy(position = layout.baseline)
    }
}

object EditableTextRecipe {
    fun targets(recipe: List<EditStep>, live: List<AnnotationElement>, sourceWidth: Int, sourceHeight: Int,
        viewWidth: Float, viewHeight: Float): List<TextEditTarget> {
        if (viewWidth <= 0 || viewHeight <= 0) return emptyList()
        var width = sourceWidth.coerceAtLeast(1).toFloat(); var height = sourceHeight.coerceAtLeast(1).toFloat()
        val targets = mutableListOf<TextEditTarget>()
        recipe.forEachIndexed { stepIndex, step -> when (step) {
            is EditStep.Draw -> {
                val fit = ImageContentRect.forFit(width.toInt(), height.toInt(), step.width, step.height)
                val matrix = Matrix().apply { setValues(floatArrayOf(1f/fit.scale,0f,-fit.left/fit.scale,0f,1f/fit.scale,-fit.top/fit.scale,0f,0f,1f)) }
                step.elements.forEachIndexed { index, element -> if (element is AnnotationElement.TextNote)
                    targets += TextEditTarget(stepIndex, index, element, fit.rect, Matrix(matrix)) }
            }
            is EditStep.Transform -> {
                val matrix = Matrix()
                when (step.kind) {
                    "rotate" -> { matrix.setValues(floatArrayOf(0f,-1f,height,1f,0f,0f,0f,0f,1f)); val w = width; width = height; height = w }
                    "horizontal" -> matrix.setValues(floatArrayOf(-1f,0f,width,0f,1f,0f,0f,0f,1f))
                    "vertical" -> matrix.setValues(floatArrayOf(1f,0f,0f,0f,-1f,height,0f,0f,1f))
                    "crop" -> { val x = (step.left*width).toInt(); val y = (step.top*height).toInt(); matrix.setTranslate(-x.toFloat(),-y.toFloat()); width = ((step.right-step.left)*width).toInt().coerceAtLeast(1).toFloat(); height = ((step.bottom-step.top)*height).toInt().coerceAtLeast(1).toFloat() }
                }
                targets.forEach { it.toView.postConcat(matrix) }
            }
        } }
        val fit = ImageContentRect.forFit(width.toInt(), height.toInt(), viewWidth, viewHeight)
        val toView = Matrix().apply { setValues(floatArrayOf(fit.scale,0f,fit.left,0f,fit.scale,fit.top,0f,0f,1f)) }
        targets.forEach { it.toView.postConcat(toView); it.imageRect = fit.rect }
        live.forEachIndexed { index, element -> if (element is AnnotationElement.TextNote)
            targets += TextEditTarget(null, index, element, fit.rect, Matrix()) }
        return targets
    }
}
