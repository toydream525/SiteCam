package com.sitecam.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = EngineeringYellow,
    onPrimary = DarkBackground,
    primaryContainer = EngineeringOrange,
    onPrimaryContainer = TextPrimaryDark,
    secondary = EngineeringAmber,
    onSecondary = DarkBackground,
    background = DarkBackground,
    onBackground = TextPrimaryDark,
    surface = DarkSurface,
    onSurface = TextPrimaryDark,
    surfaceVariant = DarkCard,
    onSurfaceVariant = TextSecondaryDark,
    outline = DarkBorder
)

private val LightColorScheme = lightColorScheme(
    primary = EngineeringOrange,
    onPrimary = LightSurface,
    primaryContainer = EngineeringYellow,
    onPrimaryContainer = TextPrimaryLight,
    secondary = EngineeringAmber,
    onSecondary = LightSurface,
    background = LightBackground,
    onBackground = TextPrimaryLight,
    surface = LightSurface,
    onSurface = TextPrimaryLight,
    surfaceVariant = LightCard,
    onSurfaceVariant = TextSecondaryLight,
    outline = LightBorder
)

@Composable
fun SiteCamTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            val insetsController = WindowCompat.getInsetsController(window, view)
            // MainActivity enables edge-to-edge. Keep dark system icons off
            // without using the API-35-deprecated bar color setters.
            insetsController.isAppearanceLightStatusBars = false
            insetsController.isAppearanceLightNavigationBars = false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.isNavigationBarContrastEnforced = false
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
