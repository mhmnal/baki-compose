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

/** Follows the system setting, exactly like the web app's Auto theme. */
@Composable
fun BakiTheme(matchWallpaper: Boolean = false, mode: String = "auto", content: @Composable () -> Unit) {
    val dark = when (mode) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }
    val fallback = if (dark) DarkPalette else LightPalette
    val palette = if (matchWallpaper) remember(dark) { dynamicPalette(dark) ?: fallback } else fallback
    CompositionLocalProvider(LocalBaki provides palette) {
        MaterialTheme(
            colorScheme = schemeOf(palette),
            content = content,
        )
    }
}
