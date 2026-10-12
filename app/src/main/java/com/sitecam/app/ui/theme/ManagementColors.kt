package com.sitecam.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val PageBackground: Color @Composable get() = MaterialTheme.colorScheme.background
val PageSurface: Color @Composable get() = MaterialTheme.colorScheme.surface
val PageCard: Color @Composable get() = MaterialTheme.colorScheme.surface
val PageBorder: Color @Composable get() = MaterialTheme.colorScheme.outline
val PageText: Color @Composable get() = MaterialTheme.colorScheme.onSurface
val PageMuted: Color @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant

/** Accent for text and icons; deep amber on light surfaces, brand yellow on dark ones. */
val AccentText: Color @Composable get() = LocalAccentText.current
