package app.baki

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The design system: spacing, radii, opacity and type.
 *
 * Everything visual should come from here rather than a literal in a screen, so the app reads as
 * one product and a change lands everywhere at once.
 */

object Space {
    val xxs = 2.dp
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
}

object Radius {
    /** M3 "full": chips, buttons, the nav bar */
    val pill = 50.dp
    /** M3 extraSmall */
    val tiny = 4.dp
    /** M3 small */
    val small = 8.dp
    /** M3 medium — inputs and small surfaces */
    val input = 12.dp
    /** M3 large — the standard card */
    val card = 16.dp
    /** M3 extraLarge — bottom sheets */
    val sheet = 28.dp
}

/** Overlay opacities, named by intent so they stop being magic numbers. */
object Veil {
    const val hairline = 0.12f
    const val dim = 0.22f
    const val overlay = 0.45f
    const val scrim = 0.45f
}

object BakiType {
    private const val TNUM = "tnum"

    /** the one hero number on a screen */
    val display = TextStyle(fontSize = 56.sp, fontWeight = FontWeight.Black, letterSpacing = (-1.5).sp)
    /** secondary hero, e.g. the month total while a day is open */
    val displaySmall = TextStyle(fontSize = 40.sp, fontWeight = FontWeight.Black, letterSpacing = (-1).sp)
    /** card headline */
    val headline = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Black, letterSpacing = (-0.5).sp)
    /** list rows and dialog titles */
    val title = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp)
    val subtitle = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
    val body = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Normal)
    val bodyStrong = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    val label = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium)
    val labelStrong = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold)
    val caption = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium)
    /** all-caps section header */
    val section = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
    val micro = TextStyle(fontSize = 9.sp, fontWeight = FontWeight.Medium)

    /**
     * Numeric variants carry tabular figures, so money keeps its width as digits change and
     * columns line up. This is the single biggest "feels professional" detail in a money app.
     */
    val displayNumeric = display.copy(fontFeatureSettings = TNUM)
    val displaySmallNumeric = displaySmall.copy(fontFeatureSettings = TNUM)
    val headlineNumeric = headline.copy(fontFeatureSettings = TNUM)
    val titleNumeric = title.copy(fontFeatureSettings = TNUM)
    val subtitleNumeric = subtitle.copy(fontFeatureSettings = TNUM)
    val bodyNumeric = body.copy(fontFeatureSettings = TNUM)
    val labelNumeric = labelStrong.copy(fontFeatureSettings = TNUM)
    val captionNumeric = caption.copy(fontFeatureSettings = TNUM)
}

/**
 * Material 3 Expressive springs, carried here because the multiplatform material3 artifact keeps
 * `MotionScheme` internal. Spatial moves (position, size) may overshoot; effects moves (alpha,
 * colour) are critically damped so nothing fades past its target.
 * Values are the published M3 standard/expressive tokens.
 */
object BakiMotion {
    /** position, size, elevation — default speed */
    fun <T> spatial(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.8f, stiffness = 380f)

    /** sub-100 ms feedback, still springy but quick */
    fun <T> spatialFast(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.6f, stiffness = 800f)

    /** hero moments that should read as a settle, not a snap */
    fun <T> spatialSlow(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.8f, stiffness = 200f)

    /** alpha, colour, elevation tint — critically damped */
    fun <T> effects(): FiniteAnimationSpec<T> = spring(dampingRatio = 1f, stiffness = 1600f)
    fun <T> effectsSlow(): FiniteAnimationSpec<T> = spring(dampingRatio = 1f, stiffness = 800f)
    fun <T> effectsFast(): FiniteAnimationSpec<T> = spring(dampingRatio = 1f, stiffness = 3800f)
}

/** Semantic colours that are not part of the sage palette but must stay coherent. */
@androidx.compose.runtime.Immutable
data class BakiSemantic(val income: Color, val expense: Color, val warn: Color)
