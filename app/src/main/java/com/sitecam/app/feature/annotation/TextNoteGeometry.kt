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
        textSize: Float = TEXT_SIZE,
        fontId: String = ""
    ): TextNoteLayout {
        val paint = if (fontId.isNotEmpty()) android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            this.textSize = textSize; typeface = com.sitecam.app.core.text.FontLibrary.typeface(fontId); isFakeBoldText = true
        } else null
        fun width(value: String) = paint?.measureText(value) ?: estimateTextWidth(value, textSize)
        val safeWidth = contentRect.width.coerceAtLeast(textSize)
        val textMaxWidth = (safeWidth - HORIZONTAL_PADDING * 2).coerceAtLeast(textSize)
        val visibleText = if (paint == null) ellipsize(text, textMaxWidth, textSize) else if (width(text) <= textMaxWidth) text else {
            var value = ""
            for (character in text) { if (width(value + character + "…") > textMaxWidth) break; value += character }
            value + "…"
        }
        val topPadding = maxOf(TOP_PADDING, paint?.fontMetrics?.top?.let { -it + 4f } ?: TOP_PADDING)
        val bottomPadding = maxOf(BOTTOM_PADDING, paint?.fontMetrics?.bottom?.let { it + 4f } ?: BOTTOM_PADDING)
        val bubbleWidth = (width(visibleText) + HORIZONTAL_PADDING * 2)
            .coerceAtMost(safeWidth)
        val left = (position.x - HORIZONTAL_PADDING).coerceIn(
            contentRect.left,
            (contentRect.right - bubbleWidth).coerceAtLeast(contentRect.left)
        )
        val baselineY = position.y.coerceIn(
            contentRect.top + topPadding,
            (contentRect.bottom - bottomPadding).coerceAtLeast(contentRect.top + topPadding)
        )
        val top = (baselineY - topPadding).coerceAtLeast(contentRect.top)
        val bottom = (baselineY + bottomPadding).coerceAtMost(contentRect.bottom)
        return TextNoteLayout(
            text = visibleText,
            baseline = Offset(left + HORIZONTAL_PADDING, baselineY),
            bubbleRect = Rect(left, top, (left + bubbleWidth).coerceAtMost(contentRect.right), bottom.coerceAtLeast(top))
        )
    }
}
