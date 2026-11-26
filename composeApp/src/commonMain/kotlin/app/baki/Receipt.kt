package app.baki

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/** A paper ticket: four small radius corners, with a shallow bite on each side at mid-height. */
internal class ReceiptShape : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val corner = with(density) { 4.dp.toPx() }
        val notch = with(density) { 7.dp.toPx() }
        val base = Path().apply {
            addRoundRect(
                RoundRect(
                    0f, 0f, size.width, size.height,
                    CornerRadius(corner, corner),
                ),
            )
        }
        val cuts = Path().apply {
            addOval(Rect(center = Offset(0f, size.height * 0.5f), radius = notch))
            addOval(Rect(center = Offset(size.width, size.height * 0.5f), radius = notch))
        }
        val outline = Path().apply { op(base, cuts, PathOperation.Difference) }
        return Outline.Generic(outline)
    }
}

/** Receipt paper is the only surface allowed a border plus an offset shadow. */
@Composable
internal fun ReceiptCard(
    reduceMotion: Boolean,
    modifier: Modifier = Modifier,
    delayMillis: Int = 0,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val progress by animateFloatAsState(
        targetValue = if (entered || reduceMotion) 1f else 0f,
        animationSpec = tween(durationMillis = 280, delayMillis = delayMillis, easing = FastOutSlowInEasing),
        label = "receipt-in",
    )
    Box(
        modifier.fillMaxWidth().graphicsLayer {
            alpha = progress
            translationY = (1f - progress) * 12.dp.toPx()
        },
    ) {
        val paper = ReceiptShape()
        Box(
            Modifier.matchParentSize()
                .offset(x = 5.dp, y = 5.dp)
                .background(scheme.primary.copy(alpha = 0.18f), paper),
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = paper,
            color = scheme.surface,
            border = BorderStroke(1.dp, scheme.outline.copy(alpha = 0.55f)),
        ) {
            Column(Modifier.padding(Space.l), content = content)
        }
    }
}

@Composable
internal fun ReceiptRule(color: Color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)) {
    Canvas(Modifier.fillMaxWidth().height(1.dp)) {
        drawLine(
            color = color,
            start = Offset(0f, 0f),
            end = Offset(size.width, 0f),
            strokeWidth = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())),
        )
    }
}

@Composable
internal fun StaggeredReceiptRow(
    index: Int,
    reduceMotion: Boolean,
    shown: Boolean,
    content: @Composable () -> Unit,
) {
    val p by animateFloatAsState(
        targetValue = if (shown || reduceMotion) 1f else 0f,
        animationSpec = if (reduceMotion) tween(durationMillis = 0) else
            tween(durationMillis = 220, delayMillis = index.coerceAtMost(8) * 45, easing = FastOutSlowInEasing),
        label = "receipt-line-in",
    )
    Box(Modifier.graphicsLayer {
        alpha = p
        translationY = (1f - p) * 7.dp.toPx()
    }) { content() }
}
