package com.sitecam.app.feature.annotation

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.sitecam.app.feature.annotation.model.AnnotationElement
import org.json.JSONArray
import org.json.JSONObject

/** Ordered operations retain one coordinate space per drawing batch, followed by whole-image transforms. */
sealed interface EditStep {
    data class Draw(val elements: List<AnnotationElement>, val width: Float, val height: Float): EditStep
    data class Transform(val kind: String, val left: Float = 0f, val top: Float = 0f, val right: Float = 1f, val bottom: Float = 1f): EditStep
}

object EditRecipe {
    fun transform(bitmap: Bitmap, step: EditStep.Transform): Bitmap {
        if (step.kind == "crop") {
            val x = (step.left * bitmap.width).toInt().coerceIn(0, bitmap.width - 1)
            val y = (step.top * bitmap.height).toInt().coerceIn(0, bitmap.height - 1)
            val w = ((step.right - step.left) * bitmap.width).toInt().coerceIn(1, bitmap.width - x)
            val h = ((step.bottom - step.top) * bitmap.height).toInt().coerceIn(1, bitmap.height - y)
            return Bitmap.createBitmap(bitmap, x, y, w, h)
        }
        val matrix = Matrix().apply { when (step.kind) {
            "horizontal" -> setScale(-1f, 1f)
            "vertical" -> setScale(1f, -1f)
            "rotate" -> setRotate(90f)
        } }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
    fun encode(steps: List<EditStep>): String = JSONObject().put("version", 1).put("steps", JSONArray().apply {
        steps.forEach { step -> put(when (step) {
            is EditStep.Transform -> JSONObject().put("kind", step.kind).put("l", step.left).put("t", step.top).put("r", step.right).put("b", step.bottom)
            is EditStep.Draw -> JSONObject().put("kind", "draw").put("w", step.width).put("h", step.height).put("elements", JSONArray().apply { step.elements.forEach { put(elementJson(it)) } })
        }) }
    }).toString()
    fun decode(json: String): List<EditStep> = runCatching {
        val steps = JSONObject(json).getJSONArray("steps")
        (0 until steps.length()).map { index -> val item = steps.getJSONObject(index)
            if (item.getString("kind") == "draw") {
                val elements = item.getJSONArray("elements")
                EditStep.Draw((0 until elements.length()).map { elementFromJson(elements.getJSONObject(it)) }, item.getDouble("w").toFloat(), item.getDouble("h").toFloat())
            } else EditStep.Transform(item.getString("kind"), item.optDouble("l", 0.0).toFloat(), item.optDouble("t", 0.0).toFloat(), item.optDouble("r", 1.0).toFloat(), item.optDouble("b", 1.0).toFloat())
        }
    }.getOrDefault(emptyList())
    private fun point(point: Offset) = JSONArray().put(point.x).put(point.y)
    private fun JSONObject.point(key: String): Offset = getJSONArray(key).let { Offset(it.getDouble(0).toFloat(), it.getDouble(1).toFloat()) }
    private fun elementJson(e: AnnotationElement): JSONObject = JSONObject().put("color", e.color.toArgb()).put("stroke", e.strokeWidth).apply {
        when(e) {
            is AnnotationElement.Arrow -> { put("type", "arrow"); put("a", point(e.start)); put("b", point(e.end)) }
            is AnnotationElement.Rectangle -> { put("type", "rect"); put("a", point(e.topLeft)); put("b", point(e.bottomRight)) }
            is AnnotationElement.Circle -> { put("type", "circle"); put("a", point(e.center)); put("radius", e.radius) }
            is AnnotationElement.TextNote -> { put("type", "text"); put("a", point(e.position)); put("text", e.text) }
            is AnnotationElement.Freehand -> { put("type", "pen"); put("points", JSONArray().apply { e.points.forEach { put(point(it)) } }) }
            is AnnotationElement.Mosaic -> { put("type", "mosaic"); put("points", JSONArray().apply { e.points.forEach { put(point(it)) } }) }
        }
    }
    private fun elementFromJson(o: JSONObject): AnnotationElement {
        val c = Color(o.getInt("color")); val w = o.getDouble("stroke").toFloat()
        return when(o.getString("type")) {
            "arrow" -> AnnotationElement.Arrow(o.point("a"), o.point("b"), c, w)
            "rect" -> AnnotationElement.Rectangle(o.point("a"), o.point("b"), c, w)
            "circle" -> AnnotationElement.Circle(o.point("a"), o.getDouble("radius").toFloat(), c, w)
            "text" -> AnnotationElement.TextNote(o.point("a"), o.getString("text"), c, w)
            else -> { val a = o.getJSONArray("points"); val points = (0 until a.length()).map { val p = a.getJSONArray(it); Offset(p.getDouble(0).toFloat(), p.getDouble(1).toFloat()) }
                if(o.getString("type") == "mosaic") AnnotationElement.Mosaic(points,c,w) else AnnotationElement.Freehand(points,c,w) }
        }
    }
}
