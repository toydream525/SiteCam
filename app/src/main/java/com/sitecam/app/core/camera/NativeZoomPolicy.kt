package com.sitecam.app.core.camera

import kotlin.math.abs

/** Routes one continuous main-camera zoom scale using only public camera metadata. */
object NativeZoomPolicy {
    fun usableLenses(lenses: List<CameraLensCapability>, active: CameraLensCapability): List<CameraLensCapability> =
        lenses.filter { it.lensFacing == active.lensFacing && it.appAccessible && it.photoBindable }

    fun nativeRatios(lens: CameraLensCapability): List<Float> {
        val reference = if (lens.parentLogicalCameraId != null) lens.equivalentZoomRatio ?: return emptyList()
            else CameraZoomMapping.referenceRatioForActiveLens(lens)
        // A logical group's physical IDs do not prove that setZoomRatio
        // will select those members. Offer only this public session's native
        // 1x; the rest of its system-managed range remains available to pinch.
        return listOf(reference).filter { it.isFinite() && it > 0f && it in displayRange(lens) }
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
        if (!exactNativeStop && requestedRatio in activeRange && requestedRatio < bestNative * 1.02f) return active
        return best
    }
}
