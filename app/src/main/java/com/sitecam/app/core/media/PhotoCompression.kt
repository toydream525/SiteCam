package com.sitecam.app.core.media

import android.graphics.Bitmap

object PhotoCompression {
    /** Caller owns the result; when no resize is needed this returns the original bitmap. */
    fun resize(bitmap: Bitmap, profile: PhotoQualityProfile): Bitmap {
        val (width, height) = profile.targetSize(bitmap.width, bitmap.height)
        return if (width == bitmap.width && height == bitmap.height) bitmap
        else Bitmap.createScaledBitmap(bitmap, width, height, true)
    }
}
