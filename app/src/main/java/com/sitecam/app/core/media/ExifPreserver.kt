package com.sitecam.app.core.media

import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.exifinterface.media.ExifInterface
import com.sitecam.app.core.watermark.model.WatermarkData
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ExifPreserver {

    fun writeExifAttributes(
        file: File,
        watermarkData: WatermarkData,
        orientation: Int = 0
    ) {
        try {
            val exif = ExifInterface(file)
            applyExif(exif, watermarkData, orientation)
            exif.saveAttributes()
        } catch (_: Exception) {
            // Non-fatal if EXIF writing fails on specific storage wrappers
        }
    }

    fun writeExifAttributesToUri(
        context: Context,
        uri: Uri,
        watermarkData: WatermarkData,
        orientation: Int = 0
    ) {
        try {
            val pfd = context.contentResolver.openFileDescriptor(uri, "rw") ?: return
            pfd.use { descriptor ->
                val exif = ExifInterface(descriptor.fileDescriptor)
                applyExif(exif, watermarkData, orientation)
                exif.saveAttributes()
            }
        } catch (_: Exception) {
            // Non-fatal fallback
        }
    }

    private fun applyExif(exif: ExifInterface, data: WatermarkData, orientation: Int) {
        val exifDateFormat = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.getDefault())
        val dateString = exifDateFormat.format(Date(data.captureTimestamp))

        exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, dateString)
        exif.setAttribute(ExifInterface.TAG_DATETIME_DIGITIZED, dateString)
        exif.setAttribute(ExifInterface.TAG_MAKE, Build.MANUFACTURER)
        exif.setAttribute(ExifInterface.TAG_MODEL, Build.MODEL)
        exif.setAttribute(ExifInterface.TAG_SOFTWARE, "SiteCam Engineering Camera")

        // Orientation tag
        val exifOrientation = when (orientation) {
            90 -> ExifInterface.ORIENTATION_ROTATE_90
            180 -> ExifInterface.ORIENTATION_ROTATE_180
            270 -> ExifInterface.ORIENTATION_ROTATE_270
            else -> ExifInterface.ORIENTATION_NORMAL
        }
        exif.setAttribute(ExifInterface.TAG_ORIENTATION, exifOrientation.toString())

        // GPS coordinates
        if (data.latitude != null && data.longitude != null) {
            exif.setLatLong(data.latitude, data.longitude)
        }
        if (data.altitude != null) {
            exif.setAltitude(data.altitude)
        }
    }
}
