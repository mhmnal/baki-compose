package app.baki

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight

/**
 * Liquid glass via Kyant's Backdrop: the surface samples the content behind it through a lens
 * (real refraction at the rim), adds vibrancy and blur, and paints a specular highlight.
 *
 * Works on Android and desktop from the same code. LOW tier falls back to a plain Material
 * surface so weak devices never pay for the shader.
 */
@Composable
fun GlassSurface(
    backdrop: Backdrop,
    shape: RoundedCornerShape,
    tier: Int,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val baki = LocalBaki.current

    if (tier <= 0) {
        Box(modifier.clip(shape).background(baki.container)) { content() }
        return
    }

    val strong = tier >= 2
    Box(
        modifier.drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            effects = {
                vibrancy()
                blur(if (strong) 2f.dp.toPx() else 1f.dp.toPx())
                // the rim bend: height controls how deep the edge distorts, amount how far it reaches
                lens(
                    if (strong) 18f.dp.toPx() else 10f.dp.toPx(),
                    if (strong) 36f.dp.toPx() else 22f.dp.toPx(),
                )
            },
            highlight = {
                Highlight(
                    width = 1.dp,
                    blurRadius = if (strong) 2.dp else 1.dp,
                    alpha = 0.35f,
                )
            },
        ),
    ) {
        content()
    }
}
