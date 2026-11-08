package com.sitecam.app.feature.gallery

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.abs

internal fun Modifier.gallerySwipe(onSwipe: ((Int) -> Unit)?): Modifier =
    if (onSwipe == null) this else pointerInput(onSwipe) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            var horizontal = false
            val threshold = 56.dp.toPx()
            do {
                val event = awaitPointerEvent()
                val change = event.changes.singleOrNull() ?: return@awaitEachGesture
                if (change.id != down.id || change.isConsumed) return@awaitEachGesture
                val delta = change.position - down.position
                if (!horizontal && abs(delta.y) > viewConfiguration.touchSlop &&
                    abs(delta.y) >= abs(delta.x)) return@awaitEachGesture
                if (abs(delta.x) > viewConfiguration.touchSlop && abs(delta.x) > abs(delta.y)) {
                    horizontal = true
                }
                if (horizontal) {
                    change.consume()
                    if (!change.pressed && abs(delta.x) >= threshold) onSwipe(if (delta.x < 0f) 1 else -1)
                }
            } while (change.pressed)
        }
    }
