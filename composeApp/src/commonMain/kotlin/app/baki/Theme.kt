package app.baki

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp

/** MD3 Expressive shape scale. */
/** One deliberate asymmetric corner for the Today hero, per the shape audit. */
val BakiHeroShape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp, bottomEnd = 32.dp, bottomStart = 8.dp)

private val Md3Shapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

/** Desktop motion can be removed without changing layout or contrast. */
val LocalReducedMotion = staticCompositionLocalOf { false }

/**
 * Baki's Roast & Pine signature is the default. Dynamic Material You remains an optional source.
 * MaterialTheme is the only design-token provider; there is no parallel palette.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BakiTheme(
    mode: String = "auto",
    colorSource: String = "baki",
    highContrast: Boolean = false,
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
    val highContrastFixed = highContrast && colorSource == "baki"
    val scheme = if (highContrastFixed) {
        if (dark) BakiDarkHighContrast else BakiLightHighContrast
    } else {
        dynamic ?: if (dark) BakiDarkColors else BakiLightColors
    }
    CompositionLocalProvider(LocalReducedMotion provides reducedMotion) {
    MaterialTheme(
        colorScheme = scheme,
        typography = Typography(),
        shapes = Md3Shapes,
        motionScheme = if (reducedMotion) {
            MotionScheme.standard()
        } else if (PhonePreview.active && PhonePreview.motionMode == "slow") {
            PreviewSlowMotionScheme
        } else if (PhonePreview.active && PhonePreview.motionMode == "standard") {
            MotionScheme.standard()
        } else {
            MotionScheme.expressive()
        },
        content = content,
    )
    }
}

/** Dynamic Material You scheme; null where the platform has no Monet. */
@Composable
expect fun rememberDynamicColorScheme(dark: Boolean): ColorScheme?

/** Whether this device can offer the System colour source. */
expect fun supportsDynamicColor(): Boolean

/** Baki's fixed Oat & Pine light palette. */
val BakiLightColors = lightColorScheme(
    primary = androidx.compose.ui.graphics.Color(0xFF0B331E),
    onPrimary = androidx.compose.ui.graphics.Color(0xFFF7F1E4),
    primaryContainer = androidx.compose.ui.graphics.Color(0xFFCBEAD5),
    onPrimaryContainer = androidx.compose.ui.graphics.Color(0xFF0B331E),
    inversePrimary = androidx.compose.ui.graphics.Color(0xFFA7E8BD),
    secondary = androidx.compose.ui.graphics.Color(0xFFB8432A),
    onSecondary = androidx.compose.ui.graphics.Color(0xFFFFFFFF),
    secondaryContainer = androidx.compose.ui.graphics.Color(0xFFFBD9CF),
    onSecondaryContainer = androidx.compose.ui.graphics.Color(0xFF3B0D02),
    tertiary = androidx.compose.ui.graphics.Color(0xFF7A5100),
    onTertiary = androidx.compose.ui.graphics.Color(0xFFFFFFFF),
    tertiaryContainer = androidx.compose.ui.graphics.Color(0xFFFBE0B0),
    onTertiaryContainer = androidx.compose.ui.graphics.Color(0xFF2A1C00),
    background = androidx.compose.ui.graphics.Color(0xFFF4EFE6),
    onBackground = androidx.compose.ui.graphics.Color(0xFF1B1712),
    surface = androidx.compose.ui.graphics.Color(0xFFFFFCF5),
    onSurface = androidx.compose.ui.graphics.Color(0xFF1B1712),
    surfaceVariant = androidx.compose.ui.graphics.Color(0xFFE8E0D4),
    onSurfaceVariant = androidx.compose.ui.graphics.Color(0xFF51483D),
    surfaceTint = androidx.compose.ui.graphics.Color(0xFF0B331E),
    inverseSurface = androidx.compose.ui.graphics.Color(0xFF332E27),
    inverseOnSurface = androidx.compose.ui.graphics.Color(0xFFF7F1E4),
    error = androidx.compose.ui.graphics.Color(0xFFB3261E),
    onError = androidx.compose.ui.graphics.Color(0xFFFFFFFF),
    errorContainer = androidx.compose.ui.graphics.Color(0xFFF9DEDC),
    onErrorContainer = androidx.compose.ui.graphics.Color(0xFF410E0B),
    outline = androidx.compose.ui.graphics.Color(0xFF776C60),
    outlineVariant = androidx.compose.ui.graphics.Color(0xFFD4C9BA),
    scrim = androidx.compose.ui.graphics.Color(0xFF000000),
    surfaceBright = androidx.compose.ui.graphics.Color(0xFFFFFDF8),
    surfaceDim = androidx.compose.ui.graphics.Color(0xFFE7DED1),
    surfaceContainerLowest = androidx.compose.ui.graphics.Color(0xFFFFFFFF),
    surfaceContainerLow = androidx.compose.ui.graphics.Color(0xFFFAF5EC),
    surfaceContainer = androidx.compose.ui.graphics.Color(0xFFF5EEE3),
    surfaceContainerHigh = androidx.compose.ui.graphics.Color(0xFFEFE6D9),
    surfaceContainerHighest = androidx.compose.ui.graphics.Color(0xFFE8DED1),
)

/** Baki's fixed Roast & Pine dark palette. */
val BakiDarkColors = darkColorScheme(
    primary = androidx.compose.ui.graphics.Color(0xFFA7E8BD),
    onPrimary = androidx.compose.ui.graphics.Color(0xFF1B110B),
    primaryContainer = androidx.compose.ui.graphics.Color(0xFF2B4A36),
    onPrimaryContainer = androidx.compose.ui.graphics.Color(0xFFD7F7E2),
    inversePrimary = androidx.compose.ui.graphics.Color(0xFF0B331E),
    secondary = androidx.compose.ui.graphics.Color(0xFFFFB4A0),
    onSecondary = androidx.compose.ui.graphics.Color(0xFF5A1808),
    secondaryContainer = androidx.compose.ui.graphics.Color(0xFF7A2D18),
    onSecondaryContainer = androidx.compose.ui.graphics.Color(0xFFFFDAD0),
    tertiary = androidx.compose.ui.graphics.Color(0xFFF2C66D),
    onTertiary = androidx.compose.ui.graphics.Color(0xFF3A2900),
    tertiaryContainer = androidx.compose.ui.graphics.Color(0xFF564000),
    onTertiaryContainer = androidx.compose.ui.graphics.Color(0xFFFFE2A6),
    background = androidx.compose.ui.graphics.Color(0xFF161210),
    onBackground = androidx.compose.ui.graphics.Color(0xFFF2ECE4),
    surface = androidx.compose.ui.graphics.Color(0xFF161210),
    onSurface = androidx.compose.ui.graphics.Color(0xFFF2ECE4),
    surfaceVariant = androidx.compose.ui.graphics.Color(0xFF4B443B),
    onSurfaceVariant = androidx.compose.ui.graphics.Color(0xFFD6CCC0),
    surfaceTint = androidx.compose.ui.graphics.Color(0xFFA7E8BD),
    inverseSurface = androidx.compose.ui.graphics.Color(0xFFF2ECE4),
    inverseOnSurface = androidx.compose.ui.graphics.Color(0xFF332E27),
    error = androidx.compose.ui.graphics.Color(0xFFFFB4AB),
    onError = androidx.compose.ui.graphics.Color(0xFF690005),
    errorContainer = androidx.compose.ui.graphics.Color(0xFF93000A),
    onErrorContainer = androidx.compose.ui.graphics.Color(0xFFFFDAD6),
    outline = androidx.compose.ui.graphics.Color(0xFFA79A8B),
    outlineVariant = androidx.compose.ui.graphics.Color(0xFF554D43),
    scrim = androidx.compose.ui.graphics.Color(0xFF000000),
    surfaceBright = androidx.compose.ui.graphics.Color(0xFF3E342B),
    surfaceDim = androidx.compose.ui.graphics.Color(0xFF161210),
    surfaceContainerLowest = androidx.compose.ui.graphics.Color(0xFF0F0C09),
    surfaceContainerLow = androidx.compose.ui.graphics.Color(0xFF1C1713),
    surfaceContainer = androidx.compose.ui.graphics.Color(0xFF211B16),
    surfaceContainerHigh = androidx.compose.ui.graphics.Color(0xFF2A231D),
    surfaceContainerHighest = androidx.compose.ui.graphics.Color(0xFF352B23),
)

/** High-contrast Oat & Pine variants. */
val BakiLightHighContrast = BakiLightColors.copy(
    primary = androidx.compose.ui.graphics.Color(0xFF062B18),
    onPrimary = androidx.compose.ui.graphics.Color(0xFFFFFDF8),
    onPrimaryContainer = androidx.compose.ui.graphics.Color(0xFF001B0D),
    secondary = androidx.compose.ui.graphics.Color(0xFF8F2D17),
    onSecondary = androidx.compose.ui.graphics.Color(0xFFFFFFFF),
    onSecondaryContainer = androidx.compose.ui.graphics.Color(0xFF2A0700),
    tertiary = androidx.compose.ui.graphics.Color(0xFF5F4000),
    onTertiary = androidx.compose.ui.graphics.Color(0xFFFFFFFF),
    onTertiaryContainer = androidx.compose.ui.graphics.Color(0xFF1F1400),
    background = androidx.compose.ui.graphics.Color(0xFFFFFBF4),
    onBackground = androidx.compose.ui.graphics.Color(0xFF000000),
    surface = androidx.compose.ui.graphics.Color(0xFFFFFBF4),
    onSurface = androidx.compose.ui.graphics.Color(0xFF000000),
    onSurfaceVariant = androidx.compose.ui.graphics.Color(0xFF2E2822),
    outline = androidx.compose.ui.graphics.Color(0xFF4A4035),
    outlineVariant = androidx.compose.ui.graphics.Color(0xFF7A6B5B),
)

/** High-contrast Roast & Pine variants. */
val BakiDarkHighContrast = BakiDarkColors.copy(
    primary = androidx.compose.ui.graphics.Color(0xFFC8F7D9),
    onPrimary = androidx.compose.ui.graphics.Color(0xFF001509),
    primaryContainer = androidx.compose.ui.graphics.Color(0xFF1E4A2D),
    onPrimaryContainer = androidx.compose.ui.graphics.Color(0xFFFFFFFF),
    secondary = androidx.compose.ui.graphics.Color(0xFFFFDAD0),
    onSecondary = androidx.compose.ui.graphics.Color(0xFF2A0802),
    secondaryContainer = androidx.compose.ui.graphics.Color(0xFF7A2D18),
    onSecondaryContainer = androidx.compose.ui.graphics.Color(0xFFFFFFFF),
    tertiary = androidx.compose.ui.graphics.Color(0xFFFFE2A6),
    onTertiary = androidx.compose.ui.graphics.Color(0xFF231800),
    tertiaryContainer = androidx.compose.ui.graphics.Color(0xFF5C4300),
    onTertiaryContainer = androidx.compose.ui.graphics.Color(0xFFFFFFFF),
    background = androidx.compose.ui.graphics.Color(0xFF080605),
    onBackground = androidx.compose.ui.graphics.Color(0xFFFFFFFF),
    surface = androidx.compose.ui.graphics.Color(0xFF080605),
    onSurface = androidx.compose.ui.graphics.Color(0xFFFFFFFF),
    onSurfaceVariant = androidx.compose.ui.graphics.Color(0xFFF2ECE4),
    outline = androidx.compose.ui.graphics.Color(0xFFD6CCC0),
    outlineVariant = androidx.compose.ui.graphics.Color(0xFFA79A8B),
)


/** A deliberately slow spring set for the desktop Phone Preview Motion Lab. */
private object PreviewSlowMotionScheme : MotionScheme {
    private fun <T> spatial(): FiniteAnimationSpec<T> =
        spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessLow)
    private fun <T> effects(): FiniteAnimationSpec<T> =
        spring(dampingRatio = 1f, stiffness = Spring.StiffnessLow)
    override fun <T> defaultSpatialSpec(): FiniteAnimationSpec<T> = spatial()
    override fun <T> fastSpatialSpec(): FiniteAnimationSpec<T> = spatial()
    override fun <T> slowSpatialSpec(): FiniteAnimationSpec<T> = spatial()
    override fun <T> defaultEffectsSpec(): FiniteAnimationSpec<T> = effects()
    override fun <T> fastEffectsSpec(): FiniteAnimationSpec<T> = effects()
    override fun <T> slowEffectsSpec(): FiniteAnimationSpec<T> = effects()
}
