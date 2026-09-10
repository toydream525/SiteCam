package com.sitecam.app.core.watermark.renderer

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import com.sitecam.app.core.watermark.engine.WatermarkLayoutEngine
import com.sitecam.app.core.watermark.model.WatermarkData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object WatermarkBitmapRenderer {

    /**
     * Renders the watermark onto the given bitmap and returns a newly created watermarked bitmap.
     * Runs strictly on the Default dispatcher to avoid blocking the main UI thread.
     */
    suspend fun renderWatermarkOnBitmap(
        sourceBitmap: Bitmap,
        watermarkData: WatermarkData,
        rotationDegrees: Int = 0,
        targetAspectRatio: Float? = null,
        qualityProfile: com.sitecam.app.core.media.PhotoQualityProfile = com.sitecam.app.core.media.PhotoQualityProfile.ORIGINAL
    ): Bitmap = withContext(Dispatchers.Default) {
        val orientedBitmap: Bitmap = if (rotationDegrees != 0) {
            val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
            val rotated = Bitmap.createBitmap(
                sourceBitmap,
                0,
                0,
                sourceBitmap.width,
                sourceBitmap.height,
                matrix,
                true
            )
            // If createBitmap returned a new bitmap, sourceBitmap is no longer needed
            if (rotated != sourceBitmap) {
                sourceBitmap.recycle()
            }
            rotated
        } else {
            sourceBitmap
        }

        val framedBitmap = targetAspectRatio
            ?.takeIf { it > 0f }
            ?.let { centerCropToAspectRatio(orientedBitmap, it) }
            ?: orientedBitmap

        val resizedBitmap = com.sitecam.app.core.media.PhotoCompression.resize(framedBitmap, qualityProfile)
        if (resizedBitmap !== framedBitmap) framedBitmap.recycle()

        // Ensure bitmap is mutable
        val mutableBitmap: Bitmap = if (resizedBitmap.isMutable) {
            resizedBitmap
        } else {
            val copy = resizedBitmap.copy(Bitmap.Config.ARGB_8888, true)
            resizedBitmap.recycle()
            copy
        }

        val canvas = Canvas(mutableBitmap)

        val layoutResult = WatermarkLayoutEngine.calculateLayout(
            canvasWidth = mutableBitmap.width.toFloat(),
            canvasHeight = mutableBitmap.height.toFloat(),
            data = watermarkData
        )

        WatermarkCanvasPainter.draw(canvas, layoutResult)

        mutableBitmap
    }

    private fun centerCropToAspectRatio(bitmap: Bitmap, targetAspectRatio: Float): Bitmap {
        val currentAspectRatio = bitmap.width.toFloat() / bitmap.height.coerceAtLeast(1)
        if (kotlin.math.abs(currentAspectRatio - targetAspectRatio) < 0.002f) return bitmap

        val cropWidth: Int
        val cropHeight: Int
        if (currentAspectRatio > targetAspectRatio) {
            cropHeight = bitmap.height
            cropWidth = (cropHeight * targetAspectRatio).toInt().coerceIn(1, bitmap.width)
        } else {
            cropWidth = bitmap.width
            cropHeight = (cropWidth / targetAspectRatio).toInt().coerceIn(1, bitmap.height)
        }
        val left = (bitmap.width - cropWidth) / 2
        val top = (bitmap.height - cropHeight) / 2
        val cropped = Bitmap.createBitmap(bitmap, left, top, cropWidth, cropHeight)
        if (cropped != bitmap) bitmap.recycle()
        return cropped
    }
}
