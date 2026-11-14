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
    content: @Composable () -> Unit,
) {
    val dark = when (mode) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }
    val dynamic = if (!Caps.isDesktop || PhonePreview.active) {
        remember(dark) { dynamicColorScheme(dark) }
    } else null
    val scheme = dynamic ?: if (dark) darkColorScheme() else lightColorScheme()
    MaterialTheme(
        colorScheme = scheme,
        typography = Typography(),
        shapes = Md3Shapes,
        motionScheme = MotionScheme.expressive(),
        content = content,
    )
}

/** Wallpaper-derived MD3 scheme; null where the platform has no Monet. */
expect fun dynamicColorScheme(dark: Boolean): ColorScheme?
