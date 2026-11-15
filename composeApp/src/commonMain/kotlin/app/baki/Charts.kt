package app.baki

import androidx.compose.material3.toShape

import androidx.compose.material3.MaterialShapes

import androidx.compose.foundation.layout.Box

import androidx.compose.foundation.shape.CircleShape

import androidx.compose.material3.MaterialTheme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.Canvas
import androidx.compose.ui.unit.sp
import kotlin.math.abs


/** The last seven days ending at [endDay]: a bar per day, green when money stayed. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun WeekStrip(store: Store, endDay: String) {
    val scheme = MaterialTheme.colorScheme
    val days = (6 downTo 0).map { addDays(endDay, -it) }
    val totals = remember(store, endDay) { days.map { dayTotals(store, it) } }
    val peak = totals.maxOfOrNull { abs(it.net) }?.coerceAtLeast(1) ?: 1
    val today = todayIso()

    BoxWithConstraints {
        val compact = maxWidth < 360.dp || LocalDensity.current.fontScale > 1.15f
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            days.forEachIndexed { i, d ->
                val t = totals[i]
                val up = t.net >= 0
                val fraction = (abs(t.net).toFloat() / peak).coerceIn(0.04f, 1f)
                Column(
                    Modifier.weight(1f)
                        .then(
                            Modifier.clip(if (d == today) MaterialTheme.shapes.large else MaterialTheme.shapes.medium)
                                .background(if (d == today) scheme.primaryContainer else scheme.surfaceContainerLow)
                                .padding(vertical = Space.s)
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (d == today) {
                        Box(
                            Modifier
                                .size(7.dp)
                                .clip(androidx.compose.material3.MaterialShapes.SoftBurst.toShape())
                                .background(scheme.onPrimaryContainer),
                        )
                        Spacer(Modifier.height(3.dp))
                    }
                    Text(
                        d.takeLast(2),
                        color = if (d == today) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
                        fontWeight = if (d == today) FontWeight.Bold else FontWeight.Normal,
                        style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                    )
                    Spacer(Modifier.height(Space.s))
                    Box(
                        Modifier.height(28.dp).width(10.dp).clip(CircleShape).background(scheme.surfaceContainerHigh),
                        contentAlignment = Alignment.BottomCenter,
                    ) {
                        Box(
                            Modifier.fillMaxWidth().fillMaxHeight(fraction)
                                .clip(CircleShape)
                                .background(if (t.entryCount == 0 && t.shiftCount == 0) scheme.surfaceContainerHigh else if (up) scheme.primary else scheme.error),
                        )
                    }
                    Spacer(Modifier.height(Space.xs))
                    Text(
                        if (t.entryCount == 0 && t.shiftCount == 0) "–" else if (BakiPrivacy.discreet) "••" else (t.net / 100).toInt().toString(),
                        color = if (d == today) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
                        style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodyMedium,
                        maxLines = 1, softWrap = false, overflow = TextOverflow.Clip,
                    )
                }
            }
        }
        
    }
}

/** Net per day across the month, as bars above and below a zero line. */
@Composable
fun TrendBars(store: Store, month: String) {
    val scheme = MaterialTheme.colorScheme
    val days = remember(store, month) { monthDays(store, month) }
    if (days.isEmpty()) return
    val peak = days.maxOfOrNull { abs(it.second.net) }?.coerceAtLeast(1) ?: 1

    Column(Modifier.semantics { contentDescription = "Daily net trend for the month" }) {
        Text("TREND", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
        Spacer(Modifier.height(Space.s))
        Row(
            Modifier.fillMaxWidth().height(96.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            days.forEach { (iso, t) ->
                val fraction = (abs(t.net).toFloat() / peak).coerceIn(0.03f, 1f)
                Column(
                    Modifier.weight(1f).fillMaxHeight(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (t.net >= 0) {
                        Box(Modifier.fillMaxWidth().fillMaxHeight(fraction * 0.5f).clip(MaterialTheme.shapes.extraSmall).background(scheme.primary))
                        Spacer(Modifier.fillMaxHeight(0.02f))
                    } else {
                        Box(Modifier.fillMaxHeight(fraction * 0.5f).fillMaxWidth())
                        Box(Modifier.fillMaxWidth().fillMaxHeight(fraction * 0.5f).clip(MaterialTheme.shapes.extraSmall).background(scheme.error))
                    }
                }
            }
        }
        Spacer(Modifier.height(Space.xs))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(days.first().first.takeLast(5), color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            Text(days.last().first.takeLast(5), color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** Which hours actually pay: rows are weekdays, columns are 4-hour blocks. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HoursHeatmap(store: Store) {
    val scheme = MaterialTheme.colorScheme
    val blocks = remember(store) { hourHeatmap(store) }
    if (blocks.isEmpty()) return
    val peak = blocks.maxOf { it.rate }.coerceAtLeast(1.0)
    val days = listOf("M", "T", "W", "T", "F", "S", "S")

    Column(Modifier.semantics { contentDescription = "Best earning hours heatmap" }) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            Box(Modifier.size(14.dp).clip(androidx.compose.material3.MaterialShapes.SoftBurst.toShape()).background(scheme.primary))
            Text("BEST HOURS", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
        }
        Spacer(Modifier.height(Space.s))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            Spacer(Modifier.width(Space.l))
            listOf("00", "04", "08", "12", "16", "20").forEach {
                Text(it, color = scheme.onSurfaceVariant, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium )
            }
        }
        Spacer(Modifier.height(3.dp))
        for (d in 0..6) {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 1.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(days[d], color = scheme.onSurfaceVariant, modifier = Modifier.width(14.dp), style = MaterialTheme.typography.bodyMedium )
                for (b in 0..5) {
                    val cell = blocks.firstOrNull { it.day == d && it.block == b }
                    val rate = cell?.rate ?: 0.0
                    val has = (cell?.hours ?: 0.0) >= 0.25
                    val fraction = if (has) (rate / peak).coerceIn(0.08, 1.0).toFloat() else 0f
                    Box(
                        Modifier.weight(1f).height(16.dp)
                            .clip(if (has) MaterialTheme.shapes.small else MaterialTheme.shapes.extraSmall)
                            .background(if (has) scheme.primary.copy(alpha = 0.15f + fraction * 0.85f) else scheme.surfaceContainerHigh),
                    )
                }
            }
        }
        Spacer(Modifier.height(Space.s))
        bestHourBlock(store)?.let { best ->
            val start = "%02d:00".format(best.block * 4)
            val end = "%02d:00".format((best.block + 1) * 4)
            Text(
                "best: ${listOf("Mon","Tue","Wed","Thu","Fri","Sat","Sun")[best.day]} $start–$end · RM ${"%.0f".format(best.rate)}/hr over ${"%.1f".format(best.hours)}h",
                color = scheme.tertiary, style = MaterialTheme.typography.bodyMedium )
        }
        Text("darker = better rate · empty = not driven", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium )
    }
}

/** A week of net values as a line with a soft fill — the shape of the week at a glance. */
@Composable
fun Sparkline(
    values: List<Double>,
    modifier: Modifier = Modifier,
    line: Color,
    fill: Color,
) {
    val scheme = MaterialTheme.colorScheme
    val summary = remember(values) {
        if (values.size < 2) "Trend chart with insufficient data."
        else "Trend chart with ${values.size} points. Lowest ${"%.2f".format(values.min())}, " +
            "highest ${"%.2f".format(values.max())}, latest ${"%.2f".format(values.last())}."
    }
    Canvas(modifier.semantics { contentDescription = summary }) {
        if (values.size < 2) return@Canvas
        
        val min = values.min()
        val max = values.max()
        val span = (max - min).takeIf { kotlin.math.abs(it) > 0.001 } ?: 1.0
        val stepX = size.width / (values.size - 1)
        val top = size.height * 0.12f
        val usable = size.height * 0.76f
        val pts = values.mapIndexed { i, v ->
            Offset(i * stepX, size.height - top - ((v - min) / span).toFloat() * usable)
        }
        val stroke = Path().apply {
            moveTo(pts[0].x, pts[0].y)
            for (i in 1 until pts.size) {
                val prev = pts[i - 1]
                val cur = pts[i]
                val midX = (prev.x + cur.x) / 2f
                cubicTo(midX, prev.y, midX, cur.y, cur.x, cur.y)
            }
        }
        val area = Path().apply {
            addPath(stroke)
            lineTo(pts.last().x, size.height)
            lineTo(pts.first().x, size.height)
            close()
        }
        drawPath(area, Brush.verticalGradient(listOf(fill.copy(alpha = 0.30f), Color.Transparent)))
        drawPath(
            stroke, line,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
        drawCircle(line, radius = 2.5.dp.toPx(), center = pts.last())
    }
}

/** Compact progress ring, used by budget envelopes. */
@Composable
fun ProgressRing(progress: Float, color: Color, track: Color, size: Dp = 30.dp, stroke: Dp = 3.dp) {
    Canvas(
        Modifier.size(size).semantics {
            contentDescription = "${(progress.coerceIn(0f, 1f) * 100).toInt()} percent"
        },
    ) {
        val sw = stroke.toPx()
        val inset = sw / 2
        val arcSize = Size(this.size.width - sw, this.size.height - sw)
        drawArc(
            color = track, startAngle = -90f, sweepAngle = 360f, useCenter = false,
            topLeft = Offset(inset, inset), size = arcSize,
            style = Stroke(sw, cap = StrokeCap.Round),
        )
        drawArc(
            color = color, startAngle = -90f, sweepAngle = 360f * progress.coerceIn(0f, 1f), useCenter = false,
            topLeft = Offset(inset, inset), size = arcSize,
            style = Stroke(sw, cap = StrokeCap.Round),
        )
    }
}
