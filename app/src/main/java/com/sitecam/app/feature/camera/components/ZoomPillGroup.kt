package com.sitecam.app.feature.camera.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sitecam.app.ui.theme.EngineeringYellow
import java.util.Locale
import kotlin.math.abs

/**
 * MIUI-style lens selector: one quiet dark rail and text-only focal presets.
 *
 * Each preset is a Material touch target (`heightIn(min = 48.dp)`) and grows with the text it
 * actually renders instead of clipping it. When the scaled labels no longer fit the window the
 * horizontal rail scrolls and keeps the selected preset visible, which is this project's agreed
 * large-font behaviour: widen by the real text width, never shrink the type or the button.
 */
@Composable
fun ZoomPillGroup(
    presets: List<Float>,
    currentZoomRatio: Float,
    onZoomSelected: (Float) -> Unit,
    modifier: Modifier = Modifier,
    isVertical: Boolean = false,
    compact: Boolean = false,
    photoOverlay: Boolean = false,
    enabled: Boolean = true
) {
    if (presets.isEmpty()) return

    val visiblePresets = presets.distinct().sorted()
    val selectedRatio = visiblePresets.lastOrNull { it <= currentZoomRatio + .001f } ?: visiblePresets.firstOrNull()
    // Minimum width only: the button may grow past it to fit its label at large font scales.
    val buttonWidth = when {
        photoOverlay -> 48.dp
        compact && isVertical -> 42.dp
        compact -> 44.dp
        isVertical -> 48.dp
        else -> 60.dp
    }

    val scrollState = rememberScrollState()
    val itemBounds = remember { mutableStateMapOf<Float, ClosedFloatingPointRange<Float>>() }
    var viewportWidth by remember { mutableStateOf(0) }
    // Bumped only when a measurement really changes, so the scroll-into-view effect settles after
    // one extra layout pass instead of running on every recomposition.
    var measuredRevision by remember { mutableStateOf(0) }

    LaunchedEffect(selectedRatio, viewportWidth, measuredRevision) {
        val ratio = selectedRatio ?: return@LaunchedEffect
        val bounds = itemBounds[ratio] ?: return@LaunchedEffect
        if (viewportWidth <= 0) return@LaunchedEffect
        val target = when {
            bounds.start < scrollState.value -> bounds.start
            bounds.endInclusive > scrollState.value + viewportWidth -> bounds.endInclusive - viewportWidth
            else -> return@LaunchedEffect
        }
        scrollState.animateScrollTo(target.toInt().coerceIn(0, scrollState.maxValue))
    }

    @Composable
    fun LensButton(ratio: Float, lensModifier: Modifier = Modifier) {
        // Adjacent controls such as an actual 0.54x lower bound and a 0.6x
        // convenience preset must never both look selected.
        val selected = ratio == selectedRatio
        val label = when {
            abs(ratio - ratio.toInt()) < 0.001f -> "${ratio.toInt()}×"
            ratio < 0.75f -> String.format(Locale.US, "%.2f×", ratio)
            else -> String.format(Locale.US, "%.1f×", ratio)
        }
        Box(
            modifier = lensModifier
                .then(if (photoOverlay) Modifier.fillMaxWidth() else Modifier)
                .widthIn(min = buttonWidth)
                .heightIn(min = if(compact) 48.dp else 60.dp)
                .background(if(selected && !photoOverlay) EngineeringYellow else Color.Transparent, RoundedCornerShape(30.dp))
                .then(if (photoOverlay && selected) Modifier.border(1.dp, EngineeringYellow, RoundedCornerShape(30.dp)) else Modifier)
                .clickable(enabled = enabled) { onZoomSelected(ratio) }
                .then(if (photoOverlay) Modifier.padding(horizontal = 6.dp) else Modifier),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                color = if (selected) { if (photoOverlay) EngineeringYellow else Color.Black } else Color.White,
                fontSize = when {
                    photoOverlay -> 14.sp
                    compact && isVertical -> 10.sp
                    compact -> 14.sp
                    isVertical -> 12.sp
                    else -> 18.sp
                },
                lineHeight = if (compact) 16.sp else 22.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                softWrap = false,
                // Never Clip: the button is sized by the text, and the ellipsis only guards a
                // pathological window that is narrower than a single label.
                overflow = TextOverflow.Ellipsis
            )
        }
    }

    Box(modifier = modifier.background(Color.Black.copy(alpha = if(photoOverlay) .18f else .72f), RoundedCornerShape(24.dp))
        .onSizeChanged { if (isVertical) viewportWidth = it.height }) {
        if (isVertical) {
            Column(Modifier.fillMaxWidth().verticalScroll(scrollState), horizontalAlignment = Alignment.CenterHorizontally) {
                visiblePresets.forEach { ratio ->
                    LensButton(ratio, Modifier.onGloballyPositioned { coordinates ->
                        val start = coordinates.positionInParent().y
                        val end = start + coordinates.size.height
                        val previous = itemBounds[ratio]
                        if (previous == null || previous.start != start || previous.endInclusive != end) {
                            itemBounds[ratio] = start..end
                            measuredRevision += 1
                        }
                    })
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .horizontalScroll(scrollState)
                    .onSizeChanged { viewportWidth = it.width },
                verticalAlignment = Alignment.CenterVertically
            ) {
                visiblePresets.forEach { ratio ->
                    LensButton(
                        ratio = ratio,
                        lensModifier = Modifier.onGloballyPositioned { coordinates ->
                            val start = coordinates.positionInParent().x
                            val end = start + coordinates.size.width
                            val previous = itemBounds[ratio]
                            if (previous == null || previous.start != start || previous.endInclusive != end) {
                                itemBounds[ratio] = start..end
                                measuredRevision += 1
                            }
                        }
                    )
                }
            }
        }
    }
}
