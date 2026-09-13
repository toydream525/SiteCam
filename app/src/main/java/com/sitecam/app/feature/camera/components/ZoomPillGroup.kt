package com.sitecam.app.feature.camera.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sitecam.app.ui.theme.EngineeringYellow
import java.util.Locale
import kotlin.math.abs

/** MIUI-style lens selector: one quiet dark rail and text-only focal presets. */
@Composable
fun ZoomPillGroup(
    presets: List<Float>,
    currentZoomRatio: Float,
    onZoomSelected: (Float) -> Unit,
    modifier: Modifier = Modifier,
    isVertical: Boolean = false,
    compact: Boolean = false
) {
    if (presets.isEmpty()) return

    val visiblePresets = presets.take(5)
    val selectedRatio = visiblePresets.minByOrNull { abs(currentZoomRatio - it) }
    val buttonWidth = when {
        compact && isVertical -> 42.dp
        compact -> 38.dp
        isVertical -> 48.dp
        else -> 44.dp
    }
    val buttonHeight = when {
        compact && isVertical -> 32.dp
        compact -> 36.dp
        isVertical -> 38.dp
        else -> 44.dp
    }

    @Composable
    fun LensButton(ratio: Float) {
        // Adjacent controls such as an actual 0.54x lower bound and a 0.6x
        // convenience preset must never both look selected.
        val selected = ratio == selectedRatio
        val label = when {
            abs(ratio - ratio.toInt()) < 0.001f -> "${ratio.toInt()}×"
            ratio < 0.75f -> String.format(Locale.US, "%.2f×", ratio)
            else -> String.format(Locale.US, "%.1f×", ratio)
        }
        Box(
            modifier = Modifier
                .width(buttonWidth)
                .height(buttonHeight)
                .clickable { onZoomSelected(ratio) },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                color = if (selected) EngineeringYellow else Color.White,
                fontSize = when {
                    compact && isVertical -> 10.sp
                    compact -> 11.sp
                    isVertical -> 12.sp
                    else -> 13.sp
                },
                lineHeight = if (compact) 14.sp else 17.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip
            )
        }
    }

    Box(modifier = modifier) {
        if (isVertical) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                visiblePresets.forEach { LensButton(it) }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                visiblePresets.forEach { LensButton(it) }
            }
        }
    }
}
