package app.baki

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.toShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TextField
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.roundToLong
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
    val funds = remember(store, month) { fundStats(store, month) }
    val cats = remember(store, month) { expensesByCategory(store, month) }
    val cost = remember(store, month) { monthCost(store, month) }
    val prevMonth = addDays("$month-01", -1).take(7)
    val prevNet = remember(store, prevMonth) { monthTotals(store, prevMonth).net }
    val dayLabel = if (days.size == 1) "1 active day" else "${days.size} active days"
    var managingFund by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().padding(horizontal = Space.l),
        verticalArrangement = Arrangement.spacedBy(Space.s),
        contentPadding = PaddingValues(top = 0.dp, bottom = 188.dp),
    ) {
        item {
            ReceiptCard(reduceMotion = repo.config.reducedMotion) {
                StatementCover(
                    month = month, t = t, days = days,
                    cost = cost, prevNet = prevNet,
                    onMonth = onMonth,
                )
            }
        }
        item {
            ReceiptCard(reduceMotion = repo.config.reducedMotion, delayMillis = 80) {
                WeekCloseCard(repo = repo, store = store)
            }
        }
        // Paper first: documents stay together, then analysis opens with a deliberate breath.
        item { Spacer(Modifier.height(Space.l)) }
        item { Md3Card(container = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.extraLarge, padding = Space.ml) { MonthInkChart(store = store, month = month) } }
        if (cats.isNotEmpty()) item {
            Md3Card { WhereItWent(cats = cats, total = t.expense, store = store) }
        }
        item { Md3Card { CalendarHeat(store, month, compact = true) } }
        item { Md3Card { HoursHeatmap(store) } }
        item {
            Md3Card(
                container = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ) { FundsSection(repo = repo, funds = funds, onManage = { managingFund = it }) }
        }
        item { Md3Card(container = MaterialTheme.colorScheme.surfaceContainerLow) { CashTruthCard(cash, month, compact = true) } }
        if (cats.isEmpty()) item {
            EmptyState("No activity yet.", "Your spending will show up here.")
        }
    }
    managingFund?.let { fund ->
        ReserveSheet(repo = repo, fund = fund, onDismiss = { managingFund = null })
    }
}

@Composable
private fun WeekCloseCard(repo: BakiRepo, store: Store) {
    val scheme = MaterialTheme.colorScheme
    val currentWeek = weekStartOf(todayIso())
    val options = remember(store, currentWeek) { weekOptions(store, todayIso()) }
    var selectedWeek by remember { mutableStateOf(currentWeek) }
    val weekStart = selectedWeek
    val view = remember(store, weekStart) { weekCloseView(store, weekStart) }
    var open by remember { mutableStateOf(false) }
    var pickerOpen by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                "/ WEEK CLOSE",
                color = scheme.primary,
                letterSpacing = 1.4.sp,
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            )
            Text("${prettyDate(view.weekStart)} – ${prettyDate(view.weekEnd)}", color = scheme.onSurface, style = MaterialTheme.typography.titleSmall)
        }
        Surface(
            color = if (view.isClosed) Color(0xFFDFF56A) else scheme.surfaceContainerHigh,
            contentColor = if (view.isClosed) Color(0xFF0B331E) else scheme.onSurfaceVariant,
            shape = ReceiptCorner,
            border = BorderStroke(1.dp, scheme.outline.copy(alpha = 0.35f)),
        ) {
            Text(
                if (view.isClosed) "CLOSED" else "OPEN",
                modifier = Modifier.padding(horizontal = Space.s, vertical = 4.dp),
                fontFamily = FontFamily.Monospace,
                letterSpacing = 0.8.sp,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            )
        }
    }
    Spacer(Modifier.height(Space.s))
    Row(verticalAlignment = Alignment.Bottom) {
        Column(Modifier.weight(1f)) {
            Text("RECORDED NET", color = scheme.onSurfaceVariant, fontFamily = FontFamily.Monospace, letterSpacing = 0.5.sp, style = MaterialTheme.typography.labelSmall)
            Text("RM ${rm(view.recordedNet)}", color = scheme.onSurface, style = MaterialTheme.typography.headlineSmall.copy(fontFeatureSettings = "tnum", fontWeight = FontWeight.Bold))
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("RECEIVED", color = scheme.onSurfaceVariant, fontFamily = FontFamily.Monospace, letterSpacing = 0.5.sp, style = MaterialTheme.typography.labelSmall)
            Text(
                if (!view.isClosed && view.received == 0L) "Not entered" else "RM ${rm(view.received)}",
                color = if (!view.isClosed && view.received == 0L) scheme.onSurfaceVariant else scheme.onSurface,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFeatureSettings = "tnum",
                    fontWeight = if (!view.isClosed && view.received == 0L) FontWeight.Normal else FontWeight.Bold,
                ),
            )
        }
    }
    Spacer(Modifier.height(Space.xs))
    Text(
        when {
            view.isClosed -> "Closed ${view.closeDate?.take(10) ?: "—"} · review or reopen"
            view.statement == null -> "No statement imported · enter what you actually received"
            !view.statementReconciled -> "Statement is not reconciled"
            else -> "Ready to close"
        },
        color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall,
    )
    Spacer(Modifier.height(Space.s))
    Row(horizontalArrangement = Arrangement.spacedBy(Space.s), verticalAlignment = Alignment.CenterVertically) {
        FilledTonalButton(onClick = { open = true }, modifier = Modifier.weight(1f).testTag("week-close-review"), enabled = view.hasActivity) {
            Text(if (view.isClosed) "Review" else "Review week", style = MaterialTheme.typography.labelLargeEmphasized)
        }
        TextButton(onClick = { pickerOpen = true }, modifier = Modifier.heightIn(min = Control.primary).testTag("week-close-picker")) {
            Text("Weeks", color = scheme.primary, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
        }
    }
    if (weekStart != currentWeek) {
        TextButton(onClick = { selectedWeek = currentWeek }, contentPadding = PaddingValues(horizontal = 0.dp)) {
            Text("Back to this week", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
        }
    }
    if (open) WeekCloseSheet(repo = repo, weekStart = weekStart, initial = view, onDismiss = { open = false })
    if (pickerOpen) WeekPickerSheet(
        options = options,
        selectedWeek = weekStart,
        currentWeek = currentWeek,
        onSelect = { selectedWeek = it; pickerOpen = false },
        onDismiss = { pickerOpen = false },
    )
}

@Composable
private fun WeekPickerSheet(
    options: List<WeekOption>,
    selectedWeek: String,
    currentWeek: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    BakiSheet(onDismiss = onDismiss) {
        Text("WEEK HISTORY", color = scheme.onSurfaceVariant, letterSpacing = 1.2.sp, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
        Text("Choose a week", color = scheme.onSurface, style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold))
        Text("Weeks with shifts, statements or a saved close.", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        options.forEach { option ->
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = if (option.weekStart == selectedWeek) scheme.primaryContainer else scheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth().testTag("week-option-${option.weekStart}").clickable { onSelect(option.weekStart) },
            ) {
                Row(Modifier.padding(horizontal = Space.m, vertical = Space.s), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "${prettyDate(option.weekStart)} – ${prettyDate(option.weekEnd)}" + if (option.weekStart == currentWeek) " · this week" else "",
                            color = if (option.weekStart == selectedWeek) scheme.onPrimaryContainer else scheme.onSurface,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        )
                        Text(
                            "${option.shiftCount} shift${if (option.shiftCount == 1) "" else "s"}" +
                                if (option.hasStatement) " · statement" else "",
                            color = if (option.weekStart == selectedWeek) scheme.onPrimaryContainer.copy(alpha = 0.75f) else scheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Text(
                        if (option.isClosed) "CLOSED" else "OPEN",
                        color = if (option.weekStart == selectedWeek) scheme.onPrimaryContainer else if (option.isClosed) scheme.tertiary else scheme.secondary,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    )
                }
            }
        }
    }
}

@Composable
private fun WeekCloseSheet(repo: BakiRepo, weekStart: String, initial: WeekCloseView, onDismiss: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    var received by remember(initial.received) { mutableStateOf(centsDigits(initial.received)) }
    var saving by remember { mutableStateOf(false) }
    var breakdownOpen by remember { mutableStateOf(false) }
    var bIncentives by remember(initial.statement) { mutableStateOf(initial.statement?.let { centsDigits((it.incentives * 100).roundToLong()) } ?: "") }
    var bTips by remember(initial.statement) { mutableStateOf(initial.statement?.let { centsDigits((it.tips * 100).roundToLong()) } ?: "") }
    var bNetGsf by remember(initial.statement) { mutableStateOf(initial.statement?.let { centsDigits((it.netGsf * 100).roundToLong()) } ?: "") }
    var bRate by remember(initial.statement) { mutableStateOf(initial.statement?.let { if (it.netGsfRate > 0) "%.1f".format(it.netGsfRate) else "" } ?: "") }
    var bDelivery by remember(initial.statement) { mutableStateOf(initial.statement?.let { centsDigits((it.deliveryIncentive * 100).roundToLong()) } ?: "") }
    var allocateConfirm by remember { mutableStateOf(false) }
    val cents = centsFromDigits(received)
    val view = remember(repo.store, weekStart) { weekCloseView(repo.store, weekStart) }
    val statementWeeklyCents = view.statement?.let { (it.weekly * 100).roundToLong() } ?: 0L
    val allocationCapacity = remember(repo.store, weekStart) { statementAllocationCapacity(repo.store, weekStart) }
    val alreadyAllocated = remember(repo.store, weekStart) {
        repo.store.shifts.any { shift -> weekStartOf(shift.date) == weekStart && shift.payouts.any { it.source == "statement:$weekStart" } }
    }
    BakiSheet(
        onDismiss = onDismiss,
        footer = {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (view.isClosed) {
                    TextButton(
                        modifier = Modifier.testTag("week-close-reopen"),
                        enabled = !repo.readOnly && !saving,
                        onClick = {
                            scope.launch {
                                saving = true
                                repo.reopenWeek(weekStart)
                                saving = false
                                onDismiss()
                            }
                        },
                    ) { Text("Reopen", color = scheme.onSurfaceVariant) }
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss, enabled = !saving) { Text("Cancel") }
                Spacer(Modifier.width(Space.s))
                Button(
                    modifier = Modifier.testTag("week-close-confirm"),
                    enabled = !repo.readOnly && !saving && view.hasActivity,
                    onClick = {
                        scope.launch {
                            saving = true
                            if (repo.closeWeek(weekStart, if (received.isBlank()) view.received else cents)) onDismiss()
                            saving = false
                        }
                    },
                ) { Text(if (view.isClosed) "Save close" else "Close week") }
            }
        },
    ) {
        Text("WEEK CLOSE", color = scheme.onSurfaceVariant, letterSpacing = 1.2.sp, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
        Text("${prettyDate(view.weekStart)} – ${prettyDate(view.weekEnd)}", color = scheme.onSurface, style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold))
        Text("Monday–Sunday · no ledger lock", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        WeekCloseRow("Shifts", "${view.shiftCount} · ${"%.1f".format(view.hours)}h")
        WeekCloseRow("Recorded shift net", "RM ${rm(view.recordedNet)}")
        WeekCloseRow("Recorded gross", "RM ${rm(view.recordedGross)}")
        WeekCloseRow("Payouts already recorded", "RM ${rm(view.recordedPayouts)}")
        view.statement?.let { st ->
            Spacer(Modifier.height(Space.xs))
            Text("STATEMENT", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
            WeekCloseRow("Gross", "RM ${rm((st.income * 100).roundToLong())}")
            WeekCloseRow("Deductions", "− RM ${rm((kotlin.math.abs(st.deductions) * 100).roundToLong())}")
            WeekCloseRow("Weekly payout", "RM ${rm((st.weekly * 100).roundToLong())}")
            if (st.netGsf > 0) WeekCloseRow("Net GSF · informational", "RM ${"%.2f".format(st.netGsf)}")
            if (st.netGsfRate > 0) WeekCloseRow("Net GSF rate", "${"%.1f".format(st.netGsfRate)}%")
            if (st.deliveryIncentive > 0) WeekCloseRow("Delivery incentive · app", "RM ${"%.2f".format(st.deliveryIncentive)}")
            if (st.incentives > 0 || st.tips > 0) {
                WeekCloseRow("Incentives / tips", "RM ${"%.2f".format(st.incentives)} / ${"%.2f".format(st.tips)}")
            }
            WeekCloseRow("Reconciled", if (view.statementReconciled) "yes" else "no")
            WeekCloseRow("Statement payout allocation", if (alreadyAllocated) "allocated" else "not allocated")
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
                if (!view.isClosed && !repo.readOnly && statementWeeklyCents > 0L) {
                    TextButton(
                        onClick = { received = centsDigits(statementWeeklyCents) },
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                    ) { Text("Use statement amount", color = scheme.primary, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)) }
                }
                if (!repo.readOnly && allocationCapacity > 0L && !alreadyAllocated) {
                    TextButton(
                        onClick = { allocateConfirm = true },
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                    ) { Text("Allocate payout", color = scheme.primary, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)) }
                }
                if (!repo.readOnly) {
                    TextButton(
                        onClick = {
                            scope.launch {
                                saving = true
                                val ok = repo.setStatementReconciled(weekStart, !view.statementReconciled)
                                saving = false
                                if (ok) ActionFeedback.show(if (view.statementReconciled) "Statement reopened" else "Statement reconciled")
                            }
                        },
                        enabled = !saving,
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                    ) { Text(if (view.statementReconciled) "Reopen statement" else "Mark reconciled", color = scheme.primary, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)) }
                }
            }
            TextButton(
                onClick = { breakdownOpen = !breakdownOpen },
                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                modifier = Modifier.testTag("week-breakdown-toggle"),
            ) { Text(if (breakdownOpen) "Hide earnings breakdown" else "Edit earnings breakdown", color = scheme.primary, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)) }
        } ?: Text("No statement imported for this week.", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        if (breakdownOpen && view.statement != null) {
            Text("WEEKLY EARNINGS BREAKDOWN", color = scheme.onSurfaceVariant, letterSpacing = 1.sp, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
            Text("Saving posts incentives, tips and delivery incentives as income entries automatically. Net GSF is informational and is not deducted from total earnings. The PDF weekly payout stays authoritative.", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            CentsField(digits = bIncentives, onDigitsChange = { bIncentives = it }, label = "Incentives (from PDF)", prefix = "RM", colors = OutlinedTextFieldDefaults.colors(), modifier = Modifier.fillMaxWidth(), enabled = !repo.readOnly)
            CentsField(digits = bTips, onDigitsChange = { bTips = it }, label = "Tips (from PDF)", prefix = "RM", colors = OutlinedTextFieldDefaults.colors(), modifier = Modifier.fillMaxWidth(), enabled = !repo.readOnly)
            CentsField(digits = bDelivery, onDigitsChange = { bDelivery = it }, label = "Delivery incentive (app)", prefix = "RM", colors = OutlinedTextFieldDefaults.colors(), modifier = Modifier.fillMaxWidth(), enabled = !repo.readOnly)
            CentsField(digits = bNetGsf, onDigitsChange = { bNetGsf = it }, label = "Net GSF · not deducted", prefix = "RM", colors = OutlinedTextFieldDefaults.colors(), modifier = Modifier.fillMaxWidth(), enabled = !repo.readOnly)
            OutlinedTextField(
                value = bRate,
                onValueChange = { bRate = it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' }.take(5) },
                label = { Text("Net GSF rate %") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
                enabled = !repo.readOnly,
            )
            Button(
                onClick = {
                    scope.launch {
                        saving = true
                        val ok = repo.updateStatementBreakdown(
                            weekStart = weekStart,
                            incentivesCents = centsFromDigits(bIncentives),
                            tipsCents = centsFromDigits(bTips),
                            netGsfCents = centsFromDigits(bNetGsf),
                            netGsfRate = (bRate.replace(',', '.').toDoubleOrNull() ?: 0.0),
                            deliveryIncentiveCents = centsFromDigits(bDelivery),
                        )
                        saving = false
                        if (ok) {
                            breakdownOpen = false
                            ActionFeedback.show("Weekly breakdown saved")
                        }
                    }
                },
                enabled = !repo.readOnly && !saving,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Save breakdown") }
        }
        Spacer(Modifier.height(Space.s))
        Text("ACTUAL PAYOUT RECEIVED", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
        CentsField(
            digits = received,
            onDigitsChange = { received = it },
            label = "Received RM",
            prefix = "",
            colors = OutlinedTextFieldDefaults.colors(),
            enabled = !repo.readOnly && !view.isClosed,
            modifier = Modifier.fillMaxWidth(),
        )
        if (view.statement != null) {
            WeeklyVarianceRow("Statement weekly vs received", (cents ?: view.received) - (view.statement.weekly * 100).roundToLong())
        }
        WeeklyVarianceRow("Recorded payouts vs received", (cents ?: view.received) - view.recordedPayouts)
        view.grossGap?.let { WeeklyVarianceRow("Logged gross vs statement gross", it) }
        Text(
            if (repo.readOnly) "Read-only: enable Allow writes in More → App."
            else if (view.isClosed) "Closed ${view.closeDate?.take(16)?.replace('T', ' ') ?: "—"}. Reopen to edit."
            else "Enter the amount that actually landed, then close.",
            color = if (repo.readOnly) scheme.secondary else scheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
        if (allocateConfirm) {
            val amount = minOf(statementWeeklyCents, allocationCapacity)
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { allocateConfirm = false },
                title = { Text("Allocate statement payout?") },
                text = {
                    Text(
                        "Allocate RM ${rm(amount)} across this week's unpaid shift gaps. " +
                            "These payouts are labelled statement-funded, not observed cash.",
                    )
                },
                confirmButton = {
                    TextButton(
                        enabled = !saving && amount > 0L,
                        onClick = {
                            scope.launch {
                                saving = true
                                val n = repo.allocateStatementPayout(weekStart, amount)
                                saving = false
                                allocateConfirm = false
                                if (n > 0) ActionFeedback.show("Allocated statement payout across $n shifts")
                            }
                        },
                    ) { Text("Allocate", color = scheme.primary) }
                },
                dismissButton = { TextButton(onClick = { allocateConfirm = false }) { Text("Cancel") } },
            )
        }
    }
}

@Composable
private fun WeekCloseRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(value, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum", fontWeight = FontWeight.Bold))
    }
}

@Composable
private fun WeeklyVarianceRow(label: String, cents: Long) {
    val scheme = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(
            (if (cents > 0) "+" else if (cents < 0) "−" else "") + "RM ${rm(kotlin.math.abs(cents))}",
            color = if (cents == 0L) scheme.tertiary else scheme.secondary,
            style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum", fontWeight = FontWeight.Bold),
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SectionMark(tint: Color, size: androidx.compose.ui.unit.Dp = 16.dp) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(tint),
    )
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
    cost: MonthCost,
    prevNet: Long,
    onMonth: (String) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val delta = t.net - prevNet
    val dayLabel = if (days.size == 1) "1 active day" else "${days.size} active days"

    Box(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(vertical = Space.s)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ShapeNavButton(
                label = "‹",
                enabled = true,
                onClick = { onMonth(addDays("$month-01", -1).take(7)) },
                description = "Previous month",
                background = scheme.surfaceContainerHighest,
                ink = scheme.onSurface,
            )
            Text(
                monthLabel(month), color = scheme.onSurface,
                textAlign = TextAlign.Center, modifier = Modifier.weight(1f),
                maxLines = if (LocalDensity.current.fontScale >= 1.6f) 2 else 1,
                overflow = TextOverflow.Ellipsis,
                style = if (LocalDensity.current.fontScale >= 1.6f)
                    MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                else MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            )
            ShapeNavButton(
                label = "›",
                enabled = true,
                onClick = { onMonth(addDays(endOfMonth(month), 1).take(7)) },
                description = "Next month",
                background = scheme.surfaceContainerHighest,
                ink = scheme.onSurface,
            )
        }
        Spacer(Modifier.height(Space.m))
        ReceiptRule()
        Spacer(Modifier.height(Space.m))
        Text(
            "/ MONTHLY STATEMENT",
            color = scheme.primary,
            letterSpacing = 1.4.sp,
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
        )
        Spacer(Modifier.height(Space.xs))
        EasedMoney(t.net, scheme.onSurface, MaterialTheme.typography.displaySmallEmphasized.copy(fontFeatureSettings = "tnum"))
        Surface(
            color = if (delta >= 0) Color(0xFFDFF56A) else scheme.errorContainer,
            contentColor = if (delta >= 0) Color(0xFF0B331E) else scheme.onErrorContainer,
            shape = ReceiptCorner,
        ) {
            Text(
                if (delta >= 0) "+RM ${rm(delta)} vs last month" else "−RM ${rm(-delta)} vs last month",
                modifier = Modifier.padding(horizontal = Space.s, vertical = 3.dp),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            )
        }
        Text(
            "$dayLabel · in RM ${rm(t.income)} · out RM ${rm(t.expense)}",
            color = scheme.onSurfaceVariant, style = MaterialTheme.typography.labelLarge,
        )
        Text(
            if (cost.km > 0.01) "running RM ${rm(cost.running / cost.km)}/km · upkeep RM ${rm(cost.upkeep / cost.km)}/km"
            else "No distance recorded this month.",
            color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(Space.xs))
        }
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
    val runningPoints = remember(shown) {
        var total = 0L
        shown.map { day -> total += day.net; total }
    }
    val runningTotal = runningPoints.lastOrNull() ?: 0L
    val active = shown.filter { it.active }
    val cfg = store.settings.profit
    val hours = remember(store, month) {
        store.shifts.filter { it.date.startsWith(month) }.sumOf { it.hours }
    }
    val targetPerDay = if (range == 7 && active.isNotEmpty()) cfg.targetRate * hours / active.size else 0.0
    val maxAbs = maxOf(1.0, shown.maxOfOrNull { abs(it.net) / 100.0 } ?: 1.0)
    val selectedDay = selected?.let { shown.getOrNull(it) }
    val anyActive = shown.any { it.active }
    val chartSummary = remember(month, range, shown, targetPerDay, runningTotal) {
        buildString {
            append(
                if (range == 7) "Daily net chart for ${monthLabel(month)}, 7 day view. "
                else "Cumulative running net for ${monthLabel(month)}, ${shown.size} days. Current total RM ${rm(runningTotal)}. "
            )
            if (targetPerDay > 0.01) append("Target RM ${rm(targetPerDay)} per day. ")
            append("Data points: ")
            append(shown.joinToString("; ") { day ->
                "${day.iso.takeLast(5)} ${if (day.active) "net RM ${rm(day.net)}" else "no entries"}"
            })
            append(". Activate to select the next day.")
        }
    }

    val largeText = LocalDensity.current.fontScale >= 1.6f
    Column(Modifier.fillMaxWidth().padding(vertical = Space.s)) {
        if (largeText) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .padding(end = Space.s)
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(scheme.primary),
                )
                Text("Month in ink", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.titleSmallEmphasized, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(Space.xs))
            ConnectedChoiceGroup(
                options = listOf("7D", "30D"),
                selected = if (range == 7) "7D" else "30D",
                onSelect = { range = if (it == "7D") 7 else 30 },
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .padding(end = Space.s)
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(scheme.primary),
                )
                Text("Month in ink", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.titleSmallEmphasized, modifier = Modifier.weight(1f))
                ConnectedChoiceGroup(
                    options = listOf("7D", "30D"),
                    selected = if (range == 7) "7D" else "30D",
                    onSelect = { range = if (it == "7D") 7 else 30 },
                    modifier = Modifier.width(144.dp),
                )
            }
        }
        Spacer(Modifier.height(Space.xs))
        if (largeText) {
            Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                Surface(shape = RoundedCornerShape(50), color = if (range == 30) scheme.primaryContainer else scheme.tertiaryContainer) {
                    Text(
                        if (range == 30) "Running net · RM ${rm(runningTotal)}" else "Target RM ${rm(targetPerDay)}/day",
                        color = if (range == 30) scheme.onPrimaryContainer else scheme.onTertiaryContainer,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = Space.s, vertical = Space.xs),
                    )
                }
                Text(if (range == 30) "Total to date" else "Tap a day for details", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                Surface(shape = RoundedCornerShape(50), color = if (range == 30) scheme.primaryContainer else scheme.tertiaryContainer) {
                    Text(
                        if (range == 30) "Running net · RM ${rm(runningTotal)}" else "Target RM ${rm(targetPerDay)}/day",
                        color = if (range == 30) scheme.onPrimaryContainer else scheme.onTertiaryContainer,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = Space.s, vertical = Space.xs),
                    )
                }
                Text(if (range == 30) "Total to date" else "Tap a day for details", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
            }
        }
        Spacer(Modifier.height(Space.s))
        // A one-active-day window does not deserve a chart or a judgemental target line.
        val sparse = range == 7 && shown.count { it.active } <= 1
        if (sparse) {
            val activeDay = shown.firstOrNull { it.active }
            Surface(
                shape = MaterialTheme.shapes.large,
                color = scheme.surfaceContainerHighest,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    Modifier.padding(horizontal = Space.m, vertical = Space.s),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (activeDay == null) "No active days yet" else "1 active day",
                            color = scheme.onSurface,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        )
                        Text(
                            activeDay?.let {
                                "${weekdayShort(it.iso)} ${prettyDateShort(it.iso)} · net RM ${rm(it.net)}"
                            } ?: "Add an entry and the chart will start here.",
                            color = scheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Text(
                        "Chart appears\nafter 2 days",
                        color = scheme.onSurfaceVariant,
                        textAlign = TextAlign.End,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        } else {
        Box(Modifier.fillMaxWidth()) {
        Canvas(
            Modifier.fillMaxWidth().height(if (sparse) 84.dp else 142.dp).clipToBounds()
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
            val topPad = 8.dp.toPx()
            val zeroY = size.height * 0.58f
            val usable = minOf(size.height * 0.40f, zeroY - topPad, size.height - zeroY - topPad)
            // Keep one-off purchases from flattening the month. Values beyond 2× target clip at
            // the edge and remain readable in the selected-day detail.
            val dataMax = if (range == 7) {
                shown.maxOfOrNull { abs(it.net) / 100.0 } ?: 1.0
            } else {
                runningPoints.maxOfOrNull { abs(it) / 100.0 } ?: 1.0
            }
            val scaleMax = if (range == 7) {
                if (sparse) maxOf(1.0, dataMax * 1.35)
                else if (targetPerDay > 0.01) maxOf(1.0, targetPerDay * 2.0) else maxOf(1.0, dataMax)
            } else maxOf(1.0, dataMax * 1.06)
            val targetY = (zeroY - (targetPerDay / scaleMax * usable).toFloat())
                .coerceIn(topPad, size.height - topPad)
            val step = size.width / shown.size.coerceAtLeast(1)

            // Selection is a quiet vertical wash, not a bar-shaped object.
            if (selected != null && selected!! in shown.indices) {
                val left = selected!! * step + step * 0.12f
                drawRoundRect(
                    color = scheme.primaryContainer.copy(alpha = 0.32f),
                    topLeft = Offset(left, topPad),
                    size = Size(step * 0.76f, size.height - topPad * 2),
                    cornerRadius = CornerRadius(step * 0.38f, step * 0.38f),
                )
            }
            drawLine(scheme.outlineVariant, Offset(0f, zeroY), Offset(size.width, zeroY), 1.dp.toPx())
            if (!sparse && range == 7 && targetPerDay > 0.01) {
                drawLine(
                    color = scheme.tertiary.copy(alpha = 0.75f),
                    start = Offset(0f, targetY), end = Offset(size.width, targetY),
                    strokeWidth = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 5.dp.toPx())),
                )
            }

            if (range == 7) {
                // Lollipop: thin stem, solid dot. One active day still reads as one value.
                shown.forEachIndexed { i, day ->
                    val value = day.net / 100.0
                    val x = (i + 0.5f) * step
                    val y = (zeroY - (value / scaleMax * usable).toFloat()).coerceIn(topPad, size.height - topPad)
                    if (day.active) {
                        val color = if (value < 0) scheme.error else scheme.primary
                        drawLine(color, Offset(x, zeroY), Offset(x, y), 2.dp.toPx(), StrokeCap.Round)
                        drawCircle(color, radius = 5.dp.toPx(), center = Offset(x, y))
                    } else {
                        drawCircle(
                            color = scheme.outlineVariant,
                            radius = 3.5.dp.toPx(),
                            center = Offset(x, zeroY),
                            style = Stroke(width = 1.2.dp.toPx()),
                        )
                    }
                }
            } else {
                // 30D: cumulative running net. The final point is the current month total;
                // days off hold the line flat rather than inventing a zero-value gap.
                val points = runningPoints.mapIndexed { i, total ->
                    Offset((i + 0.5f) * step, (zeroY - (total / 100.0 / scaleMax * usable).toFloat()).coerceIn(topPad, size.height - topPad))
                }
                if (points.isNotEmpty()) {
                    val path = Path().apply {
                        moveTo(0f, zeroY)
                        points.forEach { lineTo(it.x, it.y) }
                    }
                    val area = Path().apply {
                        addPath(path)
                        lineTo(points.last().x, zeroY)
                        lineTo(0f, zeroY)
                        close()
                    }
                    drawPath(area, Brush.verticalGradient(listOf(scheme.primary.copy(alpha = 0.18f), Color.Transparent), startY = topPad, endY = zeroY))
                    drawPath(path, scheme.primary, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                    val end = points.last()
                    drawCircle(
                        color = if (runningTotal < 0) scheme.error else scheme.primary,
                        radius = 6.dp.toPx(), center = end,
                    )
                    drawCircle(
                        color = scheme.surface, radius = 2.dp.toPx(), center = end,
                    )
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
        if (!sparse) {
        if (range == 7) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                shown.forEach { day ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(weekdayShort(day.iso), color = scheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                        Text(day.iso.takeLast(2), color = scheme.onSurfaceVariant.copy(alpha = 0.65f), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        } else {
            val ticks = listOf(0, 7, 14, 21, shown.lastIndex).distinct().filter { it in shown.indices }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                ticks.forEach { index ->
                    Text(shown[index].iso.takeLast(5), color = scheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        }
    }
}
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun WhereItWent(cats: List<Pair<String, Long>>, total: Long, store: Store) {
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().padding(vertical = Space.s)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionMark(scheme.primary, 14.dp)
            Spacer(Modifier.width(Space.s))
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
            LinearWavyProgressIndicator(
                progress = { share.toFloat().coerceIn(0.01f, 1f) },
                modifier = Modifier.fillMaxWidth().height(8.dp),
                color = scheme.primary,
                trackColor = scheme.surfaceContainerHigh,
            )
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
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionMark(scheme.primary, 14.dp)
            Spacer(Modifier.width(Space.s))
            Text("FUNDS · ROLLING", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.titleSmall)
        }
        Spacer(Modifier.height(Space.s))
        FundRow("Fuel", "fuel", funds.fuel, repo.store.settings.reserveTargets["fuel"] ?: 0.0, scheme) { onManage("fuel") }
        Spacer(Modifier.height(Space.s))
        FundRow("Wear & tear", "wear", funds.wear, repo.store.settings.reserveTargets["wear"] ?: 0.0, scheme) { onManage("wear") }
        Spacer(Modifier.height(Space.xs))
        Text("rolling reserve · top-ups are virtual allocations from cash", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(Space.xs))
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
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
        LinearWavyProgressIndicator(
            progress = { progress.toFloat() },
            modifier = Modifier.fillMaxWidth().height(8.dp),
            color = if (fund.balance >= 0) scheme.onSurface else scheme.error,
            trackColor = scheme.surfaceContainerHigh,
        )
        Spacer(Modifier.height(Space.xs))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "target RM ${rm(target)}" +
                    (if (configuredTarget > 0.0) "" else " · one-month default"),
                color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
            ReserveBadge(progress.toFloat())
        }
        if (fund.monthNet < 0) {
            Text("Shortfall RM ${rm(-fund.monthNet)}", color = scheme.error, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
            Text("this month +RM ${rm(fund.monthAccrued)} · −RM ${rm(fund.monthSpent)}", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        } else {
            Text("this month +RM ${rm(fund.monthAccrued)} · −RM ${rm(fund.monthSpent)}", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ReserveSheet(repo: BakiRepo, fund: String, onDismiss: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val canWrite = !repo.readOnly
    val state = if (fund == "fuel") repo.store.settings.reserveTargets["fuel"] ?: 0.0 else repo.store.settings.reserveTargets["wear"] ?: 0.0
    var target by remember(fund) { mutableStateOf(centsDigits((state * 100).roundToLong())) }
    var amount by remember(fund) { mutableStateOf("") }
    var status by remember(fund) { mutableStateOf("") }
    val fundName = if (fund == "fuel") "Fuel" else "Wear & tear"
    val txns = remember(repo.store.reserveTxns, fund) {
        repo.store.reserveTxns.filter { it.fund == fund }.sortedByDescending { it.updated }.take(6)
    }

    BakiSheet(
        onDismiss = onDismiss,
        footer = {
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("Done", color = scheme.primary, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
            }
        },
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(scheme.tertiaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Text("◎", color = scheme.onTertiaryContainer, style = MaterialTheme.typography.titleLarge)
            }
            Spacer(Modifier.width(Space.m))
            Column {
                Text(
                    "${fundName.uppercase()} RESERVE",
                    color = scheme.onSurface,
                    letterSpacing = 1.sp,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                )
                Text("virtual money. real peace of mind.", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            }
        }
        Text("A virtual reserve. No bank transfer happens.", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        if (!canWrite) {
            Text("Read-only · enable writes in More → Allow writes", color = scheme.secondary, style = MaterialTheme.typography.bodyMedium)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Target RM", color = scheme.onSurface, modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
            CentsField(
                digits = target,
                onDigitsChange = { target = it },
                placeholder = "auto",
                prefix = "",
                modifier = Modifier.width(120.dp),
                colors = bakiFieldColorsPublic(),
            )
        }
        Text("Leave blank for the one-month default.", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
        CentsField(
            digits = amount,
            onDigitsChange = { amount = it },
            label = "MOVE MONEY",
            textStyle = MaterialTheme.typography.headlineMediumEmphasized.copy(fontFeatureSettings = "tnum"),
            shape = MaterialTheme.shapes.extraLarge,
            filled = true,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = scheme.secondaryContainer,
                unfocusedContainerColor = scheme.secondaryContainer,
                focusedTextColor = scheme.onSecondaryContainer,
                unfocusedTextColor = scheme.onSecondaryContainer,
                focusedLabelColor = scheme.onSecondaryContainer.copy(alpha = 0.72f),
                unfocusedLabelColor = scheme.onSecondaryContainer.copy(alpha = 0.72f),
                cursorColor = scheme.onSecondaryContainer,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            Button(
                enabled = canWrite && centsFromDigits(amount) > 0L,
                onClick = {
                    val cents = centsFromDigits(amount)
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
                enabled = canWrite && centsFromDigits(amount) > 0L,
                onClick = {
                    val cents = centsFromDigits(amount)
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
                val value = centsFromDigits(target) / 100.0
                scope.launch {
                    repo.setReserveTarget(fund, value)
                    status = if (value > 0) "Target saved" else "Using one-month default"
                }
            },
        ) {
            Text(
                "Save target",
                color = if (canWrite) scheme.primary else scheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            )
        }
        if (status.isNotEmpty()) Text(status, color = scheme.primary, style = MaterialTheme.typography.bodyMedium)
        if (txns.isNotEmpty()) {
            Text("RECENT MOVEMENTS", color = scheme.onSurfaceVariant, letterSpacing = 1.sp, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
            txns.forEach { tx ->
                Row(
                    Modifier.fillMaxWidth()
                        .clip(MaterialTheme.shapes.large)
                        .background(scheme.surfaceContainerLow)
                        .padding(horizontal = Space.m, vertical = Space.s),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(if (tx.amount > 0) scheme.tertiaryContainer else scheme.errorContainer),
                    )
                    Spacer(Modifier.width(Space.s))
                    Text(tx.date, color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text(
                        (if (tx.amount > 0) "+" else "−") + "RM ${rm(kotlin.math.abs(tx.amount))}",
                        color = if (tx.amount > 0) scheme.tertiary else scheme.error,
                        style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum", fontWeight = FontWeight.Bold),
                    )
                }
            }
        }
    }
}

/** The four-number reconciliation. Cash is a flow through Baki, not a bank balance. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CashTruthCard(t: CashTruth, month: String, compact: Boolean = false) {
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().padding(vertical = Space.s)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionMark(scheme.primary, 14.dp)
            Spacer(Modifier.width(Space.s))
            Text("Cash truth", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.weight(1f))
            Text("not a bank balance", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
        }
        Spacer(Modifier.height(Space.xs))
        CashLine("Recorded net", t.recordedNet, scheme.onSurface, scheme)
        if (t.cashNet != t.recordedNet) {
            CashLine("Net cash", t.cashNet, if (t.cashNet >= 0) scheme.tertiary else scheme.error, scheme)
        }
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
