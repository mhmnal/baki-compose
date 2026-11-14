package app.baki

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlinx.coroutines.launch

/** Month at a glance: statement cover, month-in-ink, week strip, spend, calendar, reserves. */
@Composable
fun SummaryScreen(
    repo: BakiRepo,
    store: Store,
    month: String,
    onMonth: (String) -> Unit,
    onHeaderCollapsed: (Boolean) -> Unit = {},
) {
    val baki = LocalBaki.current
    val listState = rememberLazyListState()
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collect { (index, offset) -> onHeaderCollapsed(index > 0 || offset > 56) }
    }
    val t = remember(store, month) { monthTotals(store, month) }
    val cash = remember(store, month) { cashTruth(store, month) }
    val days = remember(store, month) { monthDays(store, month) }
    val best = days.maxByOrNull { it.second.net }
    val worst = days.minByOrNull { it.second.net }
    val funds = remember(store, month) { fundStats(store, month) }
    val cats = remember(store, month) { expensesByCategory(store, month) }
    val cost = remember(store, month) { monthCost(store, month) }
    val prevMonth = addDays("$month-01", -1).take(7)
    val prevNet = remember(store, prevMonth) { monthTotals(store, prevMonth).net }
    val dayLabel = if (days.size == 1) "1 active day" else "${days.size} active days"
    val streak = remember(store) { targetStreak(store, todayIso()) }
    var managingFund by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().padding(horizontal = Space.l),
        verticalArrangement = Arrangement.spacedBy(Space.s),
        contentPadding = PaddingValues(top = 0.dp, bottom = 132.dp),
    ) {
        item {
            Md3Card(
                container = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                StatementCover(
                    month = month, t = t, days = days, best = best, worst = worst,
                    cost = cost, prevNet = prevNet, streak = streak,
                    onMonth = onMonth,
                )
            }
        }
        item { Md3Card(container = MaterialTheme.colorScheme.surfaceContainerHigh) { MonthInkChart(store = store, month = month) } }
        item {
            Md3Card(
                container = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ) { WeekSection(store = store, today = todayIso()) }
        }
        if (cats.isNotEmpty()) item {
            Md3Card { WhereItWent(cats = cats, total = t.expense, store = store) }
        }
        item { Md3Card { CalendarHeat(store, month, compact = true) } }
        item {
            Md3Card(
                container = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            ) { FundsSection(repo = repo, funds = funds, onManage = { managingFund = it }) }
        }
        item { Md3Card(container = MaterialTheme.colorScheme.surfaceContainerHigh) { CashTruthCard(cash, month, compact = true) } }
        item { Md3Card { HoursHeatmap(store) } }
        if (cats.isEmpty()) item {
            EmptyState("Nothing this month", "Expenses you log will appear here.")
        }
    }
    managingFund?.let { fund ->
        ReserveSheet(repo = repo, fund = fund, onDismiss = { managingFund = null })
    }
}

@Composable
private fun Md3Card(
    container: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    val base = LocalBaki.current
    val local = base.copy(
        onSurface = contentColor,
        muted = contentColor.copy(alpha = 0.72f),
        primary = if (container == MaterialTheme.colorScheme.primaryContainer) contentColor else MaterialTheme.colorScheme.primary,
        surface = container,
        container = container,
        surfaceHigh = container,
    )
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = container, contentColor = contentColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        CompositionLocalProvider(LocalBaki provides local) {
            Column(Modifier.padding(Space.l), content = content)
        }
    }
}

@Composable
private fun StatementCover(
    month: String,
    t: Totals,
    days: List<Pair<String, Totals>>,
    best: Pair<String, Totals>?,
    worst: Pair<String, Totals>?,
    cost: MonthCost,
    prevNet: Long,
    streak: Int,
    onMonth: (String) -> Unit,
) {
    val baki = LocalBaki.current
    val delta = t.net - prevNet
    val dayLabel = if (days.size == 1) "1 active day" else "${days.size} active days"

    Column(Modifier.fillMaxWidth().padding(vertical = Space.s)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { onMonth(addDays("$month-01", -1).take(7)) }) {
                Text("‹", color = baki.onSurface, style = BakiType.body)
            }
            Text(
                monthLabel(month), color = baki.onSurface,
                textAlign = TextAlign.Center, modifier = Modifier.weight(1f), style = BakiType.bodyStrong,
            )
            TextButton(onClick = { onMonth(addDays(endOfMonth(month), 1).take(7)) }) {
                Text("›", color = baki.onSurface, style = BakiType.body)
            }
        }
        Text("Monthly statement", color = baki.muted, style = BakiType.section)
        Spacer(Modifier.height(Space.xs))
        EasedMoney(t.net, baki.onSurface, BakiType.displaySmallNumeric)
        Text(
            if (delta >= 0) "+RM ${rm(delta)} vs last month" else "−RM ${rm(-delta)} vs last month",
            color = if (delta >= 0) baki.onSurface else baki.down,
            style = BakiType.caption,
        )
        Text(
            "$dayLabel · in RM ${rm(t.income)} · out RM ${rm(t.expense)}",
            color = baki.muted, style = BakiType.label,
        )
        if (streak >= 2) {
            Text("$streak days in a row above target", color = baki.up, style = BakiType.caption)
        }
        Text(
            if (cost.km > 0.01) "running RM ${rm(cost.running / cost.km)}/km · upkeep RM ${rm(cost.upkeep / cost.km)}/km"
            else "No distance recorded this month.",
            color = baki.muted, style = BakiType.caption,
        )
        Spacer(Modifier.height(Space.xs))
        LedgerRule(alpha = 0.16f)
    }
}

private data class InkDay(val iso: String, val net: Long, val active: Boolean)

@Composable
private fun MonthInkChart(store: Store, month: String) {
    val scheme = MaterialTheme.colorScheme
    var range by remember(month) { mutableStateOf(7) }
    var selected by remember(month, range) { mutableStateOf<Int?>(null) }
    val allDays = remember(store, month) {
        monthGrid(month).filterNotNull().map { iso ->
            val t = dayTotals(store, iso)
            InkDay(iso, t.net, t.entryCount > 0 || t.shiftCount > 0)
        }
    }
    val today = todayIso()
    val visibleDays = if (month == today.take(7)) allDays.filter { it.iso <= today } else allDays
    val shown = if (range == 7) visibleDays.takeLast(7) else visibleDays
    val active = visibleDays.filter { it.active }
    val best = active.maxByOrNull { it.net }
    val worst = active.minByOrNull { it.net }
    val cfg = store.settings.profit
    val hours = remember(store, month) {
        store.shifts.filter { it.date.startsWith(month) }.sumOf { it.hours }
    }
    val targetPerDay = if (active.isNotEmpty()) cfg.targetRate * hours / active.size else 0.0
    val maxAbs = maxOf(1.0, shown.maxOfOrNull { abs(it.net) / 100.0 } ?: 1.0)
    val selectedDay = selected?.let { shown.getOrNull(it) }

    Column(Modifier.fillMaxWidth().padding(vertical = Space.s)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Month in ink", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                FilterChip(selected = range == 7, onClick = { range = 7 }, label = { Text("7D") })
                FilterChip(selected = range == 30, onClick = { range = 30 }, label = { Text("30D") })
            }
        }
        Spacer(Modifier.height(Space.xs))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            Surface(shape = RoundedCornerShape(50), color = scheme.tertiaryContainer) {
                Text(
                    "Target RM ${rm(targetPerDay)}/day",
                    color = scheme.onTertiaryContainer,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = Space.s, vertical = Space.xs),
                )
            }
            Text("Tap a bar for that day", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
        }
        Spacer(Modifier.height(Space.s))
        Canvas(
            Modifier.fillMaxWidth().height(142.dp)
                .pointerInput(shown) {
                    detectTapGestures { point ->
                        if (shown.isNotEmpty()) {
                            val index = (point.x / (size.width.toFloat() / shown.size))
                                .toInt().coerceIn(0, shown.lastIndex)
                            selected = if (selected == index) null else index
                        }
                    }
                }
                .semantics { contentDescription = "Daily net chart for $month. Tap a bar for details." },
        ) {
            val zeroY = size.height * 0.64f
            val usable = size.height * 0.50f
            val targetY = (zeroY - (targetPerDay / maxAbs * usable).toFloat())
                .coerceIn(4.dp.toPx(), size.height - 4.dp.toPx())
            // A single quiet baseline and one solid target line; no floating dashes.
            drawLine(scheme.outlineVariant, Offset(0f, zeroY), Offset(size.width, zeroY), 1.dp.toPx())
            if (targetPerDay > 0.01) {
                drawLine(scheme.tertiary.copy(alpha = 0.7f), Offset(0f, targetY), Offset(size.width, targetY), 1.5.dp.toPx())
            }
            val step = size.width / shown.size.coerceAtLeast(1)
            val barW = (step * 0.54f).coerceAtLeast(4.dp.toPx())
            shown.forEachIndexed { i, day ->
                val value = day.net / 100.0
                val h = (abs(value) / maxAbs * usable).toFloat()
                    .coerceAtLeast(if (day.active) 3.dp.toPx() else 1.dp.toPx())
                val left = i * step + (step - barW) / 2f
                val top = if (value >= 0) zeroY - h else zeroY
                val color = when {
                    !day.active -> scheme.outlineVariant.copy(alpha = 0.38f)
                    value < 0 -> scheme.error
                    else -> scheme.primary
                }
                drawRoundRect(
                    color = if (i == selected) scheme.onSurface else color,
                    topLeft = Offset(left, top),
                    size = Size(barW, h),
                    cornerRadius = CornerRadius(barW / 2f, barW / 2f),
                )
                if (i == selected) {
                    drawRoundRect(
                        color = scheme.onSurface,
                        topLeft = Offset(left, top),
                        size = Size(barW, h),
                        cornerRadius = CornerRadius(barW / 2f, barW / 2f),
                        style = Stroke(width = 1.5.dp.toPx()),
                    )
                }
            }
        }
        Spacer(Modifier.height(Space.s))
        selectedDay?.let { day ->
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = scheme.surfaceContainerHighest,
                modifier = Modifier.fillMaxWidth().padding(bottom = Space.s),
            ) {
                Row(Modifier.padding(horizontal = Space.m, vertical = Space.s), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(day.iso, color = scheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                        Text(
                            if (day.active) "Net RM ${rm(day.net)}" else "No entries",
                            color = if (day.net < 0) scheme.error else scheme.onSurface,
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    Text("tap again to close", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(shown.firstOrNull()?.iso?.takeLast(5) ?: "—", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
            Text("0", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
            Text(shown.lastOrNull()?.iso?.takeLast(5) ?: "—", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            best?.let {
                Text("+RM ${rm(it.net)} · ${it.iso.takeLast(5)}", color = scheme.tertiary, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
            }
            worst?.let { Text("${rm(it.net)} · ${it.iso.takeLast(5)}", color = scheme.error, style = MaterialTheme.typography.labelMedium) }
        }
    }
}
@Composable
private fun WeekSection(store: Store, today: String) {
    val scheme = MaterialTheme.colorScheme
    val start = remember(today) { weekStartOf(today) }
    val dates = remember(start) { (0..6).map { addDays(start, it) } }
    val values = remember(store, start) { dates.map { dayTotals(store, it) } }
    val thisWeek = remember(store, start) { weekTotals(store, start) }
    val lastWeek = remember(store, start) { weekTotals(store, addDays(start, -7)) }
    val hours = remember(store, start) { store.shifts.filter { it.date in dates }.sumOf { it.hours } }
    val actual = if (hours > 0) thisWeek.net / 100.0 / hours else 0.0
    val target = store.settings.profit.targetRate
    val peak = maxOf(1L, values.maxOfOrNull { abs(it.net) } ?: 1L)
    val best = values.maxByOrNull { it.net }

    Column(Modifier.fillMaxWidth().padding(vertical = Space.s)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("This week", color = scheme.onSecondaryContainer, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text("RM ${rm(thisWeek.net)}", color = scheme.onSecondaryContainer, style = MaterialTheme.typography.titleMedium)
        }
        Text(
            "${if (thisWeek.net - lastWeek.net >= 0) "+" else "−"}RM ${rm(abs(thisWeek.net - lastWeek.net))} vs last week · " +
                if (hours > 0) "RM ${rm(actual)}/hr vs target RM ${rm(target)}/hr" else "no hours yet",
            color = scheme.onSecondaryContainer.copy(alpha = 0.78f),
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(Space.m))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
            dates.forEachIndexed { i, iso ->
                val v = values[i].net
                val active = v != 0L || (iso == today && thisWeek.shiftCount > 0)
                val color = when {
                    v < 0 -> scheme.error
                    iso == today -> scheme.primary
                    values[i] == best -> scheme.tertiary
                    active -> scheme.primary.copy(alpha = 0.55f)
                    else -> scheme.onSecondaryContainer.copy(alpha = 0.18f)
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.fillMaxWidth().height(44.dp), contentAlignment = Alignment.BottomCenter) {
                        if (active) {
                            Box(
                                Modifier.fillMaxWidth(0.58f)
                                    .fillMaxHeight((abs(v).toFloat() / peak).coerceIn(0.05f, 1f))
                                    .clip(RoundedCornerShape(topStart = Radius.small, topEnd = Radius.small, bottomStart = Radius.tiny, bottomEnd = Radius.tiny))
                                    .background(color),
                            )
                        } else {
                            Box(
                                Modifier.size(4.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(scheme.onSecondaryContainer.copy(alpha = 0.20f)),
                            )
                        }
                    }
                    Spacer(Modifier.height(Space.xs))
                    Text(
                        weekdayShort(iso).take(1),
                        color = if (iso == today) scheme.primary else scheme.onSecondaryContainer.copy(alpha = 0.72f),
                        style = MaterialTheme.typography.labelSmall,
                    )
                    Text(
                        iso.takeLast(2),
                        color = if (iso == today) scheme.primary else scheme.onSecondaryContainer.copy(alpha = 0.55f),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
        Spacer(Modifier.height(Space.s))
        Text("${thisWeek.shiftCount} shifts · ${thisWeek.entryCount} entries", color = scheme.onSecondaryContainer.copy(alpha = 0.72f), style = MaterialTheme.typography.bodySmall)
    }
}
@Composable
private fun WhereItWent(cats: List<Pair<String, Long>>, total: Long, store: Store) {
    val baki = LocalBaki.current
    Column(Modifier.fillMaxWidth().padding(vertical = Space.s)) {
        LedgerRule(alpha = 0.16f)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Where it went", color = baki.muted, style = BakiType.section, modifier = Modifier.weight(1f))
            Text("Share", color = baki.muted, style = BakiType.nano)
        }
        Spacer(Modifier.height(Space.s))
        cats.forEachIndexed { index, (cat, cents) ->
            val share = if (total > 0) cents.toDouble() / total else 0.0
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(categoryIcon(cat), contentDescription = null, tint = baki.primary, modifier = Modifier.size(IconSize.inline))
                Spacer(Modifier.width(Space.s))
                Text(categoryLabel(store, cat), color = baki.onSurface, style = BakiType.label, modifier = Modifier.weight(1f))
                Text("RM ${rm(cents)}", color = baki.onSurface, style = BakiType.labelStrong)
                Spacer(Modifier.width(Space.s))
                Text("${(share * 100).toInt()}%", color = baki.muted, style = BakiType.captionNumeric, modifier = Modifier.width(34.dp), textAlign = TextAlign.End)
            }
            Spacer(Modifier.height(Space.xs))
            Box(Modifier.fillMaxWidth().height(3.dp).background(baki.surfaceHigh)) {
                Box(Modifier.fillMaxWidth(share.toFloat().coerceIn(0.01f, 1f)).fillMaxHeight().background(baki.primary))
            }
            if (index != cats.lastIndex) {
                Spacer(Modifier.height(Space.s))
                LedgerRule(alpha = 0.10f)
            }
        }
        Spacer(Modifier.height(Space.xs))
        LedgerRule(alpha = 0.16f)
    }
}

@Composable
private fun FundsSection(repo: BakiRepo, funds: Funds, onManage: (String) -> Unit) {
    val baki = LocalBaki.current
    Column(Modifier.fillMaxWidth().padding(vertical = Space.s)) {
        LedgerRule(alpha = 0.16f)
        Text("FUNDS · ROLLING", color = baki.muted, style = BakiType.section)
        Spacer(Modifier.height(Space.s))
        FundRow("Fuel", "fuel", funds.fuel, repo.store.settings.reserveTargets["fuel"] ?: 0.0, baki) { onManage("fuel") }
        Spacer(Modifier.height(Space.s))
        FundRow("Wear & tear", "wear", funds.wear, repo.store.settings.reserveTargets["wear"] ?: 0.0, baki) { onManage("wear") }
        Spacer(Modifier.height(Space.xs))
        Text("rolling reserve · top-ups are virtual allocations from cash", color = baki.muted, style = BakiType.caption)
        Spacer(Modifier.height(Space.xs))
        LedgerRule(alpha = 0.16f)
    }
}

@Composable
private fun FundRow(
    label: String,
    fundKey: String,
    fund: Fund,
    configuredTarget: Double,
    baki: BakiPalette,
    onManage: () -> Unit,
) {
    val target = configuredTarget.takeIf { it > 0.0 } ?: maxOf(fund.monthSpent, fund.monthAccrued, 1.0)
    val progress = (fund.balance.coerceAtLeast(0.0) / target).coerceIn(0.0, 1.0)
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = baki.onSurface, modifier = Modifier.weight(1f), style = BakiType.label)
            Text("RM ${rm(fund.balance)}", color = if (fund.balance >= 0) baki.up else baki.down, style = BakiType.labelStrong)
            Spacer(Modifier.width(Space.xs))
            TextButton(onClick = onManage, contentPadding = PaddingValues(horizontal = Space.xs)) {
                Text("Manage", color = baki.primary, style = BakiType.captionStrong)
            }
        }
        Spacer(Modifier.height(Space.xs))
        Box(Modifier.fillMaxWidth().height(4.dp).background(baki.surfaceHigh)) {
            Box(
                Modifier.fillMaxWidth(progress.toFloat()).fillMaxHeight()
                    .background(if (fund.balance >= 0) baki.up else baki.down),
            )
        }
        Spacer(Modifier.height(Space.xs))
        Text(
            "target RM ${rm(target)}" +
                (if (configuredTarget > 0.0) "" else " · one-month default"),
            color = baki.muted, style = BakiType.nano,
        )
        Text(
            "this month +RM ${rm(fund.monthAccrued)} · −RM ${rm(fund.monthSpent)}" +
                if (fund.monthNet < 0) " · shortfall RM ${rm(-fund.monthNet)}" else "",
            color = if (fund.monthNet < 0) baki.down else baki.muted, style = BakiType.caption,
        )
    }
}

@Composable
private fun ReserveSheet(repo: BakiRepo, fund: String, onDismiss: () -> Unit) {
    val baki = LocalBaki.current
    val scope = rememberCoroutineScope()
    val canWrite = !repo.readOnly
    val state = if (fund == "fuel") repo.store.settings.reserveTargets["fuel"] ?: 0.0 else repo.store.settings.reserveTargets["wear"] ?: 0.0
    var target by remember(fund) { mutableStateOf(if (state > 0) "%.2f".format(state) else "") }
    var amount by remember(fund) { mutableStateOf("") }
    var status by remember(fund) { mutableStateOf("") }
    val fundName = if (fund == "fuel") "Fuel" else "Wear & tear"
    val txns = remember(repo.store.reserveTxns, fund) {
        repo.store.reserveTxns.filter { it.fund == fund }.sortedByDescending { it.updated }.take(6)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(Radius.dialog),
        title = { Text("$fundName reserve", color = baki.onSurface, style = BakiType.title) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Space.s),
            ) {
                Text("A virtual reserve. No bank transfer happens.", color = baki.muted, style = BakiType.caption)
                if (!canWrite) Text("Read-only · enable writes in More → Allow writes", color = baki.warn, style = BakiType.caption)
                LedgerRule(alpha = 0.14f)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Target RM", color = baki.onSurface, modifier = Modifier.weight(1f), style = BakiType.label)
                    OutlinedTextField(
                        value = target,
                        onValueChange = { target = it.filter { c -> c.isDigit() || c == '.' } },
                        placeholder = { Text("auto", color = baki.muted) }, singleLine = true,
                        modifier = Modifier.width(120.dp), colors = bakiFieldColorsPublic(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                }
                Text("Leave blank for the one-month default.", color = baki.muted, style = BakiType.nano)
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Amount RM") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(), colors = bakiFieldColorsPublic(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                    Button(
                        enabled = canWrite && (amount.toDoubleOrNull() ?: 0.0) > 0.0,
                        onClick = {
                            val cents = ((amount.toDoubleOrNull() ?: 0.0) * 100).toLong()
                            scope.launch {
                                val ok = repo.addReserveTxn(fund, cents)
                                status = if (ok) "Added RM ${rm(cents)} to reserve" else "Could not add"
                                amount = ""
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = baki.primary, contentColor = baki.onPrimary),
                        shape = RoundedCornerShape(Radius.pill),
                        modifier = Modifier.weight(1f),
                    ) { Text("Top up") }
                    TextButton(
                        enabled = canWrite && (amount.toDoubleOrNull() ?: 0.0) > 0.0,
                        onClick = {
                            val cents = ((amount.toDoubleOrNull() ?: 0.0) * 100).toLong()
                            scope.launch {
                                val ok = repo.addReserveTxn(fund, -cents)
                                status = if (ok) "Withdrew RM ${rm(cents)} from reserve" else "Could not withdraw"
                                amount = ""
                            }
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text("Withdraw", color = if (canWrite) baki.down else baki.muted) }
                }
                TextButton(
                    enabled = canWrite,
                    onClick = {
                        val value = target.toDoubleOrNull()?.takeIf { it > 0.0 } ?: 0.0
                        scope.launch { repo.setReserveTarget(fund, value); status = if (value > 0) "Target saved" else "Using one-month default" }
                    },
                ) { Text("Save target", color = if (canWrite) baki.primary else baki.muted, style = BakiType.captionStrong) }
                if (status.isNotEmpty()) Text(status, color = baki.primary, style = BakiType.caption)
                if (txns.isNotEmpty()) {
                    Text("Recent movements", color = baki.muted, style = BakiType.section)
                    LedgerRule(alpha = 0.12f)
                    txns.forEach { tx ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(tx.date, color = baki.muted, style = BakiType.caption)
                            Text(
                                (if (tx.amount > 0) "+" else "−") + "RM ${rm(kotlin.math.abs(tx.amount))}",
                                color = if (tx.amount > 0) baki.up else baki.down, style = BakiType.captionNumeric,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done", color = baki.primary, style = BakiType.labelStrong) } },
    )
}

/** The four-number reconciliation. Cash is a flow through Baki, not a bank balance. */
@Composable
private fun CashTruthCard(t: CashTruth, month: String, compact: Boolean = false) {
    val baki = LocalBaki.current
    Column(Modifier.fillMaxWidth().padding(vertical = Space.s)) {
        LedgerRule(alpha = 0.12f)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Cash truth", color = baki.muted, style = BakiType.section)
            Spacer(Modifier.weight(1f))
            Text("not a bank balance", color = baki.muted, style = BakiType.nano)
        }
        Spacer(Modifier.height(Space.xs))
        CashLine("Recorded net", t.recordedNet, baki.onSurface, baki)
        CashLine("Net cash", t.cashNet, if (t.cashNet >= 0) baki.up else baki.down, baki)
        Text("cash in RM ${rm(t.cashReceived)} · cash out RM ${rm(t.cashSpent)}", color = baki.muted, style = BakiType.captionNumeric)
        if (t.unpaid > 0) {
            Text(
                "RM ${rm(t.unpaid)} still unpaid" + (t.payoutAnchor?.let { " after $it" } ?: ""),
                color = baki.warn, style = BakiType.caption,
            )
        }
        if (!compact) {
            if (t.prepaidBills > 0) Text("RM ${rm(t.prepaidBills)} paid ahead for bills", color = baki.muted, style = BakiType.caption)
            Text(
                "Operating RM ${rm(t.operatingNet)} after estimated running costs.",
                color = baki.muted, style = BakiType.nano,
            )
        }
        Spacer(Modifier.height(Space.xs))
        LedgerRule(alpha = 0.12f)
    }
}

@Composable
private fun CashLine(label: String, cents: Long, color: Color, baki: BakiPalette) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        Text(label, color = baki.muted, style = BakiType.label)
        Spacer(Modifier.weight(1f))
        Text("RM ${rm(cents)}", color = color, style = BakiType.bodyStrong)
    }
}

@Composable
private fun WeeklyReviewCard(start: String, r: WeeklyReview, target: Double, below: Int) {
    val baki = LocalBaki.current
    Column(Modifier.fillMaxWidth().padding(vertical = Space.s)) {
        LedgerRule(alpha = 0.12f)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("WEEKLY REVIEW", color = baki.muted, style = BakiType.section)
            Spacer(Modifier.weight(1f))
            Text("MON–SUN", color = baki.muted, style = BakiType.nano)
        }
        Text("${prettyDate(start)} – ${prettyDate(addDays(start, 6))}", color = baki.onSurface, style = BakiType.bodyStrong)
        Text("${r.days} days · ${"%.1f".format(r.hours)}h · RM ${rm(r.net)} net", color = baki.onSurface, style = BakiType.label)
        Text("RM ${rm(r.perHour)}/hr · fuel RM ${rm(r.fuelPerKm)}/km", color = baki.muted, style = BakiType.captionNumeric)
        if (r.bestDay.isNotEmpty()) Text("Best day: ${prettyDate(r.bestDay)} · RM ${rm(r.bestNet)}", color = baki.muted, style = BakiType.caption)
        Text(
            if (below > 0) "$below shifts below RM ${rm(target)}/hr" else "Every shift cleared RM ${rm(target)}/hr",
            color = if (below > 0) baki.warn else baki.up, style = BakiType.caption,
        )
        Spacer(Modifier.height(Space.xs))
        LedgerRule(alpha = 0.12f)
    }
}
