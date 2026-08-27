package com.sitecam.app.feature.annotation.model

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color

enum class AnnotationTool {
    ARROW, RECTANGLE, CIRCLE, FREEHAND, TEXT, MOSAIC
}

sealed class AnnotationElement {
    abstract val color: Color
    abstract val strokeWidth: Float

    data class Arrow(
        val start: Offset,
        val end: Offset,
        override val color: Color,
        override val strokeWidth: Float
    ) : AnnotationElement()

    data class Rectangle(
        val topLeft: Offset,
        val bottomRight: Offset,
        override val color: Color,
        override val strokeWidth: Float
    ) : AnnotationElement()

    data class Circle(
        val center: Offset,
        val radius: Float,
        override val color: Color,
        override val strokeWidth: Float
    ) : AnnotationElement()

    data class Freehand(
        val points: List<Offset>,
        override val color: Color,
        override val strokeWidth: Float
    ) : AnnotationElement()

    data class TextNote(
        val position: Offset,
        val text: String,
        override val color: Color,
        override val strokeWidth: Float
    ) : AnnotationElement()

    data class Mosaic(
        val points: List<Offset>,
        override val color: Color = Color.Gray,
        override val strokeWidth: Float = 40f
    ) : AnnotationElement()
}
