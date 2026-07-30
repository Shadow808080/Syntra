package com.example.syntra.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)

/**
 * The Syntra palette.
 *
 * These are state-backed on purpose: every screen already reads them by name, so
 * making them observable lets a theme change repaint the whole app instantly
 * without touching a single call site. See [AppTheme].
 */
// The initial values ARE Choice.DARK (Bawaan Syntra). They have to match, or the first
// frame — drawn before AppTheme.load() runs — flashes the old colours.
var NexusBackground by mutableStateOf(Color(0xFF000000))
var NexusSurface by mutableStateOf(Color(0xFF101014))
var NexusSurfaceElevated by mutableStateOf(Color(0xFF18181F))
var NexusSearch by mutableStateOf(Color(0xFF0C0C10))
var NexusStroke by mutableStateOf(Color(0xFF24242E))
var NexusTextPrimary by mutableStateOf(Color(0xFFF4F4F8))
var NexusTextSecondary by mutableStateOf(Color(0xFF8B8B99))
var NexusAccent by mutableStateOf(Color(0xFF3B7BFF))
var NexusAccentSoft by mutableStateOf(Color(0xFF7BA5FF))
var NexusRing by mutableStateOf(Color(0xFF6C5CE7))

/**
 * Fill behind a destructive action (block, end room, sign out).
 *
 * Translucent rather than a fixed colour: it takes on whatever surface sits under it,
 * so the same value reads as a deep maroon on the dark themes and a soft red wash on
 * the light one. The old hardcoded 0xFF3A1620 was a near-black on a white page.
 */
val DangerFill = Color(0xFFFF3B48).copy(alpha = 0.16f)
var NexusOnline by mutableStateOf(Color(0xFF23C55E))
