package app.baki

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp

/** MD3 Expressive shape scale. */
private val Md3Shapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/**
 * Dynamic Material You first, Material baseline fallback. MaterialTheme is the only design-token
 * provider; there is no parallel palette or local colour object.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BakiTheme(
    mode: String = "auto",
    colorSource: String = "wallpaper",
    reducedMotion: Boolean = false,
    content: @Composable () -> Unit,
) {
    val dark = when (mode) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }
    val dynamic = if (colorSource != "baki" && (!Caps.isDesktop || PhonePreview.active)) {
        rememberDynamicColorScheme(dark)
    } else null
    val scheme = dynamic ?: if (dark) BakiDarkColors else BakiLightColors
    MaterialTheme(
        colorScheme = scheme,
        typography = Typography(),
        shapes = Md3Shapes,
        motionScheme = if (reducedMotion) MotionScheme.standard() else MotionScheme.expressive(),
        content = content,
    )
}

/** Wallpaper-derived MD3 scheme; null where the platform has no Monet. */
@Composable
expect fun rememberDynamicColorScheme(dark: Boolean): ColorScheme?

/** Whether this device can offer the Wallpaper colour source. */
expect fun supportsDynamicColor(): Boolean

/** Baki's fixed Fig Milk palette, used when dynamic colour is unavailable or disabled. */
val BakiLightColors = lightColorScheme(
    primary = androidx.compose.ui.graphics.Color(0xFF6B3F63),
    onPrimary = androidx.compose.ui.graphics.Color(0xFFFFFFFF),
    primaryContainer = androidx.compose.ui.graphics.Color(0xFFF5D9F1),
    onPrimaryContainer = androidx.compose.ui.graphics.Color(0xFF2A0D27),
    inversePrimary = androidx.compose.ui.graphics.Color(0xFFE8B7E2),
    secondary = androidx.compose.ui.graphics.Color(0xFF8A4B3A),
    onSecondary = androidx.compose.ui.graphics.Color(0xFFFFFFFF),
    secondaryContainer = androidx.compose.ui.graphics.Color(0xFFFFDCCF),
    onSecondaryContainer = androidx.compose.ui.graphics.Color(0xFF351009),
    tertiary = androidx.compose.ui.graphics.Color(0xFF7A5A00),
    onTertiary = androidx.compose.ui.graphics.Color(0xFFFFFFFF),
    tertiaryContainer = androidx.compose.ui.graphics.Color(0xFFFFE3A3),
    onTertiaryContainer = androidx.compose.ui.graphics.Color(0xFF251A00),
    background = androidx.compose.ui.graphics.Color(0xFFFFF8F3),
    onBackground = androidx.compose.ui.graphics.Color(0xFF211A1C),
    surface = androidx.compose.ui.graphics.Color(0xFFFFF8F3),
    onSurface = androidx.compose.ui.graphics.Color(0xFF211A1C),
    surfaceVariant = androidx.compose.ui.graphics.Color(0xFFEDE0E3),
    onSurfaceVariant = androidx.compose.ui.graphics.Color(0xFF514347),
    surfaceTint = androidx.compose.ui.graphics.Color(0xFF6B3F63),
    inverseSurface = androidx.compose.ui.graphics.Color(0xFF352E31),
    inverseOnSurface = androidx.compose.ui.graphics.Color(0xFFFAEFF2),
    error = androidx.compose.ui.graphics.Color(0xFFB3261E),
    onError = androidx.compose.ui.graphics.Color(0xFFFFFFFF),
    errorContainer = androidx.compose.ui.graphics.Color(0xFFF9DEDC),
    onErrorContainer = androidx.compose.ui.graphics.Color(0xFF410E0B),
    outline = androidx.compose.ui.graphics.Color(0xFF837377),
    outlineVariant = androidx.compose.ui.graphics.Color(0xFFD8C2C7),
    scrim = androidx.compose.ui.graphics.Color(0xFF000000),
    surfaceBright = androidx.compose.ui.graphics.Color(0xFFFFF8F3),
    surfaceDim = androidx.compose.ui.graphics.Color(0xFFE5D8D5),
    surfaceContainerLowest = androidx.compose.ui.graphics.Color(0xFFFFFFFF),
    surfaceContainerLow = androidx.compose.ui.graphics.Color(0xFFFBF0EC),
    surfaceContainer = androidx.compose.ui.graphics.Color(0xFFF7EAE5),
    surfaceContainerHigh = androidx.compose.ui.graphics.Color(0xFFF2E5E0),
    surfaceContainerHighest = androidx.compose.ui.graphics.Color(0xFFECDFDA),
)

/** Baki's fixed Midnight Fig palette. */
val BakiDarkColors = darkColorScheme(
    primary = androidx.compose.ui.graphics.Color(0xFFF0B8E9),
    onPrimary = androidx.compose.ui.graphics.Color(0xFF3D1138),
    primaryContainer = androidx.compose.ui.graphics.Color(0xFF54234F),
    onPrimaryContainer = androidx.compose.ui.graphics.Color(0xFFFFD7F8),
    inversePrimary = androidx.compose.ui.graphics.Color(0xFF6B3F63),
    secondary = androidx.compose.ui.graphics.Color(0xFFFFB59E),
    onSecondary = androidx.compose.ui.graphics.Color(0xFF4E1A0D),
    secondaryContainer = androidx.compose.ui.graphics.Color(0xFF6B2D1F),
    onSecondaryContainer = androidx.compose.ui.graphics.Color(0xFFFFDBCF),
    tertiary = androidx.compose.ui.graphics.Color(0xFFF2C75C),
    onTertiary = androidx.compose.ui.graphics.Color(0xFF3E2E00),
    tertiaryContainer = androidx.compose.ui.graphics.Color(0xFF594400),
    onTertiaryContainer = androidx.compose.ui.graphics.Color(0xFFFFE08A),
    background = androidx.compose.ui.graphics.Color(0xFF151114),
    onBackground = androidx.compose.ui.graphics.Color(0xFFEBDDE2),
    surface = androidx.compose.ui.graphics.Color(0xFF151114),
    onSurface = androidx.compose.ui.graphics.Color(0xFFEBDDE2),
    surfaceVariant = androidx.compose.ui.graphics.Color(0xFF514347),
    onSurfaceVariant = androidx.compose.ui.graphics.Color(0xFFD5C2C7),
    surfaceTint = androidx.compose.ui.graphics.Color(0xFFF0B8E9),
    inverseSurface = androidx.compose.ui.graphics.Color(0xFFEBDDE2),
    inverseOnSurface = androidx.compose.ui.graphics.Color(0xFF332B2F),
    error = androidx.compose.ui.graphics.Color(0xFFFFB4AB),
    onError = androidx.compose.ui.graphics.Color(0xFF690005),
    errorContainer = androidx.compose.ui.graphics.Color(0xFF93000A),
    onErrorContainer = androidx.compose.ui.graphics.Color(0xFFFFDAD6),
    outline = androidx.compose.ui.graphics.Color(0xFF9E8A90),
    outlineVariant = androidx.compose.ui.graphics.Color(0xFF514347),
    scrim = androidx.compose.ui.graphics.Color(0xFF000000),
    surfaceBright = androidx.compose.ui.graphics.Color(0xFF3B3135),
    surfaceDim = androidx.compose.ui.graphics.Color(0xFF151114),
    surfaceContainerLowest = androidx.compose.ui.graphics.Color(0xFF0F0C0E),
    surfaceContainerLow = androidx.compose.ui.graphics.Color(0xFF1D171A),
    surfaceContainer = androidx.compose.ui.graphics.Color(0xFF211A1D),
    surfaceContainerHigh = androidx.compose.ui.graphics.Color(0xFF2A2226),
    surfaceContainerHighest = androidx.compose.ui.graphics.Color(0xFF352C30),
)
