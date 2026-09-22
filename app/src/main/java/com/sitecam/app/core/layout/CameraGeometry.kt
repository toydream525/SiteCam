package com.sitecam.app.core.layout

import com.sitecam.app.core.camera.isLandscapeWindow

data class CameraGeometry(
    val side: Boolean, val previewX: Float, val previewY: Float,
    val previewWidth: Float, val previewHeight: Float,
    val toolbarWidth: Float, val toolbarHeight: Float,
    val controlsX: Float, val controlsY: Float, val controlsWidth: Float, val controlsHeight: Float,
    val toolbarX: Float = 0f
)

fun cameraGeometry(width: Float, height: Float, density: Float, smallCover: Boolean = false,
                   creaseTop: Float = -1f, creaseBottom: Float = -1f,
                   creaseLeft: Float = -1f, creaseRight: Float = -1f): CameraGeometry {
    val w = width.coerceAtLeast(1f); val h = height.coerceAtLeast(1f)
    val side = smallCover || isLandscapeWindow(w, h, density)
    if (smallCover) {
        val dock = minOf(56f, w)
        val pw = minOf((w - dock).coerceAtLeast(1f), h * .75f)
        return CameraGeometry(true, (w-dock-pw)/2, (h-pw/.75f)/2, pw, pw/.75f, 0f, 0f, w-dock, 0f, dock, h)
    }
    if (creaseLeft > 120 && creaseRight >= creaseLeft && creaseRight < w-120) {
        if (side) {
            val tw = minOf(102f, creaseLeft*.25f)
            val pw = minOf(creaseLeft-tw-16, h*4/3)
            return CameraGeometry(true, tw, (h-pw*.75f)/2, pw, pw*.75f, tw, h,
                creaseRight+16, 0f, w-creaseRight-16, h)
        }
        // A vertical separating hinge in a tall window makes one panel the usable camera area.
        val x = if (w-creaseRight > creaseLeft) creaseRight+16 else 0f
        val panel = if (x>0) w-x else creaseLeft-16
        val g = cameraGeometry(panel,h,density)
        return g.copy(previewX=g.previewX+x, controlsX=g.controlsX+x, toolbarX=x)
    }
    val hover = creaseTop > 112 && creaseBottom < h-100 && creaseBottom >= creaseTop
    if (side) {
        val tw = minOf(102f, w*.2f); val dock = minOf(220f, w*.55f)
        val ph = if (hover) creaseTop-16 else h
        val pw = minOf((w-tw-dock).coerceAtLeast(1f), ph*4/3)
        val cy = if (hover) creaseBottom+16 else 0f
        return CameraGeometry(true, tw, (ph-pw*.75f)/2, pw, pw*.75f, tw, ph,
            tw+pw, cy, w-tw-pw, h-cy)
    }
    // The button row hugs the status bar: 56dp holds the 48dp project chip with 4dp of breathing
    // room above and below, instead of the 102dp band that used to push the row down and starve the
    // preview. Everything saved here goes to the camera area.
    val topBar = 56f
    // Keep the reference 254dp bottom band when the window is tall enough; on shorter windows the
    // preview must keep the full width instead, so the band yields down to 150dp.
    val bottomReserve = minOf(254f, maxOf(150f, h - topBar - w * 4f / 3f))
    val available = (if (hover) creaseTop-16 else h-bottomReserve)-topBar
    val pw = minOf(w, available.coerceAtLeast(1f)*.75f)
    val cy = if (hover) creaseBottom+16 else topBar+pw*4/3
    return CameraGeometry(false, (w-pw)/2, topBar, pw, pw*4/3, w, topBar, 0f, cy, w, h-cy)
}
