package app.baki

import androidx.compose.material3.Typography

import androidx.compose.material3.MaterialTheme

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
import baki_compose.composeapp.generated.resources.Res
import org.jetbrains.compose.resources.Font
import androidx.compose.runtime.remember
import androidx.compose.runtime.Composable
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

/** Keeps a long money total inside its column without flattening the hierarchy. */
fun heroMoneyStyle(cents: Long, typography: Typography): TextStyle {
    val value = kotlin.math.abs(cents)
    return when {
        value >= 100_000_00L -> typography.headlineMedium.copy(fontFeatureSettings = "tnum", fontWeight = FontWeight.Bold)
        value >= 10_000_00L -> typography.displaySmall.copy(fontFeatureSettings = "tnum", fontWeight = FontWeight.Bold)
        else -> typography.displayLarge.copy(fontFeatureSettings = "tnum", fontWeight = FontWeight.Bold)
    }
}

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
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
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
    val scheme = MaterialTheme.colorScheme
    Column(modifier.fillMaxWidth().padding(vertical = Space.xl), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, color = scheme.onSurface, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold), textAlign = TextAlign.Center, modifier = Modifier.semantics { heading() })
        if (hint != null) {
            Spacer(Modifier.height(Space.xs))
            Text(hint, color = scheme.onSurfaceVariant, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
        }
        
    }
}

