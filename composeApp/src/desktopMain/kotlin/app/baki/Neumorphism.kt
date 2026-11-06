package app.baki

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.skia.FilterBlurMode
import org.jetbrains.skia.MaskFilter
import org.jetbrains.skia.Paint
import org.jetbrains.skia.RRect

/**
 * Neumorphism: one base colour for the whole surface, depth made only by two soft shadows — light
 * from the top left, dark from the bottom right. Elements are the same colour as their background;
 * nothing is outlined, so the eye reads shape from light alone.
 *
 * Everything is derived from the active palette's surface, so all three packs stay consistent and
 * dark mode is not an afterthought.
 */
/** The active neumorphic material; provided once at the shell root. */
val LocalNeu = staticCompositionLocalOf<Neu> { neuOf(DarkPalette) }

data class Neu(
    val base: Color,
    /** slightly lifted from the base, so a panel reads above the canvas without a border */
    val panel: Color,
    val light: Color,
    val dark: Color,
    val ink: Color,
    val muted: Color,
    val accent: Color,
    val onAccent: Color,
)

fun neuOf(p: BakiPalette): Neu {
    val base = if (p.dark) {
        blend(p.surface, p.primary, 0.10f)
    } else {
        blend(p.surface, p.primary, 0.06f)
    }
    // the whole trick: the two shadows are the base moved towards white and towards ink
    val light = blend(base, Color.White, if (p.dark) 0.10f else 0.80f)
    val dark = blend(base, p.onSurface, if (p.dark) 0.60f else 0.26f)
    val panel = blend(base, Color.White, if (p.dark) 0.035f else 0.34f)
    return Neu(
        base = base,
        panel = panel,
        light = light,
        dark = dark,
        ink = p.onSurface,
        muted = p.muted,
        accent = p.primary,
        onAccent = p.onPrimary,
    )
}

/** Linear blend in sRGB, which is what these shadows are: tinted versions of the base. */
fun blend(a: Color, b: Color, t: Float): Color = Color(
    red = a.red + (b.red - a.red) * t,
    green = a.green + (b.green - a.green) * t,
    blue = a.blue + (b.blue - a.blue) * t,
    alpha = 1f,
)

private fun DrawScope.softRRect(rrect: RRect, color: Color, blurPx: Float) {
    drawIntoCanvas { canvas ->
        val paint = Paint().apply {
            isAntiAlias = true
            this.color = color.toArgb()
            maskFilter = MaskFilter.makeBlur(FilterBlurMode.NORMAL, blurPx)
        }
        canvas.nativeCanvas.drawRRect(rrect, paint)
    }
}

/**
 * A raised element: light above-left, dark below-right, then the body painted on top so only the
 * soft edges show.
 */
fun Modifier.raised(
    neu: Neu,
    corner: Dp = 20.dp,
    offset: Dp = 6.dp,
    blur: Dp = 18.dp,
): Modifier = this.drawBehind {
    val r = corner.toPx()
    val off = offset.toPx()
    val b = blur.toPx()
    val w = size.width
    val h = size.height
    softRRect(RRect.makeXYWH(off, off, w, h, r), neu.dark.copy(alpha = 0.55f), b)
    softRRect(RRect.makeXYWH(-off, -off, w, h, r), neu.light.copy(alpha = 0.95f), b)
    drawIntoCanvas { c ->
        val p = Paint().apply { isAntiAlias = true; color = neu.panel.toArgb() }
        c.nativeCanvas.drawRRect(RRect.makeXYWH(0f, 0f, w, h, r), p)
    }
}

/**
 * A pressed element (selected rows, inputs, the current tab): the same two shadows, but drawn
 * *inside* the shape so the surface looks carved out of the base.
 */
fun Modifier.inset(
    neu: Neu,
    corner: Dp = 20.dp,
    offset: Dp = 3.dp,
    blur: Dp = 8.dp,
): Modifier = this.drawBehind {
    val r = corner.toPx()
    val off = offset.toPx()
    val b = blur.toPx()
    val w = size.width
    val h = size.height
    val shape = RRect.makeXYWH(0f, 0f, w, h, r)
    drawIntoCanvas { c ->
        val canvas = c.nativeCanvas
        val fill = Paint().apply { isAntiAlias = true; color = neu.base.toArgb() }
        canvas.drawRRect(shape, fill)
        canvas.save()
        canvas.clipRRect(shape, true)
        val paint = Paint().apply { isAntiAlias = true }
        // inner dark from the top-left, inner light from the bottom-right
        paint.color = neu.dark.copy(alpha = 0.40f).toArgb()
        paint.maskFilter = MaskFilter.makeBlur(FilterBlurMode.NORMAL, b)
        canvas.drawRRect(RRect.makeXYWH(off, off, w, h, r), paint)
        paint.color = neu.light.copy(alpha = 0.90f).toArgb()
        canvas.drawRRect(RRect.makeXYWH(-off, -off, w, h, r), paint)
        canvas.restore()
    }
}

/** Raised panel: the shell's building block (rail, toolbar, content card, dialogs). */
@Composable
fun NeuPanel(
    neu: Neu,
    modifier: Modifier = Modifier,
    corner: Dp = 24.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier
            .raised(neu, corner = corner)
            .clip(RoundedCornerShape(corner)),
        content = content,
    )
}

/** Inset field surface for inputs and pressed states. */
@Composable
fun NeuInset(
    neu: Neu,
    modifier: Modifier = Modifier,
    corner: Dp = 14.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier.inset(neu, corner = corner).clip(RoundedCornerShape(corner)),
        content = content,
    )
}
