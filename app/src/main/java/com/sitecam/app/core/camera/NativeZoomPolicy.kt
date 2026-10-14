package com.sitecam.app.core.camera

import kotlin.math.abs
import kotlin.math.round

/** Routes one continuous main-camera zoom scale using only public camera metadata. */
object NativeZoomPolicy {
    fun shortcutRatio(measured: Float, range: ClosedFloatingPointRange<Float>): Float {
        if (!measured.isFinite() || measured < 1.5f) return measured
        val nominal = round(measured * 2f) / 2f
        // Only round upward: a shortcut must not request less than the optical
        // boundary. Both the label and the actual requested value use this ratio.
        return if (nominal >= measured && nominal - measured <= measured * .025f &&
            nominal in range) nominal else measured
    }

    fun usableLenses(lenses: List<CameraLensCapability>, active: CameraLensCapability): List<CameraLensCapability> =
        lenses.filter { it.lensFacing == active.lensFacing && it.appAccessible && it.photoBindable }

    fun nativeRatios(lens: CameraLensCapability): List<Float> {
        val reference = if (lens.parentLogicalCameraId != null) lens.equivalentZoomRatio ?: return emptyList()
            else CameraZoomMapping.referenceRatioForActiveLens(lens)
        // A logical group's physical IDs do not prove that setZoomRatio
        // will select those members. Offer only this public session's native
        // 1x; the rest of its system-managed range remains available to pinch.
        val range = displayRange(lens)
        return listOf(shortcutRatio(reference, range)).filter { it.isFinite() && it > 0f && it in range }
    }

    fun displayRange(lens: CameraLensCapability): ClosedFloatingPointRange<Float> {
        val reference = CameraZoomMapping.referenceRatioForActiveLens(lens)
        val min = lens.minSessionZoomRatio.coerceAtLeast(.01f) * reference
        val max = lens.maxSessionZoomRatio.coerceAtLeast(lens.minSessionZoomRatio) * reference
        return min..max
    }

    fun presets(lenses: List<CameraLensCapability>): List<Float> = lenses.flatMap(::nativeRatios)
        .sorted().fold(emptyList()) { result, ratio ->
            if (result.any { abs(it - ratio) < .025f }) result else result + ratio
        }

    fun targetLens(
        lenses: List<CameraLensCapability>,
        active: CameraLensCapability,
        requestedRatio: Float,
        exactNativeStop: Boolean = false
    ): CameraLensCapability {
        val available = usableLenses(lenses, active)
        val activeRange = displayRange(active)
        // Only public sessions enter this list. Physical metadata by itself
        // must never create a selectable lens or an optical shortcut.
        val logicalCoverage = nativeRatios(active)
        val activeBest = logicalCoverage.filter { it <= requestedRatio + .001f }.maxOrNull() ?: 0f
        val candidates = available.filter { requestedRatio in displayRange(it) }
        val best = candidates.maxByOrNull { lens ->
            nativeRatios(lens).filter { it <= requestedRatio + .001f }.maxOrNull() ?: 0f
        } ?: return active
        val bestNative = nativeRatios(best).filter { it <= requestedRatio + .001f }.maxOrNull() ?: 0f
        if (requestedRatio in activeRange && bestNative <= activeBest + .025f) return active
        // A small upward threshold avoids repeated rebinds around a native
        // boundary. Downward crossing leaves immediately so no request is
        // clamped below the current lens's optical minimum.
        if (!exactNativeStop && abs(requestedRatio - bestNative) > .001f &&
            requestedRatio in activeRange && requestedRatio < bestNative * 1.02f) return active
        return best
    }
}
