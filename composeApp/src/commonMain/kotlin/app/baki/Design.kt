package app.baki

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import baki_compose.composeapp.generated.resources.inter_variable
import baki_compose.composeapp.generated.resources.jetbrains_mono
import baki_compose.composeapp.generated.resources.space_grotesk
import baki_compose.composeapp.generated.resources.Res
import org.jetbrains.compose.resources.Font
import androidx.compose.runtime.remember
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
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
    /** the 20 dp step the dialogs and cards already use */
    val ml = 20.dp
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
/** The only four icon sizes: inline with text, leading a row, in the nav bar, and a hero glyph. */
object IconSize {
    val inline = 16.dp
    val row = 20.dp
    val bar = 24.dp
    val hero = 40.dp
}

/** Control heights: one for primary actions, one for anything that must still clear 44 dp. */
object Control {
    val primary = 48.dp
    val secondary = 44.dp
}

object Veil {
    const val hairline = 0.12f
    const val dim = 0.22f
    const val overlay = 0.45f
    const val scrim = 0.45f
}

object BakiType {
    private const val TNUM = "tnum"

    // Sizes and line heights follow the M3 type scale (57/45/28/22/16/12/11).
    // Weights run heavier than stock M3: the app's voice is a bold money figure.

    /** the one hero number on a screen — M3 displayLarge */
    var display = TextStyle(fontSize = 57.sp, lineHeight = 64.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1.2).sp)
    /** secondary hero — M3 displaySmall */
    var displaySmall = TextStyle(fontSize = 45.sp, lineHeight = 52.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.8).sp)
    /** card headline — M3 headlineMedium */
    var headline = TextStyle(fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp)
    /** dialog titles — M3 titleLarge */
    var title = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp)
    /** M3 titleMedium */
    var subtitle = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold)
    /** M3 bodyLarge */
    var body = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium)
    var bodyStrong = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold)
    /** M3 labelMedium */
    var label = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.2.sp)
    var labelStrong = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold)
    /** M3 labelSmall */
    var caption = TextStyle(fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.2.sp)
    /** all-caps section header */
    var section = TextStyle(fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.44.sp)
    var micro = TextStyle(fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium)
    /** small print that still needs to shout: 11 sp, bold, no tracking */
    var captionStrong = TextStyle(fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold)
    /** compact rows and dense secondary copy */
    var bodySmall = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Normal)
    var bodySmallStrong = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold)
    /** map attribution and other legal small print */
    var nano = TextStyle(fontSize = 9.sp, lineHeight = 12.sp, fontWeight = FontWeight.Normal)

    /**
     * Numeric variants carry tabular figures, so money keeps its width as digits change and
     * columns line up. This is the single biggest "feels professional" detail in a money app.
     */
    var displayNumeric = display.copy(fontFeatureSettings = TNUM)
    var displaySmallNumeric = displaySmall.copy(fontFeatureSettings = TNUM)
    var headlineNumeric = headline.copy(fontFeatureSettings = TNUM)
    var titleNumeric = title.copy(fontFeatureSettings = TNUM)
    var subtitleNumeric = subtitle.copy(fontFeatureSettings = TNUM)
    var bodyNumeric = body.copy(fontFeatureSettings = TNUM)
    var labelNumeric = labelStrong.copy(fontFeatureSettings = TNUM)
    var captionNumeric = caption.copy(fontFeatureSettings = TNUM)
    var bodySmallNumeric = bodySmall.copy(fontFeatureSettings = TNUM)
    private var textFamily: FontFamily? = null
    private var displayFamily: FontFamily? = null
    private var readoutFamily: FontFamily? = null

    /**
     * Applied once at startup, before any text is composed.
     *
     * Three faces, three jobs: Space Grotesk for display, JetBrains Mono for readouts (money and
     * chart labels, so figures read like an instrument), Inter for everything else.
     */
    fun install(text: FontFamily, displayFace: FontFamily, readoutFace: FontFamily) {
        if (textFamily === text && displayFamily === displayFace && readoutFamily === readoutFace) return
        textFamily = text; displayFamily = displayFace; readoutFamily = readoutFace

        display = display.copy(fontFamily = displayFace)
        displaySmall = displaySmall.copy(fontFamily = displayFace)
        headline = headline.copy(fontFamily = displayFace)
        title = title.copy(fontFamily = displayFace)

        subtitle = subtitle.copy(fontFamily = text)
        body = body.copy(fontFamily = text)
        bodyStrong = bodyStrong.copy(fontFamily = text)
        label = label.copy(fontFamily = text)
        labelStrong = labelStrong.copy(fontFamily = text)
        caption = caption.copy(fontFamily = text)
        captionStrong = captionStrong.copy(fontFamily = text)
        section = section.copy(fontFamily = text)
        micro = micro.copy(fontFamily = text)
        bodySmall = bodySmall.copy(fontFamily = text)
        bodySmallStrong = bodySmallStrong.copy(fontFamily = text)
        nano = nano.copy(fontFamily = text)

        displayNumeric = display.copy(fontFeatureSettings = TNUM, fontFamily = readoutFace)
        displaySmallNumeric = displaySmall.copy(fontFeatureSettings = TNUM, fontFamily = readoutFace)
        headlineNumeric = headline.copy(fontFeatureSettings = TNUM, fontFamily = readoutFace)
        titleNumeric = title.copy(fontFeatureSettings = TNUM, fontFamily = readoutFace)
        subtitleNumeric = subtitle.copy(fontFeatureSettings = TNUM, fontFamily = readoutFace)
        bodyNumeric = body.copy(fontFeatureSettings = TNUM, fontFamily = readoutFace)
        labelNumeric = labelStrong.copy(fontFeatureSettings = TNUM, fontFamily = readoutFace)
        captionNumeric = caption.copy(fontFeatureSettings = TNUM, fontFamily = readoutFace)
        bodySmallNumeric = bodySmall.copy(fontFeatureSettings = TNUM, fontFamily = readoutFace)
    }
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


/** The app's typeface: one variable file, every weight. */
/** Three families, each with one job: Space Grotesk for display, JetBrains Mono for readouts. */
@Composable
fun bakiFontFamilies(): Triple<FontFamily, FontFamily, FontFamily> {
    val text = Font(Res.font.inter_variable, FontWeight.Normal)
    val display = Font(Res.font.space_grotesk, FontWeight.Normal)
    val readout = Font(Res.font.jetbrains_mono, FontWeight.Normal)
    return remember { Triple(FontFamily(text), FontFamily(display), FontFamily(readout)) }
}

/**
 * The one empty state: a short line, optionally a hint. Same voice everywhere, no trailing "."
 * on the title, a real sentence in the hint.
 */
@Composable
fun EmptyState(title: String, hint: String? = null, modifier: Modifier = Modifier) {
    val baki = LocalBaki.current
    Column(modifier.fillMaxWidth().padding(vertical = Space.xl), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, color = baki.onSurface, style = BakiType.bodyStrong, textAlign = TextAlign.Center)
        if (hint != null) {
            Spacer(Modifier.height(Space.xs))
            Text(hint, color = baki.muted, style = BakiType.label, textAlign = TextAlign.Center)
        }
    }
}
