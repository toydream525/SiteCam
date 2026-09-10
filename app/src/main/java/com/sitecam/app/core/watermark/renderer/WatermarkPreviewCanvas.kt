package com.sitecam.app.core.watermark.renderer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.nativeCanvas
import com.sitecam.app.core.watermark.engine.WatermarkLayoutEngine
import com.sitecam.app.core.watermark.model.WatermarkData

@Composable
fun WatermarkPreviewCanvas(watermarkData: WatermarkData, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxSize()) {
        if(size.width > 0 && size.height > 0) WatermarkCanvasPainter.draw(drawContext.canvas.nativeCanvas,
            WatermarkLayoutEngine.calculateLayout(size.width,size.height,watermarkData))
    }
}
