package app.baki

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle

/**
 * A money figure that rolls when it changes: the new value slides up into place while the old one
 * leaves. Only fires when the formatted string actually differs, so a live tick cannot spam it.
 */
@Composable
fun RollingAmount(
    text: String,
    color: Color,
    style: TextStyle,
    modifier: Modifier = Modifier,
) {
    AnimatedContent(
        targetState = text,
        transitionSpec = {
            (slideInVertically(BakiMotion.spatial()) { it } + fadeIn(BakiMotion.effects()))
                .togetherWith(
                    slideOutVertically(BakiMotion.spatial()) { -it } + fadeOut(BakiMotion.effects()),
                )
        },
        label = "amount",
    ) { value ->
        Text(value, color = color, style = style, modifier = modifier)
    }
}
