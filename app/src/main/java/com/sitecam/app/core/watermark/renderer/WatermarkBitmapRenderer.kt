package com.sitecam.app.core.watermark.renderer

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Typeface
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
        targetAspectRatio: Float? = null
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

        // Ensure bitmap is mutable
        val mutableBitmap: Bitmap = if (framedBitmap.isMutable) {
            framedBitmap
        } else {
            val copy = framedBitmap.copy(Bitmap.Config.ARGB_8888, true)
            framedBitmap.recycle()
            copy
        }

        val canvas = Canvas(mutableBitmap)

        val layoutResult = WatermarkLayoutEngine.calculateLayout(
            canvasWidth = mutableBitmap.width.toFloat(),
            canvasHeight = mutableBitmap.height.toFloat(),
            data = watermarkData
        )

        val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = layoutResult.cardColor
            style = Paint.Style.FILL
        }

        // 1. Draw card background
        canvas.drawRoundRect(layoutResult.cardRect, 16f, 16f, cardPaint)

        // 2. Draw header background if InfoBoard
        if (layoutResult.headerRect != null) {
            val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = layoutResult.accentColor
                style = Paint.Style.FILL
            }
            canvas.drawRoundRect(layoutResult.headerRect, 16f, 16f, headerPaint)
        }

        // 3. Draw accent bar if classic or info board
        if (layoutResult.accentBarRect != null && layoutResult.headerRect == null) {
            val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = layoutResult.accentColor
                style = Paint.Style.FILL
            }
            canvas.drawRoundRect(layoutResult.accentBarRect, 8f, 8f, accentPaint)
        }

        // 4. Draw text lines
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }

        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = android.graphics.Color.parseColor("#B0BEC5")
        }

        canvas.save()
        canvas.clipRect(layoutResult.cardRect)
        for (line in layoutResult.lines) {
            textPaint.textSize = line.textSize
            textPaint.color = line.textColor
            textPaint.isFakeBoldText = line.isBold

            if (line.label.isNotEmpty()) {
                labelPaint.textSize = line.textSize
                canvas.drawText(line.label, line.x, line.y, labelPaint)
                val labelWidth = labelPaint.measureText(line.label)
                canvas.drawText(line.value, line.x + labelWidth, line.y, textPaint)
            } else {
                canvas.drawText(line.value, line.x, line.y, textPaint)
            }
        }
        canvas.restore()

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
