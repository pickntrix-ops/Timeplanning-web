package com.timeplanning.app

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Mobile's own look — distinct from WebTheme.kt's indigo (that split was
 * deliberate, requested separately: "I want the web UI to be different
 * than the app"). A teal accent instead of Material's bare purple default,
 * same structural approach as WebColorScheme (a lightColorScheme() role
 * override, no dark theme yet).
 */
val AppColorScheme: ColorScheme = lightColorScheme(
    primary = Color(0xFF00796B),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCCE9E4),
    onPrimaryContainer = Color(0xFF002420),
    secondary = Color(0xFF4A635F),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCEE9E3),
    onSecondaryContainer = Color(0xFF06201C),
    background = Color(0xFFF4FAF8),
    onBackground = Color(0xFF171D1C),
    surface = Color(0xFFF4FAF8),
    onSurface = Color(0xFF171D1C),
    surfaceVariant = Color(0xFFDAE5E2),
    onSurfaceVariant = Color(0xFF3F4947),
    outline = Color(0xFFAAB5B2),
    outlineVariant = Color(0xFFC9D5D2),
    error = Color(0xFFBA1A1A),
)
