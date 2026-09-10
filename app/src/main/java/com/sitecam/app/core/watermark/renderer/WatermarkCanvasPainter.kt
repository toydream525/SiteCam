package com.sitecam.app.core.watermark.renderer

import android.graphics.Canvas
import android.graphics.Paint
import com.sitecam.app.core.watermark.engine.WatermarkLayoutResult
import com.sitecam.app.core.watermark.engine.watermarkPaint

/** The sole drawing path for the live preview, saved photos and video overlay textures. */
object WatermarkCanvasPainter {
    fun draw(canvas: Canvas, layout: WatermarkLayoutResult) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = layout.cardColor
        canvas.drawRoundRect(layout.cardRect, layout.cornerRadius, layout.cornerRadius, paint)
        paint.color = layout.accentColor
        layout.headerRect?.let { canvas.drawRect(it,paint) }
        layout.accentBarRect?.let { canvas.drawRect(it,paint) }
        layout.decorations.forEach { paint.color=it.color; canvas.drawRect(it.rect,paint) }
        for(line in layout.lines) {
            val labelPaint=watermarkPaint(line.textSize,line.labelColor,true)
            if(line.label.isNotEmpty()) canvas.drawText(line.label,line.x,line.y,labelPaint)
            canvas.drawText(line.value,line.x+labelPaint.measureText(line.label),line.y,
                watermarkPaint(line.textSize,line.textColor,line.isBold))
        }
    }
}
