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
import kotlinx.coroutines.launch
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import dev.lennartegb.shadows.boxShadow

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
    /** the window surface. Every element is painted this colour too; only shadow separates them */
    val base: Color,
    /** the raised face: a hair above the base so the edge reads, still one material */
    val panel: Color,
    /** the directional highlight */
    val light: Color,
    /** the directional shade */
    val dark: Color,
    val ink: Color,
    val muted: Color,
    val accent: Color,
    val onAccent: Color,
    /** accent at low alpha, for selected rows that must stay readable */
    val accentSoft: Color,
    val danger: Color,
    /** shadow strength per mode: light packs lean on the highlight, dark packs on the shade */
    val lightAlpha: Float,
    val darkAlpha: Float,
    /** the veil behind a modal: always a darkened canvas, never the shade colour */
    val scrim: Color,
)

fun neuOf(p: BakiPalette): Neu {
    // a neumorphic canvas is a mid tone, not paper white: blend towards a soft grey-green
    val base = if (p.dark) {
        blend(p.surface, p.primary, 0.10f)
    } else {
        blend(blend(p.surface, p.primary, 0.05f), Color(0xFFB7C0B2), 0.42f)
    }
    // a card face is a hair above the canvas — one material, but the edge still reads — and the
    // shadows are *tinted* directions of that base: the shade runs to deep olive (ink warmed by the
    // accent), the highlight to warm white. Never neutral grey.
    val panel = blend(base, Color.White, if (p.dark) 0.035f else 0.10f)
    val olive = blend(p.onSurface, p.primary, if (p.dark) 0.45f else 0.55f)
    val light = blend(base, blend(Color.White, p.primary, 0.04f), if (p.dark) 0.12f else 0.80f)
    val dark = blend(base, if (p.dark) Color.Black else olive, if (p.dark) 0.62f else 0.30f)
    // secondary text is derived against the canvas, not the palette: the canvas is darker
    val muted = blend(base, p.onSurface, if (p.dark) 0.60f else 0.66f)
    return Neu(
        base = base,
        panel = panel,
        light = light,
        dark = dark,
        ink = p.onSurface,
        muted = muted,
        accent = p.primary,
        onAccent = p.onPrimary,
        accentSoft = blend(base, p.primary, if (p.dark) 0.22f else 0.16f),
        danger = p.down,
        lightAlpha = if (p.dark) 0.30f else 0.75f,
        darkAlpha = if (p.dark) 0.38f else 0.18f,
        scrim = if (p.dark) Color.Black.copy(alpha = 0.70f) else blend(base, p.onSurface, 0.55f).copy(alpha = 0.60f),
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
    /** nothing: text, table rows at rest */
    val flat = 0.dp
    /** chips, icon buttons, menu rows */
    val low = 4.dp
    /** cards, charts, the rail and toolbar */
    val medium = 8.dp
    /** dialogs and menus that float above everything */
    val high = 12.dp
    /** the reference ratio: blur is twice the offset, which is what makes the light soft */
    fun blur(offset: Dp): Dp = offset * 2.2f
}

fun Modifier.raised(
    neu: Neu,
    corner: Dp = 20.dp,
    offset: Dp = Elevation.medium,
    fill: Color = neu.panel,
): Modifier = this
    // the library's CSS-style box shadows: highlight up-left, shade down-right, blur 2x offset
    .boxShadow(
        blurRadius = Elevation.blur(offset),
        spreadRadius = 0.dp,
        offset = DpOffset(-offset, -offset),
        color = neu.light.copy(alpha = neu.lightAlpha),
        shape = RoundedCornerShape(corner),
        clip = false,
        inset = false,
    )
    .boxShadow(
        blurRadius = Elevation.blur(offset),
        spreadRadius = 0.dp,
        offset = DpOffset(offset, offset),
        color = neu.dark.copy(alpha = neu.darkAlpha),
        shape = RoundedCornerShape(corner),
        clip = false,
        inset = false,
    )
    .clip(RoundedCornerShape(corner))
    .background(fill)
    .border(1.dp, neu.ink.copy(alpha = if (neu.darkAlpha > 0.30f) 0.10f else 0.05f), RoundedCornerShape(corner))

/**
 * Floating layer: a dialog or menu sits *above* the canvas rather than being moulded out of it, so
 * it casts one soft shadow downwards. No opposing highlight, no side shadows.
 */
fun Modifier.elevated(
    neu: Neu,
    corner: Dp = Radius.sheet,
    drop: Dp = Elevation.high,
): Modifier = this
    .boxShadow(
        blurRadius = drop,
        spreadRadius = 0.dp,
        offset = DpOffset(0.dp, drop * 0.45f),
        color = neu.dark.copy(alpha = (neu.darkAlpha * 1.5f).coerceAtMost(0.5f)),
        shape = RoundedCornerShape(corner),
        clip = false,
        inset = false,
    )
    .clip(RoundedCornerShape(corner))
    .background(neu.panel)

/**
 * A card casts one shadow, straight down. No highlight above it and no blur reaching over the top
 * edge, because on a large face that reads as a smudge in the whitespace rather than as embossing.
 */
fun Modifier.cardSurface(
    neu: Neu,
    corner: Dp = 24.dp,
    offset: Dp = Elevation.medium,
    fill: Color = neu.panel,
): Modifier = this
    .boxShadow(
        blurRadius = Elevation.blur(offset) * 0.9f,
        spreadRadius = 0.dp,
        offset = DpOffset(0.dp, offset * 0.9f),
        color = neu.dark.copy(alpha = (neu.darkAlpha * 2.0f).coerceAtMost(0.42f)),
        shape = RoundedCornerShape(corner),
        clip = false,
        inset = false,
    )
    .clip(RoundedCornerShape(corner))
    .background(fill)
    .border(1.dp, neu.ink.copy(alpha = if (neu.darkAlpha > 0.30f) 0.10f else 0.05f), RoundedCornerShape(corner))

/** Carved: the same shadows drawn inside the shape, for pressed states, fields and selection. */
fun Modifier.inset(
    neu: Neu,
    corner: Dp = 16.dp,
    offset: Dp = Elevation.low,
    fill: Color = neu.base,
): Modifier = this
    .boxShadow(
        blurRadius = Elevation.blur(offset),
        spreadRadius = 0.dp,
        offset = DpOffset(offset, offset),
        color = neu.dark.copy(alpha = neu.darkAlpha * 0.85f),
        shape = RoundedCornerShape(corner),
        clip = true,
        inset = true,
    )
    .boxShadow(
        blurRadius = Elevation.blur(offset),
        spreadRadius = 0.dp,
        offset = DpOffset(-offset, -offset),
        color = neu.light.copy(alpha = neu.lightAlpha * 0.9f),
        shape = RoundedCornerShape(corner),
        clip = true,
        inset = true,
    )
    .clip(RoundedCornerShape(corner))
    .background(fill)


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
    Box(modifier.cardSurface(neu, corner, offset, fill), content = content)
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
    val shape = RoundedCornerShape(Radius.pill)
    val ink = when {
        !enabled -> neu.muted
        primary -> neu.onAccent
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
                    offset = Elevation.low,
                    fill = when {
                        primary -> neu.accent
                        hovered -> neu.accentSoft
                        else -> neu.base
                    },
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
                            if (on) Modifier.raised(neu, corner = 50.dp, offset = Elevation.low, fill = neu.accent)
                            else Modifier,
                        )
                        .clip(RoundedCornerShape(Radius.pill))
                        .pointerHoverIcon(PointerIcon.Hand)
                        .clickable { onSelect(value) }
                        .padding(horizontal = Space.m, vertical = Space.xs + 2.dp),
                ) {
                    Text(
                        label,
                        color = if (on) neu.onAccent else neu.muted,
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
                if (selected) Modifier.raised(neu, corner = 50.dp, offset = Elevation.low, fill = neu.accent)
                else Modifier.raised(neu, corner = 50.dp, offset = Elevation.low),
            )
            .clip(RoundedCornerShape(Radius.pill))
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable { onClick() }
            .heightIn(min = 32.dp)
            .padding(horizontal = Space.m, vertical = Space.xs),
    ) {
        Text(label, color = if (selected) neu.onAccent else neu.ink, style = BakiType.label)
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
                    Modifier.fillMaxSize(fraction.coerceIn(0.02f, 1f)).clip(RoundedCornerShape(Radius.pill))
                        .background(color),
                )
            } else {
                segments.filter { it.first > 0f }.forEach { (weight, tint) ->
                    Box(
                        Modifier.weight(weight.coerceAtLeast(0.0001f)).fillMaxSize()
                            .clip(RoundedCornerShape(Radius.pill)).background(tint),
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
        Modifier.fillMaxSize().background(neu.scrim)
            .clickable(interactionSource = MutableInteractionSource(), indication = null) { onDismiss() },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .width(width)
                .elevated(neu, corner = Radius.sheet, drop = Elevation.high)
                .clip(RoundedCornerShape(Radius.sheet))
                .background(neu.panel)
                .clickable(interactionSource = MutableInteractionSource(), indication = null) {}
                .padding(Space.xl),
            content = content,
        )
    }
}

/** The material for the current palette, computed once per palette rather than per composable. */
@Composable
fun rememberNeu(): Neu {
    val palette = LocalBaki.current
    return remember(palette) { neuOf(palette) }
}

/** The active material, so deep components do not recompute it. */
val LocalNeu = androidx.compose.runtime.staticCompositionLocalOf<Neu> { neuOf(DarkPalette) }


/**
 * The scrollbar, in the same material: an inset track with a raised thumb. Drag it, or let the wheel
 * do the work — it reflects position either way. Hidden when there is nothing to scroll.
 */
@Composable
fun NeuScrollbar(
    state: androidx.compose.foundation.ScrollState,
    neu: Neu,
    modifier: Modifier = Modifier,
) {
    val max = state.maxValue
    if (max <= 0 || state.viewportSize <= 0) return
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    androidx.compose.foundation.layout.BoxWithConstraints(modifier.width(12.dp).fillMaxHeight()) {
        val track = maxHeight
        val ratio = (state.viewportSize.toFloat() / (state.viewportSize + max)).coerceIn(0.08f, 1f)
        val thumb = (track * ratio).coerceAtLeast(32.dp)
        val travel = (track - thumb).coerceAtLeast(1.dp)
        val offsetY = travel * (state.value.toFloat() / max)
        // thumb only: a groove here reads as a hard vertical line at the card edge
        Box(
            Modifier
                .offset(y = offsetY)
                .height(thumb)
                .width(6.dp)
                .align(Alignment.TopCenter)
                .raised(neu, corner = Radius.pill, offset = 2.dp)
                .clip(RoundedCornerShape(Radius.pill))
                .pointerHoverIcon(PointerIcon.Hand)
                .pointerInput(max, travel, thumb) {
                    detectVerticalDragGestures { _, drag ->
                        val perPx = max.toFloat() / travel.toPx()
                        scope.launch { state.scrollBy(drag * perPx) }
                    }
                },
        )
    }
}
