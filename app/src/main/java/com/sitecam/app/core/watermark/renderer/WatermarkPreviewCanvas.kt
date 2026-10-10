package com.sitecam.app.core.watermark.renderer

import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.nativeCanvas
import com.sitecam.app.core.watermark.engine.WatermarkLayoutEngine
import com.sitecam.app.core.watermark.engine.WatermarkLayoutResult
import com.sitecam.app.core.watermark.model.WatermarkData

/** Normalize the live viewport to the capture frame so small windows keep the saved proportions. */
fun watermarkPreviewLayout(width: Float, height: Float, data: WatermarkData): WatermarkLayoutResult {
    val scale = minOf(width,height).coerceAtLeast(1f) / 1080f
    val layout = WatermarkLayoutEngine.calculateLayout(width/scale,height/scale,data)
    fun rect(r: RectF) = RectF(r.left*scale,r.top*scale,r.right*scale,r.bottom*scale)
    return layout.copy(cardRect=rect(layout.cardRect),accentBarRect=layout.accentBarRect?.let(::rect),
        headerRect=layout.headerRect?.let(::rect),cornerRadius=layout.cornerRadius*scale,
        lines=layout.lines.map { it.copy(x=it.x*scale,y=it.y*scale,textSize=it.textSize*scale) },
        decorations=layout.decorations.map { it.copy(rect=rect(it.rect)) })
}

@Composable
fun WatermarkPreviewCanvas(watermarkData: WatermarkData, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxSize()) {
        if(size.width > 0 && size.height > 0) WatermarkCanvasPainter.draw(drawContext.canvas.nativeCanvas,
            watermarkPreviewLayout(size.width,size.height,watermarkData))
    }
}
