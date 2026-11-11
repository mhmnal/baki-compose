package app.baki

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
@Composable
fun WeekStrip(store: Store, endDay: String) {
    val baki = LocalBaki.current
    val days = (6 downTo 0).map { addDays(endDay, -it) }
    val totals = remember(store, endDay) { days.map { dayTotals(store, it) } }
    val peak = totals.maxOfOrNull { abs(it.net) }?.coerceAtLeast(1) ?: 1
    val today = todayIso()

    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            days.forEachIndexed { i, d ->
                val t = totals[i]
                val up = t.net >= 0
                val fraction = (abs(t.net).toFloat() / peak).coerceIn(0.04f, 1f)
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(Radius.input)).background(baki.container).padding(vertical = Space.s),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        d.takeLast(2),
                        color = if (d == today) baki.primary else baki.muted,
                        fontWeight = if (d == today) FontWeight.Bold else FontWeight.Normal, style = BakiType.caption )
                    Spacer(Modifier.height(Space.s))
                    Box(
                        Modifier.height(28.dp).width(10.dp).clip(RoundedCornerShape(Radius.pill)).background(baki.surfaceHigh),
                        contentAlignment = Alignment.BottomCenter,
                    ) {
                        Box(
                            Modifier.fillMaxWidth().fillMaxHeight(fraction)
                                .clip(RoundedCornerShape(Radius.pill))
                                .background(if (t.entryCount == 0 && t.shiftCount == 0) baki.surfaceHigh else if (up) baki.primary else baki.down),
                        )
                    }
                    Spacer(Modifier.height(Space.xs))
                    Text(
                        if (t.entryCount == 0 && t.shiftCount == 0) "–" else if (BakiPrivacy.discreet) "••" else (t.net / 100).toInt().toString(),
                        color = baki.muted, style = BakiType.caption )
                }
            }
        }
    }
}

/** Net per day across the month, as bars above and below a zero line. */
@Composable
fun TrendBars(store: Store, month: String) {
    val baki = LocalBaki.current
    val days = remember(store, month) { monthDays(store, month) }
    if (days.isEmpty()) return
    val peak = days.maxOfOrNull { abs(it.second.net) }?.coerceAtLeast(1) ?: 1

    Column {
        Text("TREND", color = baki.muted, style = BakiType.captionStrong )
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
                        Box(Modifier.fillMaxWidth().fillMaxHeight(fraction * 0.5f).clip(RoundedCornerShape(Radius.tiny)).background(baki.primary))
                        Spacer(Modifier.fillMaxHeight(0.02f))
                    } else {
                        Box(Modifier.fillMaxHeight(fraction * 0.5f).fillMaxWidth())
                        Box(Modifier.fillMaxWidth().fillMaxHeight(fraction * 0.5f).clip(RoundedCornerShape(Radius.tiny)).background(baki.down))
                    }
                }
            }
        }
        Spacer(Modifier.height(Space.xs))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(days.first().first.takeLast(5), color = baki.muted, style = BakiType.caption )
            Text(days.last().first.takeLast(5), color = baki.muted, style = BakiType.caption )
        }
    }
}

/** Which hours actually pay: rows are weekdays, columns are 4-hour blocks. */
@Composable
fun HoursHeatmap(store: Store) {
    val baki = LocalBaki.current
    val blocks = remember(store) { hourHeatmap(store) }
    if (blocks.isEmpty()) return
    val peak = blocks.maxOf { it.rate }.coerceAtLeast(1.0)
    val days = listOf("M", "T", "W", "T", "F", "S", "S")

    Column {
        Text("BEST HOURS", color = baki.muted, style = BakiType.captionStrong )
        Spacer(Modifier.height(Space.s))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            Spacer(Modifier.width(Space.l))
            listOf("00", "04", "08", "12", "16", "20").forEach {
                Text(it, color = baki.muted, modifier = Modifier.weight(1f), style = BakiType.caption )
            }
        }
        Spacer(Modifier.height(3.dp))
        for (d in 0..6) {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 1.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(days[d], color = baki.muted, modifier = Modifier.width(14.dp), style = BakiType.caption )
                for (b in 0..5) {
                    val cell = blocks.firstOrNull { it.day == d && it.block == b }
                    val rate = cell?.rate ?: 0.0
                    val has = (cell?.hours ?: 0.0) >= 0.25
                    val fraction = if (has) (rate / peak).coerceIn(0.08, 1.0).toFloat() else 0f
                    Box(
                        Modifier.weight(1f).height(16.dp).clip(RoundedCornerShape(Radius.tiny))
                            .background(if (has) baki.primary.copy(alpha = 0.15f + fraction * 0.85f) else baki.surfaceHigh),
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
                color = baki.up, style = BakiType.caption )
        }
        Text("darker = better rate · empty = not driven", color = baki.muted, style = BakiType.caption )
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
    Canvas(modifier) {
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
                // gentle curve between points
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
        // the latest point gets a dot, so the eye lands on "now"
        drawCircle(line, radius = 2.5.dp.toPx(), center = pts.last())
    }
}

/** Compact progress ring, used by budget envelopes. */
@Composable
fun ProgressRing(progress: Float, color: Color, track: Color, size: Dp = 30.dp, stroke: Dp = 3.dp) {
    Canvas(Modifier.size(size)) {
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
