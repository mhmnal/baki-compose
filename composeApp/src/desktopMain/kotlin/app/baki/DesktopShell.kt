package app.baki

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.ui.draw.clipToBounds
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
            Column(Modifier.fillMaxSize().padding(horizontal = Gutter, vertical = Space.m)) {
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
                Spacer(Modifier.height(Space.m))
                Box(Modifier.weight(1f).clipToBounds()) {
                    AnimatedContent(
                        targetState = tab,
                        transitionSpec = { fadeIn(tween(140)) togetherWith fadeOut(tween(90)) },
                        label = "tab",
                    ) { current ->
                        when (current) {
                            0 -> Overview(store, month) { tab = 1 }
                            1 -> EntriesView(store, month)
                            2 -> ShiftsView(store, month)
                            3 -> RoutesView(repo, month, neu)
                            4 -> CompareView(store, month, neu)
                            else -> YearView(store)
                        }
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
        Modifier.raised(neu, corner = Radius.pill, offset = Elevation.low)
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
        Modifier.raised(neu, corner = Radius.pill, offset = Elevation.low)
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
                Modifier.elevated(neu, corner = Radius.small, drop = Elevation.medium)
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
        Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())) {
            SectionCard(neu) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Column(Modifier.weight(1f)) {
                        Text("NET THIS MONTH", color = neu.muted, style = BakiType.section)
                        EasedMoney(t.net, neu.ink, BakiType.displayNumeric)
                        Spacer(Modifier.height(Space.xs))
                        Text(
                            "${t.entryCount} entries · ${t.shiftCount} shifts",
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
            Spacer(Modifier.height(Space.l + Space.xs))
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
                    EntryTable(store, entries.take(9), compact = true)
                }
            }
            Spacer(Modifier.height(Space.l))
        }
        Spacer(Modifier.width(Gutter))
        Column(Modifier.width(Rail2).fillMaxHeight().verticalScroll(rememberScrollState())) {
            NeuPanel(neu, Modifier.fillMaxWidth(), corner = Radius.card) {
                Column(Modifier.padding(Space.l)) {
                    CategoryBlock(store, month, neu)
                    Spacer(Modifier.height(Space.l))
                    Spacer(Modifier.height(Space.xs))
                    Spacer(Modifier.height(Space.l))
                    FundsBlock(store, month, neu)
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
    val half = plot / 2

    Column {
        Text("LAST 7 DAYS", color = neu.muted, style = BakiType.section)
        Spacer(Modifier.height(Space.s))
        NeuWell(
            neu,
            Modifier.fillMaxWidth().semantics { contentDescription = "Last seven days net: $summary" },
            corner = Radius.card,
            offset = Elevation.medium,
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = Space.m, vertical = Space.m)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Space.m),
                ) {
                    days.forEach { (iso, t) ->
                        val g = SignedBars.of(t.net, peakPos, peakNeg)
                        val h = (half * g.fraction).coerceAtLeast(0.dp)
                        val offsetY = if (g.positive) half - h else half
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
                                        .offset(y = half - 0.5.dp)
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
            offset = Elevation.medium,
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
private fun CategoryBlock(store: Store, month: String, neu: Neu) {
    val cats = remember(store) { store.settings.cats.catsOut.associateBy { it.id } }
    val rows = remember(store, month) { store.entries
        .filter { it.date.startsWith(month) && it.type != "income" }
        .groupBy { it.cat }
        .map { (id, list) -> (cats[id]?.name ?: id) to list.sumOf { it.amount } }
        .sortedByDescending { it.second }
        .take(6) }
    val total = rows.sumOf { it.second }.coerceAtLeast(1)
    Text("WHERE IT WENT", color = neu.muted, style = BakiType.section)
    Spacer(Modifier.height(Space.s))
    NeuBar(
        neu = neu,
        fraction = 1f,
        modifier = Modifier.fillMaxWidth().height(12.dp),
        segments = rows.mapIndexed { i, (_, cents) ->
            (cents.toFloat() / total).coerceAtLeast(0.002f) to routeColorFor(LocalBaki.current, i)
        },
    )
    Spacer(Modifier.height(Space.m))
    rows.forEachIndexed { i, (name, cents) ->
        Row(Modifier.fillMaxWidth().padding(vertical = Space.xxs), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(9.dp).clip(RoundedCornerShape(Radius.pill))
                    .raised(neu, corner = 6.dp, offset = 2.dp, fill = routeColorFor(LocalBaki.current, i)),
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
            Modifier.fillMaxSize().padding(vertical = Space.s).verticalScroll(scroll),
        ) {
            SectionCard(neu) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    NeuField(query, { query = it }, "Filter", Modifier.width(320.dp))
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
                SectionCard(neu) { EntryTable(store, entries, compact = false) }
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
    val baki = LocalBaki.current
    val neu = rememberNeu()
    val shifts = store.shifts.filter { it.date.startsWith(month) }.sortedByDescending { it.date }
    val net = shifts.sumOf { shiftMetrics(it, store.settings.profit).net }
    val scroll = rememberScrollState()
    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize().padding(vertical = Space.s).verticalScroll(scroll)) {
        SectionCard(neu) {
        val sumHours = shifts.sumOf { it.hours }
        val avgRate = if (sumHours > 0.01) net / sumHours else 0.0
        val avgKm = if (shifts.isNotEmpty()) shifts.sumOf { it.km } / shifts.size else 0.0
        Text(
            "${shifts.size} shifts · net RM ${"%.2f".format(net)} · avg RM ${"%.2f".format(avgRate)}/h · avg ${"%.0f".format(avgKm)} km",
            color = neu.ink, style = BakiType.label,
        )
        Spacer(Modifier.height(Space.m))
        Row(Modifier.fillMaxWidth().padding(vertical = Space.s)) {
            Head("DATE", Modifier.width(150.dp))
            Head("HOURS", Modifier.width(90.dp))
            Head("KM", Modifier.width(90.dp))
            Head("GROSS", Modifier.width(130.dp))
            Head("NET", Modifier.width(130.dp))
            Head("RATE", Modifier.weight(1f))
        }
        if (shifts.isEmpty()) {
            NeuEmpty(neu, "No shifts this month", "Shifts appear here once you start one on the phone.")
        }
        Column {
            shifts.forEach { s ->
                val m = shiftMetrics(s, store.settings.profit)
                val hover = remember { MutableInteractionSource() }
                val hovered by hover.collectIsHoveredAsState()
                Row(
                    Modifier.fillMaxWidth()
                        .then(if (hovered) Modifier.raised(neu, corner = Radius.input, offset = Elevation.low) else Modifier)
                        .padding(horizontal = Space.s, vertical = Space.m),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("${s.date} ${s.start}", color = neu.ink, style = BakiType.labelNumeric, modifier = Modifier.width(150.dp))
                    Text("${"%.1f".format(s.hours)}h", color = neu.muted, style = BakiType.labelNumeric, modifier = Modifier.width(90.dp))
                    Text("${"%.1f".format(s.km)}", color = neu.muted, style = BakiType.labelNumeric, modifier = Modifier.width(90.dp))
                    Text("RM ${rm(s.gross)}", color = neu.muted, style = BakiType.labelNumeric, modifier = Modifier.width(130.dp))
                    Text("RM ${rm((m.net * 100).toLong())}", color = neu.ink, style = BakiType.labelNumeric, modifier = Modifier.width(130.dp))
                    Text("RM ${"%.2f".format(m.net / s.hours)}/h", color = neu.muted, style = BakiType.labelNumeric)
                }
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

private enum class TableSort { Date, Category, Amount }

@Composable
private fun EntryTable(
    store: Store,
    entries: List<Entry>,
    compact: Boolean,
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
            Head("NOTE", Modifier.weight(1f))
            Head("AMOUNT" + if (sort == TableSort.Amount) " ↓" else "", Modifier.width(120.dp), Alignment.End) { sort = TableSort.Amount }
        }
        Column {
            sorted.take(if (compact) 9 else sorted.size).forEach { entry ->
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = Space.s, vertical = Space.m),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(entry.date, color = neu.ink, style = BakiType.labelNumeric, modifier = Modifier.width(120.dp))
                    Row(Modifier.width(170.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(categoryIcon(entry.cat), contentDescription = null, tint = neu.muted, modifier = Modifier.size(IconSize.inline))
                        Spacer(Modifier.width(Space.s))
                        Text(cats[entry.cat]?.name ?: entry.cat, color = neu.ink, style = BakiType.label)
                    }
                    Text(
                        entry.note.ifBlank { "—" }, color = neu.muted, style = BakiType.label,
                        maxLines = 1, modifier = Modifier.weight(1f),
                    )
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
internal fun YearView(store: Store) {
    val baki = LocalBaki.current
    val neu = rememberNeu()
    val year = todayIso().substring(0, 4).toIntOrNull() ?: 2026
    val months = remember(store, year) {
        (1..12).map { m ->
            val label = "%04d-%02d".format(year, m)
            label to monthTotals(store, label)
        }
    }
    val peak = months.maxOfOrNull { kotlin.math.abs(it.second.net) }?.coerceAtLeast(1) ?: 1

    Column(Modifier.fillMaxSize().padding(vertical = Space.s).verticalScroll(rememberScrollState())) {
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
                }
            }
        }
        Spacer(Modifier.height(Space.l))
        SectionCard(neu) {
        months.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.xl)) {
                row.forEach { (label, t) ->
                    Column(Modifier.weight(1f).padding(vertical = Space.m)) {
                        val idle = t.entryCount == 0 && t.shiftCount == 0
                        val best = label == months.maxByOrNull { it.second.net }?.first && !idle
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                monthLabel(label),
                                color = (if (idle) neu.muted else neu.ink).copy(alpha = if (idle) 0.55f else 1f),
                                style = BakiType.label, modifier = Modifier.weight(1f),
                            )
                            Text(
                                "RM ${rm(t.net)}",
                                color = when {
                                    best -> neu.accent
                                    idle -> neu.muted.copy(alpha = 0.55f)
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
                    Text("DRIVES · ${routes.size} of ${shifts.size}", color = neu.muted, style = BakiType.section)
                    Spacer(Modifier.weight(1f))
                    NeuButton(neu, "Pull") { reload++ }
                }
                Spacer(Modifier.height(Space.s))
                Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())) {
                    shifts.forEach { s ->
                        val has = routes.containsKey(s.id)
                        val m = shiftMetrics(s, store.settings.profit)
                        val isSel = s.id == selected
                        Row(
                            Modifier.fillMaxWidth()
                                .then(
                                    if (isSel) Modifier.inset(neu, corner = Radius.input, offset = Elevation.medium)
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
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (loading) Text("Loading routes…", color = neu.muted, style = BakiType.body)
                    else NeuEmpty(neu, "No route for this shift", "GPS points may not have been recorded, or the route was pruned.")
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
        Column(Modifier.fillMaxSize().padding(vertical = Space.s).verticalScroll(scroll)) {
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
            Spacer(Modifier.height(Space.l))
            SectionCard(neu) {
                Row(Modifier.fillMaxWidth().padding(vertical = Space.xs)) {
                    Head("METRIC", Modifier.weight(1.2f))
                    Head(monthLabel(a).uppercase(), Modifier.weight(1f), Alignment.End)
                    Head(monthLabel(b).uppercase(), Modifier.weight(1f), Alignment.End)
                    Head("CHANGE", Modifier.weight(0.9f), Alignment.End)
                }
                rows.forEach { r ->
                    val deltaColor = when {
                        r.higherIsBetter == null -> neu.muted
                        (r.deltaLabel?.startsWith("-") == true) == r.higherIsBetter -> neu.danger
                        else -> neu.accent
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = Space.s),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(r.label, color = neu.ink, style = BakiType.label, modifier = Modifier.weight(1.2f))
                        Text(r.a, color = neu.ink, style = BakiType.labelNumeric, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
                        Text(r.b, color = neu.ink, style = BakiType.labelNumeric, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
                        val label = r.deltaLabel
                        val arrow = when {
                            label == null || label == "—" -> ""
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
            Spacer(Modifier.height(Space.l))
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
            androidx.compose.foundation.text.BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = BakiType.label.copy(color = neu.ink),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(neu.accent),
                interactionSource = interaction,
                modifier = Modifier.fillMaxWidth().padding(horizontal = Space.m, vertical = Space.s),
            )
        }
    }
}

@Composable
private fun DesktopSettings(repo: BakiRepo, onDismiss: () -> Unit) {
    val baki = LocalBaki.current
    val neu = rememberNeu()
    val profit = repo.store.settings.profit
    var fuel by remember { mutableStateOf("%.2f".format(profit.fuelPrice)) }
    var kml by remember { mutableStateOf("%.1f".format(profit.kmPerLitre)) }
    var wear by remember { mutableStateOf("%.2f".format(profit.wearPerKm)) }
    var target by remember { mutableStateOf("%.0f".format(profit.targetRate)) }
    var routeKey by remember { mutableStateOf(repo.config.routeSecret) }
    var routeSync by remember { mutableStateOf(repo.config.syncRoutes) }
    val scope = rememberCoroutineScope()

    NeuDialog(neu, onDismiss, width = 580.dp) {
        Text("Settings", color = neu.ink, style = BakiType.title)
        Spacer(Modifier.height(Space.l))
        Text("COLOUR", color = neu.muted, style = BakiType.section)
        Spacer(Modifier.height(Space.s))
        NeuSegmented(
            neu,
            listOf("sage" to "Sage", "night" to "Night", "paper" to "Paper", "mist" to "Mist"),
            repo.config.palette,
        ) { repo.setPalette(it) }
        Spacer(Modifier.height(Space.l))
        Text("APPEARANCE", color = neu.muted, style = BakiType.section)
        Spacer(Modifier.height(Space.s))
        NeuSegmented(
            neu,
            listOf("auto" to "Auto", "light" to "Light", "dark" to "Dark"),
            repo.config.themeMode,
        ) { repo.setThemeMode(it) }
        Spacer(Modifier.height(Space.l))
        Text("VEHICLE & TARGET", color = neu.muted, style = BakiType.section)
        Spacer(Modifier.height(Space.s))
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
        NeuSegmented(
            neu,
            listOf(false to "Off", true to "On"),
            routeSync,
        ) { on -> routeSync = on; repo.setRouteSync(on, routeKey) }
        Spacer(Modifier.height(Space.s))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            NeuField(routeKey, { routeKey = it }, "shared route key", Modifier.weight(1f))
            NeuButton(neu, "Save key") { repo.setRouteSync(routeSync, routeKey) }
        }
        Spacer(Modifier.height(Space.l))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            NeuButton(neu, "Close", onClick = onDismiss)
            NeuButton(neu, "Save costs", primary = true) {
                val updated = profit.copy(
                    fuelPrice = fuel.toDoubleOrNull() ?: profit.fuelPrice,
                    kmPerLitre = kml.toDoubleOrNull() ?: profit.kmPerLitre,
                    wearPerKm = wear.toDoubleOrNull() ?: profit.wearPerKm,
                    targetRate = target.toDoubleOrNull() ?: profit.targetRate,
                )
                scope.launch { repo.updateProfit(updated); onDismiss() }
            }
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
                .elevated(neu, corner = Radius.sheet, drop = Elevation.high)
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
    NeuPanel(neu, Modifier.fillMaxWidth(), corner = Radius.sheet, offset = Elevation.medium) {
        Column(Modifier.padding(Space.xl), content = content)
    }
}
