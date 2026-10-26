package com.sitecam.app.core.watermark

import android.graphics.Bitmap
import android.graphics.Color
import com.sitecam.app.core.watermark.model.WatermarkData
import com.sitecam.app.core.watermark.renderer.WatermarkBitmapRenderer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SnapshotPreviewConsistencyTest {
    @Test fun frontCameraMirrorsSceneBeforeAddingReadableWatermark() = runBlocking {
        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        for (y in 0..99) for (x in 0..99) bitmap.setPixel(x, y, if (x < 50) Color.RED else Color.BLUE)
        val result = WatermarkBitmapRenderer.renderWatermarkOnBitmap(bitmap,
            WatermarkData(enabledSystemFields = emptySet()), mirrorHorizontally = true)
        try {
            assertEquals(Color.BLUE, result.getPixel(10, 10))
            assertEquals(Color.RED, result.getPixel(90, 10))
        } finally { result.recycle() }
    }
}
