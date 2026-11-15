package app.baki

import androidx.compose.foundation.shape.CircleShape

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
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.stateDescription
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
    val scheme = MaterialTheme.colorScheme
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
        item { Md3Card(container = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.extraLarge, padding = Space.ml) { MonthInkChart(store = store, month = month) } }
        item {
            Md3Card(
                container = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = MaterialTheme.shapes.extraLarge,
                padding = Space.ml,
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
    shape: androidx.compose.ui.graphics.Shape = MaterialTheme.shapes.large,
    padding: androidx.compose.ui.unit.Dp = Space.l,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    val cardScheme = md3CardScheme(
        base = MaterialTheme.colorScheme,
        container = container,
        contentColor = contentColor,
    )
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = container, contentColor = contentColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        MaterialTheme(
            colorScheme = cardScheme,
            typography = MaterialTheme.typography,
            shapes = MaterialTheme.shapes,
        ) {
            Column(Modifier.padding(padding), content = content)
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
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
    val scheme = MaterialTheme.colorScheme
    val delta = t.net - prevNet
    val dayLabel = if (days.size == 1) "1 active day" else "${days.size} active days"

    Column(Modifier.fillMaxWidth().padding(vertical = Space.s)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { onMonth(addDays("$month-01", -1).take(7)) }) {
                Text("‹", color = scheme.onSurface, style = MaterialTheme.typography.bodyLarge)
            }
            Text(
                monthLabel(month), color = scheme.onSurface,
                textAlign = TextAlign.Center, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
            )
            TextButton(onClick = { onMonth(addDays(endOfMonth(month), 1).take(7)) }) {
                Text("›", color = scheme.onSurface, style = MaterialTheme.typography.bodyLarge)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Monthly statement",
                color = scheme.onSurfaceVariant,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            if (streak > 1) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(MaterialShapes.Cookie9Sided.toShape())
                        .background(scheme.tertiaryContainer)
                        .semantics { contentDescription = "$streak-day target streak" },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "${streak}d",
                        color = scheme.onTertiaryContainer,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }
        Spacer(Modifier.height(Space.xs))
        EasedMoney(t.net, scheme.onSurface, MaterialTheme.typography.displaySmallEmphasized.copy(fontFeatureSettings = "tnum"))
        Text(
            if (delta >= 0) "+RM ${rm(delta)} vs last month" else "−RM ${rm(-delta)} vs last month",
            color = if (delta >= 0) scheme.onSurface else scheme.error,
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            "$dayLabel · in RM ${rm(t.income)} · out RM ${rm(t.expense)}",
            color = scheme.onSurfaceVariant, style = MaterialTheme.typography.labelLarge,
        )
        if (streak >= 2) {
            Text("$streak days in a row above target", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        }
        Text(
            if (cost.km > 0.01) "running RM ${rm(cost.running / cost.km)}/km · upkeep RM ${rm(cost.upkeep / cost.km)}/km"
            else "No distance recorded this month.",
            color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(Space.xs))
    }
}

private data class InkDay(val iso: String, val net: Long, val active: Boolean)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
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
    val anyActive = shown.any { it.active }
    val chartSummary = remember(month, range, shown, targetPerDay, best, worst) {
        buildString {
            append("Daily net chart for ${monthLabel(month)}, ${range} day view. ")
            if (targetPerDay > 0.01) append("Target RM ${rm(targetPerDay)} per day. ")
            append("Data points: ")
            append(shown.joinToString("; ") { day ->
                "${day.iso.takeLast(5)} ${if (day.active) "net RM ${rm(day.net)}" else "no entries"}"
            })
            best?.let { append(". Best ${it.iso.takeLast(5)} RM ${rm(it.net)}") }
            worst?.let { append(". Lowest ${it.iso.takeLast(5)} RM ${rm(it.net)}") }
            append(". Activate to select the next day.")
        }
    }

    Column(Modifier.fillMaxWidth().padding(vertical = Space.s)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Month in ink", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.titleSmallEmphasized, modifier = Modifier.weight(1f))
            ConnectedChoiceGroup(
                options = listOf("7D", "30D"),
                selected = if (range == 7) "7D" else "30D",
                onSelect = { range = if (it == "7D") 7 else 30 },
                modifier = Modifier.width(144.dp),
            )
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
        Box(Modifier.fillMaxWidth()) {
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
                .semantics {
                    contentDescription = chartSummary
                    selectedDay?.let {
                        stateDescription = "${it.iso.takeLast(5)}: " +
                            if (it.active) "net RM ${rm(it.net)}" else "no entries"
                    }
                    onClick(label = "Select next day") {
                        selected = if (shown.isEmpty()) null else ((selected ?: -1) + 1) % shown.size
                        true
                    }
                },
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
                    .coerceAtLeast(if (day.active) 3.dp.toPx() else 0f)
                val left = i * step + (step - barW) / 2f
                val top = if (value >= 0) zeroY - h else zeroY
                // No permanent track: the card surface stays transparent behind the bars.
                // Only the chosen day gets a soft backdrop, so selection is unmistakable.
                if (i == selected) {
                    val backdropInset = step * 0.10f
                    drawRoundRect(
                        color = scheme.primaryContainer.copy(alpha = 0.34f),
                        topLeft = Offset(left - backdropInset, 4.dp.toPx()),
                        size = Size(barW + backdropInset * 2, size.height - 8.dp.toPx()),
                        cornerRadius = CornerRadius(barW / 2f, barW / 2f),
                    )
                }
                if (day.active) {
                    val color = if (value < 0) scheme.error else scheme.primary
                    if (h < 8.dp.toPx()) {
                        // A low value becomes a dot, not a horizontal pill that reads as a dash.
                        drawCircle(
                            color = color,
                            radius = 3.dp.toPx(),
                            center = Offset(left + barW / 2f, if (value >= 0) zeroY - 3.dp.toPx() else zeroY + 3.dp.toPx()),
                        )
                    } else {
                        drawRoundRect(
                            color = color,
                            topLeft = Offset(left, top),
                            size = Size(barW, h),
                            cornerRadius = CornerRadius(barW / 2f, barW / 2f),
                        )
                    }
                }
            }
        }
        if (!anyActive) {
            Text(
                "No activity in this range",
                color = scheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.align(Alignment.Center),
            )
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
            Text("This week", color = scheme.onSurface, style = MaterialTheme.typography.titleSmallEmphasized, modifier = Modifier.weight(1f))
            Text("RM ${rm(thisWeek.net)}", color = scheme.onSurface, style = MaterialTheme.typography.titleMedium)
        }
        Text(
            "${if (thisWeek.net - lastWeek.net >= 0) "+" else "−"}RM ${rm(abs(thisWeek.net - lastWeek.net))} vs last week · " +
                if (hours > 0) "RM ${rm(actual)}/hr vs target RM ${rm(target)}/hr" else "no hours yet",
            color = scheme.onSurfaceVariant,
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
                    Box(Modifier.fillMaxWidth().height(56.dp), contentAlignment = Alignment.BottomCenter) {
                        Box(
                            Modifier.width(12.dp).fillMaxHeight()
                                .clip(RoundedCornerShape(50))
                                .background(scheme.surfaceContainerHighest),
                        ) {
                            if (active) {
                                Box(
                                    Modifier.align(Alignment.BottomCenter)
                                        .fillMaxWidth()
                                        .fillMaxHeight((abs(v).toFloat() / peak).coerceIn(0.08f, 1f))
                                        .clip(RoundedCornerShape(50))
                                        .background(color),
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(Space.xs))
                    Text(
                        weekdayShort(iso).take(1),
                        color = if (iso == today) scheme.primary else scheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall,
                    )
                    Text(
                        iso.takeLast(2),
                        color = if (iso == today) scheme.primary else scheme.onSurfaceVariant.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
        Spacer(Modifier.height(Space.s))
        Text("${thisWeek.shiftCount} shifts · ${thisWeek.entryCount} entries", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
    }
}
@Composable
private fun WhereItWent(cats: List<Pair<String, Long>>, total: Long, store: Store) {
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().padding(vertical = Space.s)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Where it went", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text("Share", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
        }
        Spacer(Modifier.height(Space.s))
        cats.forEachIndexed { index, (cat, cents) ->
            val share = if (total > 0) cents.toDouble() / total else 0.0
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(categoryIcon(cat), contentDescription = null, tint = scheme.primary, modifier = Modifier.size(IconSize.inline))
                Spacer(Modifier.width(Space.s))
                Text(categoryLabel(store, cat), color = scheme.onSurface, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                Text("RM ${rm(cents)}", color = scheme.onSurface, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                Spacer(Modifier.width(Space.s))
                Text("${(share * 100).toInt()}%", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"), modifier = Modifier.width(34.dp), textAlign = TextAlign.End)
            }
            Spacer(Modifier.height(Space.xs))
            Box(Modifier.fillMaxWidth().height(3.dp).background(scheme.surfaceContainerHigh)) {
                Box(Modifier.fillMaxWidth(share.toFloat().coerceIn(0.01f, 1f)).fillMaxHeight().background(scheme.primary))
            }
            if (index != cats.lastIndex) {
                Spacer(Modifier.height(Space.s))
            }
        }
        Spacer(Modifier.height(Space.xs))
    }
}

@Composable
private fun FundsSection(repo: BakiRepo, funds: Funds, onManage: (String) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().padding(vertical = Space.s)) {
        Text("FUNDS · ROLLING", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(Space.s))
        FundRow("Fuel", "fuel", funds.fuel, repo.store.settings.reserveTargets["fuel"] ?: 0.0, scheme) { onManage("fuel") }
        Spacer(Modifier.height(Space.s))
        FundRow("Wear & tear", "wear", funds.wear, repo.store.settings.reserveTargets["wear"] ?: 0.0, scheme) { onManage("wear") }
        Spacer(Modifier.height(Space.xs))
        Text("rolling reserve · top-ups are virtual allocations from cash", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(Space.xs))
    }
}

@Composable
private fun FundRow(
    label: String,
    fundKey: String,
    fund: Fund,
    configuredTarget: Double,
    scheme: androidx.compose.material3.ColorScheme,
    onManage: () -> Unit,
) {
    val target = configuredTarget.takeIf { it > 0.0 } ?: maxOf(fund.monthSpent, fund.monthAccrued, 1.0)
    val progress = (fund.balance.coerceAtLeast(0.0) / target).coerceIn(0.0, 1.0)
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = scheme.onSurface, modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
            Text("RM ${rm(fund.balance)}", color = if (fund.balance >= 0) scheme.onSurface else scheme.error, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
            Spacer(Modifier.width(Space.xs))
            TextButton(onClick = onManage, contentPadding = PaddingValues(horizontal = Space.xs)) {
                Text("Manage", color = scheme.onSurface, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
            }
        }
        Spacer(Modifier.height(Space.xs))
        Box(Modifier.fillMaxWidth().height(4.dp).background(scheme.surfaceContainerHigh)) {
            Box(
                Modifier.fillMaxWidth(progress.toFloat()).fillMaxHeight()
                    .background(if (fund.balance >= 0) scheme.onSurface else scheme.error),
            )
        }
        Spacer(Modifier.height(Space.xs))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "target RM ${rm(target)}" +
                    (if (configuredTarget > 0.0) "" else " · one-month default"),
                color = scheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.weight(1f),
            )
            ReserveBadge(progress.toFloat())
        }
        Text(
            "this month +RM ${rm(fund.monthAccrued)} · −RM ${rm(fund.monthSpent)}" +
                if (fund.monthNet < 0) " · shortfall RM ${rm(-fund.monthNet)}" else "",
            color = if (fund.monthNet < 0) scheme.error else scheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun ReserveSheet(repo: BakiRepo, fund: String, onDismiss: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
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
        shape = MaterialTheme.shapes.extraLarge,
        title = { Text("$fundName reserve", color = scheme.onSurface, style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Space.s),
            ) {
                Text("A virtual reserve. No bank transfer happens.", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                if (!canWrite) Text("Read-only · enable writes in More → Allow writes", color = scheme.secondary, style = MaterialTheme.typography.bodyMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Target RM", color = scheme.onSurface, modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                    OutlinedTextField(
                        value = target,
                        onValueChange = { target = it.filter { c -> c.isDigit() || c == '.' } },
                        placeholder = { Text("auto", color = scheme.onSurfaceVariant) }, singleLine = true,
                        modifier = Modifier.width(120.dp), colors = bakiFieldColorsPublic(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                }
                Text("Leave blank for the one-month default.", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
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
                        colors = ButtonDefaults.buttonColors(containerColor = scheme.primary, contentColor = scheme.onPrimary),
                        shape = CircleShape,
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
                    ) { Text("Withdraw", color = if (canWrite) scheme.error else scheme.onSurfaceVariant) }
                }
                TextButton(
                    enabled = canWrite,
                    onClick = {
                        val value = target.toDoubleOrNull()?.takeIf { it > 0.0 } ?: 0.0
                        scope.launch { repo.setReserveTarget(fund, value); status = if (value > 0) "Target saved" else "Using one-month default" }
                    },
                ) { Text("Save target", color = if (canWrite) scheme.primary else scheme.onSurfaceVariant, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)) }
                if (status.isNotEmpty()) Text(status, color = scheme.primary, style = MaterialTheme.typography.bodyMedium)
                if (txns.isNotEmpty()) {
                    Text("Recent movements", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.titleSmall)
                    txns.forEach { tx ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(tx.date, color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                (if (tx.amount > 0) "+" else "−") + "RM ${rm(kotlin.math.abs(tx.amount))}",
                                color = if (tx.amount > 0) scheme.tertiary else scheme.error, style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done", color = scheme.primary, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)) } },
    )
}

/** The four-number reconciliation. Cash is a flow through Baki, not a bank balance. */
@Composable
private fun CashTruthCard(t: CashTruth, month: String, compact: Boolean = false) {
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().padding(vertical = Space.s)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Cash truth", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.weight(1f))
            Text("not a bank balance", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
        }
        Spacer(Modifier.height(Space.xs))
        CashLine("Recorded net", t.recordedNet, scheme.onSurface, scheme)
        CashLine("Net cash", t.cashNet, if (t.cashNet >= 0) scheme.tertiary else scheme.error, scheme)
        Text("cash in RM ${rm(t.cashReceived)} · cash out RM ${rm(t.cashSpent)}", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"))
        if (t.unpaid > 0) {
            Text(
                "RM ${rm(t.unpaid)} still unpaid" + (t.payoutAnchor?.let { " after $it" } ?: ""),
                color = scheme.secondary, style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (!compact) {
            if (t.prepaidBills > 0) Text("RM ${rm(t.prepaidBills)} paid ahead for bills", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            Text(
                "Operating RM ${rm(t.operatingNet)} after estimated running costs.",
                color = scheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall,
            )
        }
        Spacer(Modifier.height(Space.xs))
    }
}

@Composable
private fun CashLine(label: String, cents: Long, color: Color, scheme: androidx.compose.material3.ColorScheme) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        Text(label, color = scheme.onSurfaceVariant, style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.weight(1f))
        Text("RM ${rm(cents)}", color = color, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold))
    }
}

@Composable
private fun WeeklyReviewCard(start: String, r: WeeklyReview, target: Double, below: Int) {
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().padding(vertical = Space.s)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("WEEKLY REVIEW", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.weight(1f))
            Text("MON–SUN", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
        }
        Text("${prettyDate(start)} – ${prettyDate(addDays(start, 6))}", color = scheme.onSurface, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold))
        Text("${r.days} days · ${"%.1f".format(r.hours)}h · RM ${rm(r.net)} net", color = scheme.onSurface, style = MaterialTheme.typography.labelLarge)
        Text("RM ${rm(r.perHour)}/hr · fuel RM ${rm(r.fuelPerKm)}/km", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"))
        if (r.bestDay.isNotEmpty()) Text("Best day: ${prettyDate(r.bestDay)} · RM ${rm(r.bestNet)}", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        Text(
            if (below > 0) "$below shifts below RM ${rm(target)}/hr" else "Every shift cleared RM ${rm(target)}/hr",
            color = if (below > 0) scheme.secondary else scheme.tertiary, style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(Space.xs))
    }
}


@Composable
private fun ReserveBadge(progress: Float) {
    val full = progress >= 0.999f
    val scheme = MaterialTheme.colorScheme
    val shape = morphChipShape(full)
    Box(
        modifier = Modifier
            .padding(start = Space.xs)
            .heightIn(min = 22.dp)
            .clip(shape)
            .background(if (full) scheme.onSurface else scheme.onSurface.copy(alpha = 0.10f))
            .padding(horizontal = Space.s, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (full) "full" else "${(progress * 100).toInt()}%",
            color = if (full) scheme.tertiaryContainer else scheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}
