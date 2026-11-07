package app.baki

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * One sage family, two tones — the same rule the web app uses.
 * Light and dark are separate palettes, not a filtered version of each other.
 */
@Immutable
data class BakiPalette(
    val dark: Boolean,
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val accentStrong: Color,
    val onAccent: Color,
    val surface: Color,
    val container: Color,
    val surfaceHigh: Color,
    val onSurface: Color,
    val muted: Color,
    val up: Color,
    val down: Color,
    val warn: Color,
    /** Neutral grey soft-UI canvas and grey shadows, instead of toning the canvas with the accent. */
    val softUi: Boolean = false,
)

val DarkPalette = BakiPalette(
    dark = true,
    primary = Color(0xFFB3C78D), onPrimary = Color(0xFF141A0C),
    primaryContainer = Color(0xFF2B3420), onPrimaryContainer = Color(0xFFD3E3B4),
    accentStrong = Color(0xFF4A5A2E), onAccent = Color(0xFFE8F2D6),
    surface = Color(0xFF0D110C), container = Color(0xFF171B12), surfaceHigh = Color(0xFF1B2318),
    onSurface = Color(0xFFE8F2D6), muted = Color(0xFF9AA694),
    up = Color(0xFF6FE6C6), down = Color(0xFFFF8B74), warn = Color(0xFFF5C451),
)

val LightPalette = BakiPalette(
    dark = false,
    primary = Color(0xFF4A5A2E), onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD9E6BD), onPrimaryContainer = Color(0xFF212A15),
    accentStrong = Color(0xFF3F5128), onAccent = Color(0xFFFFFFFF),
    surface = Color(0xFFF6F8F1), container = Color(0xFFECEFE4), surfaceHigh = Color(0xFFE4E9DA),
    onSurface = Color(0xFF14180F), muted = Color(0xFF556052),
    up = Color(0xFF0F6B4F), down = Color(0xFFA63C26), warn = Color(0xFF7A5A00),
)

private fun schemeOf(p: BakiPalette) = if (p.dark) darkColorScheme(
    primary = p.primary, onPrimary = p.onPrimary,
    primaryContainer = p.primaryContainer, onPrimaryContainer = p.onPrimaryContainer,
    surface = p.surface, onSurface = p.onSurface, onSurfaceVariant = p.muted,
    background = p.surface, error = p.down,
) else lightColorScheme(
    primary = p.primary, onPrimary = p.onPrimary,
    primaryContainer = p.primaryContainer, onPrimaryContainer = p.onPrimaryContainer,
    surface = p.surface, onSurface = p.onSurface, onSurfaceVariant = p.muted,
    background = p.surface, error = p.down,
)

val LocalBaki = staticCompositionLocalOf { DarkPalette }

/** Wallpaper-derived palette; null where the platform has no Monet (Android 12+, desktop). */
expect fun dynamicPalette(dark: Boolean): BakiPalette?


/** "Night Shift": near-black, cool accent, for driving after dark without glare. */
val NightPalette = BakiPalette(
    dark = true,
    primary = Color(0xFF8FB6FF), onPrimary = Color(0xFF071018),
    primaryContainer = Color(0xFF17243A), onPrimaryContainer = Color(0xFFD6E4FF),
    accentStrong = Color(0xFF2A4A7A), onAccent = Color(0xFFEAF2FF),
    surface = Color(0xFF05070A), container = Color(0xFF0D1219), surfaceHigh = Color(0xFF141B24),
    onSurface = Color(0xFFE6EEF9), muted = Color(0xFF8C9BB0),
    up = Color(0xFF74E0B0), down = Color(0xFFFF8F7A), warn = Color(0xFFFFC857),
)

/** "Paper": warm off-white, ink-brown accent, for daylight legibility. */
val PaperPalette = BakiPalette(
    dark = false,
    primary = Color(0xFF6B5636), onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFEDE3CE), onPrimaryContainer = Color(0xFF2B2416),
    accentStrong = Color(0xFF57452B), onAccent = Color(0xFFFFFFFF),
    surface = Color(0xFFFBF7EF), container = Color(0xFFF3EDE1), surfaceHigh = Color(0xFFEAE2D2),
    onSurface = Color(0xFF1C1913), muted = Color(0xFF6B6355),
    up = Color(0xFF2C6E52), down = Color(0xFFA83E2A), warn = Color(0xFF7A5A00),
)

/** "Mist": the classic soft-UI board — neutral cool grey, near-white faces, blue accent. */
val MistLight = BakiPalette(
    dark = false,
    // a soft-UI blue, darkened from the board's #5B8DEF so white text on it clears AA
    primary = Color(0xFF2E6BD6), onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDCE8FB), onPrimaryContainer = Color(0xFF14263F),
    accentStrong = Color(0xFF2459B8), onAccent = Color(0xFFFFFFFF),
    surface = Color(0xFFE9EDF2), container = Color(0xFFF2F5F9), surfaceHigh = Color(0xFFDDE3EA),
    onSurface = Color(0xFF2A3038), muted = Color(0xFF4E5A68),
    up = Color(0xFF1F6B4B), down = Color(0xFFA8432C), warn = Color(0xFF7A5A00),
    softUi = true,
)

val MistDark = BakiPalette(
    dark = true,
    primary = Color(0xFF8FB0F2), onPrimary = Color(0xFF0B1626),
    primaryContainer = Color(0xFF243649), onPrimaryContainer = Color(0xFFD9E6FF),
    accentStrong = Color(0xFF2C4A77), onAccent = Color(0xFFEAF1FF),
    surface = Color(0xFF14171C), container = Color(0xFF1C2027), surfaceHigh = Color(0xFF23282F),
    onSurface = Color(0xFFE7EBF1), muted = Color(0xFF9AA6B4),
    up = Color(0xFF6FD6AB), down = Color(0xFFFF9379), warn = Color(0xFFF2C24E),
    softUi = true,
)

/** Resolves the user's chosen pack against light/dark. */
fun paletteFor(pack: String, dark: Boolean): BakiPalette = when (pack) {
    "night" -> NightPalette
    "paper" -> PaperPalette
    "mist" -> if (dark) MistDark else MistLight
    else -> if (dark) DarkPalette else LightPalette
}

/** Follows the system setting, exactly like the web app's Auto theme. */
@Composable
fun BakiTheme(
    matchWallpaper: Boolean = false,
    mode: String = "auto",
    pack: String = "sage",
    content: @Composable () -> Unit,
) {
    val dark = when (mode) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }
    val fallback = paletteFor(pack, dark)
    val palette = if (matchWallpaper) remember(dark) { dynamicPalette(dark) ?: fallback } else fallback
    CompositionLocalProvider(LocalBaki provides palette) {
        MaterialTheme(
            colorScheme = schemeOf(palette),
            content = content,
        )
    }
}
