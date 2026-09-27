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
    // Neutral cool-white grounds (matching the reference designs) rather than a teal tint, so every
    // screen — including ones that don't sit inside a Scaffold — reads as the same colour.
    background = Color(0xFFF8F8FA),
    onBackground = Color(0xFF15151C),
    surface = Color(0xFFF8F8FA),
    onSurface = Color(0xFF15151C),
    surfaceVariant = Color(0xFFEEF0F3),
    onSurfaceVariant = Color(0xFF6C6C78),
    outline = Color(0xFFD7D7DD),
    outlineVariant = Color(0xFFE5E5EA),
    error = Color(0xFFBA1A1A),
)
