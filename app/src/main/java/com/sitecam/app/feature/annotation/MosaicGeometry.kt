package com.sitecam.app.feature.annotation

import androidx.compose.ui.geometry.Offset
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.max

/** Lightweight samples used by both preview and final bitmap masking. */
object MosaicGeometry {
    fun samplePolyline(points: List<Offset>, step: Float): List<Offset> {
        if (points.isEmpty()) return emptyList()
        if (points.size == 1 || step <= 0f) return points
        val result = ArrayList<Offset>(points.size)
        result += points.first()
        for (index in 1 until points.size) {
            val start = points[index - 1]
            val end = points[index]
            val distance = hypot(end.x - start.x, end.y - start.y)
            val count = max(1, ceil(distance / step).toInt())
            for (i in 1..count) {
                val fraction = i.toFloat() / count
                result += Offset(
                    x = start.x + (end.x - start.x) * fraction,
                    y = start.y + (end.y - start.y) * fraction
                )
            }
        }
        return result
    }
}
