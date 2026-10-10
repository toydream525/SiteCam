package com.sitecam.app.feature.watermark

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.nativeCanvas
import com.sitecam.app.core.watermark.engine.WatermarkLayoutEngine
import com.sitecam.app.core.watermark.model.WatermarkData
import com.sitecam.app.core.watermark.renderer.WatermarkCanvasPainter
import kotlin.math.min

/** Fits the existing stamp into an editing preview without changing capture data or output. */
@Composable
fun WatermarkVisualPreview(data: WatermarkData, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val layout = WatermarkLayoutEngine.calculateLayout(1080f, 1440f, data)
        val bounds = layout.cardRect
        val fitBounds = WatermarkLayoutEngine.calculateLayout(1080f, 1440f, data.copy(fontSizeScale = 1f)).cardRect
        if(bounds.width() > 0 && bounds.height() > 0 && size.width > 0 && size.height > 0) {
            val baseScale = min(size.width * .76f / fitBounds.width(), size.height * .76f / fitBounds.height())
            val scale = min(baseScale, min(size.width * .94f / bounds.width(), size.height * .92f / bounds.height()))
            val inset = min(size.width, size.height) * .03f
            val x = if(data.position.endsWith("RIGHT")) size.width - inset - bounds.width() * scale else inset
            val y = if(data.position.startsWith("TOP")) inset else size.height - inset - bounds.height() * scale
            val canvas = drawContext.canvas.nativeCanvas
            canvas.save()
            canvas.translate(x - bounds.left * scale, y - bounds.top * scale)
            canvas.scale(scale, scale)
            WatermarkCanvasPainter.draw(canvas, layout)
            canvas.restore()
        }
    }
}
