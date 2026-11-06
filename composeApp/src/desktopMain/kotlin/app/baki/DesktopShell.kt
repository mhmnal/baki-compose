package app.baki

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/** Widths and paddings that make the desktop read as a desktop, not a stretched phone. */
private val RailWidth = 92.dp
private val Gutter = 40.dp
private val ContentMax = 1120.dp
private val Rail2 = 300.dp

private val hairline @Composable get() = LocalBaki.current.onSurface.copy(alpha = 0.07f)

private val MONTHS = listOf("January","February","March","April","May","June","July","August","September","October","November","December")

private fun monthLabel(iso: String): String {
    val y = iso.substring(0, 4).toIntOrNull() ?: return iso
    val m = iso.substring(5, 7).toIntOrNull() ?: return iso
    return "${MONTHS[(m - 1).coerceIn(0, 11)]} $y"
}

private fun shiftMonth(iso: String, delta: Int): String {
    var y = iso.substring(0, 4).toIntOrNull() ?: return iso
    var m = (iso.substring(5, 7).toIntOrNull() ?: 1) + delta
    if (m < 1) { m = 12; y -= 1 }
    if (m > 12) { m = 1; y += 1 }
    return "%04d-%02d".format(y, m)
}

@Composable
actual fun DesktopRoot(repo: BakiRepo): Boolean {
    androidx.compose.runtime.LaunchedEffect(Unit) { repo.start() }
    val store = repo.store
    var month by remember { mutableStateOf(todayIso().substring(0, 7)) }
    var tab by remember { mutableStateOf(0) }
    var editing by remember { mutableStateOf<Entry?>(null) }
    var adding by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Entry?>(null) }
    val scope = rememberCoroutineScope()

    DesktopActions.exportCsv = { PlatformEffects.exportFile("baki-${todayStamp()}.csv", "text/csv", exportCsv(repo.store)) }
    DesktopActions.newEntry = { adding = true }
    DesktopActions.applyTheme = { pack -> repo.setPalette(pack) }
    DesktopActions.statement = {
        PlatformEffects.printStatement(statementHtml(repo.store, month))
    }

    Box(Modifier.fillMaxSize().background(LocalBaki.current.surface)) {
        Row(Modifier.fillMaxSize()) {
            NavRail(
                selected = tab,
                onSelect = { tab = it },
            )
            Column(Modifier.weight(1f).fillMaxHeight()) {
                MonthBar(
                    store = store,
                    month = month,
                    onMonth = { month = it },
                    onAdd = { adding = true },
                )
                Box(Modifier.fillMaxWidth().weight(1f)) {
                    when (tab) {
                        0 -> Overview(repo, month, onEdit = { editing = it }, onDelete = { deleting = it }, onOpen = { tab = 1 })
                        1 -> EntriesView(repo, month, onEdit = { editing = it }, onDelete = { deleting = it })
                        2 -> ShiftsView(repo, month)
                        else -> YearView(store)
                    }
                }
            }
        }
    }

    if (adding) {
        DesktopEntryDialog(store = store, month = month, onDismiss = { adding = false }) { date, cat, cents, note ->
            scope.launch { repo.addEntry("expense", cents, cat, note, date); adding = false }
        }
    }
    editing?.let { entry ->
        DesktopEntryDialog(store = store, month = month, existing = entry, onDismiss = { editing = null }) { date, cat, cents, note ->
            scope.launch {
                repo.updateEntry(entry, entry.type, cents, cat, note, date)
                editing = null
            }
        }
    }
    deleting?.let { entry ->
        DeletionDialog(entry) {
            scope.launch { repo.deleteEntry(entry.id); deleting = null }
        }
    }
    return true
}

/** Menu-bar actions the window installs in main(). */
object DesktopActions {
    var exportCsv: () -> Unit = {}
    var statement: () -> Unit = {}
    var newEntry: () -> Unit = {}
    var applyTheme: (String) -> Unit = { }
}

@Composable
private fun NavRail(selected: Int, onSelect: (Int) -> Unit) {
    val baki = LocalBaki.current
    val items = listOf(
        "Overview" to BakiIcons.Today,
        "Entries" to BakiIcons.Receipt,
        "Shifts" to BakiIcons.Taxi,
        "Year" to BakiIcons.Insights,
    )
    Column(
        Modifier.width(RailWidth).fillMaxHeight()
            .background(baki.surface)
            .padding(top = Space.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("B", color = baki.primary, style = BakiType.headline, modifier = Modifier.padding(bottom = Space.xl))
        items.forEachIndexed { i, (label, icon) ->
            val on = selected == i
            val hover = remember { MutableInteractionSource() }
            val hovered by hover.collectIsHoveredAsState()
            Column(
                Modifier
                    .padding(horizontal = Space.s, vertical = Space.xxs)
                    .clip(RoundedCornerShape(Radius.input))
                    .background(if (on) baki.container else Color.Transparent)
                    .hoverable(hover)
                    .pointerHoverIcon(PointerIcon.Hand)
                    .clickable { onSelect(i) }
                    .padding(vertical = Space.s)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    icon, contentDescription = null,
                    tint = if (on) baki.primary else if (hovered) baki.onSurface else baki.muted,
                    modifier = Modifier.size(IconSize.bar),
                )
                Spacer(Modifier.height(Space.xs))
                Text(
                    label,
                    color = if (on) baki.primary else baki.muted,
                    style = BakiType.caption,
                )
            }
        }
    }
}

@Composable
private fun MonthBar(store: Store, month: String, onMonth: (String) -> Unit, onAdd: () -> Unit) {
    val baki = LocalBaki.current
    Row(
        Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = Space.ml),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(monthLabel(month), color = baki.onSurface, style = BakiType.title)
        Spacer(Modifier.width(Space.m))
        Chevron("<") { onMonth(shiftMonth(month, -1)) }
        Chevron(">") { onMonth(shiftMonth(month, 1)) }
        Spacer(Modifier.weight(1f))
        TextButton("Statement") { PlatformEffects.printStatement(statementHtml(store, month)) }
        Spacer(Modifier.width(Space.s))
        TextButton("New entry", primary = true) { onAdd() }
    }
}

@Composable
private fun Chevron(glyph: String, onClick: () -> Unit) {
    val baki = LocalBaki.current
    Box(
        Modifier.size(28.dp).clip(RoundedCornerShape(Radius.small))
            .pointerHoverIcon(PointerIcon.Hand).clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(glyph, color = baki.muted, style = BakiType.label)
    }
}

@Composable
private fun TextButton(label: String, primary: Boolean = false, onClick: () -> Unit) {
    val baki = LocalBaki.current
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    Box(
        Modifier
            .clip(RoundedCornerShape(Radius.small))
            .background(if (primary) baki.primary else if (hovered) baki.container else Color.Transparent)
            .hoverable(hover)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable { onClick() }
            .padding(horizontal = Space.m, vertical = Space.s),
    ) {
        Text(
            label,
            color = if (primary) baki.onPrimary else baki.onSurface,
            style = BakiType.labelStrong,
        )
    }
}

@Composable
private fun Overview(
    repo: BakiRepo,
    month: String,
    onEdit: (Entry) -> Unit,
    onDelete: (Entry) -> Unit,
    onOpen: () -> Unit,
) {
    val baki = LocalBaki.current
    val t = monthTotals(repo.store, month)
    val entries = repo.store.entries
        .filter { it.date.startsWith(month) }
        .sortedByDescending { it.date }
    val week = latestWeekWithData(repo.store, todayIso())
    val streak = targetStreak(repo.store, todayIso())

    Row(Modifier.fillMaxSize().padding(horizontal = Gutter)) {
        Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())) {
            Spacer(Modifier.height(Space.s))
            Row(verticalAlignment = Alignment.Bottom) {
                Column {
                    Text("NET THIS MONTH", color = baki.muted, style = BakiType.section)
                    EasedMoney(t.net, baki.onSurface, BakiType.displayNumeric)
                }
                Spacer(Modifier.width(Space.xl))
                Column(Modifier.padding(bottom = Space.s)) {
                    Text(
                        "${t.entryCount} entries · ${t.shiftCount} shifts",
                        color = baki.muted, style = BakiType.label,
                    )
                    Text(
                        "in RM ${rm(t.income)}  ·  out RM ${rm(t.expense)}",
                        color = baki.muted, style = BakiType.labelNumeric,
                    )
                }
            }
            Spacer(Modifier.height(Space.xl))
            WeekStrip(repo.store, month)
            Spacer(Modifier.height(Space.xl))
            TrendBars(repo.store, month)
            Spacer(Modifier.height(Space.xl))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("RECENT", color = baki.muted, style = BakiType.section)
                Spacer(Modifier.weight(1f))
                Text(
                    "All entries →", color = baki.primary, style = BakiType.labelStrong,
                    modifier = Modifier.pointerHoverIcon(PointerIcon.Hand).clickable { onOpen() },
                )
            }
            Spacer(Modifier.height(Space.s))
            EntriesTable(repo.store, entries.take(9), onEdit = onEdit, onDelete = onDelete, compact = true)
            Spacer(Modifier.height(Space.xxl))
        }
        Spacer(Modifier.width(Gutter))
        Column(Modifier.width(Rail2).fillMaxHeight().verticalScroll(rememberScrollState())) {
            Spacer(Modifier.height(Space.s))
            CategoriesBlock(repo.store, month)
            Hr()
            FundsBlock(repo.store, month)
            Hr()
            week?.let { (start, r) ->
                Text("LAST WEEK", color = baki.muted, style = BakiType.section)
                Spacer(Modifier.height(Space.xs))
                Text(
                    "${"%.1f".format(r.hours)} h · RM ${rm(r.net)} · RM ${"%.2f".format(r.perHour)}/h",
                    color = baki.onSurface, style = BakiType.labelNumeric,
                )
                if (streak >= 2) {
                    Spacer(Modifier.height(Space.xs))
                    Text("$streak days above target", color = baki.up, style = BakiType.caption)
                }
                Spacer(Modifier.height(Space.xs))
                Text(prettyDate(start), color = baki.muted, style = BakiType.caption)
            }
            Spacer(Modifier.height(Space.xxl))
        }
    }
}

@Composable
private fun Hr() {
    Spacer(Modifier.height(Space.l))
    Box(Modifier.fillMaxWidth().height(1.dp).background(hairline))
    Spacer(Modifier.height(Space.l))
}

@Composable
private fun WeekStrip(store: Store, month: String) {
    val baki = LocalBaki.current
    val today = todayIso()
    val days = (6 downTo 0).map { back -> dayTotals(store, addDays(today, -back)) to addDays(today, -back) }
        .map { (t, iso) -> iso to t }
    Column {
        Text("LAST 7 DAYS", color = baki.muted, style = BakiType.section)
        Spacer(Modifier.height(Space.s))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
            val peak = days.maxOfOrNull { kotlin.math.abs(it.second.net) }?.coerceAtLeast(1) ?: 1
            days.forEach { (iso, t) ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        iso.takeLast(2), color = baki.muted, style = BakiType.caption,
                    )
                    Spacer(Modifier.height(Space.xs))
                    Box(
                        Modifier.fillMaxWidth().height(46.dp),
                        contentAlignment = Alignment.BottomCenter,
                    ) {
                        val frac = (kotlin.math.abs(t.net).toFloat() / peak).coerceIn(0.06f, 1f)
                        Box(
                            Modifier.fillMaxWidth()
                                .height((46 * frac).dp)
                                .clip(RoundedCornerShape(Radius.tiny))
                                .background(if (t.net >= 0) baki.primary else baki.down),
                        )
                    }
                    Spacer(Modifier.height(Space.xxs))
                    Text(
                        if (t.entryCount == 0 && t.shiftCount == 0) "–" else rm(t.net),
                        color = baki.muted, style = BakiType.captionNumeric,
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoriesBlock(store: Store, month: String) {
    val baki = LocalBaki.current
    val cats = store.settings.cats.catsOut.associateBy { it.id }
    val rows = store.entries
        .filter { it.date.startsWith(month) && it.type != "income" }
        .groupBy { it.cat }
        .map { (id, list) -> (cats[id]?.name ?: id) to list.sumOf { it.amount } }
        .sortedByDescending { it.second }
        .take(6)
    val total = rows.sumOf { it.second }.coerceAtLeast(1)
    Text("WHERE IT WENT", color = baki.muted, style = BakiType.section)
    Spacer(Modifier.height(Space.s))
    // one stacked bar reads faster than six rows of numbers
    Row(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(Radius.tiny))) {
        rows.forEachIndexed { i, (_, cents) ->
            Box(
                Modifier.weight(cents.toFloat().coerceAtLeast(0.001f)).fillMaxHeight()
                    .background(routeColorFor(baki, i).copy(alpha = 0.85f)),
            )
        }
    }
    Spacer(Modifier.height(Space.m))
    rows.forEachIndexed { i, (name, cents) ->
        Row(Modifier.fillMaxWidth().padding(vertical = Space.xxs), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(RoundedCornerShape(Radius.pill)).background(routeColorFor(baki, i)))
            Spacer(Modifier.width(Space.s))
            Text(name, color = baki.onSurface, style = BakiType.label)
            Spacer(Modifier.weight(1f))
            Text("RM ${rm(cents)}", color = baki.muted, style = BakiType.labelNumeric)
            Spacer(Modifier.width(Space.s))
            Text(
                "${"%.0f".format(cents * 100.0 / total)}%",
                color = baki.muted, style = BakiType.captionNumeric,
            )
        }
    }
}

@Composable
private fun FundsBlock(store: Store, month: String) {
    val baki = LocalBaki.current
    val cfg = store.settings.profit
    val shifts = store.shifts.filter { it.date.startsWith(month) }
    val km = shifts.sumOf { it.km }
    val fuelAccrued = (km / cfg.kmPerLitre * cfg.fuelPrice * 100).toLong()
    val fuelSpent = store.entries.filter { it.date.startsWith(month) && isFuelCat(it.cat) }.sumOf { it.amount }
    val wearAccrued = (km * cfg.wearPerKm * 100).toLong()
    val wearSpent = 0L
    Text("FUNDS", color = baki.muted, style = BakiType.section)
    Spacer(Modifier.height(Space.s))
    listOf(
        "Fuel" to fuelAccrued to fuelSpent,
        "Wear" to wearAccrued to wearSpent,
    ).forEach { (pair, spent) ->
        val (label, accrued) = pair
        Row(Modifier.fillMaxWidth().padding(vertical = Space.xxs)) {
            Text(label, color = baki.onSurface, style = BakiType.label)
            Spacer(Modifier.weight(1f))
            Text(
                "RM ${rm(accrued - spent)}",
                color = if (accrued - spent >= 0) baki.up else baki.down,
                style = BakiType.labelNumeric,
            )
        }
        Text(
            "accrued RM ${rm(accrued)} · spent RM ${rm(spent)}",
            color = baki.muted, style = BakiType.captionNumeric,
        )
        Spacer(Modifier.height(Space.xs))
    }
}

@Composable
private fun EntriesView(repo: BakiRepo, month: String, onEdit: (Entry) -> Unit, onDelete: (Entry) -> Unit) {
    val baki = LocalBaki.current
    var query by remember { mutableStateOf("") }
    val entries = repo.store.entries
        .filter { it.date.startsWith(month) }
        .filter { e -> query.isBlank() || (e.cat + " " + e.note).contains(query, ignoreCase = true) }
        .sortedByDescending { it.date }
    Column(Modifier.fillMaxSize().padding(horizontal = Gutter)) {
        Text(
            "${entries.size} entries · RM ${rm(entries.filter { it.type != "income" }.sumOf { it.amount })} out",
            color = baki.muted, style = BakiType.label,
        )
        Spacer(Modifier.height(Space.m))
        OutlinedTextField(
            value = query, onValueChange = { query = it },
            placeholder = { Text("Filter by category or note", color = baki.muted, style = BakiType.label) },
            singleLine = true, modifier = Modifier.width(320.dp), colors = bakiFieldColorsPublic(),
        )
        Spacer(Modifier.height(Space.m))
        EntriesTable(repo.store, entries, onEdit = onEdit, onDelete = onDelete, compact = false)
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun EntriesTable(
    store: Store,
    entries: List<Entry>,
    onEdit: (Entry) -> Unit,
    onDelete: (Entry) -> Unit,
    compact: Boolean,
) {
    val baki = LocalBaki.current
    val cats = remember(store) { store.settings.cats.catsOut.associateBy { it.id } }
    var sortByAmount by remember { mutableStateOf(false) }
    val sorted = remember(entries, sortByAmount) {
        if (sortByAmount) entries.sortedByDescending { it.amount } else entries
    }

    Column(Modifier.fillMaxWidth()) {
        // header row: aligned columns, clickable sort
        Row(Modifier.fillMaxWidth().padding(vertical = Space.xs)) {
            HeaderCell("DATE", Modifier.width(120.dp))
            HeaderCell("CATEGORY", Modifier.width(160.dp))
            HeaderCell("NOTE", Modifier.weight(1f))
            HeaderCell(
                "AMOUNT " + if (sortByAmount) "↓" else "",
                Modifier.width(120.dp),
                align = Alignment.End,
                onClick = { sortByAmount = !sortByAmount },
            )
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(hairline))
        Column {
            sorted.take(if (compact) 9 else sorted.size).forEach { entry ->
                val hover = remember { MutableInteractionSource() }
                val hovered by hover.collectIsHoveredAsState()
                var menu by remember { mutableStateOf(false) }
                Box {
                    Row(
                        Modifier.fillMaxWidth()
                            .background(if (hovered) baki.container.copy(alpha = 0.55f) else Color.Transparent)
                            .hoverable(hover)
                            .pointerHoverIcon(PointerIcon.Hand)
                            .pointerInput(entry.id) {
                                awaitPointerEventScope {
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        if (event.type == androidx.compose.ui.input.pointer.PointerEventType.Press &&
                                            event.buttons.isSecondaryPressed
                                        ) {
                                            menu = true
                                        }
                                    }
                                }
                            }
                            .clickable { onEdit(entry) }
                            .padding(vertical = Space.m),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(entry.date, color = baki.muted, style = BakiType.labelNumeric, modifier = Modifier.width(120.dp))
                        Row(Modifier.width(160.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                categoryIcon(entry.cat), contentDescription = null,
                                tint = baki.muted, modifier = Modifier.size(IconSize.inline),
                            )
                            Spacer(Modifier.width(Space.s))
                            Text(cats[entry.cat]?.name ?: entry.cat, color = baki.onSurface, style = BakiType.label)
                        }
                        Text(
                            entry.note.ifBlank { "—" },
                            color = baki.muted, style = BakiType.label,
                            maxLines = 1, modifier = Modifier.weight(1f),
                        )
                        Text(
                            (if (entry.type == "income") "+" else "−") + rm(entry.amount),
                            color = if (entry.type == "income") baki.up else baki.down,
                            style = BakiType.labelNumeric,
                            modifier = Modifier.width(120.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.End,
                        )
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Edit", style = BakiType.label) }, onClick = { menu = false; onEdit(entry) })
                        DropdownMenuItem(text = { Text("Delete", style = BakiType.label) }, onClick = { menu = false; onDelete(entry) })
                    }
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(hairline))
            }
        }
    }
}

@Composable
private fun HeaderCell(
    text: String,
    modifier: Modifier = Modifier,
    align: Alignment.Horizontal = Alignment.Start,
    onClick: (() -> Unit)? = null,
) {
    val baki = LocalBaki.current
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(Space.xxs), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f), contentAlignment = if (align == Alignment.End) Alignment.CenterEnd else Alignment.CenterStart) {
            Text(
                text, color = baki.muted, style = BakiType.section,
                modifier = if (onClick != null) {
                    Modifier.pointerHoverIcon(PointerIcon.Hand).clickable { onClick() }
                } else Modifier,
            )
        }
    }
}

@Composable
private fun ShiftsView(repo: BakiRepo, month: String) {
    val baki = LocalBaki.current
    val shifts = repo.store.shifts.filter { it.date.startsWith(month) }.sortedByDescending { it.date }
    val net = shifts.sumOf { shiftMetrics(it, repo.store.settings.profit).net }
    Column(Modifier.fillMaxSize().padding(horizontal = Gutter)) {
        Text("${shifts.size} shifts · net RM ${"%.2f".format(net)}", color = baki.muted, style = BakiType.label)
        Spacer(Modifier.height(Space.m))
        Row(Modifier.fillMaxWidth().padding(vertical = Space.xs)) {
            HeaderCell("DATE", Modifier.width(140.dp))
            HeaderCell("HOURS", Modifier.width(90.dp))
            HeaderCell("KM", Modifier.width(90.dp))
            HeaderCell("GROSS", Modifier.width(120.dp))
            HeaderCell("NET", Modifier.width(120.dp))
            HeaderCell("RATE", Modifier.weight(1f))
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(hairline))
        Column {
            shifts.forEach { s ->
                val m = shiftMetrics(s, repo.store.settings.profit)
                val hover = remember { MutableInteractionSource() }
                val hovered by hover.collectIsHoveredAsState()
                Row(
                    Modifier.fillMaxWidth()
                        .background(if (hovered) baki.container.copy(alpha = 0.55f) else Color.Transparent)
                        .hoverable(hover)
                        .padding(vertical = Space.m),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("${s.date} ${s.start}", color = baki.onSurface, style = BakiType.labelNumeric, modifier = Modifier.width(140.dp))
                    Text("${"%.1f".format(s.hours)}h", color = baki.muted, style = BakiType.labelNumeric, modifier = Modifier.width(90.dp))
                    Text("${"%.1f".format(s.km)}", color = baki.muted, style = BakiType.labelNumeric, modifier = Modifier.width(90.dp))
                    Text("RM ${rm(s.gross)}", color = baki.muted, style = BakiType.labelNumeric, modifier = Modifier.width(120.dp))
                    Text("RM ${rm((m.net * 100).toLong())}", color = baki.onSurface, style = BakiType.labelNumeric, modifier = Modifier.width(120.dp))
                    Text("RM ${"%.2f".format(m.net / s.hours)}/h", color = baki.muted, style = BakiType.labelNumeric)
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(hairline))
            }
        }
    }
}

@Composable
private fun YearView(store: Store) {
    val baki = LocalBaki.current
    val year = todayIso().substring(0, 4)
    Column(Modifier.fillMaxSize().padding(horizontal = Gutter).verticalScroll(rememberScrollState())) {
        Text("$year AT A GLANCE", color = baki.muted, style = BakiType.section)
        Spacer(Modifier.height(Space.m))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.l)) {
            (1..12).chunked(6).forEach { block ->
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.m)) {
                    block.forEach { m ->
                        val label = "%04d-%02d".format(year.toInt(), m)
                        val t = monthTotals(store, label)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                monthLabel(label).take(3),
                                color = if (t.entryCount == 0 && t.shiftCount == 0) baki.muted else baki.onSurface,
                                style = BakiType.label, modifier = Modifier.width(48.dp),
                            )
                            Box(
                                Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(Radius.tiny))
                                    .background(if (t.net >= 0) baki.primary.copy(alpha = 0.75f) else baki.down.copy(alpha = 0.75f)),
                            )
                            Spacer(Modifier.width(Space.s))
                            Text(
                                "RM ${rm(t.net)}", color = baki.muted,
                                style = BakiType.captionNumeric, modifier = Modifier.width(96.dp),
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(Space.xxl))
    }
}

@Composable
private fun DesktopEntryDialog(
    store: Store,
    month: String,
    existing: Entry? = null,
    onDismiss: () -> Unit,
    onSave: (date: String, cat: String, cents: Long, note: String) -> Unit,
) {
    val baki = LocalBaki.current
    val cats = store.settings.cats.catsOut.filter { it.id != "grabfee" }
    var date by remember { mutableStateOf(existing?.date ?: todayIso()) }
    var amount by remember { mutableStateOf(existing?.amount?.let { "%.2f".format(it / 100.0) } ?: "") }
    var note by remember { mutableStateOf(existing?.note ?: "") }
    var cat by remember { mutableStateOf(existing?.cat ?: cats.firstOrNull()?.id ?: "other") }
    val cents = amount.toDoubleOrNull()?.let { (it * 100).toLong() } ?: 0L

    BakiSheet(onDismiss = onDismiss) {
        Text(if (existing == null) "New entry" else "Edit entry", color = baki.onSurface, style = BakiType.title)
        Spacer(Modifier.height(Space.m))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            OutlinedTextField(
                value = date, onValueChange = { date = it }, label = { Text("Date", style = BakiType.label) },
                singleLine = true, modifier = Modifier.weight(1f), colors = bakiFieldColorsPublic(),
            )
            OutlinedTextField(
                value = amount, onValueChange = { amount = it.filter { c -> c.isDigit() || c == '.' } },
                label = { Text("Amount (RM)", style = BakiType.label) },
                singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f), colors = bakiFieldColorsPublic(),
            )
        }
        Spacer(Modifier.height(Space.s))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
            cats.take(5).forEach { c ->
                val on = cat == c.id
                Text(
                    c.name, color = if (on) baki.onPrimaryContainer else baki.muted, style = BakiType.label,
                    modifier = Modifier
                        .clip(RoundedCornerShape(Radius.pill))
                        .background(if (on) baki.primaryContainer else baki.container)
                        .pointerHoverIcon(PointerIcon.Hand)
                        .clickable { cat = c.id }
                        .padding(horizontal = Space.m, vertical = Space.xs),
                )
            }
        }
        Spacer(Modifier.height(Space.s))
        OutlinedTextField(
            value = note, onValueChange = { note = it }, label = { Text("Note", style = BakiType.label) },
            singleLine = true, modifier = Modifier.fillMaxWidth(), colors = bakiFieldColorsPublic(),
        )
        Spacer(Modifier.height(Space.m))
        TextButton(if (existing == null) "Add entry" else "Save changes", primary = true) {
            if (cents > 0) onSave(date, cat, cents, note)
        }
    }
}

@Composable
private fun DeletionDialog(entry: Entry, onConfirm: () -> Unit) {
    val baki = LocalBaki.current
    var show by remember { mutableStateOf(true) }
    if (!show) return
    BakiSheet(onDismiss = { show = false }) {
        Text("Delete this entry?", color = baki.onSurface, style = BakiType.title)
        Spacer(Modifier.height(Space.s))
        Text("RM ${rm(entry.amount)} · ${entry.date} · ${entry.cat}", color = baki.muted, style = BakiType.label)
        Spacer(Modifier.height(Space.m))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            TextButton("Cancel") { show = false }
            TextButton("Delete", primary = true) { onConfirm() }
        }
    }
}
