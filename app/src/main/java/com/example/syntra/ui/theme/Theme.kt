package com.example.syntra.ui.theme

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

/**
 * Wraps the app in the currently selected [AppTheme] palette. Because the palette
 * values are observable, picking a new theme repaints everything immediately.
 */
@Composable
fun SyntraTheme(content: @Composable () -> Unit) {
    val palette = AppTheme.paletteOf(AppTheme.current)

    val colorScheme = if (palette.isDark) {
        darkColorScheme(
            primary = palette.accent,
            secondary = palette.accentSoft,
            tertiary = palette.ring,
            background = palette.background,
            surface = palette.surface,
            onPrimary = palette.textPrimary,
            onBackground = palette.textPrimary,
            onSurface = palette.textPrimary,
        )
    } else {
        lightColorScheme(
            primary = palette.accent,
            secondary = palette.accentSoft,
            tertiary = palette.ring,
            background = palette.background,
            surface = palette.surface,
            onPrimary = Palette_OnAccent,
            onBackground = palette.textPrimary,
            onSurface = palette.textPrimary,
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
    ) {
        // Make the app font the default family for any Text that doesn't set its
        // own, while preserving each call's own size/weight.
        CompositionLocalProvider(
            LocalTextStyle provides LocalTextStyle.current.copy(fontFamily = AppFontFamily),
            content = content,
        )
    }
}

private val Palette_OnAccent = androidx.compose.ui.graphics.Color.White
