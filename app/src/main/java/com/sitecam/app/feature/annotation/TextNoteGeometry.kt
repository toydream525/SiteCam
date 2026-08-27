package com.sitecam.app.feature.annotation

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect

data class TextNoteLayout(
    val text: String,
    val baseline: Offset,
    val bubbleRect: Rect
)

/** Shared, bounded geometry for the Compose preview and bitmap renderer. */
object TextNoteGeometry {
    const val TEXT_SIZE = 32f
    const val HORIZONTAL_PADDING = 12f
    const val TOP_PADDING = 36f
    const val BOTTOM_PADDING = 12f

    fun estimateTextWidth(text: String, textSize: Float = TEXT_SIZE): Float =
        text.sumOf { if (it.code < 128) (textSize * 0.55f).toDouble() else (textSize * 1.05f).toDouble() }
            .toFloat()

    fun ellipsize(text: String, maxWidth: Float, textSize: Float = TEXT_SIZE): String {
        if (text.isBlank() || estimateTextWidth(text, textSize) <= maxWidth) return text
        val ellipsis = "…"
        val available = (maxWidth - estimateTextWidth(ellipsis, textSize)).coerceAtLeast(0f)
        val builder = StringBuilder()
        for (character in text) {
            val candidate = builder.toString() + character
            if (estimateTextWidth(candidate, textSize) > available) break
            builder.append(character)
        }
        return builder.append(ellipsis).toString()
    }

    fun layout(
        position: Offset,
        text: String,
        contentRect: Rect,
        textSize: Float = TEXT_SIZE
    ): TextNoteLayout {
        val safeWidth = contentRect.width.coerceAtLeast(textSize)
        val textMaxWidth = (safeWidth - HORIZONTAL_PADDING * 2).coerceAtLeast(textSize)
        val visibleText = ellipsize(text, textMaxWidth, textSize)
        val bubbleWidth = (estimateTextWidth(visibleText, textSize) + HORIZONTAL_PADDING * 2)
            .coerceAtMost(safeWidth)
        val left = (position.x - HORIZONTAL_PADDING).coerceIn(
            contentRect.left,
            (contentRect.right - bubbleWidth).coerceAtLeast(contentRect.left)
        )
        val baselineY = position.y.coerceIn(
            contentRect.top + TOP_PADDING,
            (contentRect.bottom - BOTTOM_PADDING).coerceAtLeast(contentRect.top + TOP_PADDING)
        )
        val top = (baselineY - TOP_PADDING).coerceAtLeast(contentRect.top)
        val bottom = (baselineY + BOTTOM_PADDING).coerceAtMost(contentRect.bottom)
        return TextNoteLayout(
            text = visibleText,
            baseline = Offset(left + HORIZONTAL_PADDING, baselineY),
            bubbleRect = Rect(left, top, (left + bubbleWidth).coerceAtMost(contentRect.right), bottom.coerceAtLeast(top))
        )
    }
}
