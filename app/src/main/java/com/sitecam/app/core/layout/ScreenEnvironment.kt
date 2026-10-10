package com.sitecam.app.core.layout

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Constraints
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import androidx.window.layout.WindowMetricsCalculator

data class ScreenEnvironment(
    val profile: ScreenProfile = ScreenProfile(),
    val folds: List<FoldingFeature> = emptyList(),
    val foldableDevice: Boolean = false,
    val phoneGripEligible: Boolean = false
) {
    val fold: FoldingFeature? get() = folds.firstOrNull()
}

fun ScreenEnvironment.localFolds(originX: Float, originY: Float, density: Float): List<LayoutRect> =
    folds.filter { it.isSeparating || it.occlusionType == FoldingFeature.OcclusionType.FULL ||
        it.state == FoldingFeature.State.HALF_OPENED }.map {
        LayoutRect((it.bounds.left - originX) / density, (it.bounds.top - originY) / density,
            (it.bounds.right - originX) / density, (it.bounds.bottom - originY) / density)
    }

/** Stable slots keep browsing/editing state while only their measured rectangles change. */
@Composable
fun AdaptivePaneLayout(
    modifier: Modifier = Modifier,
    showSecondary: Boolean = false,
    allowTwoPane: Boolean = true,
    primaryMinWidth: Float = 320f,
    secondaryMinWidth: Float = 360f,
    primaryFraction: Float = .4f,
    primary: @Composable (split: Boolean) -> Unit,
    secondary: @Composable () -> Unit = {}
) {
    val environment = rememberScreenEnvironment()
    val density = LocalDensity.current
    val view = LocalView.current
    val activity = LocalContext.current.activity()
    var origin by remember { mutableStateOf(Offset.Zero) }
    var dimensions by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    val arrangement = adaptivePaneArrangement(dimensions.width / density.density, dimensions.height / density.density,
        density.fontScale, environment.localFolds(origin.x, origin.y, density.density), allowTwoPane,
        primaryMinWidth, secondaryMinWidth, primaryFraction)
    Layout(
        modifier = modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).onGloballyPositioned { coordinates ->
            val screen = IntArray(2); val window = IntArray(2)
            view.getLocationOnScreen(screen); view.getLocationInWindow(window)
            val hostScreen = IntArray(2); val hostWindow = IntArray(2)
            activity?.window?.decorView?.let { it.getLocationOnScreen(hostScreen); it.getLocationInWindow(hostWindow) }
            // FoldingFeature bounds belong to the activity window, including when this is a Dialog.
            origin = coordinates.positionInWindow() + Offset(
                (screen[0] - window[0] - hostScreen[0] + hostWindow[0]).toFloat(),
                (screen[1] - window[1] - hostScreen[1] + hostWindow[1]).toFloat())
            dimensions = coordinates.size
        },
        content = {
            Box(Modifier.fillMaxSize().clipToBounds()) { primary(arrangement.split) }
            Box(Modifier.fillMaxSize().clipToBounds()) { secondary() }
        }
    ) { measurables, constraints ->
        val current = adaptivePaneArrangement(constraints.maxWidth / density.density, constraints.maxHeight / density.density,
            density.fontScale, environment.localFolds(origin.x, origin.y, density.density), allowTwoPane,
            primaryMinWidth, secondaryMinWidth, primaryFraction)
        val second = current.secondary ?: current.primary
        fun LayoutRect.measure(index: Int) = measurables[index].measure(Constraints.fixed(
            (width * density.density).toInt().coerceAtLeast(0), (height * density.density).toInt().coerceAtLeast(0)))
        val firstPlaceable = current.primary.measure(0)
        val secondPlaceable = second.measure(1)
        layout(constraints.maxWidth, constraints.maxHeight) {
            if (current.split || !showSecondary) firstPlaceable.place(
                (current.primary.left * density.density).toInt(), (current.primary.top * density.density).toInt())
            if (current.split || showSecondary) secondPlaceable.place(
                (second.left * density.density).toInt(), (second.top * density.density).toInt())
        }
    }
}

private fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}

@Composable
fun rememberScreenEnvironment(): ScreenEnvironment {
    val context = LocalContext.current
    val activity = context.activity()
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current.density
    var folds by remember { mutableStateOf<List<FoldingFeature>>(emptyList()) }
    LaunchedEffect(activity) {
        if (activity != null) WindowInfoTracker.getOrCreate(activity).windowLayoutInfo(activity).collect { info ->
            folds = info.displayFeatures.filterIsInstance<FoldingFeature>()
        }
    }
    // Recompute for the current activity window, including split and freeform resizing.
    val bounds = remember(activity, configuration) {
        activity?.let { WindowMetricsCalculator.getOrCreate().computeCurrentWindowMetrics(it).bounds }
    }
    val width = bounds?.width()?.div(density) ?: configuration.screenWidthDp.toFloat()
    val height = bounds?.height()?.div(density) ?: configuration.screenHeightDp.toFloat()
    // Outer displays often have no current FoldingFeature. Keep their
    // confirmed layout by consulting the device capability as well.
    val foldableCapability = remember(activity) {
        activity?.let {
            runCatching { androidx.window.WindowSdkExtensions.getInstance().extensionVersion >= 6 &&
                WindowInfoTracker.getOrCreate(it).supportedPostures.isNotEmpty() }.getOrDefault(false) ||
                it.packageManager.hasSystemFeature("android.hardware.sensor.hinge_angle")
        } ?: false
    }
    var observedFold by remember(activity) { mutableStateOf(false) }
    LaunchedEffect(folds) { if (folds.isNotEmpty()) observedFold = true }
    val maximumBounds = remember(activity, configuration) {
        activity?.let { WindowMetricsCalculator.getOrCreate().computeMaximumWindowMetrics(it).bounds }
    }
    val deviceWidth = maximumBounds?.width()?.div(density) ?: width
    val deviceHeight = maximumBounds?.height()?.div(density) ?: height
    val foldable = foldableCapability || observedFold || folds.isNotEmpty()
    val normalTallPhone = !foldable && minOf(deviceWidth, deviceHeight) < 600f &&
        maxOf(deviceWidth, deviceHeight) / minOf(deviceWidth, deviceHeight).coerceAtLeast(1f) >= 1.85f
    return ScreenEnvironment(screenProfile("", width, height, folds.isNotEmpty()), folds,
        foldable, normalTallPhone)
}
