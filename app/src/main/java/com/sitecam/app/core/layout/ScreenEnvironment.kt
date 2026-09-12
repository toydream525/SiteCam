package com.sitecam.app.core.layout

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import androidx.window.layout.WindowMetricsCalculator

data class ScreenEnvironment(
    val profile: ScreenProfile = ScreenProfile(),
    val fold: FoldingFeature? = null
)

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
    var fold by remember { mutableStateOf<FoldingFeature?>(null) }
    LaunchedEffect(activity) {
        if (activity != null) WindowInfoTracker.getOrCreate(activity).windowLayoutInfo(activity).collect { info ->
            fold = info.displayFeatures.filterIsInstance<FoldingFeature>().firstOrNull()
        }
    }
    // Maximum metrics exclude multi-window sizing, and update after switching displays.
    val bounds = remember(activity, configuration) {
        activity?.let { WindowMetricsCalculator.getOrCreate().computeMaximumWindowMetrics(it).bounds }
    }
    val width = bounds?.width()?.div(density) ?: configuration.screenWidthDp.toFloat()
    val height = bounds?.height()?.div(density) ?: configuration.screenHeightDp.toFloat()
    return ScreenEnvironment(screenProfile("${Build.MANUFACTURER} ${Build.MODEL}", width, height, fold != null), fold)
}
