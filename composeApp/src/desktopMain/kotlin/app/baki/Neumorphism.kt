package app.baki

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.skia.FilterBlurMode
import org.jetbrains.skia.MaskFilter
import org.jetbrains.skia.Paint
import org.jetbrains.skia.RRect

/**
 * Neumorphism, as a system rather than a style.
 *
 * One base colour owns the whole window; every element is that colour, and depth comes only from
 * two soft shadows — light up-left, dark down-right, blur twice the offset. Nothing is outlined.
 * Because contrast is inherently low, every interactive element also carries a text label, a hover
 * state and a visible keyboard focus ring: the material supplies shape, the UI supplies meaning.
 *
 * Derived from the active palette, so sage/paper/night all work and dark mode is recalculated
 * rather than inverted.
 */
data class Neu(
    /** the window surface; everything sits on it */
    val base: Color,
    /** a raised element's fill, a touch above the base */
    val panel: Color,
    /** the directional highlight */
    val light: Color,
    /** the directional shade */
    val dark: Color,
    val ink: Color,
    val muted: Color,
    val accent: Color,
    val onAccent: Color,
    val danger: Color,
)

fun neuOf(p: BakiPalette): Neu {
    // the base is the palette's surface nudged towards its own accent, so the app still has an identity
    val base = blend(p.surface, p.primary, if (p.dark) 0.10f else 0.06f)
    val panel = blend(base, Color.White, if (p.dark) 0.035f else 0.32f)
    // guidance: highlight +15-25%, shade -15-25%. Dark packs need a wider spread to read at all.
    val light = blend(base, Color.White, if (p.dark) 0.16f else 0.80f)
    val dark = blend(base, if (p.dark) Color.Black else p.onSurface, if (p.dark) 0.62f else 0.24f)
    return Neu(
        base = base,
        panel = panel,
        light = light,
        dark = dark,
        ink = p.onSurface,
        muted = p.muted,
        accent = p.primary,
        onAccent = p.onPrimary,
        danger = p.down,
    )
}

/** Linear blend in sRGB; shadows are tinted versions of the base, never pure black or white. */
fun blend(a: Color, b: Color, t: Float): Color = Color(
    red = a.red + (b.red - a.red) * t,
    green = a.green + (b.green - a.green) * t,
    blue = a.blue + (b.blue - a.blue) * t,
    alpha = 1f,
)

/** Elevation tokens: offset with blur at twice the offset, which is what makes the light feel soft. */
object Elevation {
    val flat = 0.dp
    val low = 3.dp
    val medium = 6.dp
    val high = 8.dp
    fun blur(offset: Dp): Dp = offset * 2
}

private fun DrawScope.softRect(x: Float, y: Float, w: Float, h: Float, r: Float, color: Color, blur: Float) {
    if (blur <= 0f) return
    drawIntoCanvas { canvas ->
        val paint = Paint().apply {
            isAntiAlias = true
            this.color = color.toArgb()
            maskFilter = MaskFilter.makeBlur(FilterBlurMode.NORMAL, blur)
        }
        canvas.nativeCanvas.drawRRect(RRect.makeXYWH(x, y, w, h, r), paint)
    }
}

private fun DrawScope.fillRect(r: Float, color: Color, w: Float = size.width, h: Float = size.height) {
    drawIntoCanvas { c ->
        c.nativeCanvas.drawRRect(
            RRect.makeXYWH(0f, 0f, w, h, r),
            Paint().apply { isAntiAlias = true; this.color = color.toArgb() },
        )
    }
}

/** Extruded: light above-left, shade below-right, body painted last so only soft edges show. */
fun Modifier.raised(
    neu: Neu,
    corner: Dp = 20.dp,
    offset: Dp = Elevation.medium,
    fill: Color = neu.panel,
): Modifier = this.drawBehind {
    val r = corner.toPx(); val o = offset.toPx(); val b = Elevation.blur(offset).toPx()
    if (o == 0f) { fillRect(r, fill); return@drawBehind }
    softRect(o, o, size.width, size.height, r, neu.dark.copy(alpha = 0.55f), b)
    softRect(-o, -o, size.width, size.height, r, neu.light.copy(alpha = 0.95f), b)
    fillRect(r, fill)
}

/** Carved: the same shadows drawn inside the shape, for pressed states, fields and selection. */
fun Modifier.inset(
    neu: Neu,
    corner: Dp = 16.dp,
    offset: Dp = Elevation.low,
    fill: Color = neu.base,
): Modifier = this.drawBehind {
    val r = corner.toPx(); val o = offset.toPx(); val b = Elevation.blur(offset).toPx()
    fillRect(r, fill)
    if (o == 0f) return@drawBehind
    drawIntoCanvas { c ->
        val canvas = c.nativeCanvas
        canvas.save()
        canvas.clipRRect(RRect.makeXYWH(0f, 0f, size.width, size.height, r), true)
        val paint = Paint().apply { isAntiAlias = true }
        paint.maskFilter = MaskFilter.makeBlur(FilterBlurMode.NORMAL, b)
        paint.color = neu.dark.copy(alpha = 0.45f).toArgb()
        canvas.drawRRect(RRect.makeXYWH(o, o, size.width, size.height, r), paint)
        paint.color = neu.light.copy(alpha = 0.90f).toArgb()
        canvas.drawRRect(RRect.makeXYWH(-o, -o, size.width, size.height, r), paint)
        canvas.restore()
    }
}

/** Groove: a divider that reads as a pressed seam rather than a drawn line. */
fun Modifier.groove(neu: Neu, thickness: Dp = 1.dp): Modifier = this.drawBehind {
    val h = size.height
    val t = thickness.toPx()
    drawIntoCanvas { c ->
        val canvas = c.nativeCanvas
        canvas.drawRect(org.jetbrains.skia.Rect.makeXYWH(0f, h / 2, size.width, t), Paint().apply { color = neu.dark.copy(alpha = 0.35f).toArgb() })
        canvas.drawRect(org.jetbrains.skia.Rect.makeXYWH(0f, h / 2 + t, size.width, t), Paint().apply { color = neu.light.copy(alpha = 0.75f).toArgb() })
    }
}

// ---------------------------------------------------------------- components

@Composable
fun NeuPanel(
    neu: Neu,
    modifier: Modifier = Modifier,
    corner: Dp = 24.dp,
    offset: Dp = Elevation.medium,
    fill: Color = neu.panel,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier.raised(neu, corner, offset, fill).clip(RoundedCornerShape(corner)), content = content)
}

@Composable
fun NeuWell(
    neu: Neu,
    modifier: Modifier = Modifier,
    corner: Dp = 16.dp,
    offset: Dp = Elevation.low,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier.inset(neu, corner, offset).clip(RoundedCornerShape(corner)), content = content)
}

/**
 * A button is a raised surface that sinks when pressed. Focus is drawn as an accent ring because
 * neumorphism alone cannot show keyboard focus.
 */
@Composable
fun NeuButton(
    neu: Neu,
    label: String,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(50)
    val ink = when {
        !enabled -> neu.muted
        primary -> neu.accent
        else -> neu.ink
    }
    Box(
        modifier
            .then(if (focused) Modifier.border(2.dp, neu.accent, shape) else Modifier)
            .then(
                if (pressed || !enabled) Modifier.inset(neu, corner = 50.dp, offset = Elevation.low)
                else Modifier.raised(
                    neu,
                    corner = 50.dp,
                    offset = if (hovered) Elevation.low else Elevation.medium,
                    fill = neu.panel,
                ),
            )
            .clip(shape)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled) { onClick() }
            .padding(horizontal = Space.l, vertical = Space.s + 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = ink, style = BakiType.labelStrong)
    }
}

/** Icon-only control; the caller supplies the content description. */
@Composable
fun NeuIconButton(
    neu: Neu,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(Radius.input)
    Box(
        modifier
            .then(if (focused) Modifier.border(2.dp, neu.accent, shape) else Modifier)
            .then(
                if (active) Modifier.inset(neu, corner = 16.dp)
                else Modifier.raised(neu, corner = 16.dp, offset = if (hovered) Elevation.low else Elevation.medium),
            )
            .clip(shape)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(interactionSource = interaction, indication = null) { onClick() },
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** Segmented control: an inset track with a raised thumb on the active option. */
@Composable
fun <T> NeuSegmented(
    neu: Neu,
    options: List<Pair<T, String>>,
    selected: T,
    modifier: Modifier = Modifier,
    onSelect: (T) -> Unit,
) {
    NeuWell(neu, modifier, corner = 50.dp, offset = Elevation.low) {
        Row(Modifier.padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            options.forEach { (value, label) ->
                val on = value == selected
                Box(
                    Modifier
                        .then(
                            if (on) Modifier.raised(neu, corner = 50.dp, offset = Elevation.medium, fill = neu.panel)
                            else Modifier,
                        )
                        .clip(RoundedCornerShape(50))
                        .pointerHoverIcon(PointerIcon.Hand)
                        .clickable { onSelect(value) }
                        .padding(horizontal = Space.m, vertical = Space.xs + 2.dp),
                ) {
                    Text(
                        label,
                        color = if (on) neu.accent else neu.muted,
                        style = if (on) BakiType.labelStrong else BakiType.label,
                    )
                }
            }
        }
    }
}

/** Chip: raised when available, sunk when chosen. */
@Composable
fun NeuChip(
    neu: Neu,
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .then(
                if (selected) Modifier.inset(neu, corner = 50.dp)
                else Modifier.raised(neu, corner = 50.dp, offset = Elevation.low),
            )
            .clip(RoundedCornerShape(50))
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable { onClick() }
            .padding(horizontal = Space.m, vertical = Space.xs + 2.dp),
    ) {
        Text(
            label,
            color = if (selected) neu.accent else neu.ink,
            style = BakiType.label,
        )
    }
}

/** A metric block in the right rail: label, value, optional detail. */
@Composable
fun NeuMetric(
    neu: Neu,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = neu.ink,
    detail: String? = null,
) {
    Column(modifier) {
        Text(label, color = neu.muted, style = BakiType.section)
        Text(value, color = valueColor, style = BakiType.titleNumeric)
        if (detail != null) Text(detail, color = neu.muted, style = BakiType.captionNumeric)
    }
}

/** Inset track with a raised fill — progress, stacked spend, month strength. */
@Composable
fun NeuBar(
    neu: Neu,
    fraction: Float,
    modifier: Modifier = Modifier,
    height: Dp = 10.dp,
    color: Color = neu.accent,
    segments: List<Pair<Float, Color>>? = null,
) {
    NeuWell(neu, modifier, corner = height / 2, offset = 2.dp) {
        Row(Modifier.fillMaxSize().padding(2.dp)) {
            if (segments.isNullOrEmpty()) {
                if (fraction <= 0f) return@Row
                Box(
                    Modifier.fillMaxSize(fraction.coerceIn(0.02f, 1f)).clip(RoundedCornerShape(50))
                        .background(color),
                )
            } else {
                segments.filter { it.first > 0f }.forEach { (weight, tint) ->
                    Box(
                        Modifier.weight(weight.coerceAtLeast(0.0001f)).fillMaxSize()
                            .clip(RoundedCornerShape(50)).background(tint),
                    )
                }
            }
        }
    }
}

/** An empty state is a carved well: the absence of content still has to look like the material. */
@Composable
fun NeuEmpty(neu: Neu, title: String, hint: String?, modifier: Modifier = Modifier) {
    NeuWell(neu, modifier.fillMaxWidth(), corner = Radius.card) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = Space.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(title, color = neu.ink, style = BakiType.labelStrong, textAlign = TextAlign.Center)
            if (hint != null) {
                Spacer(Modifier.height(Space.xs))
                Text(hint, color = neu.muted, style = BakiType.caption, textAlign = TextAlign.Center)
            }
        }
    }
}

/** Modal: a raised panel on a dimmed base, never a bottom sheet. */
@Composable
fun NeuDialog(
    neu: Neu,
    onDismiss: () -> Unit,
    width: Dp = 520.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        Modifier.fillMaxSize().background(neu.dark.copy(alpha = 0.50f))
            .clickable(interactionSource = MutableInteractionSource(), indication = null) { onDismiss() },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .width(width)
                .raised(neu, corner = Radius.sheet)
                .clip(RoundedCornerShape(Radius.sheet))
                .background(neu.panel)
                .clickable(interactionSource = MutableInteractionSource(), indication = null) {}
                .padding(Space.xl),
            content = content,
        )
    }
}

/** The active material, so deep components do not recompute it. */
val LocalNeu = androidx.compose.runtime.staticCompositionLocalOf<Neu> { neuOf(DarkPalette) }
