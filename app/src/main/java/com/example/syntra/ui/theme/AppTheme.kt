package com.example.syntra.ui.theme

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/** A complete look: every colour the app draws with. */
data class Palette(
    val background: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val search: Color,
    val stroke: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val accent: Color,
    val accentSoft: Color,
    val ring: Color,
    val online: Color,
    val isDark: Boolean,
)

/**
 * Theme selection. More than just dark/light — each option is a full palette, and
 * switching applies it to the shared colour state so the whole app repaints.
 */
object AppTheme {

    enum class Choice(val label: String, val description: String) {
        DARK("Gelap", "Bawaan Syntra"),
        LIGHT("Terang", "Untuk ruangan yang benderang"),
        MIDNIGHT("Midnight", "Hitam pekat, hemat baterai OLED"),
        OCEAN("Ocean", "Biru laut yang tenang"),
        SUNSET("Sunset", "Hangat, jingga keunguan"),
        FOREST("Forest", "Hijau teduh"),
    }

    private const val PREFS = "syntra_settings"
    private const val KEY = "theme_choice"

    var current by mutableStateOf(Choice.MIDNIGHT)
        private set

    fun paletteOf(choice: Choice): Palette = when (choice) {
        Choice.DARK -> Palette(
            background = Color(0xFF121212),
            surface = Color(0xFF16161E),
            surfaceElevated = Color(0xFF1C1C26),
            search = Color(0xFF12121A),
            stroke = Color(0xFF24242F),
            textPrimary = Color(0xFFF4F4F8),
            textSecondary = Color(0xFF8A8A9A),
            accent = Color(0xFF3B68F5),
            accentSoft = Color(0xFF6E8BFF),
            ring = Color(0xFF6C5CE7),
            online = Color(0xFF23C55E),
            isDark = true,
        )
        Choice.LIGHT -> Palette(
            background = Color(0xFFF6F7FB),
            surface = Color(0xFFFFFFFF),
            surfaceElevated = Color(0xFFEEF1F7),
            search = Color(0xFFECEFF5),
            stroke = Color(0xFFDDE1EA),
            textPrimary = Color(0xFF14161C),
            textSecondary = Color(0xFF6B7280),
            accent = Color(0xFF2F5FE0),
            accentSoft = Color(0xFF5B84F0),
            ring = Color(0xFF6C5CE7),
            online = Color(0xFF16A34A),
            isDark = false,
        )
        Choice.MIDNIGHT -> Palette(
            background = Color(0xFF000000),
            surface = Color(0xFF0B0B0D),
            surfaceElevated = Color(0xFF141418),
            search = Color(0xFF0B0B0D),
            stroke = Color(0xFF1E1E24),
            textPrimary = Color(0xFFEDEDF2),
            textSecondary = Color(0xFF7C7C88),
            accent = Color(0xFF4C7DFF),
            accentSoft = Color(0xFF7FA0FF),
            ring = Color(0xFF7C5CFF),
            online = Color(0xFF22C55E),
            isDark = true,
        )
        Choice.OCEAN -> Palette(
            background = Color(0xFF0B1622),
            surface = Color(0xFF122031),
            surfaceElevated = Color(0xFF17293D),
            search = Color(0xFF0F1C2B),
            stroke = Color(0xFF1E3448),
            textPrimary = Color(0xFFE8F1FA),
            textSecondary = Color(0xFF7D96AD),
            accent = Color(0xFF00A8CC),
            accentSoft = Color(0xFF41C9E2),
            ring = Color(0xFF00C2A8),
            online = Color(0xFF2DD4BF),
            isDark = true,
        )
        Choice.SUNSET -> Palette(
            background = Color(0xFF1A1016),
            surface = Color(0xFF241720),
            surfaceElevated = Color(0xFF2E1D28),
            search = Color(0xFF1F131A),
            stroke = Color(0xFF3A2631),
            textPrimary = Color(0xFFFBECF2),
            textSecondary = Color(0xFFA88B98),
            accent = Color(0xFFF2663C),
            accentSoft = Color(0xFFFF8A63),
            ring = Color(0xFFE9548C),
            online = Color(0xFF35C88A),
            isDark = true,
        )
        Choice.FOREST -> Palette(
            background = Color(0xFF0E1712),
            surface = Color(0xFF14211A),
            surfaceElevated = Color(0xFF1A2B22),
            search = Color(0xFF111C16),
            stroke = Color(0xFF23372C),
            textPrimary = Color(0xFFE9F5EE),
            textSecondary = Color(0xFF87A395),
            accent = Color(0xFF2FA36B),
            accentSoft = Color(0xFF56C98D),
            ring = Color(0xFF3FBF9A),
            online = Color(0xFF4ADE80),
            isDark = true,
        )
    }

    /** Reads the saved choice and paints it. Call once at start-up. */
    fun load(context: Context) {
        val name = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
        // Default is Midnight (pure-black OLED) when the user hasn't picked a theme.
        val choice = Choice.entries.firstOrNull { it.name == name } ?: Choice.MIDNIGHT
        apply(choice)
    }

    fun select(context: Context, choice: Choice) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, choice.name).apply()
        apply(choice)
    }

    private fun apply(choice: Choice) {
        current = choice
        val p = paletteOf(choice)
        NexusBackground = p.background
        NexusSurface = p.surface
        NexusSurfaceElevated = p.surfaceElevated
        NexusSearch = p.search
        NexusStroke = p.stroke
        NexusTextPrimary = p.textPrimary
        NexusTextSecondary = p.textSecondary
        NexusAccent = p.accent
        NexusAccentSoft = p.accentSoft
        NexusRing = p.ring
        NexusOnline = p.online
    }
}
