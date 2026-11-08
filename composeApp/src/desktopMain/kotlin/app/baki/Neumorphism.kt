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
import org.jetbrains.skia.RRect
import org.jetbrains.skia.Paint
import org.jetbrains.skia.MaskFilter
import org.jetbrains.skia.FilterBlurMode
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

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
    /** positive semantics: money in, above-target rate */
    val up: Color,
    val danger: Color,
    /** shadow strength per mode: light packs lean on the highlight, dark packs on the shade */
    val lightAlpha: Float,
    val darkAlpha: Float,
    /** the veil behind a modal: always a darkened canvas, never the shade colour */
    val scrim: Color,
    /** true for dark packs, so shadow distances can shorten */
    val isDark: Boolean = false,
)

fun neuOf(p: BakiPalette): Neu {
    // Soft UI 2.0 tokens (the desktop brief): one light and one dark canvas, one light source.
    val dark = p.dark
    val base = if (dark) Color(0xFF1E222B) else Color(0xFFE0E5EC)
    val ink = if (dark) Color(0xFFF8FAFC) else Color(0xFF1E293B)
    // The brief's #64748B secondary is 3.76:1 on #E0E5EC - below AA for body copy. Nudged 20% toward
    // ink it reaches 4.68:1 while still reading as secondary.
    val muted = if (dark) Color(0xFF94A3B8) else blend(Color(0xFF64748B), ink, 0.20f)
    val accent = Color(0xFF3B82F6)   // Electric Blue
    val up = Color(0xFF10B981)       // Emerald
    val danger = Color(0xFFEF4444)
    // Highlight and shade are directions of the same canvas, never pure black/white.
    val light = if (dark) blend(base, Color.White, 0.14f) else blend(base, Color.White, 0.92f)
    val shade = if (dark) blend(base, Color.Black, 0.62f) else Color(0xFFA3B1C6)
    return Neu(
        base = base,
        panel = base,                       // a single moulded surface: face == canvas
        light = light,
        dark = shade,
        ink = ink,
        muted = muted,
        accent = accent,
        onAccent = Color.White,
        accentSoft = blend(base, accent, if (dark) 0.28f else 0.14f),
        up = up,
        danger = danger,
        lightAlpha = if (dark) 0.12f else 0.80f,
        darkAlpha = if (dark) 0.55f else 0.60f,
        scrim = Color(0xFF0F172A).copy(alpha = if (dark) 0.66f else 0.45f),
        isDark = dark,
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
    /** Cards: 9dp out, 16dp blur. */
    val card = 9.dp
    val cardBlur = 16.dp
    /** Controls (buttons, chips, segmented thumb): 5dp out, 10dp blur. */
    val control = 5.dp
    val controlBlur = 10.dp
    /** Wells, inputs, tracks: carved 4dp with an 8dp blur. */
    val inset = 4.dp
    val insetBlur = 8.dp
    /** Floating layers keep their own drop. */
    val floating = 12.dp

    /**
     * How strongly a cavity carves. One place to tune: 0 = flat, 1 = the outer shadow's strength.
     * 0.62 reads as depth without looking like a drawn border.
     */
    const val INSET_STRENGTH = 0.72f

    /** Dark packs need shorter distances; 40% less keeps the edges readable without glare. */
    fun distance(neu: Neu, base: Dp): Dp = if (neu.isDark) base * 0.6f else base
    fun blur(neu: Neu, base: Dp): Dp = if (neu.isDark) base * 0.6f else base
}

fun Modifier.raised(
    neu: Neu,
    corner: Dp = 20.dp,
    offset: Dp = Elevation.control,
    blur: Dp = Elevation.controlBlur,
    fill: Color = neu.base,
): Modifier = this
    .graphicsLayer()
    .drawBehind {
        val o = Elevation.distance(neu, offset).toPx()
        val b = Elevation.blur(neu, blur).toPx()
        val r = corner.toPx()
        val shape = Path().apply {
            addRoundRect(RoundRect(0f, 0f, size.width, size.height, r, r))
        }
        fun shadow(color: Color, alpha: Float, dx: Float, dy: Float) {
            drawIntoCanvas { canvas ->
                val paint = Paint().apply {
                    isAntiAlias = true
                    this.color = color.copy(alpha = alpha).toArgb()
                    maskFilter = MaskFilter.makeBlur(FilterBlurMode.NORMAL, b * 0.6f)
                }
                canvas.nativeCanvas.drawRRect(
                    RRect.makeXYWH(dx, dy, size.width, size.height, r, r), paint,
                )
            }
        }
        // shade first, highlight last: the lit corner must not be tinted by the shade
        shadow(neu.dark, neu.darkAlpha, o, o)
        shadow(neu.light, neu.lightAlpha, -o, -o)
        drawPath(shape, fill)
    }
    .clip(RoundedCornerShape(corner))

/**
 * Floating layer: a dialog or menu sits *above* the canvas rather than being moulded out of it, so
 * it casts one soft shadow downwards. No opposing highlight, no side shadows.
 */
fun Modifier.elevated(
    neu: Neu,
    corner: Dp = Radius.sheet,
    drop: Dp = Elevation.floating,
): Modifier = this
    .graphicsLayer()
    .drawBehind {
        val b = drop.toPx()
        val o = (drop * 0.45f).toPx()
        val r = corner.toPx()
        val shape = Path().apply {
            addRoundRect(RoundRect(0f, 0f, size.width, size.height, r, r))
        }
        drawIntoCanvas { canvas ->
            val paint = Paint().apply {
                isAntiAlias = true
                color = neu.dark.copy(alpha = (neu.darkAlpha * 0.9f).coerceAtMost(0.5f)).toArgb()
                maskFilter = MaskFilter.makeBlur(FilterBlurMode.NORMAL, b * 0.45f)
            }
            canvas.nativeCanvas.drawRRect(
                RRect.makeXYWH(0f, o, size.width, size.height, r, r), paint,
            )
        }
        drawPath(shape, neu.panel)
    }
    .clip(RoundedCornerShape(corner))

/**
 * A card casts the full pair: the 9/16 emboss that makes the face sit in the material. The face is the
 * canvas colour, so the only thing separating it from the page is the light and the shade.
 */
fun Modifier.cardSurface(
    neu: Neu,
    corner: Dp = 24.dp,
    offset: Dp = Elevation.card,
    blur: Dp = Elevation.cardBlur,
    fill: Color = neu.base,
): Modifier = raised(neu, corner = corner, offset = offset, blur = blur, fill = fill)

/** Carved: the same shadows drawn inside the shape, for pressed states, fields and selection. */
fun Modifier.inset(
    neu: Neu,
    corner: Dp = 16.dp,
    offset: Dp = Elevation.inset,
    blur: Dp = Elevation.insetBlur,
    fill: Color = neu.base,
): Modifier = this
    .graphicsLayer()
    .drawBehind {
        val r = corner.toPx()
        val o = Elevation.distance(neu, offset).toPx()
        val b = Elevation.blur(neu, blur).toPx()
        val shape = Path().apply { addRoundRect(RoundRect(0f, 0f, size.width, size.height, r, r)) }
        drawPath(shape, fill)
        // inner shadows painted as blurred strokes *clipped to the shape*: nothing can leak past a
        // rounded corner, which is what the library's inset did (dark crescents and a bright seam)
        clipPath(shape) {
            drawIntoCanvas { canvas ->
                fun ring(color: Color, alpha: Float, dx: Float) {
                    val paint = Paint().apply {
                        isAntiAlias = true
                        this.color = color.copy(alpha = alpha).toArgb()
                        maskFilter = MaskFilter.makeBlur(FilterBlurMode.NORMAL, b * 0.6f)
                        mode = org.jetbrains.skia.PaintMode.STROKE
                        strokeWidth = b
                    }
                    val nc = canvas.nativeCanvas
                    nc.save()
                    nc.translate(dx, dx)
                    nc.drawRRect(RRect.makeXYWH(0f, 0f, size.width, size.height, r, r), paint)
                    nc.restore()
                }
                // BAKI_INSET lets the strength be judged live without a rebuild
                val strength = System.getenv("BAKI_INSET")?.toFloatOrNull() ?: Elevation.INSET_STRENGTH
                ring(neu.dark, neu.darkAlpha * strength, o)          // shade from the top-left
                ring(neu.light, neu.lightAlpha * (strength + 0.05f), -o) // light from below-right
            }
        }
    }

@Composable
fun NeuPanel(
    neu: Neu,
    modifier: Modifier = Modifier,
    corner: Dp = 24.dp,
    offset: Dp = Elevation.card,
    fill: Color = neu.base,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier.cardSurface(neu, corner = corner, offset = offset, fill = fill), content = content)
}

@Composable
fun NeuWell(
    neu: Neu,
    modifier: Modifier = Modifier,
    corner: Dp = 16.dp,
    offset: Dp = Elevation.inset,
    /** false paints a flat tray: used by the chart plots, where the cavity added nothing but corner noise */
    inset: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    // When inset, the modifier already fills the shape and draws the inner shadows. Painting a
    // background afterwards sat on top of them and flattened the cavity - the inset looked absent.
    if (inset) {
        Box(modifier.inset(neu, corner, offset).clip(RoundedCornerShape(corner)), content = content)
    } else {
        Box(modifier.clip(RoundedCornerShape(corner)).background(neu.base), content = content)
    }
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
                if (pressed || !enabled) Modifier.inset(neu, corner = 50.dp, offset = Elevation.inset)
                else Modifier.raised(
                    neu,
                    corner = 50.dp,
                    offset = Elevation.control,
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
                else Modifier.raised(neu, corner = 16.dp, offset = if (hovered) Elevation.control else Elevation.card),
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
    NeuWell(neu, modifier, corner = 50.dp, offset = Elevation.inset) {
        Row(Modifier.padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            options.forEach { (value, label) ->
                val on = value == selected
                Box(
                    Modifier
                        .then(
                            if (on) Modifier.inset(neu, corner = 50.dp, offset = Elevation.inset, fill = neu.accent)
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
                if (selected) Modifier.inset(neu, corner = 50.dp, offset = Elevation.inset, fill = neu.accent)
                else Modifier.raised(neu, corner = 50.dp, offset = Elevation.control),
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
        // Floating layers are frosted glass, not another extrusion: translucent face, thin light
        // border, and it never casts a neumorphic double shadow.
        Column(
            Modifier
                .width(width)
                .clip(RoundedCornerShape(Radius.sheet))
                .background(
                    if (neu.isDark) Color(0xFF1E222B).copy(alpha = 0.86f)
                    else Color(0xFFF8FAFC).copy(alpha = 0.88f),
                )
                .border(
                    1.dp,
                    Color.White.copy(alpha = if (neu.isDark) 0.16f else 0.45f),
                    RoundedCornerShape(Radius.sheet),
                )
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
