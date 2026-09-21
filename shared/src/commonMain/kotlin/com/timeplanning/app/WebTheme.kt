package com.timeplanning.app

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * A distinct look for the web target — same screens as the app (Today,
 * Add Task, Calendar), but styled for a desktop browser rather than a phone:
 * a cleaner indigo accent than mobile's default Material baseline, and (see
 * the content-width cap in App.kt) a centered panel instead of a
 * full-bleed stretch across a wide viewport.
 */
val WebColorScheme: ColorScheme = lightColorScheme(
    primary = Color(0xFF5B4FE0),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE3E0FF),
    onPrimaryContainer = Color(0xFF160F52),
    secondary = Color(0xFF5F5C71),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE5E0F9),
    onSecondaryContainer = Color(0xFF1B1830),
    background = Color(0xFFEEEDF6),
    onBackground = Color(0xFF1B1B23),
    surface = Color.White,
    onSurface = Color(0xFF1B1B23),
    surfaceVariant = Color(0xFFE5E1EC),
    onSurfaceVariant = Color(0xFF47464F),
    outline = Color(0xFFC9C5D3),
    outlineVariant = Color(0xFFDDD9E5),
    error = Color(0xFFBA1A1A),
)

/** Content stays this wide at most on web, centered on the page background — see App.kt. */
val WebContentMaxWidth = 720
