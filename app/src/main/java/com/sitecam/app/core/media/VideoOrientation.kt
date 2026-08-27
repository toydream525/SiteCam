package com.sitecam.app.core.media

/**
 * Maps the coded dimensions of a video to the dimensions a player presents
 * after applying the container rotation metadata.
 *
 * CameraX commonly writes a portrait clip as landscape coded pixels plus a
 * 90/270 degree rotation tag.  Video effects are applied to coded pixels, so
 * an overlay must be laid out in the presented coordinate system and then
 * mapped back before it is handed to Media3.
 */
data class VideoDisplaySize(val width: Int, val height: Int)
data class VideoPoint(val x: Float, val y: Float)

fun normalizedVideoRotation(rotationDegrees: Int): Int =
    ((rotationDegrees % 360) + 360) % 360

fun displaySizeForVideoRotation(
    codedWidth: Int,
    codedHeight: Int,
    rotationDegrees: Int
): VideoDisplaySize {
    require(codedWidth > 0 && codedHeight > 0)
    return when (normalizedVideoRotation(rotationDegrees)) {
        90, 270 -> VideoDisplaySize(codedHeight, codedWidth)
        else -> VideoDisplaySize(codedWidth, codedHeight)
    }
}

/** Rotation needed to turn a display-oriented overlay back into coded pixels. */
fun overlayRotationToCodedPixels(rotationDegrees: Int): Int = when (
    normalizedVideoRotation(rotationDegrees)
) {
    90 -> -90
    180 -> 180
    270 -> 90
    else -> 0
}

/** Coordinate equivalent of the bitmap inverse rotation used by the burner. */
fun displayPointToCodedPixels(
    point: VideoPoint,
    codedWidth: Float,
    codedHeight: Float,
    rotationDegrees: Int
): VideoPoint = when (normalizedVideoRotation(rotationDegrees)) {
    90 -> VideoPoint(point.y, codedHeight - point.x)
    180 -> VideoPoint(codedWidth - point.x, codedHeight - point.y)
    270 -> VideoPoint(codedWidth - point.y, point.x)
    else -> point
}
