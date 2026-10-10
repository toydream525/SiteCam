package com.sitecam.app.core.watermark.renderer

import android.graphics.Canvas
import android.graphics.Paint
import com.sitecam.app.core.watermark.engine.WatermarkLayoutResult
import com.sitecam.app.core.watermark.engine.watermarkPaint

/** The sole drawing path for the live preview, saved photos and video overlay textures. */
object WatermarkCanvasPainter {
    fun draw(canvas: Canvas, layout: WatermarkLayoutResult) {
        // Single shared alpha for the card fill, bars, dividers and every text
        // layer. Layout colors stay opaque, so nothing is faded twice, and all
        // three render paths (preview, photo, video) fade identically.
        val alpha = ((layout.opacity.takeIf { it.isFinite() } ?: 1f).coerceIn(0f,1f) * 255).toInt()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        fun fill(color: Int) { paint.color = color; paint.alpha = alpha }
        fill(layout.cardColor)
        canvas.drawRoundRect(layout.cardRect, layout.cornerRadius, layout.cornerRadius, paint)
        fill(layout.accentColor)
        layout.headerRect?.let { canvas.drawRect(it,paint) }
        layout.accentBarRect?.let { canvas.drawRect(it,paint) }
        layout.decorations.forEach { fill(it.color); canvas.drawRect(it.rect,paint) }
        for(line in layout.lines) {
            val labelPaint=watermarkPaint(line.textSize,line.labelColor,true).apply { this.alpha = alpha }
            if(line.label.isNotEmpty()) canvas.drawText(line.label,line.x,line.y,labelPaint)
            canvas.drawText(line.value,line.x+labelPaint.measureText(line.label),line.y,
                watermarkPaint(line.textSize,line.textColor,line.isBold).apply { this.alpha = alpha })
        }
    }
}
