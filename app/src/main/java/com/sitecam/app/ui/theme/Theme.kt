package com.sitecam.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/** Accent for text and icons; light surfaces get [AccentTextLight] instead of the brand yellow. */
val LocalAccentText = staticCompositionLocalOf { EngineeringYellow }

private val DarkColorScheme = darkColorScheme(
    primary = EngineeringYellow,
    onPrimary = DarkBackground,
    primaryContainer = EngineeringOrange,
    onPrimaryContainer = TextPrimaryDark,
    secondary = EngineeringAmber,
    secondaryContainer = EngineeringYellow,
    onSecondaryContainer = TextPrimaryLight,
    tertiary = EngineeringYellow,
    tertiaryContainer = EngineeringYellow,
    onTertiaryContainer = TextPrimaryLight,
    onSecondary = DarkBackground,
    background = DarkBackground,
    onBackground = TextPrimaryDark,
    surface = DarkSurface,
    onSurface = TextPrimaryDark,
    surfaceVariant = DarkCard,
    surfaceContainerLowest = DarkBackground,
    surfaceContainerLow = DarkSurface,
    surfaceContainer = DarkCard,
    surfaceContainerHigh = DarkCard,
    surfaceContainerHighest = DarkSurface,
    onSurfaceVariant = TextSecondaryDark,
    outline = DarkBorder
)

private val LightColorScheme = lightColorScheme(
    primary = EngineeringYellow,
    onPrimary = TextPrimaryLight,
    primaryContainer = EngineeringYellow,
    onPrimaryContainer = TextPrimaryLight,
    secondary = EngineeringAmber,
    secondaryContainer = EngineeringYellow,
    onSecondaryContainer = TextPrimaryLight,
    tertiary = EngineeringYellow,
    tertiaryContainer = EngineeringYellow,
    onTertiaryContainer = TextPrimaryLight,
    onSecondary = LightSurface,
    background = LightBackground,
    onBackground = TextPrimaryLight,
    surface = LightSurface,
    onSurface = TextPrimaryLight,
    surfaceVariant = LightCard,
    surfaceContainerLowest = LightBackground,
    surfaceContainerLow = LightSurface,
    surfaceContainer = LightSurface,
    surfaceContainerHigh = LightCard,
    surfaceContainerHighest = LightCard,
    onSurfaceVariant = TextSecondaryLight,
    outline = LightBorder
)

@Composable
fun SiteCamTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    updateSystemBars: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current
    if (updateSystemBars && !view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            val insetsController = WindowCompat.getInsetsController(window, view)
            // MainActivity enables edge-to-edge. Keep dark system icons off
            // without using the API-35-deprecated bar color setters.
            insetsController.isAppearanceLightStatusBars = !darkTheme
            insetsController.isAppearanceLightNavigationBars = !darkTheme
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.isNavigationBarContrastEnforced = false
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography
    ) {
        CompositionLocalProvider(LocalAccentText provides if (darkTheme) EngineeringYellow else AccentTextLight) {
            content()
        }
    }
}
