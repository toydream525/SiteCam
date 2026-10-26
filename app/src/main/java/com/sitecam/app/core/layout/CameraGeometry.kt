package com.sitecam.app.core.layout

import com.sitecam.app.core.camera.isLandscapeWindow

data class CameraGeometry(
    val side: Boolean, val previewX: Float, val previewY: Float,
    val previewWidth: Float, val previewHeight: Float,
    val toolbarWidth: Float, val toolbarHeight: Float,
    val controlsX: Float, val controlsY: Float, val controlsWidth: Float, val controlsHeight: Float,
    val toolbarX: Float = 0f, val hover: Boolean = false, val toolbarY: Float = 0f,
    val immersivePhoneLandscape: Boolean = false
)

fun cameraGeometry(width: Float, height: Float, density: Float, smallCover: Boolean = false,
                   creaseTop: Float = -1f, creaseBottom: Float = -1f,
                   creaseLeft: Float = -1f, creaseRight: Float = -1f,
                   folds: List<LayoutRect> = emptyList(), fontScale: Float = 1f,
                   phoneGripSpace: Boolean = false,
                   minimumPortraitControlsHeight: Float = 0f,
                   referenceLandscape: Boolean = false): CameraGeometry {
    val w = width.coerceAtLeast(1f); val h = height.coerceAtLeast(1f)
    val side = smallCover || isLandscapeWindow(w, h, density) || (w >= 600f && w / h >= .82f)
    if (folds.isNotEmpty()) {
        val regions = unobstructedRegions(w, h, folds, 16f)
        val horizontal = folds.any { it.width > it.height }
        if (horizontal) {
            val pair = regions.flatMap { top -> regions.mapNotNull { bottom ->
                if (top.bottom <= bottom.top && top.width >= 120f && top.height >= 112f &&
                    bottom.width >= 180f && bottom.height >= 100f) top to bottom else null
            } }.maxByOrNull { (top, bottom) -> top.area + bottom.area }
            if (pair != null) {
                val (top, bottom) = pair
                val pw = minOf(top.width, top.height * 4f / 3f)
                return CameraGeometry(true, top.left + (top.width - pw) / 2f,
                    top.top + (top.height - pw * .75f) / 2f, pw, pw * .75f, pw, 56f,
                    bottom.left, bottom.top, bottom.width, bottom.height, hover = true)
            }
        }
        val sidePair = regions.flatMap { left -> regions.mapNotNull { right ->
            if (left.right <= right.left && left.width >= 120f && right.width >= 148f &&
                minOf(left.bottom, right.bottom) - maxOf(left.top, right.top) >= 160f) left to right else null
        } }.maxByOrNull { (left, right) -> left.area + right.area }
        if (sidePair != null) {
            val (left, right) = sidePair
            val pw = minOf(left.width, left.height * 4f / 3f)
            val dock = minOf(256f, right.width)
            return CameraGeometry(true, left.left + (left.width - pw) / 2f,
                left.top + (left.height - pw * .75f) / 2f, pw, pw * .75f, pw, 56f,
                right.right - dock, right.top, dock, right.height)
        }
        val region = regions.maxByOrNull { it.area } ?: LayoutRect(0f, 0f, w, h)
        return cameraGeometry(region.width, region.height, density, smallCover, fontScale = fontScale).let { g ->
            g.copy(previewX = g.previewX + region.left, previewY = g.previewY + region.top,
                controlsX = g.controlsX + region.left, controlsY = g.controlsY + region.top,
                toolbarX = g.toolbarX + region.left, toolbarY = g.toolbarY + region.top)
        }
    }
    if (smallCover) {
        val dock = minOf(56f, w)
        val pw = minOf((w - dock).coerceAtLeast(1f), h * .75f)
        return CameraGeometry(true, (w-dock-pw)/2, (h-pw/.75f)/2, pw, pw/.75f, 0f, 0f, w-dock, 0f, dock, h)
    }
    if (creaseLeft > 120 && creaseRight >= creaseLeft && creaseRight < w-120) {
        if (side) {
            val panel = creaseLeft-16
            val pw = minOf(panel, h*4/3)
            val dock = minOf(160f, (w-creaseRight-16).coerceAtLeast(1f))
            return CameraGeometry(true, (panel-pw)/2, (h-pw*.75f)/2, pw, pw*.75f, panel, 56f,
                w-dock, 0f, dock, h)
        }
        // A vertical separating hinge in a tall window makes one panel the usable camera area.
        val x = if (w-creaseRight > creaseLeft) creaseRight+16 else 0f
        val panel = if (x>0) w-x else creaseLeft-16
        val g = cameraGeometry(panel,h,density)
        return g.copy(previewX=g.previewX+x, controlsX=g.controlsX+x, toolbarX=x)
    }
    val hover = creaseTop > 112 && creaseBottom < h-100 && creaseBottom >= creaseTop
    if (hover) {
        val availableHeight = creaseTop - 16f
        val pw = minOf(w, availableHeight * 4f / 3f)
        val controlsY = creaseBottom + 16f
        return CameraGeometry(true, (w-pw)/2f, (availableHeight-pw*.75f)/2f, pw, pw*.75f,
            pw, 56f, 0f, controlsY, w, h-controlsY, hover = true)
    }
    if (side) {
        // 34-v3 covers ordinary phones and tablets in landscape. A phone-sized
        // landscape window always keeps the ~48dp grip, even when the device
        // aspect is below the tall-phone threshold that gates phoneGripSpace.
        // referenceLandscape stays required so foldables keep their own
        // confirmed structure instead of being pulled into this branch.
        val isPhoneSizedLandscape = referenceLandscape && isLandscapeWindow(w, h, density) && minOf(w, h) < 600f
        if (phoneGripSpace || isPhoneSizedLandscape || (referenceLandscape && isLandscapeWindow(w, h, density))) {
            val grip = if ((isPhoneSizedLandscape || phoneGripSpace) && minOf(w, h) < 600f) minOf(48f, w * .08f) else 0f
            val tools = 56f
            val dock = minOf(w * .30f, 184f + 32f * (fontScale.coerceIn(1f, 2f) - 1f))
            val previewArea = (w - tools - dock - grip).coerceAtLeast(1f)
            val pw = minOf(previewArea, h * 4f / 3f)
            val px = tools + (previewArea - pw) / 2f
            return CameraGeometry(true, px, (h - pw * .75f) / 2f,
                pw, pw * .75f, tools, h, px + pw, 0f, dock, h,
                toolbarX = px - tools, immersivePhoneLandscape = true)
        }
        val tw = 0f; val dock = (w-h*4/3).coerceIn(minOf(148f,w*.28f), minOf(256f,w*.28f))
        val ph = h
        val previewArea = w-tw-dock
        val pw = minOf(previewArea.coerceAtLeast(1f), ph*4/3)
        val cy = 0f
        return CameraGeometry(true, (previewArea-pw)/2, (ph-pw*.75f)/2, pw, pw*.75f, pw, 72f,
            w-dock, cy, dock, h-cy)
    }
    // The button row hugs the status bar: 56dp holds the 48dp project chip with 4dp of breathing
    // room above and below, instead of the 102dp band that used to push the row down and starve the
    // preview. Everything saved here goes to the camera area.
    val topBar = if (w < 368f + 120f * (fontScale.coerceAtLeast(1f) - 1f)) 104f else 56f
    val referenceBottom = minOf(254f, maxOf(150f, h - topBar - w * 4f / 3f))
    // A phone's dock includes its grip space and the controls' actual text
    // height. Let the 4:3 preview shrink before clipping that measured dock.
    val initialPhoneDockFloor = 280f + 40f * (fontScale.coerceAtLeast(1f) - 1f)
    val effectiveControlsHeight = if (minimumPortraitControlsHeight > 0f) minimumPortraitControlsHeight else initialPhoneDockFloor
    val bottomReserve = if (phoneGripSpace) maxOf(referenceBottom, effectiveControlsHeight)
        .coerceAtMost((h - topBar - 1f).coerceAtLeast(0f)) else referenceBottom
    val available = h-bottomReserve-topBar
    val pw = minOf(w, available.coerceAtLeast(1f)*.75f)
    val cy = topBar+pw*4/3
    return CameraGeometry(false, (w-pw)/2, topBar, pw, pw*4/3, w, topBar, 0f, cy, w, h-cy)
}

object CameraLayoutCache {
    @Volatile
    var cachedPortraitControlsHeight: Float = 0f
}
