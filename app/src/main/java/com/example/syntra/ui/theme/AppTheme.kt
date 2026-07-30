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
        DARK("Bawaan Syntra", "Hitam pekat, aksen biru"),
        LIGHT("Terang", "Untuk ruangan yang benderang"),
        MIDNIGHT("Midnight", "Hitam pekat, hemat baterai OLED"),
        OCEAN("Ocean", "Biru laut yang tenang"),
        SUNSET("Sunset", "Hangat, jingga keunguan"),
        FOREST("Forest", "Hijau teduh"),
        CUSTOM("Custom", "Pilih warna aksenmu sendiri"),
    }

    private const val PREFS = "syntra_settings"
    private const val KEY = "theme_choice"
    private const val KEY_CUSTOM = "theme_custom_accent"

    /**
     * The user's own accent, for [Choice.CUSTOM]. Compose state, so picking a colour
     * repaints immediately instead of waiting for a restart.
     */
    var customAccent by mutableStateOf(Color(0xFF2E6BF0))
        private set

    var current by mutableStateOf(Choice.DARK)
        private set

    fun paletteOf(choice: Choice): Palette = when (choice) {
        // Bawaan Syntra. Short-video dark: the background is true black, like TikTok's
        // feed, so video and photos have nothing competing with them and the whole app
        // matches the Shorts player instead of stepping down to grey around it.
        // Surfaces climb in small steps from there, which is what keeps a black UI
        // legible — depth has to come from the surfaces, since the base cannot go lower.
        //
        // The accent stays Syntra blue — that is the brand, not a starting point.
        // Only the base moved to black; the blue is lifted a little because a mid blue
        // that reads fine on #121212 loses its edge against true black.
        Choice.DARK -> Palette(
            background = Color(0xFF000000),
            surface = Color(0xFF101014),
            surfaceElevated = Color(0xFF18181F),
            search = Color(0xFF0C0C10),
            stroke = Color(0xFF24242E),
            textPrimary = Color(0xFFF4F4F8),
            textSecondary = Color(0xFF8B8B99),
            accent = Color(0xFF3B7BFF),
            accentSoft = Color(0xFF7BA5FF),
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
        Choice.CUSTOM -> customPalette(customAccent)
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

    /**
     * Builds a palette around an arbitrary accent, on the dark base.
     *
     * Only the accent family moves. Letting a user recolour surfaces and text is how
     * you end up with unreadable combinations they cannot undo; the accent is the one
     * colour that is decorative everywhere it appears, so it is the only one exposed.
     * [accentSoft] is a lightened sibling, derived rather than picked, so the pair
     * always has a sensible relationship.
     */
    private fun customPalette(accent: Color): Palette {
        val soft = Color(
            red = accent.red + (1f - accent.red) * 0.38f,
            green = accent.green + (1f - accent.green) * 0.38f,
            blue = accent.blue + (1f - accent.blue) * 0.38f,
        )
        return Palette(
            background = Color(0xFF0B0B10),
            surface = Color(0xFF14141B),
            surfaceElevated = Color(0xFF1B1B24),
            search = Color(0xFF101016),
            stroke = Color(0xFF222230),
            textPrimary = Color(0xFFF4F4F8),
            textSecondary = Color(0xFF8A8A9A),
            accent = accent,
            accentSoft = soft,
            ring = soft,
            online = Color(0xFF23C55E),
            isDark = true,
        )
    }

    /** Store a new custom accent and switch to it. */
    fun selectCustom(context: Context, accent: Color) {
        customAccent = accent
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong(KEY_CUSTOM, accent.value.toLong())
            .putString(KEY, Choice.CUSTOM.name)
            .apply()
        apply(Choice.CUSTOM)
    }

    /** Reads the saved choice and paints it. Call once at start-up. */
    fun load(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        // The accent has to be restored BEFORE apply(), or a CUSTOM theme repaints
        // with the default blue on every launch.
        val saved = prefs.getLong(KEY_CUSTOM, 0L)
        if (saved != 0L) customAccent = Color(saved.toULong())
        val name = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
        // Default is the house theme, not Midnight. Midnight is a deliberate choice
        // someone makes for their OLED; what a new install should open in is Syntra's
        // own look.
        val choice = Choice.entries.firstOrNull { it.name == name } ?: Choice.DARK
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
