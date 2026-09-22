package com.sitecam.app.ui.theme

import androidx.compose.ui.graphics.Color

val EngineeringYellow = Color(0xFFFFB300)
val EngineeringOrange = Color(0xFFF57C00)
val EngineeringAmber = Color(0xFFFFC107)

val DarkBackground = Color(0xFF121212)
val DarkSurface = Color(0xFF1E1E1E)
val DarkCard = Color(0xFF282828)
val DarkBorder = Color(0xFF383838)

/**
 * Camera viewfinder letterbox / control-shelf black.
 *
 * The camera route deliberately renders a true black frame (the live image and the shelves around
 * it), unlike the #121212 [DarkBackground] used by the rest of the app. Keep every camera shelf,
 * dock, scrim and the un-granted camera page on this single constant so the route never flips
 * between two near-black backgrounds.
 */
val Letterbox = Color(0xFF000000)

val LightBackground = Color(0xFFF5F5F5)
val LightSurface = Color(0xFFFFFFFF)
val LightCard = Color(0xFFF0F0F0)
val LightBorder = Color(0xFFE0E0E0)

val TextPrimaryDark = Color(0xFFFFFFFF)
val TextSecondaryDark = Color(0xFFB0BEC5)
val TextPrimaryLight = Color(0xFF1E1E1E)
val TextSecondaryLight = Color(0xFF607D8B)

val SuccessGreen = Color(0xFF4CAF50)
val WarningYellow = Color(0xFFFFC107)
val ErrorRed = Color(0xFFF44336)
val InfoBlue = Color(0xFF2196F3)
