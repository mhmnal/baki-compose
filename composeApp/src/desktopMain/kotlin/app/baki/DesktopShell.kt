package app.baki

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
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
private val Gutter = 28.dp
private val ToolbarHeight = 64.dp
private val Rail2 = 320.dp

@Composable
actual fun DesktopRoot(repo: BakiRepo): Boolean {
    val store = repo.store
    val baki = LocalBaki.current
    val neu = neuOf(baki)
    // BAKI_VIEW lets the design be captured deterministically (entries/shifts/year/settings/statement)
    val preview = remember { System.getenv("BAKI_VIEW").orEmpty() }
    var month by remember { mutableStateOf(todayIso().substring(0, 7)) }
    var tab by remember {
        mutableStateOf(when (preview) { "entries" -> 1; "shifts" -> 2; "year" -> 3; else -> 0 })
    }
    var editing by remember { mutableStateOf<Entry?>(null) }
    var adding by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Entry?>(null) }
    var statement by remember { mutableStateOf(preview == "statement") }
    var settingsOpen by remember { mutableStateOf(preview == "settings") }
    val scope = rememberCoroutineScope()
    val rootFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        // a laptop is a full editor: writes must reach the server
        if (!repo.config.allowWrites) repo.setAllowWrites(true)
        repo.start()
        runCatching { rootFocus.requestFocus() }
    }

    DesktopActions.exportCsv = {
        PlatformEffects.exportFile("baki-${todayStamp()}.csv", "text/csv", exportCsv(repo.store))
    }
    DesktopActions.statement = { statement = true }
    DesktopActions.newEntry = { adding = true }
    DesktopActions.applyTheme = { repo.setPalette(it) }

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
                    e.isCtrlPressed && e.key == Key.N -> { adding = true; true }
                    e.key == Key.Escape -> {
                        statement = false; settingsOpen = false; adding = false; editing = null; deleting = null
                        true
                    }
                    else -> false
                }
            },
    ) {
        Row(
            Modifier.fillMaxSize().padding(Space.l),
            horizontalArrangement = Arrangement.spacedBy(Space.m),
        ) {
            Rail(neu, tab, onSelect = { tab = it }, onSettings = { settingsOpen = true })
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.m)) {
                Toolbar(
                    neu = neu,
                    month = month,
                    status = repo.status,
                    onPrev = { month = shiftMonth(month, -1) },
                    onNext = { month = shiftMonth(month, 1) },
                    onStatement = { statement = true },
                    onNew = { adding = true },
                )
                NeuPanel(neu, Modifier.weight(1f), corner = Radius.sheet) {
                    AnimatedContent(
                        targetState = tab,
                        transitionSpec = { fadeIn(tween(140)) togetherWith fadeOut(tween(90)) },
                        label = "tab",
                    ) { current ->
                        when (current) {
                            0 -> Overview(store, month, { editing = it }, { deleting = it }, { tab = 1 })
                            1 -> EntriesView(store, month, { editing = it }, { deleting = it })
                            2 -> ShiftsView(store, month)
                            else -> YearView(store)
                        }
                    }
                }
            }
        }
    }

    if (statement) StatementPreview(store, month) { statement = false }
    if (settingsOpen) DesktopSettings(repo) { settingsOpen = false }
    if (adding) {
        EntryDialog(store, month, null, { adding = false }) { date, cat, cents, note ->
            scope.launch { repo.addEntry("expense", cents, cat, note, date); adding = false }
        }
    }
    editing?.let { entry ->
        EntryDialog(store, month, entry, { editing = null }) { date, cat, cents, note ->
            scope.launch { repo.updateEntry(entry, entry.type, cents, cat, note, date); editing = null }
        }
    }
    deleting?.let { entry ->
        DeleteDialog(entry, { deleting = null }) { scope.launch { repo.deleteEntry(entry.id); deleting = null } }
    }
    return true
}

/** Actions the window and the rail can trigger. */
object DesktopActions {
    var exportCsv: () -> Unit = {}
    var statement: () -> Unit = {}
    var newEntry: () -> Unit = {}
    var applyTheme: (String) -> Unit = { }
}

// ------------------------------------------------------------------ chrome

@Composable
private fun Rail(neu: Neu, selected: Int, onSelect: (Int) -> Unit, onSettings: () -> Unit) {
    val items = listOf(
        "Overview" to BakiIcons.Today,
        "Entries" to BakiIcons.Receipt,
        "Shifts" to BakiIcons.Taxi,
        "Year" to BakiIcons.Insights,
    )
    NeuPanel(neu, Modifier.width(RailWidth).fillMaxHeight(), corner = Radius.sheet) {
        Column(
            Modifier.fillMaxHeight().padding(vertical = Space.l),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Space.s),
        ) {
            Text("B", color = neu.accent, style = BakiType.headline)
            Spacer(Modifier.height(Space.s))
            items.forEachIndexed { i, (label, icon) ->
                Tip(label) {
                    NeuIconButton(neu, Modifier.size(48.dp), active = selected == i, onClick = { onSelect(i) }) {
                        Icon(
                            icon, contentDescription = label,
                            tint = if (selected == i) neu.accent else neu.ink,
                            modifier = Modifier.size(IconSize.bar),
                        )
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            Tip("Settings") {
                NeuIconButton(neu, Modifier.size(48.dp), onClick = onSettings) {
                    Icon(BakiIcons.Settings, contentDescription = "Settings", tint = neu.ink, modifier = Modifier.size(IconSize.bar))
                }
            }
        }
    }
}

@Composable
private fun Toolbar(
    neu: Neu,
    month: String,
    status: String,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onStatement: () -> Unit,
    onNew: () -> Unit,
) {
    NeuPanel(neu, Modifier.fillMaxWidth().height(ToolbarHeight), corner = Radius.sheet) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = Space.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Tip("Previous month") {
                NeuIconButton(neu, Modifier.size(40.dp), onClick = onPrev) {
                    Icon(BakiIcons.ArrowRight, contentDescription = "Previous month", tint = neu.ink,
                        modifier = Modifier.size(IconSize.inline).rotate180())
                }
            }
            Text(
                monthLabel(month), color = neu.ink, style = BakiType.subtitle,
                modifier = Modifier.padding(horizontal = Space.m),
            )
            Tip("Next month") {
                NeuIconButton(neu, Modifier.size(40.dp), onClick = onNext) {
                    Icon(BakiIcons.ArrowRight, contentDescription = "Next month", tint = neu.ink,
                        modifier = Modifier.size(IconSize.inline))
                }
            }
            Spacer(Modifier.width(Space.l))
            Box(
                Modifier.size(8.dp).clip(RoundedCornerShape(50))
                    .background(if (status == "synced") neu.accent else neu.danger),
            )
            Spacer(Modifier.width(Space.xs))
            Text(status, color = neu.muted, style = BakiType.caption)
            Spacer(Modifier.weight(1f))
            NeuButton(neu, "Statement", onClick = onStatement)
            Spacer(Modifier.width(Space.s))
            NeuButton(neu, "New entry", primary = true, onClick = onNew)
        }
    }
}

private fun Modifier.rotate180(): Modifier = this.then(Modifier.rotate(180f))

@OptIn(ExperimentalComposeUiApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun Tip(text: String, content: @Composable () -> Unit) {
    val neu = LocalBaki.current.let { neuOf(it) }
    TooltipArea(
        tooltip = {
            Box(
                Modifier.raised(neu, corner = Radius.small, offset = Elevation.low)
                    .clip(RoundedCornerShape(Radius.small))
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
    onEdit: (Entry) -> Unit,
    onDelete: (Entry) -> Unit,
    onOpen: () -> Unit,
) {
    val baki = LocalBaki.current
    val neu = neuOf(baki)
    val t = monthTotals(store, month)
    val entries = store.entries.filter { it.date.startsWith(month) }.sortedByDescending { it.date }
    val week = latestWeekWithData(store, todayIso())
    val streak = targetStreak(store, todayIso())

    Row(Modifier.fillMaxSize().padding(Gutter)) {
        Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())) {
            Text("NET THIS MONTH", color = neu.muted, style = BakiType.section)
            EasedMoney(t.net, neu.ink, BakiType.displayNumeric)
            Spacer(Modifier.height(Space.xs))
            Text(
                "${t.entryCount} entries · ${t.shiftCount} shifts · in RM ${rm(t.income)} · out RM ${rm(t.expense)}",
                color = neu.muted, style = BakiType.labelNumeric,
            )
            Spacer(Modifier.height(Space.xl))
            WeekChart(store, neu)
            Spacer(Modifier.height(Space.xl))
            MonthTrend(store, month, neu)
            Spacer(Modifier.height(Space.xl))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("RECENT", color = neu.muted, style = BakiType.section)
                Spacer(Modifier.weight(1f))
                NeuButton(neu, "All entries", onClick = onOpen)
            }
            Spacer(Modifier.height(Space.s))
            if (entries.isEmpty()) {
                NeuEmpty(neu, "Nothing this month", "Entries you log will appear here, newest first.")
            } else {
                EntryTable(store, entries.take(9), onEdit, onDelete, compact = true)
            }
            Spacer(Modifier.height(Space.xl))
        }
        Spacer(Modifier.width(Gutter))
        Column(Modifier.width(Rail2).fillMaxHeight().verticalScroll(rememberScrollState())) {
            NeuPanel(neu, Modifier.fillMaxWidth(), corner = Radius.card) {
                Column(Modifier.padding(Space.l)) {
                    CategoryBlock(store, month, neu)
                    Spacer(Modifier.height(Space.l))
                    Box(Modifier.fillMaxWidth().height(2.dp).groove(neu))
                    Spacer(Modifier.height(Space.l))
                    FundsBlock(store, month, neu)
                    Spacer(Modifier.height(Space.l))
                    Box(Modifier.fillMaxWidth().height(2.dp).groove(neu))
                    Spacer(Modifier.height(Space.l))
                    week?.let { (start, r) ->
                        NeuMetric(
                            neu, "LAST WEEK",
                            "${"%.1f".format(r.hours)} h · RM ${rm(r.net)}",
                            detail = "RM ${"%.2f".format(r.perHour)}/h · ${prettyDate(start)}",
                        )
                        if (streak >= 2) {
                            Spacer(Modifier.height(Space.xs))
                            Text("$streak days above target", color = neu.accent, style = BakiType.caption)
                        }
                    }
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
    val peak = days.maxOfOrNull { kotlin.math.abs(it.second.net) }?.coerceAtLeast(1) ?: 1
    Column {
        Text("LAST 7 DAYS", color = neu.muted, style = BakiType.section)
        Spacer(Modifier.height(Space.s))
        val weekSummary = days.joinToString(", ") { (iso, t) -> "${weekdayShort(iso)} ${rm(t.net)}" }
        NeuPanel(
            neu,
            Modifier.fillMaxWidth().semantics { contentDescription = "Last seven days net: $weekSummary" },
            corner = Radius.card,
        ) {
            Row(
                Modifier.fillMaxWidth().padding(Space.m),
                horizontalArrangement = Arrangement.spacedBy(Space.m),
                verticalAlignment = Alignment.Bottom,
            ) {
                days.forEach { (iso, t) ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            if (t.entryCount == 0 && t.shiftCount == 0) "–" else rm(t.net),
                            color = if (t.net >= 0) neu.muted else neu.danger,
                            style = BakiType.captionNumeric,
                        )
                        Spacer(Modifier.height(Space.xs))
                        Box(Modifier.fillMaxWidth().height(88.dp), contentAlignment = Alignment.BottomCenter) {
                            val frac = (kotlin.math.abs(t.net).toFloat() / peak).coerceIn(0.05f, 1f)
                            Box(
                                Modifier.fillMaxWidth(0.62f).height((88 * frac).dp)
                                    .raised(neu, corner = 10.dp, offset = 3.dp,
                                        fill = if (t.net >= 0) neu.accent else neu.danger)
                                    .clip(RoundedCornerShape(Radius.small)),
                            )
                        }
                        Spacer(Modifier.height(Space.xs))
                        Text(weekdayShort(iso), color = neu.muted, style = BakiType.caption)
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
    val peak = days.maxOfOrNull { kotlin.math.abs(it.second.net) }?.coerceAtLeast(1) ?: 1
    Column {
        Text("TREND", color = neu.muted, style = BakiType.section)
        Spacer(Modifier.height(Space.s))
        NeuPanel(
            neu,
            Modifier.fillMaxWidth().semantics {
                contentDescription = "Month trend, peak RM ${rm(days.maxOfOrNull { it.second.net } ?: 0)}"
            },
            corner = Radius.card,
        ) {
            Column(Modifier.padding(Space.m)) {
                Row(
                    Modifier.fillMaxWidth().height(120.dp),
                    horizontalArrangement = Arrangement.spacedBy(Space.xxs),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    days.forEach { (_, t) ->
                        Box(Modifier.weight(1f).height(120.dp), contentAlignment = Alignment.BottomCenter) {
                            val frac = (kotlin.math.abs(t.net).toFloat() / peak).coerceIn(0.02f, 1f)
                            Box(
                                Modifier.fillMaxWidth().height((120 * frac).dp)
                                    .raised(neu, corner = 8.dp, offset = 2.dp,
                                        fill = if (t.net >= 0) neu.accent else neu.danger)
                                    .clip(RoundedCornerShape(Radius.small)),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(Space.s))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(days.first().first.takeLast(5), color = neu.muted, style = BakiType.captionNumeric)
                    Text("peak RM ${rm(days.maxOfOrNull { it.second.net } ?: 0)}", color = neu.muted, style = BakiType.captionNumeric)
                    Text(days.last().first.takeLast(5), color = neu.muted, style = BakiType.captionNumeric)
                }
            }
        }
    }
}

@Composable
private fun CategoryBlock(store: Store, month: String, neu: Neu) {
    val cats = store.settings.cats.catsOut.associateBy { it.id }
    val rows = store.entries
        .filter { it.date.startsWith(month) && it.type != "income" }
        .groupBy { it.cat }
        .map { (id, list) -> (cats[id]?.name ?: id) to list.sumOf { it.amount } }
        .sortedByDescending { it.second }
        .take(6)
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
                Modifier.size(9.dp).clip(RoundedCornerShape(50))
                    .raised(neu, corner = 6.dp, offset = 2.dp, fill = routeColorFor(LocalBaki.current, i)),
            )
            Spacer(Modifier.width(Space.s))
            Text(name, color = neu.ink, style = BakiType.label)
            Spacer(Modifier.weight(1f))
            Text("RM ${rm(cents)}", color = neu.muted, style = BakiType.labelNumeric)
            Spacer(Modifier.width(Space.s))
            Text("${"%.0f".format(cents * 100.0 / total)}%", color = neu.muted, style = BakiType.captionNumeric)
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
            Text(
                "RM ${rm(accrued - spent)}",
                color = if (accrued - spent >= 0) neu.accent else neu.danger,
                style = BakiType.labelNumeric,
            )
        }
        Text("accrued RM ${rm(accrued)} · spent RM ${rm(spent)}", color = neu.muted, style = BakiType.captionNumeric)
        Spacer(Modifier.height(Space.xs))
    }
}

// ------------------------------------------------------------------ tables

@Composable
internal fun EntriesView(store: Store, month: String, onEdit: (Entry) -> Unit, onDelete: (Entry) -> Unit) {
    val baki = LocalBaki.current
    val neu = neuOf(baki)
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<String?>(null) }
    val tableFocus = remember { FocusRequester() }
    val entries = store.entries
        .filter { it.date.startsWith(month) }
        .filter { e -> query.isBlank() || (e.cat + " " + e.note).contains(query, ignoreCase = true) }
        .sortedByDescending { it.date }
    LaunchedEffect(Unit) { runCatching { tableFocus.requestFocus() } }

    fun move(delta: Int) {
        if (entries.isEmpty()) return
        val current = entries.indexOfFirst { it.id == selected }
        val next = when {
            current < 0 -> 0
            else -> (current + delta).coerceIn(0, entries.lastIndex)
        }
        selected = entries[next].id
    }

    val scroll = rememberScrollState()
    Box(Modifier.fillMaxSize()) {
    Column(
        Modifier.fillMaxSize().padding(Gutter).verticalScroll(scroll)
            .focusRequester(tableFocus).focusable()
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (e.key) {
                    Key.DirectionDown -> { move(1); true }
                    Key.DirectionUp -> { move(-1); true }
                    Key.Enter, Key.NumPadEnter ->
                        entries.firstOrNull { it.id == selected }?.let { onEdit(it); true } ?: false
                    Key.Delete, Key.Backspace ->
                        entries.firstOrNull { it.id == selected }?.let { onDelete(it); true } ?: false
                    Key.Escape -> { selected = null; true }
                    else -> false
                }
            },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            NeuField(query, { query = it }, "Filter", Modifier.width(320.dp))
            Spacer(Modifier.width(Space.m))
            Text(
                "${entries.size} entries · RM ${rm(entries.filter { it.type != "income" }.sumOf { it.amount })} out",
                color = neu.muted, style = BakiType.label,
            )
            Spacer(Modifier.weight(1f))
            Text("↑ ↓ select · Enter edit · Delete remove", color = neu.muted, style = BakiType.caption)
        }
        Spacer(Modifier.height(Space.m))
        if (entries.isEmpty()) {
            NeuEmpty(
                neu,
                "Nothing this month",
                if (query.isBlank()) "Entries you log will appear here, newest first." else "No entry matches that filter.",
            )
        } else {
            EntryTable(store, entries, onEdit, onDelete, compact = false, selectedId = selected, onSelect = { selected = it })
        }
    }
        androidx.compose.foundation.VerticalScrollbar(
            adapter = androidx.compose.foundation.rememberScrollbarAdapter(scroll),
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(Space.xs),
        )
    }
}

@Composable
internal fun ShiftsView(store: Store, month: String) {
    val baki = LocalBaki.current
    val neu = neuOf(baki)
    val shifts = store.shifts.filter { it.date.startsWith(month) }.sortedByDescending { it.date }
    val net = shifts.sumOf { shiftMetrics(it, store.settings.profit).net }
    val scroll = rememberScrollState()
    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize().padding(Gutter).verticalScroll(scroll)) {
        Text("${shifts.size} shifts · net RM ${"%.2f".format(net)}", color = neu.muted, style = BakiType.label)
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
        androidx.compose.foundation.VerticalScrollbar(
            adapter = androidx.compose.foundation.rememberScrollbarAdapter(scroll),
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(Space.xs),
        )
    }
}

private enum class TableSort { Date, Category, Amount }

@Composable
private fun EntryTable(
    store: Store,
    entries: List<Entry>,
    onEdit: (Entry) -> Unit,
    onDelete: (Entry) -> Unit,
    compact: Boolean,
    selectedId: String? = null,
    onSelect: (String) -> Unit = {},
) {
    val baki = LocalBaki.current
    val neu = neuOf(baki)
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
                val hover = remember { MutableInteractionSource() }
                val hovered by hover.collectIsHoveredAsState()
                val isSelected = entry.id == selectedId
                var menu by remember { mutableStateOf(false) }
                Box {
                    Row(
                        Modifier.fillMaxWidth()
                            .then(
                                when {
                                    isSelected -> Modifier.inset(neu, corner = Radius.input)
                                    hovered -> Modifier.raised(neu, corner = Radius.input, offset = Elevation.low)
                                    else -> Modifier
                                },
                            )
                            .clip(RoundedCornerShape(Radius.input))
                            .hoverableRow(hover)
                            .pointerHoverIcon(PointerIcon.Hand)
                            .pointerInput(entry.id) {
                                var last = 0L
                                awaitPointerEventScope {
                                    while (true) {
                                        val e = awaitPointerEvent()
                                        if (e.type == androidx.compose.ui.input.pointer.PointerEventType.Press) {
                                            if (e.buttons.isSecondaryPressed) menu = true
                                            if (e.buttons.isPrimaryPressed) {
                                                val now = System.currentTimeMillis()
                                                if (now - last < 350) onEdit(entry)
                                                last = now
                                            }
                                        }
                                    }
                                }
                            }
                            .clickable(interactionSource = hover, indication = null) { onSelect(entry.id) }
                            .padding(horizontal = Space.s, vertical = Space.m),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(entry.date, color = neu.muted, style = BakiType.labelNumeric, modifier = Modifier.width(120.dp))
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
                    androidx.compose.material3.DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text("Edit", style = BakiType.label) },
                            onClick = { menu = false; onEdit(entry) },
                        )
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text("Delete", style = BakiType.label) },
                            onClick = { menu = false; onDelete(entry) },
                        )
                    }
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
    val neu = neuOf(LocalBaki.current)
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
    val neu = neuOf(baki)
    val year = todayIso().substring(0, 4).toIntOrNull() ?: 2026
    val months = (1..12).map { m ->
        val label = "%04d-%02d".format(year, m)
        label to monthTotals(store, label)
    }
    val peak = months.maxOfOrNull { kotlin.math.abs(it.second.net) }?.coerceAtLeast(1) ?: 1

    Column(Modifier.fillMaxSize().padding(Gutter).verticalScroll(rememberScrollState())) {
        Text("$year AT A GLANCE", color = neu.muted, style = BakiType.section)
        Spacer(Modifier.height(Space.m))
        months.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.xl)) {
                row.forEach { (label, t) ->
                    Column(Modifier.weight(1f).padding(vertical = Space.s)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                monthLabel(label),
                                color = if (t.entryCount == 0 && t.shiftCount == 0) neu.muted else neu.ink,
                                style = BakiType.label, modifier = Modifier.weight(1f),
                            )
                            Text("RM ${rm(t.net)}", color = neu.muted, style = BakiType.labelNumeric)
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
        Spacer(Modifier.height(Space.xl))
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
    val neu = neuOf(LocalBaki.current)
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
                modifier = Modifier.fillMaxWidth().padding(horizontal = Space.m, vertical = Space.s + 2.dp),
            )
        }
    }
}

@Composable
private fun EntryDialog(
    store: Store,
    month: String,
    existing: Entry?,
    onDismiss: () -> Unit,
    onSave: (date: String, cat: String, cents: Long, note: String) -> Unit,
) {
    val neu = neuOf(LocalBaki.current)
    val cats = store.settings.cats.catsOut.filter { it.id != "grabfee" }
    var date by remember { mutableStateOf(existing?.date ?: todayIso()) }
    var amount by remember { mutableStateOf(existing?.amount?.let { "%.2f".format(it / 100.0) } ?: "") }
    var note by remember { mutableStateOf(existing?.note ?: "") }
    var cat by remember { mutableStateOf(existing?.cat ?: cats.firstOrNull()?.id ?: "other") }
    val cents = amount.toDoubleOrNull()?.let { (it * 100).toLong() } ?: 0L

    NeuDialog(neu, onDismiss) {
        Text(if (existing == null) "New entry" else "Edit entry", color = neu.ink, style = BakiType.title)
        Spacer(Modifier.height(Space.l))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            NeuField(date, { date = it }, "Date", Modifier.weight(1f))
            NeuField(amount, { amount = it.filter { c -> c.isDigit() || c == '.' } }, "Amount (RM)", Modifier.weight(1f))
        }
        Spacer(Modifier.height(Space.m))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
            cats.take(5).forEach { c ->
                NeuChip(neu, c.name, selected = cat == c.id) { cat = c.id }
            }
        }
        Spacer(Modifier.height(Space.m))
        NeuField(note, { note = it }, "Note", Modifier.fillMaxWidth())
        Spacer(Modifier.height(Space.l))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            NeuButton(neu, "Cancel", onClick = onDismiss)
            NeuButton(neu, if (existing == null) "Add entry" else "Save changes", primary = true, enabled = cents > 0) {
                if (cents > 0) onSave(date, cat, cents, note)
            }
        }
    }
}

@Composable
private fun DeleteDialog(entry: Entry, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val neu = neuOf(LocalBaki.current)
    NeuDialog(neu, onDismiss) {
        Text("Delete this entry?", color = neu.ink, style = BakiType.title)
        Spacer(Modifier.height(Space.s))
        Text("RM ${rm(entry.amount)} · ${entry.date} · ${entry.cat}", color = neu.muted, style = BakiType.label)
        Spacer(Modifier.height(Space.l))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            NeuButton(neu, "Cancel", onClick = onDismiss)
            NeuButton(neu, "Delete", primary = true, onClick = onConfirm)
        }
    }
}

@Composable
private fun DesktopSettings(repo: BakiRepo, onDismiss: () -> Unit) {
    val baki = LocalBaki.current
    val neu = neuOf(baki)
    val profit = repo.store.settings.profit
    var fuel by remember { mutableStateOf("%.2f".format(profit.fuelPrice)) }
    var kml by remember { mutableStateOf("%.1f".format(profit.kmPerLitre)) }
    var wear by remember { mutableStateOf("%.2f".format(profit.wearPerKm)) }
    var target by remember { mutableStateOf("%.0f".format(profit.targetRate)) }
    val scope = rememberCoroutineScope()

    NeuDialog(neu, onDismiss, width = 580.dp) {
        Text("Settings", color = neu.ink, style = BakiType.title)
        Spacer(Modifier.height(Space.l))
        Text("COLOUR", color = neu.muted, style = BakiType.section)
        Spacer(Modifier.height(Space.s))
        NeuSegmented(
            neu,
            listOf("sage" to "Sage", "night" to "Night", "paper" to "Paper"),
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
    val neu = neuOf(baki)
    val t = monthTotals(store, month)
    val shifts = store.shifts.filter { it.date.startsWith(month) }.sortedBy { it.date }
    val entries = store.entries.filter { it.date.startsWith(month) }.sortedBy { it.date }

    Box(
        Modifier.fillMaxSize().background(neu.dark.copy(alpha = 0.38f)).clickable(
            interactionSource = MutableInteractionSource(), indication = null,
        ) { onDismiss() },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.width(780.dp).heightIn(max = 860.dp)
                .raised(neu, corner = Radius.sheet)
                .clip(RoundedCornerShape(Radius.sheet))
                .background(neu.panel)
                .clickable(interactionSource = MutableInteractionSource(), indication = null) {}
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 48.dp, vertical = 36.dp),
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
