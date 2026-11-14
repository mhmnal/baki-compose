package app.baki

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * The live-shift trace: the line is the road, the dashes are movement, and the head dot is the car.
 * The previous expressive revision used this instead of a pulsing status dot. Paused and
 * reduced-motion states freeze the trace in place.
 */
@Composable
fun LiveTrace(
    active: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
    reducedMotion: Boolean = false,
) {
    val phase = if (active && !reducedMotion) {
        val transition = rememberInfiniteTransition(label = "liveTrace")
        val p by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 2800, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "liveTracePhase",
        )
        p
    } else {
        0.46f
    }

    Canvas(
        modifier.semantics {
            contentDescription = when {
                !active -> "Live route paused"
                reducedMotion -> "Live route, reduced motion"
                else -> "Live route moving"
            }
        },
    ) {
        val w = size.width
        val h = size.height
        val x0 = 1.dp.toPx()
        val x1 = w - 1.dp.toPx()
        val cy = h * 0.12f
        val y0 = h * 0.68f
        val y1 = h * 0.42f
        val path = Path().apply {
            moveTo(x0, y0)
            quadraticBezierTo(w * 0.48f, cy, x1, y1)
        }

        drawPath(
            path = path,
            color = color.copy(alpha = if (active) 0.26f else 0.18f),
            style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round),
        )
        drawPath(
            path = path,
            color = color.copy(alpha = if (active) 0.9f else 0.45f),
            style = Stroke(
                width = 1.5.dp.toPx(),
                cap = StrokeCap.Round,
                pathEffect = if (active && !reducedMotion) {
                    PathEffect.dashPathEffect(
                        intervals = floatArrayOf(5.dp.toPx(), 4.dp.toPx()),
                        phase = -phase * 22.dp.toPx(),
                    )
                } else null,
            ),
        )

        val t = if (active) phase else 0.46f
        val x = (1 - t) * (1 - t) * x0 + 2 * (1 - t) * t * (w * 0.48f) + t * t * x1
        val y = (1 - t) * (1 - t) * y0 + 2 * (1 - t) * t * cy + t * t * y1
        drawCircle(color.copy(alpha = 0.18f), radius = 4.5.dp.toPx(), center = Offset(x, y))
        drawCircle(color, radius = 2.dp.toPx(), center = Offset(x, y))
    }
}
