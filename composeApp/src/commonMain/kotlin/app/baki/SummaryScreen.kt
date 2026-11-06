package app.baki

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Month at a glance: totals, best/worst day, where the money went, funds. */
@Composable
fun SummaryScreen(store: Store, month: String, onMonth: (String) -> Unit) {
    val baki = LocalBaki.current
    // aggregations are memoised per store+month: recomposition no longer re-scans everything
    val t = remember(store, month) { monthTotals(store, month) }
    val days = remember(store, month) { monthDays(store, month) }
    val best = days.maxByOrNull { it.second.net }
    val worst = days.minByOrNull { it.second.net }
    val funds = remember(store, month) { fundStats(store, month) }
    val cats = remember(store, month) { expensesByCategory(store, month) }
    val maxCat = cats.maxOfOrNull { it.second } ?: 1L
    val avgDay = if (days.isEmpty()) 0L else t.expense / days.size

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(top = 18.dp, bottom = 132.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { onMonth(addDays("$month-01", -1).take(7)) }) {
                    Text("‹", color = baki.onSurface, fontSize = 22.sp)
                }
                Text(
                    monthLabel(month), color = baki.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center, modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { onMonth(addDays(endOfMonth(month), 1).take(7)) }) {
                    Text("›", color = baki.onSurface, fontSize = 22.sp)
                }
            }
        }

        item {
            Column {
                Text("this month", color = baki.muted, fontSize = 12.sp)
                Text("RM ${rm(t.net)}", color = baki.onSurface, style = BakiType.displaySmallNumeric)
                Text(
                    "${days.size} days · in ${rm(t.income)} · out ${rm(t.expense)} · avg ${rm(avgDay)}/day",
                    color = baki.muted, fontSize = 12.sp,
                )
                val streak = remember(store) { targetStreak(store, todayIso()) }
                if (streak >= 2) {
                    Text(
                        "$streak days in a row above target",
                        color = baki.up, style = BakiType.caption,
                    )
                }
            }
        }

        item {
            val thisWeek = weekTotals(store, weekStartOf(todayIso()))
            val lastWeek = weekTotals(store, addDays(weekStartOf(todayIso()), -7))
            val delta = thisWeek.net - lastWeek.net
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.card)).background(baki.container).padding(14.dp)) {
                Text("THIS WEEK", color = baki.muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("RM ${rm(thisWeek.net)}", color = baki.onSurface, fontSize = 22.sp, fontWeight = FontWeight.Black)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "${if (delta >= 0) "+" else "−"}RM ${rm(if (delta >= 0) delta else -delta)} vs last week",
                        color = if (delta >= 0) baki.up else baki.down, fontSize = 11.sp,
                    )
                }
                Text(
                    "${thisWeek.shiftCount} shifts · ${thisWeek.entryCount} entries · last week RM ${rm(lastWeek.net)}",
                    color = baki.muted, fontSize = 11.sp,
                )
                // is the week clearing the rate you set? measured on shift earnings, not on net
                // after spending — otherwise a fuel week would look like a slow week
                run {
                    val start = remember(store) { weekStartOf(todayIso()) }
                    val weekShifts = remember(store, start) { store.shifts.filter { it.date >= start && it.date <= todayIso() } }
                    val weekHours = weekShifts.sumOf { it.hours }
                    val cfg = store.settings.profit
                    val weekShiftNet = weekShifts.sumOf {
                        shiftMetrics(it, cfg, overheadPerHour(store, it.date.take(7))).net
                    }
                    val target = cfg.targetRate
                    if (weekHours > 0) {
                        val actual = weekShiftNet / weekHours
                        val hit = actual >= target
                        Text(
                            (if (hit) "✓ " else "✗ ") +
                                "RM ${"%.0f".format(actual)}/hr vs target RM ${"%.0f".format(target)}/hr",
                            color = if (hit) baki.up else baki.down, fontSize = 11.sp,
                        )
                        // the shape of the week, not just its total
                        val last7 = remember(store, todayIso()) {
                            (6 downTo 0).map { dayTotals(store, addDays(todayIso(), -it)).net / 100.0 }
                        }
                        Spacer(Modifier.height(6.dp))
                        Sparkline(
                            values = last7,
                            modifier = Modifier.fillMaxWidth().height(34.dp),
                            line = baki.primary,
                            fill = baki.primary,
                        )
                    }
                }
            }
        }

        payoutDue(store)?.let { due ->
            item {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.card)).background(baki.container).padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("AWAITING PAYOUT", color = baki.muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        Text("RM ${"%.2f".format(due)}", color = if (due > 0) baki.up else baki.muted, fontSize = 28.sp, fontWeight = FontWeight.Black)
                        statementCoveredUntil(store)?.let {
                            Text("earned after the statement covering up to $it", color = baki.muted, fontSize = 11.sp)
                        }
                    }
                    Icon(BakiIcons.Wallet, contentDescription = null, tint = baki.up, modifier = Modifier.size(22.dp))
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("best day", best?.let { "RM ${rm(it.second.net)}" } ?: "—", best?.first?.takeLast(5) ?: "", baki.up, Modifier.weight(1f))
                StatTile("worst day", worst?.let { "RM ${rm(it.second.net)}" } ?: "—", worst?.first?.takeLast(5) ?: "", baki.down, Modifier.weight(1f))
            }
        }

        item {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.card)).background(baki.container).padding(14.dp)) {
                Text("FUNDS", color = baki.muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                FundRow("Fuel", funds.fuel, baki)
                Spacer(Modifier.height(8.dp))
                FundRow("Wear & tear", funds.wear, baki)
                Spacer(Modifier.height(6.dp))
                Text("accrued from km · drained by real spending", color = baki.muted, fontSize = 11.sp)
            }
        }

        item {
            Text("WHERE IT WENT", color = baki.muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        items(cats) { (cat, cents) ->
            Column {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(cat, color = baki.onSurface, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    Text("RM ${rm(cents)}", color = baki.onSurface, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(4.dp))
                Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(Radius.pill)).background(baki.surfaceHigh)) {
                    Box(
                        Modifier.fillMaxWidth((cents.toFloat() / maxCat).coerceIn(0.02f, 1f))
                            .height(6.dp).clip(RoundedCornerShape(Radius.pill)).background(baki.primary),
                    )
                }
            }
        }
        item {
            val cost = remember(store, month) { monthCost(store, month) }
            val series = remember(store, month) { monthCostSeries(store, month, 4) }
            val peak = series.maxOfOrNull { it.perKm }?.coerceAtLeast(0.01) ?: 0.01
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.card)).background(baki.container).padding(14.dp)) {
                Text("COST PER KM", color = baki.muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("RM ${"%.2f".format(cost.perKm)}", color = baki.onSurface, fontSize = 28.sp, fontWeight = FontWeight.Black)
                    Spacer(Modifier.width(8.dp))
                    Text("${"%.0f".format(cost.km)} km", color = baki.muted, fontSize = 12.sp)
                }
                Text(
                    if (cost.km > 0.01)
                        "running RM ${"%.2f".format(cost.running / cost.km)}/km · upkeep RM ${"%.2f".format(cost.upkeep / cost.km)}/km"
                    else "no distance recorded this month",
                    color = baki.muted, fontSize = 11.sp,
                )
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    series.forEach { m ->
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                Modifier.fillMaxWidth().height(38.dp),
                                contentAlignment = Alignment.BottomCenter,
                            ) {
                                Box(
                                    Modifier.fillMaxWidth()
                                        .fillMaxHeight(if (m.km > 0.01) (m.perKm / peak).toFloat().coerceIn(0.06f, 1f) else 0.04f)
                                        .clip(RoundedCornerShape(Radius.tiny))
                                        .background(if (m.month == month) baki.primary else baki.primary.copy(alpha = 0.35f)),
                                )
                            }
                            Text(m.month.takeLast(2), color = baki.muted, fontSize = 11.sp)
                            Text(
                                if (m.km > 0.01) "%.2f".format(m.perKm) else "–",
                                color = if (m.month == month) baki.onSurface else baki.muted,
                                fontSize = 11.sp, fontWeight = if (m.month == month) FontWeight.Bold else FontWeight.Normal,
                            )
                        }
                    }
                }
            }
        }
        item { HoursHeatmap(store); Spacer(Modifier.height(12.dp)) }
        item { TrendBars(store, month); Spacer(Modifier.height(10.dp)) }
        item { CalendarHeat(store, month); Spacer(Modifier.height(4.dp)) }
        if (cats.isEmpty()) item {
            Text("No expenses recorded this month.", color = baki.muted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, sub: String, tint: Color, modifier: Modifier = Modifier) {
    val baki = LocalBaki.current
    Column(modifier.clip(RoundedCornerShape(Radius.card)).background(baki.container).padding(14.dp)) {
        Text(label, color = baki.muted, fontSize = 11.sp)
        Text(value, color = tint, fontSize = 22.sp, fontWeight = FontWeight.Black)
        if (sub.isNotEmpty()) Text(sub, color = baki.muted, fontSize = 11.sp)
    }
}

@Composable
private fun FundRow(label: String, fund: Fund, baki: BakiPalette) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = baki.onSurface, fontSize = 12.sp, modifier = Modifier.weight(1f))
        Text(
            "RM ${"%.2f".format(fund.balance)}",
            color = if (fund.balance >= 0) baki.up else baki.down,
            fontSize = 12.sp, fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.width(8.dp))
        Text("acc ${"%.2f".format(fund.accrued)} · spent ${"%.2f".format(fund.spent)}", color = baki.muted, fontSize = 11.sp)
    }
}
