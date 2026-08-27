package com.sitecam.app.core.watermark.renderer

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import com.sitecam.app.core.watermark.engine.WatermarkLayoutEngine
import com.sitecam.app.core.watermark.model.WatermarkData

@Composable
fun WatermarkPreviewCanvas(
    watermarkData: WatermarkData,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val canvasWidth = size.width
        val canvasHeight = size.height

        if (canvasWidth <= 0 || canvasHeight <= 0) return@Canvas

        val layoutResult = WatermarkLayoutEngine.calculateLayout(
            canvasWidth = canvasWidth,
            canvasHeight = canvasHeight,
            data = watermarkData
        )

        // 1. Draw card background
        drawRoundRect(
            color = Color(layoutResult.cardColor),
            topLeft = Offset(layoutResult.cardRect.left, layoutResult.cardRect.top),
            size = Size(layoutResult.cardRect.width(), layoutResult.cardRect.height()),
            cornerRadius = CornerRadius(16f, 16f)
        )

        // 2. Draw header if InfoBoard
        if (layoutResult.headerRect != null) {
            drawRoundRect(
                color = Color(layoutResult.accentColor),
                topLeft = Offset(layoutResult.headerRect.left, layoutResult.headerRect.top),
                size = Size(layoutResult.headerRect.width(), layoutResult.headerRect.height()),
                cornerRadius = CornerRadius(16f, 16f)
            )
        }

        // 3. Draw accent bar if classic
        if (layoutResult.accentBarRect != null && layoutResult.headerRect == null) {
            drawRoundRect(
                color = Color(layoutResult.accentColor),
                topLeft = Offset(layoutResult.accentBarRect.left, layoutResult.accentBarRect.top),
                size = Size(layoutResult.accentBarRect.width(), layoutResult.accentBarRect.height()),
                cornerRadius = CornerRadius(8f, 8f)
            )
        }

        // 4. Draw texts on native canvas for precise font measurement & styling
        drawContext.canvas.nativeCanvas.apply {
            save()
            clipRect(layoutResult.cardRect)
            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            }
            val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                color = android.graphics.Color.parseColor("#B0BEC5")
            }

            for (line in layoutResult.lines) {
                textPaint.textSize = line.textSize
                textPaint.color = line.textColor
                textPaint.isFakeBoldText = line.isBold

                if (line.label.isNotEmpty()) {
                    labelPaint.textSize = line.textSize
                    drawText(line.label, line.x, line.y, labelPaint)
                    val labelWidth = labelPaint.measureText(line.label)
                    drawText(line.value, line.x + labelWidth, line.y, textPaint)
                } else {
                    drawText(line.value, line.x, line.y, textPaint)
                }
            }
            restore()
        }
    }
}
