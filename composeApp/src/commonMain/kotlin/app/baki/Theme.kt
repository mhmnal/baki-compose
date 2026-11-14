package app.baki

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

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
    /** Paper Ledger: receipt rules, serif amounts and stamp-like status labels. */
    val paper: Boolean = false,
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

/** Official MD3 shape hierarchy. */
private val Md3Shapes = Shapes(
    extraSmall = RoundedCornerShape(Radius.tiny),
    small = RoundedCornerShape(Radius.small),
    medium = RoundedCornerShape(Radius.card),
    large = RoundedCornerShape(Radius.large),
    extraLarge = RoundedCornerShape(Radius.sheet),
)

private fun paletteOf(s: androidx.compose.material3.ColorScheme, dark: Boolean) = BakiPalette(
    dark = dark, paper = false,
    primary = s.primary, onPrimary = s.onPrimary,
    primaryContainer = s.primaryContainer, onPrimaryContainer = s.onPrimaryContainer,
    accentStrong = s.primary, onAccent = s.onPrimary,
    surface = s.surface, container = s.surfaceContainerLow,
    surfaceHigh = s.surfaceContainerHigh,
    onSurface = s.onSurface, muted = s.onSurfaceVariant,
    up = s.tertiary, down = s.error, warn = s.secondary,
)

/** Material components now speak the same type system as the hand-built screens. */
private fun bakiTypography() = Typography(
    displayLarge = BakiType.display,
    displayMedium = BakiType.displaySmall,
    displaySmall = BakiType.headline,
    headlineLarge = BakiType.headline,
    headlineMedium = BakiType.headline,
    headlineSmall = BakiType.title,
    titleLarge = BakiType.title,
    titleMedium = BakiType.subtitle,
    titleSmall = BakiType.labelStrong,
    bodyLarge = BakiType.body,
    bodyMedium = BakiType.caption,
    bodySmall = BakiType.bodySmall,
    labelLarge = BakiType.label,
    labelMedium = BakiType.caption,
    labelSmall = BakiType.micro,
)

private fun schemeOf(p: BakiPalette) = if (p.dark) darkColorScheme(
    primary = p.primary, onPrimary = p.onPrimary,
    primaryContainer = p.primaryContainer, onPrimaryContainer = p.onPrimaryContainer,
    surface = p.surface, onSurface = p.onSurface,
    surfaceVariant = p.container, onSurfaceVariant = p.muted,
    surfaceContainerLowest = p.surface, surfaceContainerLow = p.container,
    surfaceContainer = p.container, surfaceContainerHigh = p.surfaceHigh,
    surfaceContainerHighest = p.surfaceHigh,
    outline = p.muted.copy(alpha = 0.55f), outlineVariant = p.surfaceHigh,
    background = p.surface, error = p.down,
) else lightColorScheme(
    primary = p.primary, onPrimary = p.onPrimary,
    primaryContainer = p.primaryContainer, onPrimaryContainer = p.onPrimaryContainer,
    surface = p.surface, onSurface = p.onSurface,
    surfaceVariant = p.container, onSurfaceVariant = p.muted,
    surfaceContainerLowest = p.surface, surfaceContainerLow = p.container,
    surfaceContainer = p.container, surfaceContainerHigh = p.surfaceHigh,
    surfaceContainerHighest = p.surfaceHigh,
    outline = p.muted.copy(alpha = 0.55f), outlineVariant = p.surfaceHigh,
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

/** Paper Ledger light: warm paper, ink, forest and a red audit stamp. */
val PaperPalette = BakiPalette(
    dark = false, paper = true,
    primary = Color(0xFF3F5E49), onPrimary = Color(0xFFF7F0DF),
    primaryContainer = Color(0xFFDDE5D2), onPrimaryContainer = Color(0xFF1E2A20),
    accentStrong = Color(0xFF2F4636), onAccent = Color(0xFFF7F0DF),
    surface = Color(0xFFF6EFE0), container = Color(0xFFEFE6D5), surfaceHigh = Color(0xFFE2D6C1),
    onSurface = Color(0xFF211E18), muted = Color(0xFF6E6555),
    up = Color(0xFF3F5E49), down = Color(0xFFA34532), warn = Color(0xFF8A5A1F),
)

/** Paper Ledger night: warm black paper, cream ink, brass rules and a soft red stamp. */
val PaperNightPalette = BakiPalette(
    dark = true, paper = true,
    primary = Color(0xFFC9A66B), onPrimary = Color(0xFF1A1910),
    primaryContainer = Color(0xFF3A3323), onPrimaryContainer = Color(0xFFF0E3C8),
    accentStrong = Color(0xFF8E7448), onAccent = Color(0xFF000000),
    surface = Color(0xFF1B1F18), container = Color(0xFF22271E), surfaceHigh = Color(0xFF2D3327),
    onSurface = Color(0xFFECE3D0), muted = Color(0xFFA29782),
    up = Color(0xFFA9C29A), down = Color(0xFFCF785F), warn = Color(0xFFD6A35E),
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
    "paper" -> if (dark) PaperNightPalette else PaperPalette
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
    // Material You first: use the wallpaper scheme wherever Monet is available. The static
    // Material baseline is the fallback, not the old bespoke packs.
    val dynamic = if (!Caps.isDesktop || PhonePreview.active) remember(dark) { dynamicPalette(dark) } else null
    val scheme = dynamic?.let { schemeOf(it) } ?: if (dark) darkColorScheme() else lightColorScheme()
    val palette = remember(scheme, dark) { paletteOf(scheme, dark) }
    CompositionLocalProvider(LocalBaki provides palette) {
        MaterialTheme(
            colorScheme = scheme,
            typography = if (!Caps.isDesktop || PhonePreview.active) bakiTypography() else Typography(),
            shapes = if (!Caps.isDesktop || PhonePreview.active) Md3Shapes else Shapes(),
            content = content,
        )
    }
}
