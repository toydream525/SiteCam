package com.sitecam.app.feature.gallery

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlin.math.abs

@Composable
fun ZoomablePhoto(uri: String, description: String, onSwipe: ((Int) -> Unit)? = null) {
    var scale by rememberSaveable(uri) { mutableFloatStateOf(1f) }
    var offset by rememberSaveable(uri, stateSaver = Saver<Offset, List<Float>>(save = { listOf(it.x, it.y) }, restore = { Offset(it[0], it[1]) })) { mutableStateOf(Offset.Zero) }
    val context = androidx.compose.ui.platform.LocalContext.current
    var imageSize by remember(uri) { mutableStateOf(IntSize.Zero) }
    val request = remember(uri) { coil.request.ImageRequest.Builder(context).data(uri).size(coil.size.Size.ORIGINAL).build() }
    var size by remember { mutableStateOf(IntSize.Zero) }
    fun bound(value: Offset, zoom: Float): Offset {
        val fit = if (imageSize.width > 0 && imageSize.height > 0) minOf(size.width.toFloat() / imageSize.width, size.height.toFloat() / imageSize.height) else 1f
        val maxX = ((imageSize.width * fit * zoom - size.width) / 2f).coerceAtLeast(0f)
        val maxY = ((imageSize.height * fit * zoom - size.height) / 2f).coerceAtLeast(0f)
        return Offset(value.x.coerceIn(-maxX, maxX), value.y.coerceIn(-maxY, maxY))
    }
    LaunchedEffect(size, imageSize) { offset = bound(offset, scale) }
    val swipe by rememberUpdatedState(onSwipe)
    Box(Modifier.fillMaxSize().clipToBounds().onSizeChanged { size = it }
        .pointerInput(uri) { detectTapGestures(onDoubleTap = { point ->
            if (scale > 1f) { scale = 1f; offset = Offset.Zero }
            else { scale = 2f; offset = bound(Offset(size.width / 2f, size.height / 2f) - point, scale) }
        }) }
        .pointerInput(uri) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                var singleFinger = true
                var pastSlop = false
                var paging = false
                var gestureZoom = 1f
                var gesturePan = Offset.Zero
                do {
                    val event = awaitPointerEvent()
                    if (event.changes.any { it.isConsumed }) break
                    singleFinger = singleFinger && event.changes.size == 1
                    val zoom = event.calculateZoom()
                    val pan = event.calculatePan()
                    if (!pastSlop) {
                        gestureZoom *= zoom
                        gesturePan += pan
                        val zoomDistance = abs(1f - gestureZoom) * event.calculateCentroidSize(useCurrent = false)
                        pastSlop = maxOf(zoomDistance, gesturePan.getDistance()) > viewConfiguration.touchSlop
                        paging = pastSlop && singleFinger && scale == 1f && swipe != null &&
                            abs(gesturePan.x) > abs(gesturePan.y)
                    }
                    if (!pastSlop) continue
                    paging = paging && singleFinger
                    if (paging) {
                        val change = event.changes.single()
                        val distance = change.position.x - down.position.x
                        if (change.positionChanged()) change.consume()
                        if (!change.pressed && abs(distance) >= 56.dp.toPx()) swipe?.invoke(if (distance < 0f) 1 else -1)
                    } else if (scale > 1f || !singleFinger) {
                        val centroid = event.calculateCentroid(useCurrent = false)
                        if (centroid != Offset.Unspecified) {
                            val next = (scale * zoom).coerceIn(1f, 8f)
                            val center = Offset(size.width / 2f, size.height / 2f)
                            offset = bound((offset + center - centroid) * (next / scale) + centroid - center + pan, next)
                            scale = next
                        }
                        event.changes.forEach { if (it.positionChanged()) it.consume() }
                    }
                } while (event.changes.any { it.pressed })
            }
        }) {
        AsyncImage(request, description, Modifier.fillMaxSize().graphicsLayer {
            scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y
        }, contentScale = ContentScale.Fit, onSuccess = { state -> imageSize = IntSize(state.result.drawable.intrinsicWidth, state.result.drawable.intrinsicHeight) })
    }
}
