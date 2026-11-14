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
import androidx.compose.ui.unit.dp
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
    /** Paper Ledger: receipt rules, serif amounts and stamp-like status labels. */
    val paper: Boolean = false,
)

// Legacy palette names remain for source compatibility, but every active colour now comes from
// MaterialTheme.colorScheme. No component references a hardcoded palette hex.
private fun baselinePalette(dark: Boolean) =
    paletteOf(if (dark) darkColorScheme() else lightColorScheme(), dark)

val DarkPalette = baselinePalette(true)
val LightPalette = baselinePalette(false)
val NightPalette = DarkPalette
val PaperPalette = LightPalette.copy(paper = true)
val PaperNightPalette = DarkPalette.copy(paper = true)
val MistLight = LightPalette.copy(softUi = true)
val MistDark = DarkPalette.copy(softUi = true)

/** Legacy pack resolution kept for persisted configs; all packs resolve to MD3 baseline roles. */
fun paletteFor(pack: String, dark: Boolean): BakiPalette = when (pack) {
    "paper" -> if (dark) PaperNightPalette else PaperPalette
    "mist" -> if (dark) MistDark else MistLight
    else -> if (dark) DarkPalette else LightPalette
}

/** Official MD3 shape hierarchy. */
private val Md3Shapes = Shapes(
    extraSmall = RoundedCornerShape(Radius.tiny),
    small = RoundedCornerShape(Radius.small),
    medium = RoundedCornerShape(Radius.large),
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
