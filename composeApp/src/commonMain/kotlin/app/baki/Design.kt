package app.baki

import androidx.graphics.shapes.rectangle

import androidx.compose.material3.toPath

import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.testTag


import androidx.compose.foundation.layout.Arrangement

import androidx.compose.foundation.layout.Row

import androidx.compose.foundation.layout.PaddingValues

import androidx.compose.material3.Typography

import androidx.compose.material3.MaterialTheme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selectableGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.draw.clip
import androidx.compose.material3.toShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import baki_compose.composeapp.generated.resources.Res
import baki_compose.composeapp.generated.resources.barlow_condensed_bold
import org.jetbrains.compose.resources.Font
import androidx.compose.runtime.remember
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
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
        value >= 100_000_00L -> typography.headlineMediumEmphasized.copy(fontFeatureSettings = "tnum")
        value >= 10_000_00L -> typography.displaySmallEmphasized.copy(fontFeatureSettings = "tnum")
        else -> typography.displayLargeEmphasized.copy(fontFeatureSettings = "tnum")
    }
}

/**
 * The one empty state: a short line, optionally a hint. Same voice everywhere, no trailing "."
 * on the title, a real sentence in the hint.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun EmptyState(title: String, hint: String? = null, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier.fillMaxWidth().padding(vertical = Space.xl), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(42.dp)
                .clip(androidx.compose.material3.MaterialShapes.SoftBurst.toShape())
                .background(scheme.primaryContainer),
        )
        Spacer(Modifier.height(Space.m))
        Text(title, color = scheme.onSurface, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold), textAlign = TextAlign.Center, modifier = Modifier.semantics { heading() })
        if (hint != null) {
            Spacer(Modifier.height(Space.xs))
            Text(hint, color = scheme.onSurfaceVariant, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
        }
        
    }
}


/**
 * Official M3 Expressive connected button group primitive.
 *
 * 2dp inner spacing, fully rounded outer edges, square inner edges, and shape-morphing on the
 * selected item. Use only for single-select choices that used to be segmented controls.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ConnectedChoiceGroup(
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val colors = androidx.compose.material3.ToggleButtonDefaults.toggleButtonColors(
        containerColor = scheme.surfaceContainerHigh,
        contentColor = scheme.onSurfaceVariant,
        checkedContainerColor = scheme.primary,
        checkedContentColor = scheme.onPrimary,
    )
    Row(
        modifier = modifier.semantics { selectableGroup() },
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        options.forEachIndexed { index, label ->
            val checked = selected == label
            androidx.compose.material3.ToggleButton(
                checked = checked,
                onCheckedChange = { onSelect(label) },
                shapes = when {
                    options.size == 1 -> androidx.compose.material3.ButtonGroupDefaults.connectedMiddleButtonShapes()
                    index == 0 -> androidx.compose.material3.ButtonGroupDefaults.connectedLeadingButtonShapes()
                    index == options.lastIndex -> androidx.compose.material3.ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> androidx.compose.material3.ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
                colors = colors,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = Control.primary)
                    .testTag("baki-choice")
                    .semantics { role = androidx.compose.ui.semantics.Role.RadioButton },
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = Space.s, vertical = Space.xs),
            ) {
                Text(
                    label,
                    style = if (checked) MaterialTheme.typography.labelLargeEmphasized
                    else MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private val ChipSoftRect = androidx.graphics.shapes.RoundedPolygon.rectangle(
    width = 1f,
    height = 1f,
    rounding = androidx.graphics.shapes.CornerRounding(radius = 0.18f),
)
private val ChipPill = androidx.graphics.shapes.RoundedPolygon.rectangle(
    width = 1f,
    height = 1f,
    rounding = androidx.graphics.shapes.CornerRounding(radius = 0.5f),
)

/** Spring shape morph from a soft-cornered chip/badge to a pill. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun morphChipShape(selected: Boolean): androidx.compose.ui.graphics.Shape =
    rememberMorphShape(ChipSoftRect, ChipPill, selected, "chipShape")

/** Morph between two normalized polygons using the expressive fast-spatial spring. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun rememberMorphShape(
    from: androidx.graphics.shapes.RoundedPolygon,
    to: androidx.graphics.shapes.RoundedPolygon,
    atEnd: Boolean,
    label: String,
): androidx.compose.ui.graphics.Shape {
    val progress by animateFloatAsState(
        targetValue = if (atEnd) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = label,
    )
    val morph = remember(from, to) {
        androidx.graphics.shapes.Morph(from.normalized(), to.normalized())
    }
    return remember(morph, progress) { MorphShape(morph, progress) }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
private class MorphShape(
    private val morph: androidx.graphics.shapes.Morph,
    private val progress: Float,
) : androidx.compose.ui.graphics.Shape {
    override fun createOutline(
        size: androidx.compose.ui.geometry.Size,
        layoutDirection: androidx.compose.ui.unit.LayoutDirection,
        density: androidx.compose.ui.unit.Density,
    ): androidx.compose.ui.graphics.Outline {
        val path = androidx.compose.ui.graphics.Path()
        morph.toPath(progress.coerceIn(0f, 1f), path)
        val matrix = androidx.compose.ui.graphics.Matrix().apply { scale(size.width, size.height) }
        path.transform(matrix)
        val bounds = path.getBounds()
        path.translate(
            androidx.compose.ui.geometry.Offset(
                size.width / 2f - bounds.center.x,
                size.height / 2f - bounds.center.y,
            ),
        )
        return androidx.compose.ui.graphics.Outline.Generic(path)
    }
}


/** Shared nested scheme for content placed on a tinted card. Kept testable so card contrast cannot regress. */
internal fun md3CardScheme(
    base: androidx.compose.material3.ColorScheme,
    container: androidx.compose.ui.graphics.Color,
    contentColor: androidx.compose.ui.graphics.Color,
): androidx.compose.material3.ColorScheme = base.copy(
    onSurface = contentColor,
    onSurfaceVariant = contentColor.copy(alpha = 0.72f),
    surface = container,
    surfaceContainer = container,
    surfaceContainerLow = container,
    surfaceContainerHigh = container,
    primary = if (container == base.primaryContainer) contentColor else base.primary,
    onPrimary = if (container == base.primaryContainer) container else base.onPrimary,
)

/**
 * A compact, shaped back/forward control used by the day and month navigators.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ShapeNavButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    description: String,
    modifier: Modifier = Modifier,
    background: Color = MaterialTheme.colorScheme.primaryContainer,
    ink: Color = MaterialTheme.colorScheme.onPrimaryContainer,
) {
    Box(
        modifier
            .size(48.dp)
            .clip(androidx.compose.foundation.shape.CircleShape)
            .background(if (enabled) background else background.copy(alpha = 0.35f))
            .clickable(enabled = enabled, onClick = onClick)
            .semantics {
                role = Role.Button
                contentDescription = description
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (enabled) ink else ink.copy(alpha = 0.40f),
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
        )
    }
}

/** Consistent expressive heading for the remaining dialog shells. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BakiDialogHeader(
    title: String,
    subtitle: String? = null,
    symbol: String = "✦",
    background: Color = MaterialTheme.colorScheme.tertiaryContainer,
    ink: Color = MaterialTheme.colorScheme.onTertiaryContainer,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.m),
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(androidx.compose.material3.MaterialShapes.SoftBurst.toShape())
                .background(background),
            contentAlignment = Alignment.Center,
        ) {
            Text(symbol, color = ink, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
        }
        Column {
            Text(
                title,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            )
            if (subtitle != null) {
                Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/** Brand display face used only for the Today hero amount. Barlow Condensed has tabular figures. */
@Composable
fun bakiDisplayFontFamily(): FontFamily = FontFamily(
    Font(Res.font.barlow_condensed_bold, weight = FontWeight.Bold),
)
