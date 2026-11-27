package app.baki

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.focusable
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.launch

// ---------------------------------------------------------------------------
// Shared desk primitives
// ---------------------------------------------------------------------------

@Composable
internal fun DeskCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier, shape = InstrumentShape, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(Space.l), content = content)
    }
}

@Composable
internal fun DeskLabel(text: String, color: Color? = null) {
    Text(
        if (text.startsWith("/")) text else "/ $text",
        color = color ?: MaterialTheme.colorScheme.onSurfaceVariant,
        letterSpacing = 1.3.sp,
        fontFamily = FontFamily.Monospace,
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
    )
}

@Composable
internal fun DeskMetric(label: String, value: String, tint: Color = MaterialTheme.colorScheme.onSurface) {
    Column {
        Text(
            label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 1.1.sp,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
        )
        Spacer(Modifier.height(2.dp))
        Text(value, color = tint, style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum", fontWeight = FontWeight.Bold))
    }
}

@Composable
internal fun DeskMoney(
    text: String,
    color: Color = MaterialTheme.colorScheme.onSurface,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.displayMedium,
) {
    Text(text, color = color, style = style.copy(fontFeatureSettings = "tnum", fontWeight = FontWeight.Black), maxLines = 1)
}

@Composable
internal fun DeskEmpty(title: String, body: String) {
    DeskCard(Modifier.fillMaxWidth()) {
        Text(title, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
        Spacer(Modifier.height(Space.xs))
        Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun DeskTableHead(vararg cells: Pair<String, Float>) {
    val scheme = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().padding(vertical = Space.s), verticalAlignment = Alignment.CenterVertically) {
        cells.forEach { (label, weight) ->
            Text(
                label,
                color = scheme.onSurfaceVariant,
                letterSpacing = 0.9.sp,
                fontFamily = FontFamily.Monospace,
                textAlign = if (label == "AMOUNT" || label == "RUNNING") TextAlign.End else TextAlign.Start,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.weight(weight),
            )
        }
    }
    HorizontalDivider(color = scheme.outlineVariant)
}

@Composable
private fun DeskTableRow(
    values: List<Pair<String, Float>>,
    colors: List<Color> = emptyList(),
    alignments: List<TextAlign> = emptyList(),
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val reduceMotion = LocalReducedMotion.current
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    var focused by remember { mutableStateOf(false) }
    val base = if (selected) scheme.primaryContainer.copy(alpha = 0.35f) else Color.Transparent
    val hover = scheme.onSurface.copy(alpha = 0.045f)
    val background by animateColorAsState(
        targetValue = if (hovered && !selected) hover else base,
        animationSpec = if (reduceMotion) tween(0) else tween(120),
        label = "desk-row-hover",
    )
    Row(
        Modifier.fillMaxWidth()
            .background(background)
            .then(
                if (onClick != null) Modifier
                    .clickable(interactionSource = interaction, indication = null, onClick = onClick)
                    .hoverable(interaction)
                    .focusable(interactionSource = interaction)
                    .onFocusChanged { focused = it.isFocused }
                    .drawBehind { if (focused) drawRect(color = scheme.primary, style = Stroke(1.dp.toPx())) }
                else Modifier,
            )
            .padding(vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        values.forEachIndexed { index, (value, weight) ->
            Text(
                value,
                color = colors.getOrNull(index) ?: scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = alignments.getOrNull(index) ?: TextAlign.Start,
                style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                modifier = Modifier.weight(weight),
            )
        }
    }
    HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.55f))
}

// ---------------------------------------------------------------------------
// Overview
// ---------------------------------------------------------------------------

@Composable
internal fun DeskOverview(repo: BakiRepo, month: String) {
    val store = repo.store
    val scheme = MaterialTheme.colorScheme
    val t = remember(store, month) { monthTotals(store, month) }
    val shifts = remember(store, month) { store.shifts.filter { it.date.startsWith(month) } }
    val entries = remember(store) { store.entries.sortedWith(compareByDescending<Entry> { it.date }.thenByDescending { it.loggedAt }).take(7) }
    val days = remember(store, month) { monthDays(store, month) }
    val hours = shifts.sumOf { it.hours }
    val km = shifts.sumOf { shiftKm(it, store.settings.profit) }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = Space.xl),
        verticalArrangement = Arrangement.spacedBy(Space.m),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(top = Space.s, bottom = Space.xl),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Space.xl),
                verticalAlignment = Alignment.Top,
            ) {
                Column(Modifier.weight(1.3f)) {
                ReceiptCard(
                    reduceMotion = repo.config.reducedMotion,
                    modifier = Modifier,
                ) {
                    Text(
                        "/ 01  MONTH STATEMENT",
                        color = scheme.primary,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.4.sp,
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    )
                    Spacer(Modifier.height(Space.s))
                    DeskMoney(
                        "RM ${rm(t.net)}",
                        if (t.net < 0) scheme.error else scheme.onSurface,
                        MaterialTheme.typography.displayLarge,
                    )
                    Text(
                        "in RM ${rm(t.income)} · out RM ${rm(t.expense)} · ${t.entryCount} entries",
                        color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(Space.m))
                    ReceiptRule()
                    Spacer(Modifier.height(Space.m))
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.xl)) {
                        DeskMetric("HOURS", "%.1f".format(hours))
                        DeskMetric("KM", "%.0f".format(km))
                        DeskMetric("SHIFTS", shifts.size.toString())
                    }
                    Spacer(Modifier.height(Space.s))
                    Text(
                        if (shifts.isEmpty()) "No shifts recorded this month."
                        else "${days.size} active ${if (days.size == 1) "day" else "days"} recorded · read-only desk",
                        color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Spacer(Modifier.height(Space.xl))
                DeskLatestEntries(store, entries)
                }
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(if (repo.status == "offline") scheme.error else scheme.tertiary))
                        Spacer(Modifier.width(Space.s))
                        Text(
                            "/ ANALYSIS",
                            color = scheme.primary,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.4.sp,
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        )
                        Spacer(Modifier.weight(1f))
                        Text(repo.status, color = scheme.onSurface, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                    }
                    Spacer(Modifier.height(Space.xs))
                    Text(
                        "read-only desk · phone remains the writer · last sync ${repo.lastSyncAt?.let { clockOf(it) } ?: "not yet"}",
                        color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(Space.m))
                    DeskCard(Modifier.fillMaxWidth()) { DeskRunningCard(store, month, showTotal = false) }
                    if (remember(store) { hourHeatmap(store).isNotEmpty() }) {
                        Spacer(Modifier.height(Space.m))
                        DeskCard(Modifier.fillMaxWidth()) { HoursHeatmap(store) }
                    }
                }
            }
        }
        item {
            Text(
                "/ 03  VEHICLE & CASH",
                color = scheme.primary,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.4.sp,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(Space.m), verticalAlignment = Alignment.Top) {
                VehicleWearCard(store, Modifier.weight(1f))
                VehicleEconomyCard(store, Modifier.weight(1f))
            }
        }
        item {
            DeskCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DeskLabel("CASH FLOW · ${monthLabel(month).uppercase()}")
                    Spacer(Modifier.weight(1f))
                    Text("bills · payouts", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                }
                Spacer(Modifier.height(Space.m))
                CashFlowGrid(store, month)
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(Space.m)) {
                val funds = remember(store, month) { fundStats(store, month) }
                val due = remember(store, month) { Bills.due(store, month) }
                val unpaid = remember(store, month) { Bills.unpaid(store, month) }
                val cats = remember(store, month) { expensesByCategory(store, month).take(4) }
                DeskCard(Modifier.weight(1f)) {
                    DeskLabel("COMMITMENTS")
                    Spacer(Modifier.height(Space.s))
                    DeskMoney("RM ${rm(due.sumOf { it.amount })}", scheme.onSurface, MaterialTheme.typography.headlineMedium)
                    Text("${unpaid.size} still open · ${due.size - unpaid.size} covered", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(Space.s))
                    due.take(3).forEach { bill ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                            Text(bill.name, color = scheme.onSurface, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("RM ${rm(bill.amount)}", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"))
                        }
                    }
                }
                DeskCard(Modifier.weight(1f)) {
                    DeskLabel("RESERVES")
                    Spacer(Modifier.height(Space.s))
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.xl)) {
                        DeskMetric("FUEL", "RM ${rm(funds.fuel.balance)}")
                        DeskMetric("WEAR", "RM ${rm(funds.wear.balance)}")
                    }
                    Spacer(Modifier.height(Space.s))
                    Text("rolling provisions · read-only", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                }
                DeskCard(Modifier.weight(1f)) {
                    DeskLabel("SPEND BY CATEGORY")
                    Spacer(Modifier.height(Space.s))
                    if (cats.isEmpty()) {
                        Text("No expenses this month.", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                    } else {
                        cats.forEach { (id, cents) ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                                Text(categoryLabel(store, id), color = scheme.onSurface, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("RM ${rm(cents)}", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DeskLatestEntries(store: Store, entries: List<Entry>) {
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "/ 02  LATEST ENTRIES",
                color = scheme.primary,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.4.sp,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            )
            Spacer(Modifier.weight(1f))
            Text("read-only", color = scheme.tertiary, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
        }
        Spacer(Modifier.height(Space.s))
        Box(Modifier.fillMaxWidth().height(1.dp).background(scheme.outlineVariant))
        Spacer(Modifier.height(Space.s))
        DeskTableHead("DATE" to 0.9f, "CATEGORY" to 1.1f, "NOTE" to 2.4f, "AMOUNT" to 1f)
        if (entries.isEmpty()) {
            Text("No entries yet.", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = Space.m))
        } else {
            entries.forEach { e ->
                val name = categoryLabel(store, e.cat)
                DeskTableRow(
                    listOf(
                        shortDate(e.date) to 0.9f,
                        name to 1.1f,
                        e.note.ifBlank { "—" } to 2.4f,
                        "${if (e.type == "income") "+" else "−"}RM ${rm(e.amount)}" to 1f,
                    ),
                    colors = listOf(scheme.onSurfaceVariant, scheme.onSurface, scheme.onSurface, if (e.type == "income") scheme.tertiary else scheme.secondary),
                )
            }
        }
    }
}

@Composable
private fun DeskRunningCard(store: Store, month: String, showTotal: Boolean = true) {
    val scheme = MaterialTheme.colorScheme
    var allTime by remember(month) { mutableStateOf(false) }
    val points = remember(store, month, allTime) {
        if (allTime) allTimeRunningPoints(store) else monthRunningPoints(store, month)
    }
    val total = points.lastOrNull()?.total ?: 0L
    Row(verticalAlignment = Alignment.CenterVertically) {
        DeskLabel(if (allTime) "ALL-TIME RECORDED NET" else "RUNNING NET · ${monthLabel(month).uppercase()}")
        Spacer(Modifier.weight(1f))
        Text(
            if (allTime) "${points.size} months" else "${points.size} days",
            color = scheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall,
        )
    }
    Spacer(Modifier.height(Space.xs))
    Row(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
        DeskFilter("Month", !allTime) { allTime = false }
        DeskFilter("All time", allTime) { allTime = true }
    }
    Spacer(Modifier.height(Space.s))
    if (showTotal) {
        Row(verticalAlignment = Alignment.Bottom) {
            DeskMoney(
                "RM ${rm(total)}",
                if (total < 0) scheme.error else scheme.onSurface,
                MaterialTheme.typography.headlineLarge,
            )
        }
    }
    Spacer(Modifier.height(Space.s))
    if (points.isEmpty()) {
        Text("No activity yet.", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        return
    }
    val chartDescription = "Running net chart, ${points.size} " +
        (if (allTime) "months" else "days") +
        ", from ${points.first().label} RM ${rm(points.first().total)} to ${points.last().label} RM ${rm(points.last().total)}"
    Canvas(
        Modifier.fillMaxWidth().height(150.dp).semantics { contentDescription = chartDescription },
    ) {
        val topPad = 8.dp.toPx()
        val zeroY = size.height * 0.78f
        val usable = zeroY - topPad
        val maxAbs = maxOf(1.0, points.maxOfOrNull { kotlin.math.abs(it.total) / 100.0 } ?: 1.0) * 1.06
        val step = size.width / points.size.coerceAtLeast(1)
        fun yFor(cents: Long): Float =
            (zeroY - (cents / 100.0 / maxAbs * usable).toFloat()).coerceIn(topPad, zeroY)
        val offsets = points.mapIndexed { i, p -> Offset((i + 0.5f) * step, yFor(p.total)) }
        drawLine(scheme.outlineVariant, Offset(0f, zeroY), Offset(size.width, zeroY), 1.dp.toPx())
        val path = Path().apply {
            moveTo(0f, zeroY)
            offsets.forEach { lineTo(it.x, it.y) }
        }
        val area = Path().apply {
            addPath(path)
            lineTo(offsets.last().x, zeroY)
            lineTo(0f, zeroY)
            close()
        }
        drawPath(area, Brush.verticalGradient(listOf(scheme.primary.copy(alpha = 0.18f), Color.Transparent), startY = topPad, endY = zeroY))
        drawPath(path, scheme.primary, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        val end = offsets.last()
        drawCircle(scheme.primary, radius = 6.dp.toPx(), center = end)
        drawCircle(scheme.surface, radius = 2.dp.toPx(), center = end)
        // Highlight the current total as the one meaningful point on the line.
        drawCircle(if (total < 0) scheme.error else scheme.primary, radius = 8.dp.toPx(), center = end, style = Stroke(width = 1.dp.toPx()))
    }
    Spacer(Modifier.height(Space.xs))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        val labels = if (points.size <= 2) points.map { it.label } else listOf(points.first().label, points[points.size / 2].label, points.last().label)
        labels.forEach { label ->
            Text(
                if (allTime) monthLabel(label).split(" ").first() else label.takeLast(5),
                color = scheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}


// ---------------------------------------------------------------------------
// Ledger
// ---------------------------------------------------------------------------

@Composable
internal fun DeskLedger(repo: BakiRepo, month: String) {
    val store = repo.store
    val scheme = MaterialTheme.colorScheme
    var query by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(repo.config.deskQuery) }
    var cat by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(repo.config.deskCategory) }
    var sort by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(repo.config.deskSort) }
    androidx.compose.runtime.LaunchedEffect(query, cat, sort) { repo.setDeskLedgerPrefs(query, cat, sort) }
    val cats = remember(store) { store.settings.cats.catsOut }
    val monthEntries = remember(store, month) { store.entries.filter { it.date.startsWith(month) } }
    val filtered = monthEntries
        .filter { cat == "all" || it.cat == cat }
        .filter { query.isBlank() || (it.note + " " + categoryLabel(store, it.cat)).contains(query, ignoreCase = true) }
    val chronological = remember(monthEntries) {
        var running = 0L
        monthEntries.sortedWith(compareBy<Entry> { it.date }.thenBy { it.loggedAt.ifBlank { it.updated } })
            .associate { e ->
                running += if (e.type == "income") e.amount else -e.amount
                e.id to running
            }
    }
    val rows = filtered.sortedWith(
        when (sort) {
            "Amount" -> compareByDescending<Entry> { it.amount }
            "Category" -> compareBy<Entry> { it.cat }.thenByDescending { it.date }
            else -> compareByDescending<Entry> { it.date }.thenByDescending { it.loggedAt }
        },
    )
    val out = filtered.filter { it.type != "income" }.sumOf { it.amount }

    Column(Modifier.fillMaxSize().padding(horizontal = Space.xl)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "/ 01  LEDGER",
                color = scheme.primary,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.4.sp,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            )
            Spacer(Modifier.weight(1f))
            Text("${rows.size} entries · RM ${rm(out)} out", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"))
            Spacer(Modifier.width(Space.m))
            Text("READ-ONLY", color = scheme.tertiary, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
        }
        Spacer(Modifier.height(Space.s))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.m)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Filter notes or categories") },
                singleLine = true,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.widthIn(max = 360.dp),
            )
        }
        Spacer(Modifier.height(Space.s))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
            DeskFilter("All", cat == "all") { cat = "all" }
            cats.filter { c -> monthEntries.any { it.cat == c.id } }.forEach { c ->
                DeskFilter(c.name, cat == c.id) { cat = c.id }
            }
        }
        Spacer(Modifier.height(Space.xs))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.xs), verticalAlignment = Alignment.CenterVertically) {
            Text("SORT", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
            listOf("Newest", "Amount", "Category").forEach { label ->
                DeskFilter(label, sort == label) { sort = label }
            }
        }
        Spacer(Modifier.height(Space.s))
        Column(Modifier.fillMaxWidth().weight(1f)) {
            DeskTableHead(
                "DATE" to 0.85f, "CATEGORY" to 1.1f, "NOTE" to 2.4f,
                "AMOUNT" to 1f, "RUNNING" to 1.2f,
            )
            LazyColumn(Modifier.fillMaxSize()) {
                if (rows.isEmpty()) item {
                    Text("No entries match this view.", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = Space.l))
                }
                items(rows, key = { it.id }) { e ->
                    DeskTableRow(
                        listOf(
                            shortDate(e.date) to 0.85f,
                            categoryLabel(store, e.cat) to 1.1f,
                            e.note.ifBlank { "—" } to 2.4f,
                            (if (e.type == "income") "+" else "−") + "RM " + rm(e.amount) to 1f,
                            "RM " + rm(chronological[e.id] ?: 0L) to 1.2f,
                        ),
                        colors = listOf(
                            scheme.onSurfaceVariant, scheme.onSurface, scheme.onSurface,
                            if (e.type == "income") scheme.tertiary else scheme.onSurface,
                            scheme.onSurfaceVariant,
                        ),
                        alignments = listOf(TextAlign.Start, TextAlign.Start, TextAlign.Start, TextAlign.End, TextAlign.End),
                    )
                }
            }
        }
    }
}

@Composable
private fun DeskFilter(label: String, selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Text(
        label,
        color = if (selected) scheme.onPrimary else scheme.onSurfaceVariant,
        style = MaterialTheme.typography.labelLarge.copy(fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium),
        modifier = Modifier
            .clip(CircleShape)
            .background(if (selected) scheme.primary else scheme.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = Space.m, vertical = 9.dp),
    )
}

// ---------------------------------------------------------------------------
// Shifts
// ---------------------------------------------------------------------------

@Composable
internal fun DeskShifts(repo: BakiRepo, month: String) {
    val store = repo.store
    val scheme = MaterialTheme.colorScheme
    val shifts = remember(store, month) {
        store.shifts.filter { it.date.startsWith(month) }.sortedByDescending { "${it.date} ${it.start}" }
    }
    var selectedId by remember(month) { mutableStateOf(shifts.firstOrNull()?.id) }
    val selected = shifts.firstOrNull { it.id == selectedId } ?: shifts.firstOrNull()

    if (shifts.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(horizontal = Space.xl, vertical = Space.m)) {
            DeskEmpty("No shifts this month", "Shifts recorded on the phone will appear here as a read-only ledger.")
        }
        return
    }

    Row(Modifier.fillMaxSize().padding(horizontal = Space.xl, vertical = Space.s), horizontalArrangement = Arrangement.spacedBy(Space.l)) {
        Column(Modifier.weight(1.05f).fillMaxHeight()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "/ 01  SHIFTS",
                    color = scheme.primary,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.4.sp,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                )
                Spacer(Modifier.weight(1f))
                Text("${shifts.size} shifts · ${"%.1f".format(shifts.sumOf { it.hours })}h", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"))
            }
            Spacer(Modifier.height(Space.s))
            Box(Modifier.fillMaxWidth().height(1.dp).background(scheme.outlineVariant))
            Spacer(Modifier.height(Space.xs))
            LazyColumn(Modifier.fillMaxSize()) {
                val weeks = shifts.groupBy { weekStartOf(it.date) }.toList().sortedByDescending { it.first }
                weeks.forEach { (week, weekShifts) ->
                    item(key = "head-$week") {
                        Row(Modifier.fillMaxWidth().padding(top = Space.m, bottom = Space.xs), verticalAlignment = Alignment.Bottom) {
                            Text(
                                "WEEK OF ${prettyDate(week)}",
                                color = scheme.onSurface,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 1.1.sp,
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            )
                            Spacer(Modifier.weight(1f))
                            Text(
                                "${weekShifts.size} shifts · ${"%.1f".format(weekShifts.sumOf { it.hours })}h · ${"%.0f".format(weekShifts.sumOf { shiftKm(it, store.settings.profit) })} km",
                                color = scheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                            )
                        }
                        DeskTableHead("DATE" to 1.1f, "START" to 0.8f, "HOURS" to 0.7f, "KM" to 0.7f, "NET" to 1f)
                    }
                    items(weekShifts, key = { it.id }) { sh ->
                        val metrics = shiftMetrics(sh, store.settings.profit)
                        DeskTableRow(
                            listOf(
                                shortDate(sh.date) to 1.1f,
                                sh.start to 0.8f,
                                "%.1f".format(sh.hours) to 0.7f,
                                "%.0f".format(metrics.km) to 0.7f,
                                "RM ${rm(metrics.net)}" to 1f,
                            ),
                            colors = listOf(scheme.onSurfaceVariant, scheme.onSurfaceVariant, scheme.onSurface, scheme.onSurfaceVariant, if (metrics.net < 0) scheme.error else scheme.onSurface),
                            alignments = listOf(TextAlign.Start, TextAlign.Start, TextAlign.End, TextAlign.End, TextAlign.End),
                            selected = sh.id == selected?.id,
                            onClick = { selectedId = sh.id },
                        )
                    }
                    item(key = "space-$week") { Spacer(Modifier.height(Space.s)) }
                }
            }
        }
        if (selected != null) {
            DeskShiftDetail(repo, selected, Modifier.weight(1.25f).fillMaxHeight())
        }
    }
}

@Composable
private fun DeskShiftDetail(repo: BakiRepo, shift: Shift, modifier: Modifier) {
    val store = repo.store
    val scheme = MaterialTheme.colorScheme
    val metrics = remember(store, shift) { shiftMetrics(shift, store.settings.profit) }
    var route by remember(shift.id) { mutableStateOf<ShiftRoute?>(null) }
    LaunchedEffect(shift.id) { route = runCatching { RouteStore.route(shift.id) }.getOrNull() }

    Column(modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Space.m)) {
        DeskCard(Modifier.fillMaxWidth()) {
            DeskLabel("NET · ${prettyDate(shift.date)}")
            Spacer(Modifier.height(Space.xs))
            DeskMoney("RM ${rm(metrics.net)}", if (metrics.net < 0) scheme.error else scheme.onSurface, MaterialTheme.typography.displayLarge)
            Spacer(Modifier.height(Space.m))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.xl)) {
                DeskMetric("HOURS", "%.1f".format(shift.hours))
                DeskMetric("KM", "%.1f".format(metrics.km))
                DeskMetric("GROSS", "RM ${rm(shift.gross)}")
                DeskMetric("RM / H", "RM ${rm(if (shift.hours > 0) metrics.net / shift.hours else 0.0)}")
            }
            Spacer(Modifier.height(Space.s))
            Text("Started ${shift.start} · read-only on the desk", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        }
        DeskCard(Modifier.fillMaxWidth()) {
            DeskLabel("PAYOUTS")
            Spacer(Modifier.height(Space.s))
            if (shift.payouts.isEmpty()) {
                Text("No payout breakdown recorded for this shift.", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            } else {
                shift.payouts.forEachIndexed { index, payout ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        Text(clockOf(payout.at), color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.weight(1f))
                        Text("RM ${rm(payout.c)}", color = scheme.onSurface, style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum", fontWeight = FontWeight.Bold))
                    }
                    if (index < shift.payouts.lastIndex) HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.4f))
                }
            }
        }
        if (route != null && route!!.points.size >= 2) {
            DeskCard(Modifier.fillMaxWidth()) {
                DeskLabel("ROUTE")
                Spacer(Modifier.height(Space.s))
                RouteMap(
                    routes = listOf(MapRoute(route!!, scheme.tertiary, "Shift ${shift.start}")),
                    modifier = Modifier.fillMaxWidth().height(260.dp),
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Routes
// ---------------------------------------------------------------------------

@Composable
internal fun DeskRoutes(repo: BakiRepo, month: String) {
    val store = repo.store
    val scheme = MaterialTheme.colorScheme
    val shifts = remember(store, month) {
        store.shifts.filter { it.date.startsWith(month) }.sortedByDescending { "${it.date} ${it.start}" }
    }
    var routes by remember(month) { mutableStateOf<Map<String, ShiftRoute>>(emptyMap()) }
    var selectedId by remember(month) { mutableStateOf<String?>(null) }
    var loading by remember(month) { mutableStateOf(true) }
    var reload by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(month, reload) {
        loading = true
        runCatching { repo.pullRoutes("$month-01") }
        val found = mutableMapOf<String, ShiftRoute>()
        shifts.forEach { sh ->
            RouteStore.route(sh.id)?.takeIf { it.points.size >= 2 }?.let { found[sh.id] = it }
        }
        routes = found
        loading = false
        if (selectedId == null || selectedId !in found) selectedId = found.keys.firstOrNull() ?: shifts.firstOrNull()?.id
    }

    val selectedShift = shifts.firstOrNull { it.id == selectedId }
    val selectedRoute = selectedId?.let { routes[it] }

    Row(Modifier.fillMaxSize().padding(horizontal = Space.xl, vertical = Space.s), horizontalArrangement = Arrangement.spacedBy(Space.m)) {
        Surface(Modifier.width(340.dp).fillMaxHeight(), shape = MaterialTheme.shapes.extraLarge, color = scheme.surfaceContainerLow) {
            Column(Modifier.fillMaxSize().padding(Space.m)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DeskLabel("DRIVES · ${routes.size}/${shifts.size}")
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { reload++ }, enabled = !loading) {
                        Text(if (loading) "Pulling…" else "Pull", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                    }
                }
                Spacer(Modifier.height(Space.xs))
                if (shifts.isEmpty()) {
                    Text("No shifts this month.", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                } else {
                    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                        items(shifts, key = { it.id }) { sh ->
                            val hasRoute = routes.containsKey(sh.id)
                            val active = sh.id == selectedId
                            Row(
                                Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large)
                                    .background(if (active) scheme.primaryContainer else Color.Transparent)
                                    .clickable { selectedId = sh.id }
                                    .padding(horizontal = Space.m, vertical = Space.s),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(prettyDate(sh.date), color = if (active) scheme.onPrimaryContainer else scheme.onSurface, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                                    Text("${sh.start} · ${"%.0f".format(shiftKm(sh, store.settings.profit))} km", color = if (active) scheme.onPrimaryContainer.copy(alpha = 0.75f) else scheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                                }
                                Text(if (hasRoute) "route" else "—", color = if (hasRoute) scheme.tertiary else scheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                            }
                        }
                    }
                }
            }
        }
        Surface(Modifier.weight(1f).fillMaxHeight(), shape = MaterialTheme.shapes.extraLarge, color = scheme.surfaceContainerLow) {
            if (selectedShift == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Choose a shift to review its drive.", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
                }
            } else if (selectedRoute == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(Space.xl)) {
                        DeskLabel("NO ROUTE")
                        Spacer(Modifier.height(Space.s))
                        Text("${prettyDate(selectedShift.date)} · ${selectedShift.start}", color = scheme.onSurface, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                        Spacer(Modifier.height(Space.xs))
                        Text("Location may have been off, or the route has not been pulled yet.", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            } else {
                Column(Modifier.fillMaxSize().padding(Space.m)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Column(Modifier.weight(1f)) {
                            DeskLabel("ROUTE REVIEW")
                            Text(
                                prettyDate(selectedShift.date) + " · " + selectedShift.start,
                                color = scheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            )
                        }
                        Spacer(Modifier.width(Space.m))
                        DeskMetric("DISTANCE", "%.1f km".format(routeMetres(selectedRoute.points) / 1000.0))
                        Spacer(Modifier.width(Space.l))
                        DeskMetric("TIME", "%.1f h".format(selectedShift.hours))
                    }
                    Spacer(Modifier.height(Space.m))
                    RouteMap(
                        routes = listOf(MapRoute(selectedRoute, scheme.tertiary, "Shift ${selectedShift.start}")),
                        modifier = Modifier.fillMaxWidth().weight(1f),
                    )
                    Spacer(Modifier.height(Space.s))
                    Text(
                        "Read-only route from the phone · RM ${rm(shiftMetrics(selectedShift, store.settings.profit).let { m -> if (m.km > 0.01) m.net / m.km else 0.0 })}/km · pull again if a drive is missing.",
                        color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Compare / Year
// ---------------------------------------------------------------------------

@Composable
internal fun DeskCompare(repo: BakiRepo, month: String) {
    val store = repo.store
    val scheme = MaterialTheme.colorScheme
    var a by remember(month) { mutableStateOf(month) }
    var b by remember(month) { mutableStateOf(addDays("$month-01", -1).take(7)) }
    val rows = remember(store, a, b) { compareMonths(store, a, b) }
    Column(Modifier.fillMaxSize().padding(horizontal = Space.xl, vertical = Space.s)) {
        Text(
            "/ 01  COMPARE",
            color = scheme.primary,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 1.4.sp,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
        )
        Spacer(Modifier.height(Space.s))
        Row(verticalAlignment = Alignment.Bottom) {
            Column {
                Text("/ PERIOD A", color = scheme.onSurfaceVariant, fontFamily = FontFamily.Monospace, letterSpacing = 1.1.sp, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                DeskMonthStepper(a, { a = it }, compact = true)
            }
            Spacer(Modifier.weight(1f))
            Text("VERSUS", color = scheme.onSurfaceVariant, fontFamily = FontFamily.Monospace, letterSpacing = 1.4.sp, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End) {
                Text("/ PERIOD B", color = scheme.onSurfaceVariant, fontFamily = FontFamily.Monospace, letterSpacing = 1.1.sp, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                DeskMonthStepper(b, { b = it }, compact = true)
            }
        }
        Spacer(Modifier.height(Space.m))
        Box(Modifier.fillMaxWidth().height(1.dp).background(scheme.outlineVariant))
        DeskTableHead("METRIC" to 1.2f, monthLabel(a).uppercase() to 1f, monthLabel(b).uppercase() to 1f, "CHANGE" to 0.8f)
        LazyColumn(Modifier.fillMaxSize()) {
            items(rows, key = { it.label }) { row ->
                DeskTableRow(
                    listOf(row.label to 1.2f, row.a to 1f, row.b to 1f, (row.changeLabel ?: "—") to 0.8f),
                    colors = listOf(
                        scheme.onSurface, scheme.onSurface, scheme.onSurface,
                        when (row.good) { true -> scheme.tertiary; false -> scheme.error; null -> scheme.onSurfaceVariant },
                    ),
                )
            }
        }
    }
}

@Composable
internal fun DeskYear(repo: BakiRepo, month: String) {
    val store = repo.store
    val scheme = MaterialTheme.colorScheme
    val year = month.take(4).toIntOrNull() ?: todayIso().take(4).toIntOrNull() ?: 2026
    val months = remember(store, year) {
        (1..12).map { m ->
            val label = "%04d-%02d".format(year, m)
            label to monthTotals(store, label)
        }
    }
    val yearShifts = remember(store, year) { store.shifts.filter { it.date.startsWith(year.toString()) } }
    val net = months.sumOf { it.second.net }
    val peak = months.maxOfOrNull { kotlin.math.abs(it.second.net) }?.coerceAtLeast(1L) ?: 1L
    var statementOpen by remember { mutableStateOf(false) }
    if (statementOpen) DeskStatementPreview(store, month) { statementOpen = false }

    Column(Modifier.fillMaxSize().padding(horizontal = Space.xl, vertical = Space.s)) {
        Text(
            "/ 01  YEAR",
            color = scheme.primary,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 1.4.sp,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
        )
        Spacer(Modifier.height(Space.s))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.xl), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1.2f)) {
                DeskLabel("$year AT A GLANCE")
                Spacer(Modifier.height(Space.xs))
                DeskMoney("RM ${rm(net)} net", if (net < 0) scheme.error else scheme.onSurface, MaterialTheme.typography.displayMedium)
                Spacer(Modifier.height(Space.s))
                Text("in RM ${rm(months.sumOf { it.second.income })} · out RM ${rm(months.sumOf { it.second.expense })}", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            }
            Column(Modifier.weight(0.65f)) {
                DeskLabel("THE YEAR IN MOTION")
                Spacer(Modifier.height(Space.m))
                Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                    DeskMetric("HOURS", "%.0f".format(yearShifts.sumOf { it.hours }))
                    DeskMetric("KM", "%.0f".format(yearShifts.sumOf { shiftKm(it, store.settings.profit) }))
                    DeskMetric("SHIFTS", yearShifts.size.toString())
                }
            }
            Column(Modifier.weight(0.85f)) {
                DeskLabel("STATEMENT")
                Spacer(Modifier.height(Space.xs))
                Text(monthLabel(month), color = scheme.onSurface, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                Spacer(Modifier.height(Space.s))
                FilledTonalButton(onClick = { statementOpen = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Preview", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                    TextButton(onClick = { PlatformEffects.printStatement(statementHtml(store, month)) }, modifier = Modifier.weight(1f)) {
                        Text("Print", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                    }
                    TextButton(onClick = { PlatformEffects.exportFile("baki-$year.csv", "text/csv", annualCsv(store, year)) }, modifier = Modifier.weight(1f)) {
                        Text("CSV", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                    }
                    TextButton(onClick = { PlatformEffects.printStatement(annualSummaryHtml(store, year)) }, modifier = Modifier.weight(1f)) {
                        Text("PDF", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                    }
                }
            }
        }
        Spacer(Modifier.height(Space.m))
        Box(Modifier.fillMaxWidth().height(1.dp).background(scheme.outlineVariant))
        Spacer(Modifier.height(Space.m))
        VehicleWearCard(store, Modifier.fillMaxWidth())
        Spacer(Modifier.height(Space.s))
        DeskLabel("MONTHS")
        Spacer(Modifier.height(Space.xs))
        LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
            items(months) { (label, t) ->
                val fraction = (kotlin.math.abs(t.net).toFloat() / peak).coerceIn(0.03f, 1f)
                Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(monthLabel(label).substringBefore(' '), color = if (label == month) scheme.primary else scheme.onSurface, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = if (label == month) FontWeight.Bold else FontWeight.Medium), modifier = Modifier.width(120.dp))
                    Box(Modifier.weight(1f).height(10.dp).clip(CircleShape).background(scheme.surfaceContainerHigh)) {
                        Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().clip(CircleShape).background(if (t.net < 0) scheme.secondary else scheme.primary))
                    }
                    Text("RM ${rm(t.net)}", color = if (t.net < 0) scheme.secondary else scheme.onSurface, style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum", fontWeight = FontWeight.Bold), modifier = Modifier.width(150.dp).padding(start = Space.m), maxLines = 1)
                }
                HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.4f))
            }
        }
    }
}

@Composable
private fun DeskStatementPreview(store: Store, month: String, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            Modifier.fillMaxSize().padding(Space.xl),
            verticalArrangement = Arrangement.spacedBy(Space.s),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                FilledTonalButton(onClick = { PlatformEffects.printStatement(statementHtml(store, month)) }) { Text("Print / PDF") }
                Spacer(Modifier.width(Space.s))
                TextButton(onClick = onDismiss) { Text("Close") }
            }
            DeskStatementPaper(store = store, month = month, modifier = Modifier.weight(1f).fillMaxWidth())
        }
    }
}

@Composable
internal fun DeskStatementPaper(
    store: Store,
    month: String,
    modifier: Modifier = Modifier.fillMaxHeight().widthIn(max = 1040.dp),
) {
    val scheme = MaterialTheme.colorScheme
    val t = monthTotals(store, month)
    val shifts = store.shifts.filter { it.date.startsWith(month) }.sortedBy { it.date }
    val entries = store.entries.filter { it.date.startsWith(month) }.sortedBy { it.date }

    Surface(
        modifier,
        shape = ReceiptShape(),
        color = scheme.surface,
        border = BorderStroke(1.dp, scheme.outline.copy(alpha = 0.55f)),
    ) {
        Column(Modifier.fillMaxSize().padding(Space.xl)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "/ STATEMENT · ${monthLabel(month).uppercase()}",
                        color = scheme.primary,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.4.sp,
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    )
                    Spacer(Modifier.height(Space.xs))
                    Text(
                        "NET",
                        color = scheme.onSurfaceVariant,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    )
                    DeskMoney("RM ${rm(t.net)}", if (t.net < 0) scheme.error else scheme.onSurface, MaterialTheme.typography.displayMedium)
                    Text("${t.entryCount} entries · ${t.shiftCount} shifts · in RM ${rm(t.income)} · out RM ${rm(t.expense)}", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                }
            }
            Spacer(Modifier.height(Space.m))
            ReceiptRule()
            Spacer(Modifier.height(Space.m))
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                Text(
                    "/ 01  SHIFTS",
                    color = scheme.primary,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.2.sp,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                )
                Spacer(Modifier.height(Space.xs))
                DeskTableHead("DATE" to 1f, "START" to 0.8f, "HOURS" to 0.7f, "KM" to 0.7f, "GROSS" to 1f, "NET" to 1f)
                shifts.forEach { sh ->
                    val m = shiftMetrics(sh, store.settings.profit)
                    DeskTableRow(
                        listOf(sh.date to 1f, sh.start to 0.8f, "%.1f".format(sh.hours) to 0.7f, "%.0f".format(sh.km) to 0.7f, "RM ${rm(sh.gross)}" to 1f, "RM ${rm(m.net)}" to 1f),
                    )
                }
                Spacer(Modifier.height(Space.m))
                Text(
                    "/ 02  ENTRIES",
                    color = scheme.primary,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.2.sp,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                )
                Spacer(Modifier.height(Space.xs))
                DeskTableHead("DATE" to 1f, "CATEGORY" to 1f, "NOTE" to 2.4f, "AMOUNT" to 1f)
                entries.forEach { e ->
                    DeskTableRow(
                        listOf(shortDate(e.date) to 1f, categoryLabel(store, e.cat) to 1f, e.note.ifBlank { "—" } to 2.4f, (if (e.type == "income") "+" else "−") + "RM " + rm(e.amount) to 1f),
                        colors = listOf(scheme.onSurfaceVariant, scheme.onSurface, scheme.onSurface, if (e.type == "income") scheme.tertiary else scheme.secondary),
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Settings
// ---------------------------------------------------------------------------

@Composable
internal fun DeskSettings(repo: BakiRepo) {
    val scheme = MaterialTheme.colorScheme
    var url by remember { mutableStateOf(repo.config.serverUrl) }
    var token by remember { mutableStateOf(repo.config.serverToken) }
    var routeKey by remember { mutableStateOf(repo.config.routeSecret) }
    var routeSync by remember { mutableStateOf(repo.config.syncRoutes) }
    var saved by remember { mutableStateOf<String?>(null) }
    var pendingRestore by remember { mutableStateOf<String?>(null) }
    var restoreSummary by remember { mutableStateOf<BackupSummary?>(null) }
    var restoreError by remember { mutableStateOf<String?>(null) }
    var backupPassMode by remember { mutableStateOf<String?>(null) }
    var encryptedRestore by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val pickRestore = rememberJsonImport { text ->
        if (text != null) {
            if (isEncryptedBackup(text)) {
                encryptedRestore = text
                backupPassMode = "restore"
                restoreError = null
            } else {
                val summary = summarizeBackup(text)
                if (summary == null) {
                    restoreError = "That file is not a Baki backup."
                    PlatformEffects.haptic(Haptic.Warn)
                } else {
                    restoreError = null
                    restoreSummary = summary
                    pendingRestore = text
                }
            }
        }
    }

    fun note(text: String) { saved = text }

    Column(
        Modifier.fillMaxSize().padding(horizontal = Space.xl, vertical = Space.s).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Space.m),
    ) {
        DeskCard(Modifier.fillMaxWidth()) {
            DeskLabel("CONNECTION")
            Spacer(Modifier.height(Space.s))
            OutlinedTextField(
                value = url, onValueChange = { url = it }, singleLine = true,
                label = { Text("Server address") }, modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
            )
            Spacer(Modifier.height(Space.s))
            OutlinedTextField(
                value = token, onValueChange = { token = it }, singleLine = true,
                label = { Text("API token (optional)") }, visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large,
            )
            Spacer(Modifier.height(Space.s))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s), verticalAlignment = Alignment.CenterVertically) {
                FilledTonalButton(onClick = {
                    repo.setServer(url, token)
                    scope.launch {
                        runCatching { repo.sync() }
                        note("Connection saved and synced")
                    }
                }, colors = androidx.compose.material3.ButtonDefaults.filledTonalButtonColors(
                    containerColor = scheme.primaryContainer,
                    contentColor = scheme.onPrimaryContainer,
                )) { Text("Save & sync") }
                TextButton(onClick = { scope.launch { runCatching { repo.sync() }; note("Sync requested") } }) { Text("Sync now") }
                Spacer(Modifier.weight(1f))
                Text(
                    "${repo.status} · ${repo.lastSyncAt?.let { clockOf(it) } ?: "not synced"}" +
                        if (repo.lastMergeConflicts > 0) " · ${repo.lastMergeConflicts} overlaps" else "",
                    color = if (repo.lastMergeConflicts > 0) scheme.secondary else scheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (repo.lastMergeConflicts > 0) {
                Spacer(Modifier.height(Space.xs))
                Text(
                    "${repo.lastMergeConflicts} record(s) overlapped on the last pull · newest edit wins.",
                    color = scheme.secondary,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Text(
                "Pull runs before push. Newest edit per record wins; deletions stay deleted.",
                color = scheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        DeskCard(Modifier.fillMaxWidth()) {
            DeskLabel("APPEARANCE")
            Spacer(Modifier.height(Space.s))
            ConnectedChoiceGroup(
                options = listOf("Auto", "Light", "Dark"),
                selected = when (repo.config.themeMode) { "light" -> "Light"; "dark" -> "Dark"; else -> "Auto" },
                onSelect = { repo.setThemeMode(it.lowercase()) },
            )
            Spacer(Modifier.height(Space.s))
            ConnectedChoiceGroup(
                options = listOf("System", "Baki"),
                selected = if (repo.config.colorSource == "baki") "Baki" else "System",
                onSelect = { repo.setColorSource(if (it == "Baki") "baki" else "wallpaper") },
            )
            Spacer(Modifier.height(Space.s))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("High contrast", color = scheme.onSurface, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold))
                    Text("stronger text and outlines", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                }
                Switch(checked = repo.config.highContrast, onCheckedChange = { repo.setHighContrast(it) })
            }
        }
        DeskCard(Modifier.fillMaxWidth()) {
            DeskLabel("ROUTES")
            Spacer(Modifier.height(Space.s))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Sync route traces", color = scheme.onSurface, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold))
                    Text("Routes are sealed end-to-end; the server never sees coordinates.", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                }
                Switch(checked = routeSync, onCheckedChange = { routeSync = it })
            }
            Spacer(Modifier.height(Space.s))
            OutlinedTextField(
                value = routeKey, onValueChange = { routeKey = it }, singleLine = true,
                label = { Text("Shared route key") }, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large,
            )
            Spacer(Modifier.height(Space.s))
            FilledTonalButton(onClick = {
                repo.setRouteSync(routeSync, routeKey)
                note("Route sync saved")
            }, colors = androidx.compose.material3.ButtonDefaults.filledTonalButtonColors(
                containerColor = scheme.primaryContainer,
                contentColor = scheme.onPrimaryContainer,
            )) { Text("Save route settings") }
        }
        DeskCard(Modifier.fillMaxWidth()) {
            DeskLabel("EXPORT")
            Spacer(Modifier.height(Space.s))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s), verticalAlignment = Alignment.CenterVertically) {
                FilledTonalButton(onClick = { PlatformEffects.exportFile("baki-${todayStamp()}.csv", "text/csv", exportCsv(repo.store)) }) { Text("CSV") }
                FilledTonalButton(onClick = {
                    scope.launch {
                        val json = runCatching { backupBundleJson(repo) }.getOrNull()
                        val saved = json != null &&
                            PlatformEffects.exportFile("baki-${todayStamp()}-backup.json", "application/json", json)
                        if (saved && json != null) repo.markBackupExported(json)
                        note(
                            when {
                                json == null -> "Backup could not be created"
                                saved -> "Backup saved to Downloads"
                                else -> "Backup could not be saved to Downloads"
                            },
                        )
                    }
                }, colors = androidx.compose.material3.ButtonDefaults.filledTonalButtonColors(
                    containerColor = scheme.primaryContainer,
                    contentColor = scheme.onPrimaryContainer,
                )) { Text("Backup JSON") }
                FilledTonalButton(onClick = {
                    scope.launch {
                        val routes = runCatching { RouteStore.routesSince(addDays(todayIso(), -30)) }.getOrDefault(emptyList())
                        PlatformEffects.exportFile("baki-${todayStamp()}-routes.gpx", "application/gpx+xml", exportGpx(todayIso(), routes))
                        note("Exported ${routes.size} routes")
                    }
                }) { Text("Routes GPX") }
                FilledTonalButton(
                    onClick = { pickRestore() },
                    colors = androidx.compose.material3.ButtonDefaults.filledTonalButtonColors(
                        containerColor = scheme.primaryContainer,
                        contentColor = scheme.onPrimaryContainer,
                    ),
                ) { Text("Restore backup") }
                FilledTonalButton(onClick = { backupPassMode = "create" }) { Text("Encrypted") }
                Spacer(Modifier.weight(1f))
                saved?.let { Text(it, color = scheme.tertiary, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)) }
            }
            Spacer(Modifier.height(Space.s))
            Text(
                repo.config.lastBackupAt?.let { "Last backup ${ago(nowMillis(), it)} · ${repo.config.lastBackupEntries} entries, ${repo.config.lastBackupShifts} shifts" }
                    ?: "No portable backup yet. Create one before resetting this device.",
                color = if (repo.config.lastBackupAt == null) scheme.secondary else scheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "Restore merges into the local copy and keeps writes off. Desktop never edits ledger records.",
                color = scheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
            restoreError?.let { Text(it, color = scheme.error, style = MaterialTheme.typography.bodyMedium) }
        }
        DeskCard(Modifier.fillMaxWidth()) {
            DeskLabel("PHONE PREVIEW")
            Spacer(Modifier.height(Space.xs))
            Text(
                "Open the interactive phone shell beside the desk. It uses an isolated copy of the ledger for motion and UI testing.",
                color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(Space.s))
            FilledTonalButton(onClick = {
                PlatformEffects.openPhonePreview()
                note("Opening Phone Preview")
            }) { Text("Open Phone Preview", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)) }
        }
        DeskCard(Modifier.fillMaxWidth()) {
            DeskLabel("READ-ONLY DESK")
            Spacer(Modifier.height(Space.xs))
            Text(
                "Financial records are reviewed here, never edited. Add and change entries on the phone. Desktop writing is limited to sync, theme and export settings.",
                color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium,
            )
        }
    }

    backupPassMode?.let { mode ->
        BackupPassphraseDialog(
            title = if (mode == "create") "Create encrypted backup" else "Open encrypted backup",
            confirmLabel = if (mode == "create") "Create" else "Open",
            onConfirm = { passphrase ->
                backupPassMode = null
                if (mode == "create") {
                    scope.launch {
                        val json = runCatching { backupBundleJson(repo) }.getOrNull()
                        val sealed = json?.let { encryptBackup(it, passphrase) }
                        if (json != null && sealed != null) {
                            val saved = PlatformEffects.exportFile("baki-${todayStamp()}-backup.bak", "application/octet-stream", sealed)
                            if (saved) repo.markBackupExported(json)
                            note(if (saved) "Encrypted backup saved to Downloads" else "Encrypted backup could not be saved to Downloads")
                        } else note("Could not create encrypted backup")
                    }
                } else {
                    val text = encryptedRestore
                    val decoded = text?.let { decryptBackup(it, passphrase) }
                    val summary = decoded?.let { summarizeBackup(it) }
                    if (text == null || decoded == null || summary == null) {
                        restoreError = "Wrong passphrase or damaged encrypted backup"
                        encryptedRestore = null
                        PlatformEffects.haptic(Haptic.Warn)
                    } else {
                        encryptedRestore = null
                        restoreSummary = summary
                        pendingRestore = decoded
                    }
                }
            },
            onDismiss = { backupPassMode = null; encryptedRestore = null },
        )
    }

    restoreSummary?.let { summary ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { restoreSummary = null; pendingRestore = null },
            title = { Text("Restore this backup?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
                    Text("Exported ${summary.exportedAt.ifBlank { "unknown time" }}")
                    Text(
                        "${summary.entries} entries · ${summary.shifts} shifts · ${summary.bills} bills · ${summary.statements} statements · ${summary.routes} routes",
                    )
                    Text("This merges into the local copy. Same-id records follow the newest edit; nothing is deleted.")
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val text = pendingRestore
                    restoreSummary = null
                    pendingRestore = null
                    if (text != null) scope.launch {
                        val added = repo.importJson(text)
                        note(if (added > 0) "Restored $added records" else "Backup merged")
                    }
                }) { Text("Restore") }
            },
            dismissButton = { TextButton(onClick = { restoreSummary = null; pendingRestore = null }) { Text("Cancel") } },
        )
    }
}

private fun shortDate(iso: String): String {
    if (iso.length < 10) return iso
    val day = iso.substring(8, 10).trimStart('0').ifBlank { "0" }
    val month = when (iso.substring(5, 7)) {
        "01" -> "Jan"; "02" -> "Feb"; "03" -> "Mar"; "04" -> "Apr"
        "05" -> "May"; "06" -> "Jun"; "07" -> "Jul"; "08" -> "Aug"
        "09" -> "Sep"; "10" -> "Oct"; "11" -> "Nov"; else -> "Dec"
    }
    return "$day $month"
}
