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
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            days.forEachIndexed { i, d ->
                val t = totals[i]
                val up = t.net >= 0
                val fraction = (abs(t.net).toFloat() / peak).coerceIn(0.04f, 1f)
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(baki.container).padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        d.takeLast(2),
                        color = if (d == today) baki.primary else baki.muted,
                        fontSize = 10.sp,
                        fontWeight = if (d == today) FontWeight.Bold else FontWeight.Normal,
                    )
                    Spacer(Modifier.height(6.dp))
                    Box(
                        Modifier.height(28.dp).width(10.dp).clip(RoundedCornerShape(50)).background(baki.surfaceHigh),
                        contentAlignment = Alignment.BottomCenter,
                    ) {
                        Box(
                            Modifier.fillMaxWidth().fillMaxHeight(fraction)
                                .clip(RoundedCornerShape(50))
                                .background(if (t.entryCount == 0 && t.shiftCount == 0) baki.surfaceHigh else if (up) baki.primary else baki.down),
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (t.entryCount == 0 && t.shiftCount == 0) "–" else (t.net / 100).toInt().toString(),
                        color = baki.muted, fontSize = 9.sp,
                    )
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
        Text("TREND", color = baki.muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
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
                        Box(Modifier.fillMaxWidth().fillMaxHeight(fraction * 0.5f).clip(RoundedCornerShape(3.dp)).background(baki.primary))
                        Spacer(Modifier.fillMaxHeight(0.02f))
                    } else {
                        Box(Modifier.fillMaxHeight(fraction * 0.5f).fillMaxWidth())
                        Box(Modifier.fillMaxWidth().fillMaxHeight(fraction * 0.5f).clip(RoundedCornerShape(3.dp)).background(baki.down))
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(days.first().first.takeLast(5), color = baki.muted, fontSize = 9.sp)
            Text(days.last().first.takeLast(5), color = baki.muted, fontSize = 9.sp)
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
        Text("BEST HOURS", color = baki.muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            Spacer(Modifier.width(14.dp))
            listOf("00", "04", "08", "12", "16", "20").forEach {
                Text(it, color = baki.muted, fontSize = 8.sp, modifier = Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(3.dp))
        for (d in 0..6) {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 1.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(days[d], color = baki.muted, fontSize = 9.sp, modifier = Modifier.width(14.dp))
                for (b in 0..5) {
                    val cell = blocks.firstOrNull { it.day == d && it.block == b }
                    val rate = cell?.rate ?: 0.0
                    val has = (cell?.hours ?: 0.0) >= 0.25
                    val fraction = if (has) (rate / peak).coerceIn(0.08, 1.0).toFloat() else 0f
                    Box(
                        Modifier.weight(1f).height(16.dp).clip(RoundedCornerShape(4.dp))
                            .background(if (has) baki.primary.copy(alpha = 0.15f + fraction * 0.85f) else baki.surfaceHigh),
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        bestHourBlock(store)?.let { best ->
            val start = "%02d:00".format(best.block * 4)
            val end = "%02d:00".format((best.block + 1) * 4)
            Text(
                "best: ${listOf("Mon","Tue","Wed","Thu","Fri","Sat","Sun")[best.day]} $start–$end · RM ${"%.0f".format(best.rate)}/hr over ${"%.1f".format(best.hours)}h",
                color = baki.up, fontSize = 10.sp,
            )
        }
        Text("darker = better rate · empty = not driven", color = baki.muted, fontSize = 9.sp)
    }
}
