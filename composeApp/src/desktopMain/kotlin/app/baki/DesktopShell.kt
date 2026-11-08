package app.baki

import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** The desktop's proportions. Everything derives from these, not from literals in screens. */
private val RailWidth = 84.dp
private val Gutter = 32.dp
private val ToolbarHeight = 64.dp
private val Rail2 = 320.dp

@Composable
actual fun DesktopRoot(repo: BakiRepo): Boolean {
    val store = repo.store
    val baki = LocalBaki.current
    val neu = rememberNeu()
    // BAKI_VIEW lets the design be captured deterministically
    val preview = remember { System.getenv("BAKI_VIEW").orEmpty() }
    var month by remember { mutableStateOf(todayIso().substring(0, 7)) }
    var tab by remember {
        mutableStateOf(
            when (preview) {
                "entries" -> 1
                "shifts" -> 2
                "routes" -> 3
                "compare" -> 4
                "year" -> 5
                else -> 0
            },
        )
    }
    var statement by remember { mutableStateOf(preview == "statement") }
    var settingsOpen by remember { mutableStateOf(preview == "settings") }
    val scope = rememberCoroutineScope()
    val rootFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        // capture/test hook: configure route sync without touching the encrypted config file
        System.getenv("BAKI_ROUTE_SECRET")?.takeIf { it.isNotBlank() }?.let { repo.setRouteSync(true, it) }
        System.getenv("BAKI_PALETTE")?.takeIf { it.isNotBlank() }?.let { repo.setPalette(it) }
        System.getenv("BAKI_MODE")?.takeIf { it.isNotBlank() }?.let { repo.setThemeMode(it) }
        repo.start()
        runCatching { rootFocus.requestFocus() }
    }

    // the desk reads and prints; it never creates, edits or deletes a record
    DesktopActions.exportCsv = {
        PlatformEffects.exportFile("baki-${todayStamp()}.csv", "text/csv", exportCsv(repo.store))
    }
    DesktopActions.statement = { statement = true }
    DesktopActions.sync = { scope.launch { repo.sync() } }
    DesktopActions.applyTheme = { repo.setPalette(it) }

    androidx.compose.runtime.CompositionLocalProvider(
        androidx.compose.foundation.text.selection.LocalTextSelectionColors provides
            androidx.compose.foundation.text.selection.TextSelectionColors(
                handleColor = neu.accent,
                backgroundColor = neu.accent.copy(alpha = 0.28f),
            ),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(neu.base)
                .focusRequester(rootFocus)
                .focusable()
                .onPreviewKeyEvent { e ->
                    if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when {
                        e.isCtrlPressed && e.key == Key.DirectionLeft -> { month = shiftMonth(month, -1); true }
                        e.isCtrlPressed && e.key == Key.DirectionRight -> { month = shiftMonth(month, 1); true }
                        e.isCtrlPressed && e.key == Key.F -> { tab = 1; true }
                        e.key == Key.Escape -> { statement = false; settingsOpen = false; true }
                        else -> false
                    }
                },
        ) {
            Column(Modifier.fillMaxSize().padding(horizontal = Gutter, vertical = Space.l)) {
                Header(
                    neu = neu,
                    month = month,
                    status = repo.status,
                    tab = tab,
                    onTab = { tab = it },
                    onPrev = { month = shiftMonth(month, -1) },
                    onNext = { month = shiftMonth(month, 1) },
                    onStatement = { statement = true },
                    onRefresh = { scope.launch { repo.sync() } },
                    onSettings = { settingsOpen = true },
                )
                Spacer(Modifier.height(Space.xxl))
                Box(Modifier.weight(1f)) {
                    // the scroll viewport must clip; this fade makes the cut invisible
                    Box(
                        Modifier.fillMaxWidth().height(18.dp).align(Alignment.TopCenter)
                            .background(
                                androidx.compose.ui.graphics.Brush.verticalGradient(
                                    listOf(neu.base, neu.base.copy(alpha = 0f)),
                                ),
                            ),
                    )
                    // no crossfade: a fade composites two embossed pages, so the shadows visibly
                    // "settle" as the outgoing layer fades. Neumorphism reads better as an instant swap.
                    when (tab) {
                        0 -> Overview(store, month) { tab = 1 }
                        1 -> EntriesView(store, month)
                        2 -> ShiftsView(store, month)
                        3 -> RoutesView(repo, month, neu)
                        4 -> CompareView(store, month, neu)
                        else -> YearView(store) { month = it; tab = 0 }
                    }
                }
            }
        }

        if (statement) StatementPreview(store, month) { statement = false }
        if (settingsOpen) DesktopSettings(repo) { settingsOpen = false }
    }
    return true
}

/** Actions the window and the rail can trigger. */
object DesktopActions {
    var exportCsv: () -> Unit = {}
    var statement: () -> Unit = {}
    var sync: () -> Unit = {}
    var applyTheme: (String) -> Unit = { }
}

// ------------------------------------------------------------------ chrome

@Composable
private fun Header(
    neu: Neu,
    month: String,
    status: String,
    tab: Int,
    onTab: (Int) -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onStatement: () -> Unit,
    onRefresh: () -> Unit,
    onSettings: () -> Unit,
) {
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
        val wide = maxWidth >= 1180.dp
        if (wide) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                HeaderIdentity(neu)
                MonthStepper(neu, month, onPrev, onNext)
                Spacer(Modifier.width(Space.l))
                NeuSegmented(
                    neu,
                    listOf(0 to "Overview", 1 to "Entries", 2 to "Shifts", 3 to "Routes", 4 to "Compare", 5 to "Year"),
                    tab,
                    onSelect = onTab,
                )
                Spacer(Modifier.weight(1f))
                SyncBadge(neu, status)
                Spacer(Modifier.width(Space.l))
                NeuButton(neu, "Statement", onClick = onStatement)
                Spacer(Modifier.width(Space.s))
                NeuButton(neu, "Refresh", primary = true, onClick = onRefresh)
                Spacer(Modifier.width(Space.s))
                SettingsButton(neu, onSettings)
            }
        } else {
            // narrow: identity and actions on one line, navigation on its own line
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    HeaderIdentity(neu)
                    MonthStepper(neu, month, onPrev, onNext)
                    Spacer(Modifier.weight(1f))
                    SyncBadge(neu, status)
                    Spacer(Modifier.width(Space.m))
                    NeuButton(neu, "Refresh", primary = true, onClick = onRefresh)
                    Spacer(Modifier.width(Space.s))
                    SettingsButton(neu, onSettings)
                }
                Spacer(Modifier.height(Space.m))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    NeuSegmented(
                        neu,
                        listOf(0 to "Overview", 1 to "Entries", 2 to "Shifts", 3 to "Routes", 4 to "Compare", 5 to "Year"),
                        tab,
                        onSelect = onTab,
                    )
                    Spacer(Modifier.weight(1f))
                    NeuButton(neu, "Statement", onClick = onStatement)
                }
            }
        }
    }
}

@Composable
private fun HeaderIdentity(neu: Neu) {
    Text("Baki", color = neu.ink, style = BakiType.title)
    Spacer(Modifier.width(Space.xl))
}

@Composable
private fun MonthStepper(neu: Neu, month: String, onPrev: () -> Unit, onNext: () -> Unit) {
    // one raised object: a flat chevron at each end and a recessed display in the middle
    Row(
        Modifier.raised(neu, corner = Radius.pill, offset = Elevation.control)
            .padding(horizontal = Space.xs, vertical = Space.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Tip("Previous month") {
            NeuIconButton(neu, Modifier.size(30.dp), onClick = onPrev) {
                Icon(
                    BakiIcons.ArrowRight, contentDescription = "Previous month",
                    tint = neu.ink, modifier = Modifier.size(IconSize.inline).rotate180(),
                )
            }
        }
        Box(
            Modifier.height(30.dp)
                .inset(neu, corner = Radius.small, offset = 2.dp)
                .padding(horizontal = Space.m),
            contentAlignment = Alignment.Center,
        ) {
            Text(monthLabel(month), color = neu.ink, style = BakiType.labelNumeric)
        }
        Tip("Next month") {
            NeuIconButton(neu, Modifier.size(30.dp), onClick = onNext) {
                Icon(
                    BakiIcons.ArrowRight, contentDescription = "Next month",
                    tint = neu.ink, modifier = Modifier.size(IconSize.inline),
                )
            }
        }
    }
}

@Composable
private fun SyncBadge(neu: Neu, status: String) {
    Row(
        Modifier.raised(neu, corner = Radius.pill, offset = Elevation.control)
            .padding(horizontal = Space.m, vertical = Space.xs + 1.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(7.dp).clip(RoundedCornerShape(Radius.pill))
                .background(if (status == "synced") neu.accent else neu.danger),
        )
        Spacer(Modifier.width(Space.s))
        Text(status, color = neu.ink, style = BakiType.caption)
    }
}

@Composable
private fun SettingsButton(neu: Neu, onSettings: () -> Unit) {
    Tip("Settings") {
        NeuIconButton(neu, Modifier.size(36.dp), onClick = onSettings) {
            Icon(BakiIcons.Settings, contentDescription = "Settings", tint = neu.ink, modifier = Modifier.size(IconSize.inline))
        }
    }
}

private fun Modifier.rotate180(): Modifier = this.then(Modifier.rotate(180f))

/** Tooltip surface: a small raised chip, same material as everything else. */
@OptIn(ExperimentalComposeUiApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun Tip(text: String, content: @Composable () -> Unit) {
    val neu = rememberNeu()
    TooltipArea(
        tooltip = {
            Box(
                Modifier.elevated(neu, corner = Radius.small, drop = Elevation.floating)
                    .clip(RoundedCornerShape(Radius.small))
                    .background(neu.panel)
                    .padding(horizontal = Space.s, vertical = Space.xs),
            ) { Text(text, color = neu.ink, style = BakiType.caption) }
        },
        delayMillis = 450,
    ) { content() }
}

// ----------------------------------------------------------------- overview

@Composable
private fun Overview(
    store: Store,
    month: String,
    onOpen: () -> Unit,
) {
    val baki = LocalBaki.current
    val neu = rememberNeu()
    val t = remember(store, month) { monthTotals(store, month) }
    val entries = remember(store, month) {
        store.entries.filter { it.date.startsWith(month) }.sortedByDescending { it.date }
    }
    val week = remember(store) { latestWeekWithData(store, todayIso()) }
    val streak = remember(store) { targetStreak(store, todayIso()) }

    Row(Modifier.fillMaxSize().padding(vertical = Space.s)) {
        Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(top = Space.xxl, bottom = Space.xl)) {
            SectionCard(neu) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Column(Modifier.weight(1f)) {
                        Text("NET THIS MONTH", color = neu.muted, style = BakiType.section)
                        EasedMoney(t.net, neu.ink, BakiType.displayNumeric)
                        Spacer(Modifier.height(Space.xs))
                        Text(
                            "${t.entryCount} entries · ${t.shiftCount} shifts · after all costs",
                            color = neu.muted, style = BakiType.labelNumeric,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.xl)) {
                        NeuMetric(neu, "IN", "RM ${rm(t.income)}")
                        NeuMetric(neu, "OUT", "RM ${rm(t.expense)}", valueColor = neu.danger)
                        val monthShifts = store.shifts.filter { it.date.startsWith(month) }
                        val monthHours = monthShifts.sumOf { it.hours }
                        if (monthHours > 0.05) {
                            NeuMetric(neu, "PER HOUR", "RM ${"%.2f".format(t.net / 100.0 / monthHours)}")
                        }
                    }
                }
            }
            Spacer(Modifier.height(Space.xxl))
            SectionCard(neu) { WeekChart(store, neu) }
            Spacer(Modifier.height(Space.l))
            SectionCard(neu) { MonthTrend(store, month, neu) }
            Spacer(Modifier.height(Space.l))
            SectionCard(neu) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("RECENT", color = neu.muted, style = BakiType.section)
                    Spacer(Modifier.weight(1f))
                    NeuButton(neu, "All entries", onClick = onOpen)
                }
                Spacer(Modifier.height(Space.s))
                if (entries.isEmpty()) {
                    NeuEmpty(neu, "Nothing this month", "Entries you log will appear here, newest first.")
                } else {
                    EntryTable(store, entries.take(9), compact = true, showNote = entries.any { it.note.isNotBlank() })
                }
            }
            Spacer(Modifier.height(Space.l))
        }
        Spacer(Modifier.width(Gutter))
        Column(Modifier.width(Rail2).fillMaxHeight().verticalScroll(rememberScrollState()).padding(top = Space.xxl, bottom = Space.xl)) {
            NeuPanel(neu, Modifier.fillMaxWidth(), corner = Radius.card) {
                Column(Modifier.padding(Space.l)) {
                    CategoryBlock(store, month, neu)
                    Spacer(Modifier.height(Space.l))
                    Spacer(Modifier.height(Space.xs))
                    Spacer(Modifier.height(Space.l))
                    FundsBlock(store, month, neu)
                    Spacer(Modifier.height(Space.l))
                    BillsBlock(store, month, neu)
                    Spacer(Modifier.height(Space.l))
                    Spacer(Modifier.height(Space.xs))
                    Spacer(Modifier.height(Space.l))
                    week?.let { (start, r) ->
                        NeuMetric(
                            neu, "LAST WEEK",
                            "${"%.1f".format(r.hours)} h · RM ${rm(r.net)}",
                            detail = "RM ${"%.2f".format(r.perHour)}/h · ${prettyDate(start)}",
                        )
                        Spacer(Modifier.height(Space.l))
                        Spacer(Modifier.height(Space.xs))
                        Spacer(Modifier.height(Space.l))
                    }
                    val days = store.shifts.filter { it.date.startsWith(month) }
                    val hours = days.sumOf { it.hours }
                    NeuMetric(neu, "HOURS", "${"%.1f".format(hours)} h", detail = "${days.size} shifts")
                    Spacer(Modifier.height(Space.m))
                    NeuMetric(
                        neu, "STREAK",
                        if (streak >= 2) "$streak days" else "—",
                        detail = "above target, in a row",
                        valueColor = if (streak >= 2) neu.accent else neu.ink,
                    )
                }
            }
            Spacer(Modifier.height(Space.xl))
        }
    }
}

@Composable
private fun WeekChart(store: Store, neu: Neu) {
    val today = todayIso()
    val days = (6 downTo 0).map { back -> addDays(today, -back) to dayTotals(store, addDays(today, -back)) }
    val peakPos = days.maxOfOrNull { it.second.net.coerceAtLeast(0L) }?.coerceAtLeast(1L) ?: 1L
    val peakNeg = days.maxOfOrNull { (-it.second.net).coerceAtLeast(0L) }?.coerceAtLeast(1L) ?: 1L
    val summary = days.joinToString(", ") { (iso, t) -> "${weekdayShort(iso)} ${rm(t.net)}" }
    val plot = 96.dp

    Column {
        Text("LAST 7 DAYS", color = neu.muted, style = BakiType.section)
        Spacer(Modifier.height(Space.s))
        NeuWell(
            neu,
            Modifier.fillMaxWidth().semantics { contentDescription = "Last seven days net: $summary" },
            corner = Radius.card,
            offset = Elevation.card,
            inset = false,
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = Space.m, vertical = Space.m)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Space.m),
                ) {
                    val zeroShare = SignedBars.zeroFraction(peakPos, peakNeg)
                    val zeroY = plot * zeroShare
                    days.forEach { (iso, t) ->
                        val g = SignedBars.of(t.net, peakPos, peakNeg)
                        val h = (plot * g.fraction).coerceAtLeast(0.dp)
                        val offsetY = if (g.positive) (zeroY - h).coerceAtLeast(0.dp) else zeroY
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                if (t.entryCount == 0 && t.shiftCount == 0) "–" else rm(t.net),
                                color = if (t.net >= 0) neu.ink else neu.danger,
                                style = BakiType.captionNumeric,
                            )
                            Spacer(Modifier.height(Space.xs))
                            // bars stand on the zero line: gains above, losses hanging below
                            Box(Modifier.fillMaxWidth().height(plot)) {
                                Box(
                                    Modifier.align(Alignment.TopCenter).fillMaxWidth().height(1.dp)
                                        .offset(y = zeroY - 0.5.dp)
                                        .background(neu.ink.copy(alpha = 0.14f)),
                                )
                                if (!g.zero) {
                                    Box(
                                        Modifier
                                            .align(Alignment.TopCenter)
                                            .offset(y = offsetY)
                                            .fillMaxWidth(0.58f)
                                            .height(h.coerceAtLeast(2.dp))
                                            .clip(
                                                if (g.positive) RoundedCornerShape(topStart = Radius.bar, topEnd = Radius.bar)
                                                else RoundedCornerShape(bottomStart = Radius.bar, bottomEnd = Radius.bar),
                                            )
                                            .background(if (g.positive) neu.accent else neu.danger),
                                    )
                                }
                            }
                            Spacer(Modifier.height(Space.xs))
                            Text(weekdayShort(iso), color = neu.ink, style = BakiType.caption)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthTrend(store: Store, month: String, neu: Neu) {
    val days = monthDays(store, month)
    if (days.isEmpty()) return
    var running = 0L
    val points = days.map { (iso, t) -> iso to (running + t.net).also { running = it } }
    val last = points.last().second
    val low = minOf(0L, points.minOf { it.second })
    val high = maxOf(0L, points.maxOf { it.second })

    Column {
        Text("MONTH TRAJECTORY", color = neu.muted, style = BakiType.section)
        Spacer(Modifier.height(Space.s))
        NeuWell(
            neu,
            Modifier.fillMaxWidth().semantics {
                contentDescription = "Cumulative net through $month, ending at RM ${rm(last)}"
            },
            corner = Radius.card,
            offset = Elevation.card,
            inset = false,
        ) {
            Column(Modifier.fillMaxWidth().padding(Space.m)) {
                Canvas(Modifier.fillMaxWidth().height(132.dp)) {
                    val span = (high - low).coerceAtLeast(1L).toFloat()
                    fun y(v: Long): Float = size.height - ((v - low).toFloat() / span) * size.height
                    drawLine(
                        color = neu.ink.copy(alpha = 0.14f),
                        start = androidx.compose.ui.geometry.Offset(0f, y(0L)),
                        end = androidx.compose.ui.geometry.Offset(size.width, y(0L)),
                        strokeWidth = 1.dp.toPx(),
                    )
                    val step = if (points.size > 1) size.width / (points.size - 1) else size.width
                    val path = Path()
                    points.forEachIndexed { i, (_, v) ->
                        val x = if (points.size > 1) i * step else size.width / 2
                        if (i == 0) path.moveTo(x, y(v)) else path.lineTo(x, y(v))
                    }
                    drawPath(
                        path = path,
                        color = neu.accent,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(
                            width = 2.dp.toPx(),
                            cap = androidx.compose.ui.graphics.StrokeCap.Round,
                            join = androidx.compose.ui.graphics.StrokeJoin.Round,
                        ),
                    )
                    val endX = if (points.size > 1) size.width else size.width / 2
                    drawCircle(neu.accent, radius = 3.5.dp.toPx(), center = androidx.compose.ui.geometry.Offset(endX, y(last)))
                }
                Spacer(Modifier.height(Space.s))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(points.first().first.takeLast(5), color = neu.ink, style = BakiType.captionNumeric)
                    Text("now RM ${rm(last)}", color = if (last >= 0) neu.accent else neu.danger, style = BakiType.captionNumeric)
                    Text(points.last().first.takeLast(5), color = neu.ink, style = BakiType.captionNumeric)
                }
            }
        }
    }
}

@Composable
private fun TaxPackDialog(store: Store, year: Int, onDismiss: () -> Unit) {
    val neu = rememberNeu()
    var totalKm by remember { mutableStateOf("") }
    val tax = remember(store, totalKm) { taxYear(store, year, totalKm.toDoubleOrNull()) }
    val scope = rememberCoroutineScope()

    NeuDialog(neu, onDismiss, width = 760.dp) {
        Text("Tax pack · $year", color = neu.ink, style = BakiType.title)
        Spacer(Modifier.height(Space.xs))
        Text(
            "Borang B figures: income, deductible expenses, mileage and the business-use share.",
            color = neu.muted, style = BakiType.caption,
        )
        Spacer(Modifier.height(Space.l))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.xl)) {
            NeuMetric(neu, "INCOME", "RM ${rm(tax.income)}")
            NeuMetric(neu, "DEDUCTIBLE", "RM ${rm(tax.deductible)}")
            NeuMetric(neu, "NET", "RM ${rm(tax.netBusinessIncome)}", valueColor = neu.accent)
            NeuMetric(neu, "BUSINESS USE", "${"%.0f".format(tax.businessUse * 100)}%")
        }
        Spacer(Modifier.height(Space.l))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            NeuField(
                totalKm, { totalKm = it.filter { c -> c.isDigit() } },
                "total km this year (optional)", Modifier.width(300.dp),
                placeholder = "${"%.0f".format(tax.businessKm)}",
            )
            Text(
                "blank = all driving counted as business (${"%,.0f".format(tax.businessKm)} km logged)",
                color = neu.muted, style = BakiType.caption,
            )
        }
        Spacer(Modifier.height(Space.m))
        Row(Modifier.fillMaxWidth().padding(vertical = Space.xs)) {
            Head("EXPENSE", Modifier.weight(1f))
            Head("AMOUNT", Modifier.width(140.dp), Alignment.End)
            Head("DEDUCTIBLE", Modifier.width(110.dp), Alignment.End)
        }
        Column(Modifier.fillMaxWidth().heightIn(max = 240.dp).verticalScroll(rememberScrollState())) {
            tax.lines.forEach { line ->
                Row(Modifier.fillMaxWidth().padding(vertical = Space.xs), verticalAlignment = Alignment.CenterVertically) {
                    Text(line.label, color = neu.ink, style = BakiType.label, modifier = Modifier.weight(1f))
                    Number("RM ${rm(line.amount)}", neu.ink, Modifier.width(140.dp))
                    Number(if (line.deductible) "yes" else "—", if (line.deductible) neu.up else neu.muted, Modifier.width(110.dp))
                }
            }
            if (tax.lines.isEmpty()) {
                Text("No expenses logged for $year.", color = neu.muted, style = BakiType.caption)
            }
        }
        Spacer(Modifier.height(Space.s))
        Text(
            "Tolls are reimbursed inside Grab fares (not a separate deduction). Food and personal spending are listed but not claimable.",
            color = neu.muted, style = BakiType.caption,
        )
        Spacer(Modifier.height(Space.l))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            NeuButton(neu, "Open / print", primary = true) {
                PlatformEffects.printStatement(taxPackHtml(tax))
            }
            NeuButton(neu, "Save CSV") {
                PlatformEffects.exportFile("baki-tax-$year.csv", "text/csv", taxPackCsv(tax))
            }
            NeuButton(neu, "Close", onClick = onDismiss)
        }
    }
}

@Composable
private fun CategoryBlock(store: Store, month: String, neu: Neu) {
    val baki = LocalBaki.current
    val cats = remember(store) { store.settings.cats.catsOut.associateBy { it.id } }
    val rows = remember(store, month) {
        store.entries
            .filter { it.date.startsWith(month) && it.type != "income" }
            .groupBy { it.cat }
            .map { (id, list) -> Triple(id, cats[id]?.name ?: id, list.sumOf { it.amount }) }
            .sortedByDescending { it.third }
            .take(6)
    }
    // one colour per slice, never repeated within the chart; "Other" is always the neutral grey
    val colors = remember(rows, baki) { chartColors(baki, rows.map { it.first }) }
    val total = rows.sumOf { it.third }.coerceAtLeast(1)

    Text("WHERE IT WENT", color = neu.muted, style = BakiType.section)
    Spacer(Modifier.height(Space.s))
    NeuBar(
        neu = neu,
        fraction = 1f,
        modifier = Modifier.fillMaxWidth().height(12.dp),
        segments = rows.mapIndexed { i, (_, _, cents) ->
            (cents.toFloat() / total).coerceAtLeast(0.002f) to colors[i]
        },
    )
    Spacer(Modifier.height(Space.m))
    rows.forEachIndexed { i, (_, name, cents) ->
        Row(Modifier.fillMaxWidth().padding(vertical = Space.xxs), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(9.dp).clip(RoundedCornerShape(Radius.pill))
                    .raised(neu, corner = 6.dp, offset = 2.dp, fill = colors[i]),
            )
            Spacer(Modifier.width(Space.s))
            Text(name, color = neu.ink, style = BakiType.label)
            Spacer(Modifier.weight(1f))
            Text("RM ${rm(cents)}", color = neu.ink, style = BakiType.labelNumeric)
            Spacer(Modifier.width(Space.s))
            Text("${"%.0f".format(cents * 100.0 / total)}%", color = neu.ink, style = BakiType.captionNumeric)
        }
    }
}


@Composable
private fun BillsBlock(store: Store, month: String, neu: Neu) {
    val committed = Bills.due(store, month)
    if (committed.isEmpty() && store.bills.none { it.optional }) return
    val paid = committed.count { Bills.isCovered(store, it, Bills.periodOf(it, month)) }
    Text("BILLS", color = neu.muted, style = BakiType.section)
    Spacer(Modifier.height(Space.s))
    Text(
        "RM ${rm(Bills.committed(store, month))} due this month · $paid of ${committed.size} covered",
        color = neu.ink, style = BakiType.labelNumeric,
    )
    Text(
        "average RM ${rm(Bills.averageMonthly(store))}/month incl. annual",
        color = neu.muted, style = BakiType.caption,
    )
    val nextOpen = Bills.nextOpenMonth(store, month)
    Text("next open month: ${monthLabel(nextOpen)}", color = neu.muted, style = BakiType.caption)
    // optional goals are paid from what the month leaves over
    store.bills.filter { it.optional }.forEach { bill ->
        val net = monthTotals(store, month).net
        val covered = Bills.isCovered(store, bill, Bills.periodOf(bill, month))
        Text(
            if (covered) "${bill.name}: covered" else "leftover RM ${rm(net)} · ${bill.name} RM ${rm(bill.amount)}",
            color = when {
                covered -> neu.up
                net >= bill.amount -> neu.ink
                else -> neu.muted
            },
            style = BakiType.captionNumeric,
        )
    }
}

@Composable
private fun FundsBlock(store: Store, month: String, neu: Neu) {
    val cfg = store.settings.profit
    val shifts = store.shifts.filter { it.date.startsWith(month) }
    val km = shifts.sumOf { it.km }
    val fuelAccrued = (km / cfg.kmPerLitre * cfg.fuelPrice * 100).toLong()
    val fuelSpent = store.entries.filter { it.date.startsWith(month) && isFuelCat(it.cat) }.sumOf { it.amount }
    val wearAccrued = (km * cfg.wearPerKm * 100).toLong()
    Text("FUNDS", color = neu.muted, style = BakiType.section)
    Spacer(Modifier.height(Space.s))
    listOf(Triple("Fuel", fuelAccrued, fuelSpent), Triple("Wear", wearAccrued, 0L)).forEach { (label, accrued, spent) ->
        Row(Modifier.fillMaxWidth().padding(vertical = Space.xxs)) {
            Text(label, color = neu.ink, style = BakiType.label)
            Spacer(Modifier.weight(1f))
            val delta = accrued - spent
            Text(
                "RM ${rm(delta)}",
                color = if (delta >= 0) neu.accent else neu.danger,
                style = BakiType.subtitleNumeric,
            )
        }
        val delta = accrued - spent
        Text("accrued RM ${rm(accrued)} · spent RM ${rm(spent)}", color = neu.muted, style = BakiType.captionNumeric)
        if (delta < 0) Text("shortfall", color = neu.danger, style = BakiType.caption)
        Spacer(Modifier.height(Space.xs))
    }
}

// ------------------------------------------------------------------ tables

@Composable
internal fun EntriesView(store: Store, month: String) {
    val neu = rememberNeu()
    var query by remember { mutableStateOf("") }
    var catFilter by remember { mutableStateOf<String?>(null) }
    val catsById = remember(store) { store.settings.cats.catsOut.associateBy { it.id } }
    val monthEntries = store.entries.filter { it.date.startsWith(month) }
    val catOptions = remember(monthEntries) {
        monthEntries.filter { it.type != "income" }
            .groupBy { it.cat }
            .map { (id, list) -> id to list.sumOf { it.amount } }
            .sortedByDescending { it.second }
            .take(8)
    }
    val entries = monthEntries
        .filter { catFilter == null || it.cat == catFilter }
        .filter { e -> query.isBlank() || (e.cat + " " + e.note).contains(query, ignoreCase = true) }
        .sortedByDescending { it.date }

    val scroll = rememberScrollState()
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(scroll).padding(top = Space.xxl, bottom = Space.xl),
        ) {
            SectionCard(neu) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    NeuField(query, { query = it }, "Filter", Modifier.width(320.dp), placeholder = "Search category or note")
                    Spacer(Modifier.width(Space.m))
                    Text(
                        "${entries.size} entries · RM ${rm(entries.filter { it.type != "income" }.sumOf { it.amount })} out",
                        color = neu.ink, style = BakiType.label,
                    )
                    Spacer(Modifier.weight(1f))
                    Text("read-only · edit on the phone", color = neu.muted, style = BakiType.caption)
                }
                Spacer(Modifier.height(Space.m))
                Row(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                    NeuChip(neu, "All", selected = catFilter == null) { catFilter = null }
                    catOptions.forEach { (id, _) ->
                        NeuChip(neu, catsById[id]?.name ?: id, selected = catFilter == id) { catFilter = id }
                    }
                }
            }
            Spacer(Modifier.height(Space.l))
            if (entries.isEmpty()) {
                NeuEmpty(
                    neu,
                    "Nothing this month",
                    if (query.isBlank()) "Entries you log on the phone appear here, newest first." else "No entry matches that filter.",
                )
            } else {
                SectionCard(neu) {
                    EntryTable(store, entries, compact = false, showNote = monthEntries.any { it.note.isNotBlank() })
                }
            }
        }
        NeuScrollbar(
            state = scroll,
            neu = neu,
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(vertical = Space.s),
        )
    }
}

@Composable
internal fun ShiftsView(store: Store, month: String) {
    val neu = rememberNeu()
    val cfg = store.settings.profit
    val shifts = store.shifts.filter { it.date.startsWith(month) }.sortedByDescending { it.date }
    val totals = shifts.map { it to shiftMetrics(it, cfg) }
    val net = totals.sumOf { (_, m) -> (m.net * 100).toLong() } / 100.0
    val sumHours = shifts.sumOf { it.hours }
    val sumKm = shifts.sumOf { it.km }
    val sumGross = shifts.sumOf { it.gross }
    val avgRate = if (sumHours > 0.01) net / sumHours else 0.0
    val scroll = rememberScrollState()

    fun rateColor(rate: Double) = if (rate >= cfg.targetRate) neu.up else neu.danger

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(top = Space.xxl, bottom = Space.xl)) {
            SectionCard(neu) {
                Text(
                    "${shifts.size} shifts · net RM ${"%.2f".format(net)} (after fuel & wear) · avg RM ${"%.2f".format(avgRate)}/h",
                    color = neu.ink, style = BakiType.label,
                )
                Text(
                    "rate coloured against target RM ${"%.2f".format(cfg.targetRate)}/h · Overview adds other expenses",
                    color = neu.muted, style = BakiType.caption,
                )
                Spacer(Modifier.height(Space.m))
                Row(Modifier.fillMaxWidth().padding(vertical = Space.s)) {
                    Head("DATE", Modifier.width(150.dp))
                    Head("HOURS", Modifier.width(90.dp), Alignment.End)
                    Head("KM", Modifier.width(90.dp), Alignment.End)
                    Head("GROSS", Modifier.width(130.dp), Alignment.End)
                    Head("NET", Modifier.width(130.dp), Alignment.End)
                    Head("RATE", Modifier.weight(1f), Alignment.End)
                }
                if (shifts.isEmpty()) {
                    NeuEmpty(neu, "No shifts this month", "Shifts appear here once you start one on the phone.")
                }
                Column {
                    totals.forEach { (s, m) ->
                        val hover = remember { MutableInteractionSource() }
                        val hovered by hover.collectIsHoveredAsState()
                        val rate = if (s.hours > 0.01) m.net / s.hours else 0.0
                        Row(
                            Modifier.fillMaxWidth()
                                .then(if (hovered) Modifier.raised(neu, corner = Radius.input, offset = Elevation.control) else Modifier)
                                .padding(horizontal = Space.s, vertical = Space.m),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("${s.date} ${s.start}", color = neu.ink, style = BakiType.labelNumeric, modifier = Modifier.width(150.dp))
                            Number("${"%.1f".format(s.hours)}h", neu.muted, Modifier.width(90.dp))
                            Number("${"%.1f".format(s.km)}", neu.muted, Modifier.width(90.dp))
                            Number("RM ${rm(s.gross)}", neu.muted, Modifier.width(130.dp))
                            Number("RM ${rm((m.net * 100).toLong())}", neu.ink, Modifier.width(130.dp))
                            Number("RM ${"%.2f".format(rate)}/h", rateColor(rate), Modifier.weight(1f))
                        }
                    }
                }
                if (shifts.isNotEmpty()) {
                    Spacer(Modifier.height(Space.s))
                    Box(Modifier.fillMaxWidth().height(1.dp).background(neu.ink.copy(alpha = 0.12f)))
                    Spacer(Modifier.height(Space.s))
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = Space.s, vertical = Space.s),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("TOTAL", color = neu.ink, style = BakiType.section, modifier = Modifier.width(150.dp))
                        Number("${"%.1f".format(sumHours)}h", neu.ink, Modifier.width(90.dp))
                        Number("${"%.1f".format(sumKm)}", neu.ink, Modifier.width(90.dp))
                        Number("RM ${rm(sumGross)}", neu.ink, Modifier.width(130.dp))
                        Number("RM ${rm((net * 100).toLong())}", neu.ink, Modifier.width(130.dp))
                        Number("RM ${"%.2f".format(avgRate)}/h", rateColor(avgRate), Modifier.weight(1f))
                    }
                }
            }
        }
        NeuScrollbar(
            state = scroll,
            neu = neu,
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(vertical = Space.s),
        )
    }
}

@Composable
private fun Number(text: String, color: Color, modifier: Modifier) {
    Text(text, color = color, style = BakiType.labelNumeric, modifier = modifier, textAlign = TextAlign.End)
}

private enum class TableSort { Date, Category, Amount }

@Composable
private fun EntryTable(
    store: Store,
    entries: List<Entry>,
    compact: Boolean,
    showNote: Boolean = true,
) {
    val neu = rememberNeu()
    val cats = remember(store) { store.settings.cats.catsOut.associateBy { it.id } }
    var sort by remember { mutableStateOf(TableSort.Date) }
    val sorted = remember(entries, sort) {
        when (sort) {
            TableSort.Date -> entries
            TableSort.Category -> entries.sortedBy { cats[it.cat]?.name ?: it.cat }
            TableSort.Amount -> entries.sortedByDescending { it.amount }
        }
    }

    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(vertical = Space.s)) {
            Head("DATE" + if (sort == TableSort.Date) " ↓" else "", Modifier.width(120.dp)) { sort = TableSort.Date }
            Head("CATEGORY" + if (sort == TableSort.Category) " ↓" else "", Modifier.width(170.dp)) { sort = TableSort.Category }
            if (showNote) Head("NOTE", Modifier.weight(1f)) else Spacer(Modifier.weight(1f))
            Head("AMOUNT" + if (sort == TableSort.Amount) " ↓" else "", Modifier.width(120.dp), Alignment.End) { sort = TableSort.Amount }
        }
        Column {
            sorted.take(if (compact) 9 else sorted.size).forEach { entry ->
                val hover = remember { MutableInteractionSource() }
                val hovered by hover.collectIsHoveredAsState()
                Row(
                    Modifier.fillMaxWidth()
                        .then(if (hovered) Modifier.inset(neu, corner = Radius.input, offset = 2.dp) else Modifier)
                        .clip(RoundedCornerShape(Radius.input))
                        .hoverable(hover)
                        .padding(horizontal = Space.s, vertical = Space.m),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(entry.date, color = neu.ink, style = BakiType.labelNumeric, modifier = Modifier.width(120.dp))
                    Row(Modifier.width(170.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(categoryIcon(entry.cat), contentDescription = null, tint = neu.muted, modifier = Modifier.size(IconSize.inline))
                        Spacer(Modifier.width(Space.s))
                        Text(cats[entry.cat]?.name ?: entry.cat, color = neu.ink, style = BakiType.label)
                    }
                    if (showNote) {
                        Text(
                            entry.note.ifBlank { "—" }, color = neu.muted, style = BakiType.label,
                            maxLines = 1, modifier = Modifier.weight(1f),
                        )
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                    Text(
                        (if (entry.type == "income") "+" else "−") + rm(entry.amount),
                        color = if (entry.type == "income") neu.accent else neu.danger,
                        style = BakiType.labelNumeric,
                        modifier = Modifier.width(120.dp),
                        textAlign = TextAlign.End,
                    )
                }
            }
        }
    }
}

private fun Modifier.hoverableRow(source: MutableInteractionSource): Modifier = this.hoverable(source)


@Composable
private fun Head(
    text: String,
    modifier: Modifier = Modifier,
    align: Alignment.Horizontal = Alignment.Start,
    onClick: (() -> Unit)? = null,
) {
    val neu = rememberNeu()
    Box(modifier, contentAlignment = if (align == Alignment.End) Alignment.CenterEnd else Alignment.CenterStart) {
        Text(
            text, color = neu.muted, style = BakiType.section,
            modifier = if (onClick != null) Modifier.pointerHoverIcon(PointerIcon.Hand).clickable { onClick() } else Modifier,
        )
    }
}

// -------------------------------------------------------------------- year

@Composable
internal fun YearView(store: Store, onMonth: (String) -> Unit) {
    val baki = LocalBaki.current
    val neu = rememberNeu()
    val year = todayIso().substring(0, 4).toIntOrNull() ?: 2026
    var taxOpen by remember { mutableStateOf(false) }
    if (taxOpen) TaxPackDialog(store, year) { taxOpen = false }
    val months = remember(store, year) {
        (1..12).map { m ->
            val label = "%04d-%02d".format(year, m)
            label to monthTotals(store, label)
        }
    }
    val peak = months.maxOfOrNull { kotlin.math.abs(it.second.net) }?.coerceAtLeast(1) ?: 1

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = Space.xxl, bottom = Space.xl)) {
        SectionCard(neu) {
            val best = months.maxByOrNull { it.second.net }
            val yearShifts = store.shifts.filter { it.date.startsWith(year.toString()) }
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text("$year AT A GLANCE", color = neu.muted, style = BakiType.section)
                    Text("RM ${rm(months.sumOf { it.second.net })} net", color = neu.ink, style = BakiType.headlineNumeric)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Space.xl)) {
                    NeuMetric(neu, "IN", "RM ${rm(months.sumOf { it.second.income })}")
                    NeuMetric(neu, "OUT", "RM ${rm(months.sumOf { it.second.expense })}", valueColor = neu.danger)
                    NeuMetric(neu, "HOURS", "${"%.1f".format(yearShifts.sumOf { it.hours })} h")
                    NeuMetric(neu, "KM", "${"%.0f".format(yearShifts.sumOf { it.km })}")
                    best?.let {
                        NeuMetric(neu, "BEST MONTH", monthLabel(it.first).substringBefore(' '),
                            detail = "RM ${rm(it.second.net)}", valueColor = neu.accent)
                    }
                    Spacer(Modifier.width(Space.l))
                    NeuButton(neu, "Tax pack", primary = true) { taxOpen = true }
                }
            }
        }
        Spacer(Modifier.height(Space.xxl))
        SectionCard(neu) {
        months.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.xl)) {
                row.forEach { (label, t) ->
                    val idle = t.entryCount == 0 && t.shiftCount == 0
                    val future = label > todayIso().substring(0, 7)
                    val best = label == months.maxByOrNull { it.second.net }?.first && !idle && !future
                    val hover = remember { MutableInteractionSource() }
                    val hovered by hover.collectIsHoveredAsState()
                    Column(
                        Modifier.weight(1f).padding(vertical = Space.m)
                            .then(if (hovered) Modifier.inset(neu, corner = Radius.input) else Modifier)
                            .clip(RoundedCornerShape(Radius.input))
                            .hoverable(hover)
                            .pointerHoverIcon(PointerIcon.Hand)
                            .clickable(interactionSource = hover, indication = null) { onMonth(label) }
                            .padding(horizontal = Space.s, vertical = Space.xs),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                monthLabel(label),
                                color = (if (idle || future) neu.muted else neu.ink)
                                    .copy(alpha = if (idle || future) 0.55f else 1f),
                                style = BakiType.label, modifier = Modifier.weight(1f),
                            )
                            Text(
                                if (future) "—" else "RM ${rm(t.net)}",
                                color = when {
                                    best -> neu.accent
                                    idle || future -> neu.muted.copy(alpha = 0.55f)
                                    else -> neu.ink
                                },
                                style = BakiType.labelNumeric,
                            )
                        }
                        Spacer(Modifier.height(Space.xs))
                        NeuBar(
                            neu = neu,
                            fraction = if (t.net == 0L) 0f else (kotlin.math.abs(t.net).toFloat() / peak).coerceIn(0.02f, 1f),
                            modifier = Modifier.fillMaxWidth().height(12.dp),
                            color = if (t.net >= 0) neu.accent else neu.danger,
                        )
                    }
                }
                // keep the last row aligned to the same three columns
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
            Spacer(Modifier.height(Space.m))
        }
        }
        Spacer(Modifier.height(Space.xl))
    }
}

// ------------------------------------------------------------------ routes

/**
 * Drive review: the phone records the path, this is where it can be studied. Read-only, like the
 * rest of the desk.
 */
@Composable
internal fun RoutesView(repo: BakiRepo, month: String, neu: Neu) {
    val store = repo.store
    val shifts = remember(store, month) {
        store.shifts.filter { it.date.startsWith(month) }.sortedByDescending { "${it.date} ${it.start}" }
    }
    var routes by remember(month) { mutableStateOf<Map<String, ShiftRoute>>(emptyMap()) }
    var loading by remember(month) { mutableStateOf(true) }
    var selected by remember { mutableStateOf<String?>(null) }

    var reload by remember { mutableStateOf(0) }
    LaunchedEffect(month, reload) {
        loading = true
        runCatching { repo.pullRoutes("$month-01") }
        val found = mutableMapOf<String, ShiftRoute>()
        shifts.forEach { s ->
            RouteStore.route(s.id)?.takeIf { it.points.size >= 2 }?.let { found[s.id] = it }
        }
        routes = found
        loading = false
        if (reload > 0) { /* nothing to re-key: map identity changed above */ }
        if (selected == null || selected !in found) selected = found.keys.firstOrNull() ?: shifts.firstOrNull()?.id
    }

    if (shifts.isEmpty()) {
        NeuEmpty(neu, "No shifts this month", "Start a shift on the phone; its route will be reviewable here.")
        return
    }

    val shift = shifts.firstOrNull { it.id == selected }
    val route = selected?.let { routes[it] }

    Row(Modifier.fillMaxSize().padding(vertical = Space.s), horizontalArrangement = Arrangement.spacedBy(Space.l)) {
        NeuPanel(neu, Modifier.width(330.dp).fillMaxHeight(), corner = Radius.sheet) {
            Column(Modifier.fillMaxSize().padding(Space.m)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("DRIVES · ${routes.size} of ${shifts.size} routes pulled", color = neu.muted, style = BakiType.section)
                    Spacer(Modifier.weight(1f))
                    NeuButton(neu, "Pull") { reload++ }
                }
                Spacer(Modifier.height(Space.s))
                Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(top = Space.s)) {
                    shifts.forEach { s ->
                        val has = routes.containsKey(s.id)
                        val m = shiftMetrics(s, store.settings.profit)
                        val isSel = s.id == selected
                        Row(
                            Modifier.fillMaxWidth()
                                .then(
                                    if (isSel) Modifier.inset(neu, corner = Radius.input, offset = Elevation.inset)
                                    else Modifier,
                                )
                                .clip(RoundedCornerShape(Radius.input))
                                .pointerHoverIcon(PointerIcon.Hand)
                                .clickable { selected = s.id }
                                .padding(horizontal = Space.s, vertical = Space.s),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier.size(width = 3.dp, height = 26.dp)
                                    .clip(RoundedCornerShape(Radius.pill))
                                    .background(if (isSel) neu.accent else androidx.compose.ui.graphics.Color.Transparent),
                            )
                            Spacer(Modifier.width(Space.s))
                            Box(
                                Modifier.size(6.dp).clip(RoundedCornerShape(Radius.pill))
                                    .background(if (has) neu.accent else neu.muted.copy(alpha = 0.4f)),
                            )
                            Spacer(Modifier.width(Space.s))
                            Column(Modifier.weight(1f)) {
                                Text("${s.date} · ${s.start}", color = neu.ink, style = BakiType.labelNumeric)
                                Text(
                                    "${"%.1f".format(s.km)} km · ${"%.1f".format(s.hours)} h · RM ${rm((m.net * 100).toLong())}",
                                    color = neu.muted, style = BakiType.captionNumeric,
                                )
                            }
                        }
                    }
                }
            }
        }

        NeuPanel(neu, Modifier.weight(1f).fillMaxHeight(), corner = Radius.sheet) {
            if (shift == null || route == null) {
                // centred in the pane, not a full-width band clipped by the card
                Box(Modifier.fillMaxSize().padding(Space.xl), contentAlignment = Alignment.Center) {
                    Column(
                        Modifier.widthIn(max = 360.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        if (loading) {
                            Text("Loading routes…", color = neu.muted, style = BakiType.body)
                        } else {
                            Text("No route for this shift", color = neu.ink, style = BakiType.bodyStrong, textAlign = TextAlign.Center)
                            Spacer(Modifier.height(Space.xs))
                            Text(
                                "GPS points may not have been recorded, or the route was pruned. If the drive exists on the phone, pull it here.",
                                color = neu.muted, style = BakiType.caption, textAlign = TextAlign.Center,
                            )
                            Spacer(Modifier.height(Space.m))
                            NeuButton(neu, "Pull routes", primary = true) { reload++ }
                        }
                    }
                }
            } else {
                Column(Modifier.fillMaxSize().padding(Space.l)) {
                    val m = shiftMetrics(shift, store.settings.profit)
                    Row(verticalAlignment = Alignment.Bottom) {
                        Column(Modifier.weight(1f)) {
                            Text("${shift.date} · ${shift.start}", color = neu.ink, style = BakiType.title)
                            Text("${route.points.size} GPS points", color = neu.muted, style = BakiType.caption)
                        }
                        NeuMetric(neu, "KM", "%.1f".format(shift.km))
                        Spacer(Modifier.width(Space.l))
                        NeuMetric(neu, "HOURS", "%.1f".format(shift.hours))
                        Spacer(Modifier.width(Space.l))
                        NeuMetric(neu, "NET", "RM ${rm((m.net * 100).toLong())}", valueColor = neu.accent)
                        Spacer(Modifier.width(Space.l))
                        NeuMetric(neu, "RM / KM", "%.2f".format(if (shift.km > 0.01) m.net / shift.km else 0.0))
                        Spacer(Modifier.width(Space.l))
                        NeuMetric(neu, "RM / H", "%.2f".format(if (shift.hours > 0.01) m.net / shift.hours else 0.0))
                    }
                    Spacer(Modifier.height(Space.l))
                    Box(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(Radius.card))) {
                        RouteMap(
                            routes = listOf(MapRoute(route, routeColorFor(LocalBaki.current, 0), "${shift.date} ${shift.start}")),
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
    }
}

// ----------------------------------------------------------------- compare

/**
 * The desk's real job: two periods side by side, with deltas that say whether a change is good.
 */
@Composable
internal fun CompareView(store: Store, month: String, neu: Neu) {
    var a by remember(month) { mutableStateOf(month) }
    var b by remember(month) { mutableStateOf(shiftMonth(month, -1)) }
    val rows = remember(store, a, b) { compareMonths(store, a, b) }
    val catA = remember(store, a) { categoryTotals(store, a) }
    val catB = remember(store, b) { categoryTotals(store, b) }

    val scroll = rememberScrollState()
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(top = Space.xxl, bottom = Space.xl)) {
            SectionCard(neu) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("PERIOD A", color = neu.muted, style = BakiType.section)
                    Spacer(Modifier.width(Space.s))
                    MonthStepper(neu, a, { a = shiftMonth(a, -1) }, { a = shiftMonth(a, 1) })
                    Spacer(Modifier.weight(1f))
                    Text("VERSUS", color = neu.muted, style = BakiType.section)
                    Spacer(Modifier.weight(1f))
                    Text("PERIOD B", color = neu.muted, style = BakiType.section)
                    Spacer(Modifier.width(Space.s))
                    MonthStepper(neu, b, { b = shiftMonth(b, -1) }, { b = shiftMonth(b, 1) })
                }
            }
            Spacer(Modifier.height(Space.xxl))
            SectionCard(neu) {
                Row(Modifier.fillMaxWidth().padding(vertical = Space.xs)) {
                    Head("METRIC", Modifier.weight(1.2f))
                    Head(monthLabel(a).uppercase(), Modifier.weight(1f), Alignment.End)
                    Head(monthLabel(b).uppercase(), Modifier.weight(1f), Alignment.End)
                    Head("CHANGE", Modifier.weight(0.9f), Alignment.End)
                }
                rows.forEach { r ->
                    val deltaColor = when (r.good) {
                        null -> neu.muted
                        true -> neu.up
                        false -> neu.danger
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = Space.s),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(r.label, color = neu.ink, style = BakiType.label, modifier = Modifier.weight(1.2f))
                        Text(r.a, color = neu.ink, style = BakiType.labelNumeric, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
                        Text(r.b, color = neu.ink, style = BakiType.labelNumeric, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
                        val label = r.changeLabel
                        val arrow = when {
                            label == null || label == "—" || label == "new" -> ""
                            label.startsWith("-") -> "↓ "
                            label.startsWith("+") -> "↑ "
                            else -> ""
                        }
                        Text(
                            "$arrow${label ?: "—"}",
                            color = deltaColor,
                            style = BakiType.labelNumeric,
                            modifier = Modifier.weight(0.9f),
                            textAlign = TextAlign.End,
                        )
                    }
                }
            }
            Spacer(Modifier.height(Space.xxl))
            SectionCard(neu) {
                Text("WHERE IT WENT, SIDE BY SIDE", color = neu.muted, style = BakiType.section)
                Spacer(Modifier.height(Space.s))
                val names = (catA.map { it.first } + catB.map { it.first }).distinct()
                val top = names
                    .map { name ->
                        name to ((catA.firstOrNull { it.first == name }?.second ?: 0L) to
                            (catB.firstOrNull { it.first == name }?.second ?: 0L))
                    }
                    .sortedByDescending { maxOf(it.second.first, it.second.second) }
                    .take(7)
                val peak = top.maxOfOrNull { maxOf(it.second.first, it.second.second) }?.coerceAtLeast(1) ?: 1
                top.forEach { (name, values) ->
                    val (va, vb) = values
                    Row(Modifier.fillMaxWidth().padding(vertical = Space.xs), verticalAlignment = Alignment.CenterVertically) {
                        Text(name, color = neu.ink, style = BakiType.label, modifier = Modifier.width(150.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Box(Modifier.fillMaxWidth().height(8.dp)) {
                                Box(
                                    Modifier.fillMaxWidth((va.toFloat() / peak).coerceIn(0f, 1f)).height(8.dp)
                                        .clip(RoundedCornerShape(Radius.pill)).background(neu.accent),
                                )
                            }
                            Box(Modifier.fillMaxWidth().height(8.dp)) {
                                Box(
                                    Modifier.fillMaxWidth((vb.toFloat() / peak).coerceIn(0f, 1f)).height(8.dp)
                                        .clip(RoundedCornerShape(Radius.pill)).background(neu.muted),
                                )
                            }
                        }
                        Spacer(Modifier.width(Space.m))
                        Column(Modifier.width(150.dp), horizontalAlignment = Alignment.End) {
                            Text("RM ${rm(va)}", color = neu.ink, style = BakiType.captionNumeric)
                            Text("RM ${rm(vb)}", color = neu.muted, style = BakiType.captionNumeric)
                        }
                    }
                }
                Spacer(Modifier.height(Space.xs))
                Text("accent = ${monthLabel(a)} · grey = ${monthLabel(b)}", color = neu.muted, style = BakiType.caption)
            }
            Spacer(Modifier.height(Space.l))
        }
        NeuScrollbar(
            state = scroll,
            neu = neu,
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(vertical = Space.s),
        )
    }
}

// ----------------------------------------------------------------- dialogs

@Composable
private fun NeuField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    masked: Boolean = false,
) {
    val neu = rememberNeu()
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Column(modifier) {
        if (label.isNotEmpty()) {
            Text(label, color = neu.muted, style = BakiType.caption, modifier = Modifier.padding(bottom = Space.xxs))
        }
        NeuWell(
            neu,
            Modifier.fillMaxWidth().then(
                if (focused) Modifier.border(2.dp, neu.accent, RoundedCornerShape(Radius.input)) else Modifier,
            ),
            corner = Radius.input,
        ) {
            androidx.compose.foundation.layout.Box(
                Modifier.fillMaxWidth().padding(horizontal = Space.m, vertical = Space.s),
            ) {
                androidx.compose.foundation.text.BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle = BakiType.label.copy(color = neu.ink),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(neu.accent),
                    visualTransformation = if (masked) androidx.compose.ui.text.input.PasswordVisualTransformation()
                    else androidx.compose.ui.text.input.VisualTransformation.None,
                    interactionSource = interaction,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (value.isEmpty() && placeholder != null) {
                    Text(placeholder, color = neu.muted.copy(alpha = 0.75f), style = BakiType.label)
                }
            }
        }
    }
}

@Composable
private fun DesktopSettings(repo: BakiRepo, onDismiss: () -> Unit) {
    val neu = rememberNeu()
    val profit = repo.store.settings.profit
    var fuel by remember { mutableStateOf("%.2f".format(profit.fuelPrice)) }
    var kml by remember { mutableStateOf("%.1f".format(profit.kmPerLitre)) }
    var wear by remember { mutableStateOf("%.2f".format(profit.wearPerKm)) }
    var target by remember { mutableStateOf("%.0f".format(profit.targetRate)) }
    var routeKey by remember { mutableStateOf(repo.config.routeSecret) }
    var routeSync by remember { mutableStateOf(repo.config.syncRoutes) }
    var revealKey by remember { mutableStateOf(false) }
    var hydrated by remember { mutableStateOf(false) }
    var keyHydrated by remember { mutableStateOf(false) }

    // autosave: a short settle, then persist; no Save buttons
    LaunchedEffect(fuel, kml, wear, target) {
        if (!hydrated) { hydrated = true; return@LaunchedEffect }
        delay(450)
        val p = repo.store.settings.profit
        repo.updateProfit(
            p.copy(
                fuelPrice = fuel.toDoubleOrNull() ?: p.fuelPrice,
                kmPerLitre = kml.toDoubleOrNull()?.takeIf { it > 0.0 } ?: p.kmPerLitre,
                wearPerKm = wear.toDoubleOrNull() ?: p.wearPerKm,
                targetRate = target.toDoubleOrNull() ?: p.targetRate,
            ),
        )
    }
    LaunchedEffect(routeKey) {
        if (!keyHydrated) { keyHydrated = true; return@LaunchedEffect }
        delay(450)
        repo.setRouteSync(routeSync, routeKey)
    }

    NeuDialog(neu, onDismiss, width = 620.dp) {
        Text("Settings", color = neu.ink, style = BakiType.title)
        Spacer(Modifier.height(Space.l))

        Text("COLOUR", color = neu.muted, style = BakiType.section)
        Spacer(Modifier.height(Space.xs))
        NeuSegmented(
            neu,
            listOf("sage" to "Sage", "night" to "Night", "paper" to "Paper", "mist" to "Mist"),
            repo.config.palette,
        ) { repo.setPalette(it) }
        Spacer(Modifier.height(Space.l))

        Text("APPEARANCE", color = neu.muted, style = BakiType.section)
        Spacer(Modifier.height(Space.xs))
        NeuSegmented(
            neu,
            listOf("auto" to "Auto", "light" to "Light", "dark" to "Dark"),
            repo.config.themeMode,
        ) { repo.setThemeMode(it) }
        Spacer(Modifier.height(Space.xs))
        Text(
            "Colour picks the palette, Appearance picks light or dark. They are separate axes: Night is a " +
                "dark palette, so it stays dark even when Appearance is Light.",
            color = neu.muted, style = BakiType.caption,
        )
        Spacer(Modifier.height(Space.l))

        Text("VEHICLE & TARGET", color = neu.muted, style = BakiType.section)
        Spacer(Modifier.height(Space.xs))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            NeuField(fuel, { fuel = it.filter { c -> c.isDigit() || c == '.' } }, "RM / litre", Modifier.weight(1f))
            NeuField(kml, { kml = it.filter { c -> c.isDigit() || c == '.' } }, "km / litre", Modifier.weight(1f))
        }
        Spacer(Modifier.height(Space.s))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            NeuField(wear, { wear = it.filter { c -> c.isDigit() || c == '.' } }, "wear RM / km", Modifier.weight(1f))
            NeuField(target, { target = it.filter { c -> c.isDigit() || c == '.' } }, "target RM / hour", Modifier.weight(1f))
        }
        Spacer(Modifier.height(Space.l))

        Text("ROUTE TRACES", color = neu.muted, style = BakiType.section)
        Spacer(Modifier.height(Space.xs))
        Text(
            "Sealed with a key only your two devices know; the server stores ciphertext.",
            color = neu.muted, style = BakiType.caption,
        )
        Spacer(Modifier.height(Space.s))
        NeuSegmented(neu, listOf(false to "Off", true to "On"), routeSync) { on ->
            routeSync = on
            repo.setRouteSync(on, routeKey)
        }
        Spacer(Modifier.height(Space.s))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            NeuField(routeKey, { routeKey = it }, "shared route key", Modifier.weight(1f), masked = !revealKey)
            NeuButton(neu, if (revealKey) "Hide" else "Show") { revealKey = !revealKey }
        }
        Spacer(Modifier.height(Space.xs))
        Text("Saved automatically as you type.", color = neu.muted, style = BakiType.caption)
        Spacer(Modifier.height(Space.l))

        Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            NeuButton(neu, "Close", primary = true, onClick = onDismiss)
        }
    }
}

@Composable
private fun StatementPreview(store: Store, month: String, onDismiss: () -> Unit) {
    val baki = LocalBaki.current
    val neu = rememberNeu()
    val t = monthTotals(store, month)
    val shifts = store.shifts.filter { it.date.startsWith(month) }.sortedBy { it.date }
    val entries = store.entries.filter { it.date.startsWith(month) }.sortedBy { it.date }

    Box(
        Modifier.fillMaxSize().background(neu.scrim).clickable(
            interactionSource = MutableInteractionSource(), indication = null,
        ) { onDismiss() },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.width(780.dp).heightIn(max = 860.dp)
                .elevated(neu, corner = Radius.sheet, drop = Elevation.floating)
                .clip(RoundedCornerShape(Radius.sheet))
                .background(neu.panel)
                .clickable(interactionSource = MutableInteractionSource(), indication = null) {}
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.xxl + Space.l, vertical = Space.xxl + Space.xs),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Statement", color = neu.ink, style = BakiType.title)
                    Text(monthLabel(month), color = neu.muted, style = BakiType.label)
                }
                NeuButton(neu, "Print / PDF") { PlatformEffects.printStatement(statementHtml(store, month)) }
                Spacer(Modifier.width(Space.s))
                NeuButton(neu, "Close", onClick = onDismiss)
            }
            Spacer(Modifier.height(Space.l))
            Text("NET", color = neu.muted, style = BakiType.section)
            Text("RM ${rm(t.net)}", color = neu.ink, style = BakiType.displayNumeric)
            Text(
                "${t.entryCount} entries · ${t.shiftCount} shifts · in RM ${rm(t.income)} · out RM ${rm(t.expense)}",
                color = neu.muted, style = BakiType.labelNumeric,
            )
            Spacer(Modifier.height(Space.l))
            Text("SHIFTS", color = neu.muted, style = BakiType.section)
            Spacer(Modifier.height(Space.s))
            shifts.forEach { sh ->
                val m = shiftMetrics(sh, store.settings.profit)
                Row(Modifier.fillMaxWidth().padding(vertical = Space.xs)) {
                    Text("${sh.date} ${sh.start}", color = neu.ink, style = BakiType.labelNumeric, modifier = Modifier.width(160.dp))
                    Text("${"%.1f".format(sh.hours)} h", color = neu.muted, style = BakiType.labelNumeric, modifier = Modifier.width(80.dp))
                    Text("${"%.1f".format(sh.km)} km", color = neu.muted, style = BakiType.labelNumeric, modifier = Modifier.width(100.dp))
                    Spacer(Modifier.weight(1f))
                    Text("RM ${rm((m.net * 100).toLong())}", color = neu.ink, style = BakiType.labelNumeric)
                }
            }
            Spacer(Modifier.height(Space.l))
            Text("ENTRIES", color = neu.muted, style = BakiType.section)
            Spacer(Modifier.height(Space.s))
            entries.forEach { e ->
                Row(Modifier.fillMaxWidth().padding(vertical = Space.xs)) {
                    Text(e.date, color = neu.muted, style = BakiType.labelNumeric, modifier = Modifier.width(120.dp))
                    Text(e.cat, color = neu.ink, style = BakiType.label, modifier = Modifier.width(150.dp))
                    Text(e.note.ifBlank { "—" }, color = neu.muted, style = BakiType.label, modifier = Modifier.weight(1f))
                    Text(
                        (if (e.type == "income") "+" else "−") + "RM ${rm(e.amount)}",
                        color = neu.ink, style = BakiType.labelNumeric,
                    )
                }
            }
            Spacer(Modifier.height(Space.l))
            Text("Generated ${todayIso()} · figures in Malaysian ringgit.", color = neu.muted, style = BakiType.caption)
        }
    }
}

// ------------------------------------------------------------------- utils

private val MONTHS = listOf(
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December",
)

internal fun shiftMonth(iso: String, delta: Int): String {
    var y = iso.substring(0, 4).toIntOrNull() ?: return iso
    var m = (iso.substring(5, 7).toIntOrNull() ?: 1) + delta
    if (m < 1) { m = 12; y -= 1 }
    if (m > 12) { m = 1; y += 1 }
    return "%04d-%02d".format(y, m)
}

private fun weekdayShort(iso: String): String =
    listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")[
        kotlinx.datetime.LocalDate.parse(iso).dayOfWeek.ordinal.coerceIn(0, 6),
    ]

/** A card: the base material extruded with the card tier of the shadow scale. */
@Composable
private fun SectionCard(neu: Neu, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    NeuPanel(neu, Modifier.fillMaxWidth(), corner = Radius.sheet, offset = Elevation.card) {
        Column(Modifier.padding(Space.xl), content = content)
    }
}
