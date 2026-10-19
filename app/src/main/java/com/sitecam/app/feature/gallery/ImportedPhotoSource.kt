package com.sitecam.app.feature.gallery

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/** Read-only source; the system picker grant is the only photo-library access required. */
object ImportedPhotoSource {
    data class Metadata(val captureTimestamp: Long?, val orientation: Int, val width: Int = 0, val height: Int = 0)
    fun metadata(context: Context, uri: Uri): Metadata {
        val metadata = context.contentResolver.openInputStream(uri)?.use {
            runCatching {
                val exif = ExifInterface(it)
                Metadata(parseOriginalTime(exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL), exif.getAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL)),
                    exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL))
            }.getOrDefault(Metadata(null, ExifInterface.ORIENTATION_NORMAL))
        } ?: error("无法读取照片")
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "无法读取照片" }
        val swapped = metadata.orientation in setOf(ExifInterface.ORIENTATION_TRANSPOSE, ExifInterface.ORIENTATION_ROTATE_90,
            ExifInterface.ORIENTATION_TRANSVERSE, ExifInterface.ORIENTATION_ROTATE_270)
        return metadata.copy(width = if (swapped) bounds.outHeight else bounds.outWidth, height = if (swapped) bounds.outWidth else bounds.outHeight)
    }
    fun parseOriginalTime(value: String?, offset: String? = null): Long? {
        if (value.isNullOrBlank()) return null
        return runCatching {
            val pattern = if (offset.isNullOrBlank()) "yyyy:MM:dd HH:mm:ss" else "yyyy:MM:dd HH:mm:ssXXX"
            val text = value + offset.orEmpty()
            val parser = SimpleDateFormat(pattern, Locale.US).apply { isLenient = false }
            val pos = java.text.ParsePosition(0)
            parser.parse(text, pos)?.takeIf { pos.index == text.length }?.time
        }.getOrNull()
    }
    fun orientationMatrix(orientation: Int): Matrix = Matrix().apply {
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> setScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { setRotate(90f); postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_90 -> setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> { setRotate(-90f); postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_270 -> setRotate(-90f)
        }
    }
    fun decode(context: Context, uri: Uri, maxLongEdge: Int?, orientation: Int): Bitmap {
        require(maxLongEdge == null || maxLongEdge > 0) { "照片尺寸必须为正数" }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "无法读取照片" }
        var sample = 1
        if (maxLongEdge != null) while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxLongEdge) sample *= 2
        val decoded = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.ARGB_8888 })
        } ?: error("无法读取照片")
        val oriented = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, orientationMatrix(orientation), true)
        if (oriented !== decoded) decoded.recycle()
        return oriented
    }
}
