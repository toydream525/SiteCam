package com.sitecam.app.feature.camera.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.sitecam.app.ui.theme.EngineeringYellow
import com.sitecam.app.ui.theme.ErrorRed
import kotlin.math.roundToInt

@Composable
fun FocusRing(
    position: Offset?,
    result: Boolean? = null,
    requestId: Int = 0,
    onAnimationEnd: () -> Unit
) {
    if (position == null) return

    val scale = remember { Animatable(1.5f) }
    val alpha = remember { Animatable(1f) }

    LaunchedEffect(position, result, requestId) {
        scale.snapTo(1.5f)
        alpha.snapTo(1f)

        scale.animateTo(
            targetValue = 1.0f,
            animationSpec = tween(durationMillis = 250)
        )
        if (result != null) {
            alpha.animateTo(targetValue = 0f, animationSpec = tween(durationMillis = 300, delayMillis = 700))
            onAnimationEnd()
        }
    }

    val ringSize = 72.dp

    Canvas(
        modifier = Modifier
            .size(ringSize)
            .offset {
                IntOffset(
                    x = (position.x - 36.dp.toPx()).roundToInt(),
                    y = (position.y - 36.dp.toPx()).roundToInt()
                )
            }
    ) {
        val radius = (size.minDimension / 2f) * scale.value
        drawCircle(
            color = (when(result) { true -> Color(0xFF68C77A); false -> ErrorRed; null -> EngineeringYellow }).copy(alpha = alpha.value),
            radius = radius,
            center = center,
            style = Stroke(width = 2.dp.toPx())
        )
    }
}
