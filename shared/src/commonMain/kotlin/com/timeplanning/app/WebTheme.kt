package com.timeplanning.app

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** The web accent — the primary (re-plan) button, the selected nav item's edge, focus. */
val WebAccent = Color(0xFF6E3D5F)

/** The selected sidebar item's soft fill. */
val WebAccentSoft = Color(0xFFEBD8E3)

/** Sidebar sits a shade lighter than the warm page. */
val WebSidebarBackground = Color(0xFFFCF9F7)

/** Calendar colours for things that aren't a task category: fixed commitments and Google Calendar events. */
val WebCommitmentColor = Color(0xFFE7DCD6)
val WebCalendarEventColor = Color(0xFFF6CCD6)

/**
 * A distinct, soft look for the web target: a warm blush-white page, hairline
 * dividers, muted plum-grey secondary text, and one plum accent (WebAccent).
 * Navigation is a persistent left sidebar (WebSidebar) rather than the phone's
 * bottom bar.
 */
val WebColorScheme: ColorScheme = lightColorScheme(
    primary = WebAccent,
    onPrimary = Color.White,
    primaryContainer = WebAccentSoft,
    onPrimaryContainer = Color(0xFF3A1F31),
    secondary = Color(0xFF7A6872),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF1E6EA),
    onSecondaryContainer = Color(0xFF2E2229),
    background = Color(0xFFF8F2EF),
    onBackground = Color(0xFF2E2229),
    surface = Color(0xFFF8F2EF),
    onSurface = Color(0xFF2E2229),
    surfaceVariant = Color(0xFFF0E8E4),
    onSurfaceVariant = Color(0xFF85767E),
    outline = Color(0xFFE3D8D3),
    outlineVariant = Color(0xFFEDE4E0),
    error = Color(0xFFB3261E),
)

/** True wherever the web look applies (square cards, the web text scale) — now the whole app, phone included. Separate from LocalInWebShell, which only hides the phone's bottom bar inside the web sidebar layout. */
val LocalWebStyle = staticCompositionLocalOf { false }

/** True inside the web shell (WebSidebar + page) — the per-screen navigation bars (BottomNavBar, WebTopNav) hide themselves, since the sidebar already provides it. */
val LocalInWebShell = staticCompositionLocalOf { false }

/** Content stays this wide at most on web, centered on the page background — see App.kt. */
val WebContentMaxWidth = 720
