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
    val cash = remember(store, month) { cashTruth(store, month) }
    val weekReview = remember(store) { latestWeekWithData(store, todayIso()) }
    val days = remember(store, month) { monthDays(store, month) }
    val best = days.maxByOrNull { it.second.net }
    val worst = days.minByOrNull { it.second.net }
    val funds = remember(store, month) { fundStats(store, month) }
    val cats = remember(store, month) { expensesByCategory(store, month) }
    val maxCat = cats.maxOfOrNull { it.second } ?: 1L
    val avgDay = if (days.isEmpty()) 0L else t.expense / days.size
    val dayLabel = if (days.size == 1) "1 day" else "${days.size} days"

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = Space.l),
        verticalArrangement = Arrangement.spacedBy(Space.m),
        contentPadding = PaddingValues(top = 18.dp, bottom = 132.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { onMonth(addDays("$month-01", -1).take(7)) }) {
                    Text("‹", color = baki.onSurface, style = BakiType.body )
                }
                Text(
                    monthLabel(month), color = baki.onSurface,
                    textAlign = TextAlign.Center, modifier = Modifier.weight(1f), style = BakiType.bodyStrong )
                TextButton(onClick = { onMonth(addDays(endOfMonth(month), 1).take(7)) }) {
                    Text("›", color = baki.onSurface, style = BakiType.body )
                }
            }
        }

        item {
            Column {
                Text("THIS MONTH", color = baki.muted, style = BakiType.section)
                EasedMoney(t.net, baki.onSurface, BakiType.displaySmallNumeric)
                Text(
                    "$dayLabel · in RM ${rm(t.income)} · out RM ${rm(t.expense)} · avg RM ${rm(avgDay)}/day",
                    color = baki.muted, style = BakiType.label )
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
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.card)).background(baki.container).padding(Space.l)) {
                Text("THIS WEEK", color = baki.muted, style = BakiType.captionStrong )
                Spacer(Modifier.height(Space.s))
                Row(verticalAlignment = Alignment.Bottom) {
                    EasedMoney(thisWeek.net, baki.onSurface, BakiType.title)
                    Spacer(Modifier.width(Space.s))
                    Text(
                        "${if (delta >= 0) "+" else "−"}RM ${rm(if (delta >= 0) delta else -delta)} vs last week",
                        color = if (delta >= 0) baki.up else baki.down, style = BakiType.caption )
                }
                Text(
                    "${thisWeek.shiftCount} shifts · ${thisWeek.entryCount} entries · last week RM ${rm(lastWeek.net)}",
                    color = baki.muted, style = BakiType.caption )
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
                            color = if (hit) baki.up else baki.down, style = BakiType.caption )
                        // the shape of the week, not just its total
                        val last7 = remember(store, todayIso()) {
                            (6 downTo 0).map { dayTotals(store, addDays(todayIso(), -it)).net / 100.0 }
                        }
                        Spacer(Modifier.height(Space.s))
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

        item { CashTruthCard(cash, month) }

        weekReview?.let { (start, review) ->
            item {
                WeeklyReviewCard(start, review, store.settings.profit.targetRate, shiftsBelowTarget(store, start, store.settings.profit.targetRate).size)
            }
        }

        item {
            if (days.size > 1) {
                Row(horizontalArrangement = Arrangement.spacedBy(Space.m)) {
                    StatTile("best day", best?.let { "RM ${rm(it.second.net)}" } ?: "—", best?.first?.let { prettyDate(it) } ?: "", baki.up, Modifier.weight(1f))
                    StatTile("worst day", worst?.let { "RM ${rm(it.second.net)}" } ?: "—", worst?.first?.let { prettyDate(it) } ?: "", baki.down, Modifier.weight(1f))
                }
            } else if (best != null) {
                StatTile("only day", "RM ${rm(best.second.net)}", prettyDate(best.first), baki.up, Modifier.fillMaxWidth())
            }
        }

        item {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.card)).background(baki.container).padding(Space.l)) {
                Text("FUNDS", color = baki.muted, style = BakiType.captionStrong )
                Spacer(Modifier.height(Space.s))
                FundRow("Fuel", funds.fuel, baki)
                Spacer(Modifier.height(Space.s))
                FundRow("Wear & tear", funds.wear, baki)
                Spacer(Modifier.height(Space.s))
                Text("accrued from km · drained by real spending", color = baki.muted, style = BakiType.caption )
            }
        }

        item {
            Text("WHERE IT WENT", color = baki.muted, style = BakiType.captionStrong )
        }
        items(cats) { (cat, cents) ->
            Column {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(cat, color = baki.onSurface, style = BakiType.label )
                    Text("RM ${rm(cents)}", color = baki.onSurface, style = BakiType.labelStrong )
                }
                Spacer(Modifier.height(Space.xs))
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
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.card)).background(baki.container).padding(Space.l)) {
                Text("COST PER KM", color = baki.muted, letterSpacing = 1.sp, style = BakiType.captionStrong )
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("RM ${"%.2f".format(cost.perKm)}", color = baki.onSurface, style = BakiType.headline )
                    Spacer(Modifier.width(Space.s))
                    Text("${"%.0f".format(cost.km)} km", color = baki.muted, style = BakiType.label )
                }
                Text(
                    if (cost.km > 0.01)
                        "running RM ${"%.2f".format(cost.running / cost.km)}/km · upkeep RM ${"%.2f".format(cost.upkeep / cost.km)}/km"
                    else "No distance recorded this month.",
                    color = baki.muted, style = BakiType.caption )
                Spacer(Modifier.height(Space.m))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.s)) {
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
                            Text(m.month.takeLast(2), color = baki.muted, style = BakiType.caption )
                            Text(
                                if (m.km > 0.01) "%.2f".format(m.perKm) else "–",
                                color = if (m.month == month) baki.onSurface else baki.muted, fontWeight = if (m.month == month) FontWeight.Bold else FontWeight.Normal, style = BakiType.caption )
                        }
                    }
                }
            }
        }
        item { HoursHeatmap(store); Spacer(Modifier.height(Space.m)) }
        // the week, in sentences: numbers are cold, a sentence is memorable
        latestWeekWithData(store, todayIso())?.let { (start, r) ->
            item {
                Column(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(Radius.card))
                        .background(baki.container)
                        .padding(Space.l),
                ) {
                    Text(
                        (if (start == weekStartOf(todayIso())) "THIS WEEK" else "LAST WEEK") +
                            " · ${prettyDate(start)} – ${prettyDate(addDays(start, 6))}",
                        color = baki.muted, style = BakiType.section,
                    )
                    Spacer(Modifier.height(Space.s))
                    Text(
                        "You drove ${"%.1f".format(r.hours)} h over ${r.days} day(s) and kept " +
                            "RM ${rm(r.net)} — RM ${"%.2f".format(r.perHour)}/h.",
                        color = baki.onSurface, style = BakiType.body,
                    )
                    if (r.fuelPerKm > 0.0) {
                        Text(
                            "Fuel took RM ${"%.2f".format(r.fuelPerKm)} of every km.",
                            color = baki.muted, style = BakiType.caption,
                        )
                    }
                    if (r.bestNet > 0) {
                        Text(
                            "Best day: ${prettyDate(r.bestDay)} at RM ${rm(r.bestNet)}.",
                            color = baki.up, style = BakiType.caption,
                        )
                    }
                }
                Spacer(Modifier.height(Space.m))
            }
        }

        item { TrendBars(store, month); Spacer(Modifier.height(Space.m)) }
        item { CalendarHeat(store, month); Spacer(Modifier.height(Space.xs)) }
        if (cats.isEmpty()) item {
            EmptyState("Nothing this month", "Expenses you log will appear here.")
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, sub: String, tint: Color, modifier: Modifier = Modifier) {
    val baki = LocalBaki.current
    Column(modifier.clip(RoundedCornerShape(Radius.card)).background(baki.container).padding(Space.l)) {
        Text(label, color = baki.muted, style = BakiType.caption )
        Text(value, color = tint, style = BakiType.title )
        if (sub.isNotEmpty()) Text(sub, color = baki.muted, style = BakiType.caption )
    }
}

@Composable
private fun FundRow(label: String, fund: Fund, baki: BakiPalette) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = baki.onSurface, modifier = Modifier.weight(1f), style = BakiType.label )
        Text(
            "RM ${"%.2f".format(fund.balance)}",
            color = if (fund.balance >= 0) baki.up else baki.down, style = BakiType.labelStrong )
        Spacer(Modifier.width(Space.s))
        Text("acc ${"%.2f".format(fund.accrued)} · spent ${"%.2f".format(fund.spent)}", color = baki.muted, style = BakiType.caption )
    }
}


/** The four-number reconciliation. Cash is a flow through Baki, not a bank balance. */
@Composable
private fun CashTruthCard(t: CashTruth, month: String) {
    val baki = LocalBaki.current
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.card)).background(baki.container).padding(Space.l),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("CASH TRUTH", color = baki.muted, letterSpacing = 1.sp, style = BakiType.captionStrong )
            Spacer(Modifier.weight(1f))
            Text("not a bank balance", color = baki.muted, style = BakiType.nano )
        }
        Spacer(Modifier.height(Space.s))
        CashLine("Recorded net", t.recordedNet, baki.onSurface, baki)
        CashLine("Net cash", t.cashNet, if (t.cashNet >= 0) baki.up else baki.down, baki)
        Text(
            "cash in RM ${rm(t.cashReceived)} · cash out RM ${rm(t.cashSpent)}",
            color = baki.muted, style = BakiType.captionNumeric,
        )
        if (t.unpaid > 0) {
            Spacer(Modifier.height(Space.xs))
            Text(
                "RM ${rm(t.unpaid)} still unpaid" +
                    (t.payoutAnchor?.let { " after the statement through $it" } ?: " — no statement anchor"),
                color = baki.warn, style = BakiType.caption,
            )
        }
        if (t.prepaidBills > 0) {
            Text(
                "RM ${rm(t.prepaidBills)} paid ahead for future bills",
                color = baki.muted, style = BakiType.caption,
            )
        }
        Spacer(Modifier.height(Space.s))
        Text(
            "Operating RM ${rm(t.operatingNet)} after estimated running costs. " +
                "Cash counts payouts only when the latest statement or a payout breakdown settles them.",
            color = baki.muted, style = BakiType.nano,
        )
    }
}

@Composable
private fun CashLine(label: String, cents: Long, color: Color, baki: BakiPalette) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        Text(label, color = baki.muted, style = BakiType.label)
        Spacer(Modifier.weight(1f))
        Text("RM ${rm(cents)}", color = color, style = BakiType.bodyStrong )
    }
}


@Composable
private fun WeeklyReviewCard(start: String, r: WeeklyReview, target: Double, below: Int) {
    val baki = LocalBaki.current
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.card)).background(baki.container).padding(Space.l),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("WEEKLY REVIEW", color = baki.muted, letterSpacing = 1.sp, style = BakiType.captionStrong )
            Spacer(Modifier.weight(1f))
            Text("MON–SUN", color = baki.muted, style = BakiType.nano )
        }
        Text(
            "${prettyDate(start)} – ${prettyDate(addDays(start, 6))}",
            color = baki.onSurface, style = BakiType.bodyStrong,
        )
        Text(
            "${r.days} ${if (r.days == 1) "day" else "days"} · ${"%.1f".format(r.hours)}h · RM ${rm(r.net)} net",
            color = baki.onSurface, style = BakiType.label,
        )
        Text(
            "RM ${"%.2f".format(r.perHour)}/hr · fuel RM ${"%.3f".format(r.fuelPerKm)}/km",
            color = baki.muted, style = BakiType.captionNumeric,
        )
        if (r.bestDay.isNotEmpty()) {
            Text("Best day: ${prettyDate(r.bestDay)} · RM ${rm(r.bestNet)}", color = baki.muted, style = BakiType.caption )
        }
        if (below > 0) {
            Text(
                "$below ${if (below == 1) "shift" else "shifts"} below RM ${"%.0f".format(target)}/hr",
                color = baki.warn, style = BakiType.caption,
            )
        } else {
            Text("Every shift cleared RM ${"%.0f".format(target)}/hr", color = baki.up, style = BakiType.caption )
        }
    }
}
