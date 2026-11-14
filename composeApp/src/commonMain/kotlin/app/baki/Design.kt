package app.baki

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import baki_compose.composeapp.generated.resources.inter_variable
import baki_compose.composeapp.generated.resources.jetbrains_mono
import baki_compose.composeapp.generated.resources.source_serif_4
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
    /** MD3 shape scale: XS 4, S 8, M 12, L 16, XL 28. */
    val pill = 20.dp
    val glass = 28.dp
    val tiny = 4.dp
    val small = 8.dp
    val bar = 8.dp
    val input = 12.dp
    val card = 12.dp
    val large = 16.dp
    val dialog = 28.dp
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

/** Control heights: every interactive control clears the 48dp accessibility floor. */
object Control {
    val primary = 48.dp
    val secondary = 48.dp
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
    var display = TextStyle(fontSize = 57.sp, lineHeight = 64.sp, fontWeight = FontWeight.Normal, letterSpacing = (-1.2).sp)
    /** secondary hero — M3 displaySmall */
    var displaySmall = TextStyle(fontSize = 45.sp, lineHeight = 52.sp, fontWeight = FontWeight.Normal, letterSpacing = (-0.8).sp)
    /** card headline — M3 headlineMedium */
    var headline = TextStyle(fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.Normal, letterSpacing = (-0.4).sp)
    /** dialog titles — M3 titleLarge */
    var title = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Normal, letterSpacing = 0.sp)
    /** M3 titleMedium */
    var subtitle = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold)
    /** M3 bodyLarge */
    var body = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium)
    var bodyStrong = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold)
    /** M3 labelMedium */
    var label = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.2.sp)
    var labelStrong = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold)
    /** M3 labelSmall */
    var caption = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.15.sp)
    /** all-caps section header */
    var section = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp)
    var micro = TextStyle(fontSize = 12.sp, lineHeight = 17.sp, fontWeight = FontWeight.Medium)
    /** small print that still needs to shout: 14 sp, bold, no tracking */
    var captionStrong = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold)
    /** compact rows and dense secondary copy */
    var bodySmall = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Normal)
    var bodySmallStrong = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold)
    /** map attribution and other legal small print */
    var nano = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Normal)

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
    fun install(text: FontFamily, displayFace: FontFamily, readoutFace: FontFamily, paper: Boolean = true) {
        if (textFamily === text && displayFamily === displayFace && readoutFamily === readoutFace) return
        textFamily = text; displayFamily = displayFace; readoutFamily = readoutFace

        val displayWeight = if (paper) FontWeight.Normal else FontWeight.Bold
        val titleWeight = if (paper) FontWeight.Normal else FontWeight.SemiBold
        display = display.copy(fontFamily = displayFace, fontWeight = displayWeight)
        displaySmall = displaySmall.copy(fontFamily = displayFace, fontWeight = displayWeight)
        headline = headline.copy(fontFamily = text, fontWeight = titleWeight)
        title = title.copy(fontFamily = text, fontWeight = titleWeight)

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

/** Keeps a long money total inside its column without flattening the hierarchy. */
fun heroMoneyStyle(cents: Long): TextStyle {
    val value = kotlin.math.abs(cents)
    return when {
        value >= 100_000_00L -> BakiType.headlineNumeric
        value >= 10_000_00L -> BakiType.displaySmallNumeric
        else -> BakiType.displayNumeric
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
    // Paper Ledger on the phone; the desk keeps its Space Grotesk / JetBrains Mono voice.
    val paper = !Caps.isDesktop || PhonePreview.active
    val serif = Font(Res.font.source_serif_4, FontWeight.Normal)
    val display = if (paper) FontFamily(serif) else FontFamily(Font(Res.font.space_grotesk, FontWeight.Normal))
    val readout = if (paper) FontFamily(serif) else FontFamily(Font(Res.font.jetbrains_mono, FontWeight.Normal))
    return remember { Triple(FontFamily(text), display, readout) }
}

/** Retained as a zero-height compatibility shim for older layouts; MD3 separates by tone. */
@Composable
fun LedgerRule(modifier: Modifier = Modifier, double: Boolean = false, alpha: Float = 0.32f) = Unit

/** Press feedback for the few surfaces that are not Material buttons. */
@Composable
fun Modifier.tactileClickable(
    enabled: Boolean = true,
    haptic: Boolean = true,
    onClick: () -> Unit,
): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.98f else 1f,
        animationSpec = BakiMotion.spatialFast(),
        label = "pressScale",
    )
    return this.graphicsLayer {
        scaleX = scale; scaleY = scale
    }.clickable(
        interactionSource = interaction,
        indication = LocalIndication.current,
        enabled = enabled,
    ) {
        if (haptic) PlatformEffects.haptic(Haptic.Tick)
        onClick()
    }
}

/**
 * The one empty state: a short line, optionally a hint. Same voice everywhere, no trailing "."
 * on the title, a real sentence in the hint.
 */
@Composable
fun EmptyState(title: String, hint: String? = null, modifier: Modifier = Modifier) {
    val baki = LocalBaki.current
    Column(modifier.fillMaxWidth().padding(vertical = Space.xl), horizontalAlignment = Alignment.CenterHorizontally) {
        if (baki.paper) LedgerRule(alpha = 0.16f)
        if (baki.paper) Spacer(Modifier.height(Space.m))
        Text(title, color = baki.onSurface, style = BakiType.bodyStrong, textAlign = TextAlign.Center, modifier = Modifier.semantics { heading() })
        if (hint != null) {
            Spacer(Modifier.height(Space.xs))
            Text(hint, color = baki.muted, style = BakiType.label, textAlign = TextAlign.Center)
        }
        if (baki.paper) {
            Spacer(Modifier.height(Space.m))
            LedgerRule(alpha = 0.16f)
        }
    }
}

/** A full-width paper action: 48dp target, ink label, a rule beneath; no filled card. */
@Composable
fun LedgerAction(
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val baki = LocalBaki.current
    Column(
        modifier.fillMaxWidth().clickable(enabled = enabled) { onClick() }
            .semantics { role = androidx.compose.ui.semantics.Role.Button },
    ) {
        Text(
            label, color = if (enabled) baki.primary else baki.muted,
            style = BakiType.labelStrong, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().heightIn(min = Control.primary).padding(vertical = Space.m),
        )
        LedgerRule(alpha = 0.18f)
    }
}
