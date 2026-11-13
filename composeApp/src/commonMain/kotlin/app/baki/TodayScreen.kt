package app.baki

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val BANDS = mapOf(
    "quiet" to "Quiet", "slow" to "Slow", "steady" to "Steady", "good" to "Good", "peak" to "Peak",
)

@Composable
fun bandColor(band: String, baki: BakiPalette): Color = when (band) {
    "quiet" -> baki.down
    "slow", "steady" -> baki.warn
    "good" -> baki.up
    else -> baki.primary
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TodayScreen(
    repo: BakiRepo,
    day: String,
    today: String,
    onDay: (String) -> Unit,
    onPayout: () -> Unit,
    onPayouts: () -> Unit,
    onQuickAdd: (String, Long) -> Unit,
    onEditEntry: (Entry) -> Unit,
    onDeleteEntry: (Entry) -> Unit,
    onPreset: (String) -> Unit,
    onSeeAll: () -> Unit,
    onAdd: () -> Unit,
) {
    val baki = LocalBaki.current
    val store = repo.store
    // memoised: a sync tick or the 1s live tick must not re-scan the whole store
    val t = remember(store, day) { dayTotals(store, day) }
    val operating = remember(store, day) { dayOperating(store, day) }
    val food = remember(store, day) { dailyFood(store, day) }
    val entries = remember(store, day) {
        entriesOn(store, day).sortedWith(
            compareByDescending<Entry> { it.loggedAt.ifBlank { it.updated } },
        )
    }
    val shifts = remember(store, day) { shiftsOn(store, day) }
    val cfg = store.settings.profit
    val live = repo.live
    val shift = shifts.maxByOrNull { it.updated }
    val scope = rememberCoroutineScope()
    val quick = remember(store) { quickAdds(store) }



    PullToRefreshBox(
        isRefreshing = repo.status == "syncing",
        onRefresh = { scope.launch { repo.sync() } },
        modifier = Modifier.fillMaxSize(),
        // the header dot already reports sync state, so no spinner over the content
        indicator = {},
    ) {
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = Space.l),
        verticalArrangement = Arrangement.spacedBy(Space.m),
        // leave room for the floating bar and the + so nothing ends up underneath them
        contentPadding = PaddingValues(top = 6.dp, bottom = 132.dp),
    ) {
        item { DayPill(day, today, onDay) }

        item {
            BoxWithConstraints {
                val compact = maxWidth < 340.dp || LocalDensity.current.fontScale > 1.15f
                Column {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("RM", color = baki.muted,
                            modifier = Modifier.padding(bottom = Space.s, end = 6.dp), style = BakiType.title )
                        EasedMoney(t.net, baki.onSurface, heroMoneyStyle(t.net), prefix = "")
                    }
                    if (compact) {
                        Text(
                            if (t.net >= 0) "RECORDED NET" else "RECORDED OUT",
                            color = baki.muted, letterSpacing = 1.5.sp, style = BakiType.captionStrong,
                        )
                        if (operating.shiftCount > 0) {
                            Spacer(Modifier.height(Space.xs))
                            Column {
                                Text(
                                    "OPERATING · RM ${rm(operating.net)}" +
                                        (operating.perHour?.let { " · ${rm(it)}/h" } ?: ""),
                                    color = if (operating.net >= 0) baki.onSurface else baki.down,
                                    style = BakiType.labelNumeric,
                                )
                                Text("after running costs", color = baki.muted, style = BakiType.nano)
                            }
                        }
                    } else {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                            Text(
                                if (t.net >= 0) "RECORDED NET" else "RECORDED OUT",
                                color = baki.muted, letterSpacing = 1.5.sp, style = BakiType.captionStrong,
                                modifier = Modifier.weight(1f),
                            )
                            if (operating.shiftCount > 0) {
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("OPERATING", color = baki.muted, letterSpacing = 1.2.sp, style = BakiType.captionStrong )
                                    Text(
                                        "RM ${rm(operating.net)}" +
                                            (operating.perHour?.let { " · ${rm(it)}/h" } ?: ""),
                                        color = if (operating.net >= 0) baki.onSurface else baki.down,
                                        style = BakiType.labelNumeric,
                                    )
                                    Text("after running costs", color = baki.muted, style = BakiType.nano)
                                }
                            }
                        }
                    }
                    if (baki.paper) {
                        Spacer(Modifier.height(Space.s))
                        LedgerRule(double = true)
                    }
                }
            }
        }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.m)) {
                Chip("in RM ${rm(t.income)}", baki.up, Modifier.weight(1f))
                Chip("out RM ${rm(t.expense)}", baki.down, Modifier.weight(1f))
            }
        }

        food?.let { f ->
            item {
                BoxWithConstraints {
                    val compact = maxWidth < 340.dp || LocalDensity.current.fontScale > 1.15f
                    val panel = Modifier.fillMaxWidth()
                        .then(
                            if (baki.paper) Modifier.padding(vertical = Space.s)
                            else Modifier.clip(RoundedCornerShape(Radius.input)).background(baki.container)
                                .padding(horizontal = Space.m, vertical = Space.s)
                        )
                    Column {
                    if (compact) {
                        Column(panel, verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                            Text(if (day == today) "FOOD TODAY" else "FOOD", color = baki.muted, style = BakiType.captionStrong)
                            Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                                Text(
                                    "RM ${rm(f.spent)} / ${rm(f.cap)}",
                                    color = if (f.over) baki.down else baki.onSurface,
                                    style = BakiType.labelNumeric,
                                )
                                Text(
                                    if (f.over) "RM ${rm(-f.remaining)} over" else "RM ${rm(f.remaining)} left",
                                    color = if (f.over) baki.down else baki.up,
                                    style = BakiType.caption,
                                )
                            }
                        }
                    } else {
                        Row(panel, verticalAlignment = Alignment.CenterVertically) {
                            Text(if (day == today) "FOOD TODAY" else "FOOD", color = baki.muted, style = BakiType.captionStrong)
                            Spacer(Modifier.weight(1f))
                            Text(
                                "RM ${rm(f.spent)} / ${rm(f.cap)}",
                                color = if (f.over) baki.down else baki.onSurface,
                                style = BakiType.labelNumeric,
                            )
                            Spacer(Modifier.width(Space.s))
                            Text(
                                if (f.over) "RM ${rm(-f.remaining)} over" else "RM ${rm(f.remaining)} left",
                                color = if (f.over) baki.down else baki.up,
                                style = BakiType.caption,
                            )
                        }
                    }
                    if (baki.paper) LedgerRule(alpha = 0.16f)
                    }
                }
            }
        }

        if (live != null && day == today) {
            item {
                // one-shot entrance: start from false so the first composition actually animates in
                val visible = remember {
                    MutableTransitionState(false).apply { targetState = true }
                }
                AnimatedVisibility(
                    visibleState = visible,
                    enter = fadeIn(BakiMotion.effects()) +
                        scaleIn(initialScale = 0.97f, animationSpec = BakiMotion.spatial()) +
                        slideInVertically(BakiMotion.spatialSlow()) { it / 12 },
                ) {
                    LiveCard(repo, live, onPayout, onPayouts)
                }
            }
        }

        item { WeekStrip(store, day) }

        val paidUntil = coverPaidUntil(store)
        if (paidUntil != null && nowMillis() < paidUntil) {
            item {
                Row(verticalAlignment = Alignment.Top) {
                    Icon(
                        BakiIcons.Shield, contentDescription = null, tint = baki.up,
                        modifier = Modifier.padding(top = 1.dp).size(14.dp),
                    )
                    Spacer(Modifier.width(Space.s))
                    // weight so a wrapped line stays beside the icon instead of under it
                    Text(
                        "Cover paid until ${clockOf(paidUntil)}",
                        color = baki.up, style = BakiType.caption, modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        if (shift != null) {
            item {
                val m = shiftMetrics(shift, cfg, overheadPerHour(store, shift.date.take(7)))
                Column(
                    Modifier.fillMaxWidth()
                        .then(
                            if (baki.paper) Modifier.padding(vertical = Space.m)
                            else Modifier.clip(RoundedCornerShape(Radius.card)).background(baki.container).padding(Space.l)
                        ),
                ) {
                    if (baki.paper) LedgerRule(alpha = 0.18f)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "SHIFT NET · AFTER CAR COST", color = baki.muted, letterSpacing = 1.sp,
                            style = BakiType.captionStrong, modifier = Modifier.weight(1f),
                        )
                        if (baki.paper) {
                            Text(
                                (BANDS[m.band] ?: "—").uppercase(),
                                color = bandColor(m.band, baki),
                                style = BakiType.captionStrong,
                            )
                        } else {
                            Box(
                                Modifier.clip(RoundedCornerShape(Radius.pill))
                                    .background(bandColor(m.band, baki).copy(alpha = 0.18f))
                                    .padding(horizontal = Space.m, vertical = 4.dp),
                            ) {
                                Text(BANDS[m.band] ?: "—", color = bandColor(m.band, baki), style = BakiType.captionStrong)
                            }
                        }
                    }
                    Spacer(Modifier.height(Space.xs))
                    BoxWithConstraints {
                        val compact = maxWidth < 340.dp || LocalDensity.current.fontScale > 1.15f
                        if (compact) {
                            Column {
                                Text("RM ${rm(m.net)}", color = baki.onSurface, style = BakiType.headlineNumeric, maxLines = 1)
                                m.netHr?.let {
                                    Text("RM ${rm(it)}/hr", color = bandColor(m.band, baki), style = BakiType.bodyStrong, maxLines = 1)
                                }
                            }
                        } else {
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text("RM ${rm(m.net)}", color = baki.onSurface, style = BakiType.headlineNumeric)
                                Spacer(Modifier.weight(1f))
                                m.netHr?.let {
                                    Text("RM ${rm(it)}/hr", color = bandColor(m.band, baki), style = BakiType.bodyStrong )
                                }
                            }
                        }
                    }
                    Text(
                        "${"%.1f".format(shift.hours)}h · ${"%.0f".format(m.km)} km" +
                            (m.grossPerKm?.let { " · RM ${rm(it)}/km" } ?: "") +
                            " · fuel + wear RM ${rm(m.running)}",
                        color = baki.muted, style = BakiType.caption )
                    // what this shift had to beat
                    m.netHr?.let { rate ->
                        val delta = rate - cfg.targetRate
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                if (delta >= 0) "✓" else "✗",
                                color = if (delta >= 0) baki.up else baki.down, style = BakiType.captionStrong )
                            Spacer(Modifier.width(Space.xs))
                            Text(
                                "target RM ${rm(cfg.targetRate)}/hr · " +
                                    if (delta >= 0) "RM ${rm(delta)} above"
                                    else "RM ${rm(-delta)} short",
                                color = if (delta >= 0) baki.up else baki.down, style = BakiType.caption,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    if (day == today && live == null && !repo.readOnly) {
                        Spacer(Modifier.height(Space.m))
                        PillButton(
                            "Live", baki.surfaceHigh, baki.onSurface,
                            Modifier.fillMaxWidth(), { repo.startLive() },
                        )
                    }
                    if (baki.paper) {
                        Spacer(Modifier.height(Space.m))
                        LedgerRule(alpha = 0.18f)
                    }
                }
            }
        } else if (live == null && !repo.readOnly && day == today) {
            item {
                PillButton(
                    "Start a live shift", baki.primary, baki.onPrimary,
                    Modifier.fillMaxWidth().height(48.dp), { repo.startLive() },
                )
            }
        }

        // the day's movement: between the shift card and the ledger
        item { TodayRouteCard(store = store, day = day, live = live) }

        // what you keep buying, one tap away
        if (quick.isNotEmpty()) item {
            Column {
                Text("QUICK ADD", color = baki.muted, style = BakiType.section)
                Spacer(Modifier.height(Space.s))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Space.s),
                    verticalArrangement = Arrangement.spacedBy(Space.s),
                ) {
                    val cats = store.settings.cats.catsOut
                    quick.forEach { q ->
                        val name = cats.firstOrNull { it.id == q.cat }?.name ?: q.cat
                        QuickAction(name, "RM ${rm(q.cents)}") {
                            PlatformEffects.haptic(Haptic.Tick)
                            onQuickAdd(q.cat, q.cents)
                        }
                    }
                    QuickAction("More", null) { onAdd() }
                }
            }
        }

        if (quick.isEmpty()) item {
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Space.m),
                verticalArrangement = Arrangement.spacedBy(Space.s),
            ) {
                // skip categories already offered as one-tap amounts above: the same chip twice is noise
                val presets = remember(store, quick) {
                    val counts = store.entries.filter { it.type != "income" }.groupingBy { it.cat }.eachCount()
                    val quickCats = quick.map { it.cat }.toSet()
                    val used = store.settings.cats.catsOut
                        .filter { it.id != "grabfee" && it.id !in quickCats && (counts[it.id] ?: 0) > 0 }
                        .sortedByDescending { counts[it.id] ?: 0 }
                    (if (used.isEmpty()) store.settings.cats.catsOut.filter { it.id != "grabfee" && it.id !in quickCats } else used)
                        .take(6)
                }
                presets.forEach { c ->
                    QuickAction(c.name, null, icon = categoryIcon(c.id, c.name)) {
                        PlatformEffects.haptic(Haptic.Tick)
                        onPreset(c.id)
                    }
                }
            }
        }

        item {
            // reserve the floating + gutter so the larger label never runs underneath it
            Row(Modifier.fillMaxWidth().padding(end = 68.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${if (day == today) "TODAY" else day.uppercase()} · ${t.entryCount} ENTRIES",
                    color = baki.muted, letterSpacing = 1.sp, style = BakiType.captionStrong )
                Spacer(Modifier.weight(1f))
                Text("see all →", color = baki.muted, modifier = Modifier.clickable { onSeeAll() }, style = BakiType.caption )
            }
        }

        items(shifts, key = { it.id }) { sh ->
            val m = shiftMetrics(sh, cfg, overheadPerHour(store, sh.date.take(7)))
            Box(Modifier.animateItem()) {
                RowCardIcon(
                    icon = BakiIcons.Car,
                    title = "Shift ${sh.start.ifEmpty { sh.date }}",
                    sub = "${m.band} · ${"%.1f".format(sh.hours)}h · ${"%.0f".format(m.km)} km",
                    amount = "RM ${rm(m.net)}",
                    tint = baki.up,
                    onEdit = { onEditEntry(Entry(sh.id, sh.date, "shift", "shift", 0)) },
                )
            }
        }
        items(entries, key = { it.id }) { e ->
            Box(Modifier.animateItem()) {
                RowCardIcon(
                    icon = categoryIcon(e.cat),
                    title = e.note.ifEmpty { e.cat },
                    sub = listOfNotNull(
                        e.cat,
                        e.odo?.takeIf { it > 0 }?.let { "$it km" },
                        loggedTime(e.loggedAt)?.let { "logged $it" },
                    ).joinToString(" · "),
                    amount = (if (e.type == "income") "+" else "−") + rm(e.amount),
                    tint = if (e.type == "income") baki.up else baki.down,
                    sharedKey = "entry-${e.id}",
                    onEdit = { onEditEntry(e) },
                    onDelete = { onDeleteEntry(e) },
                )
            }
        }
        if (shifts.isEmpty() && entries.isEmpty()) item {
            Column(
                Modifier.fillMaxWidth().padding(top = 26.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier.size(52.dp).then(
                        if (baki.paper) Modifier
                        else Modifier.clip(RoundedCornerShape(Radius.card)).background(baki.container)
                    ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(BakiIcons.Receipt, contentDescription = null, tint = baki.muted, modifier = Modifier.size(IconSize.bar))
                }
                Spacer(Modifier.height(Space.m))
                Text("Nothing recorded yet", color = baki.onSurface, style = BakiType.bodyStrong)
                Spacer(Modifier.height(3.dp))
                Text(
                    "Tap + to log a spending entry.",
                    color = baki.muted, style = BakiType.caption,
                )
                if (baki.paper) {
                    Spacer(Modifier.height(Space.m))
                    LedgerRule(alpha = 0.16f)
                }
            }
        }
    }
    }
}

@Composable
private fun QuickAction(
    label: String,
    amount: String?,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onClick: () -> Unit,
) {
    val baki = LocalBaki.current
    if (baki.paper) {
        Column(
            Modifier.clickable { onClick() }.padding(horizontal = Space.s),
        ) {
            Row(
                Modifier.heightIn(min = Control.secondary),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.s),
            ) {
                if (icon != null) {
                    Icon(icon, contentDescription = null, tint = baki.primary, modifier = Modifier.size(IconSize.inline))
                }
                Text(label, color = baki.onSurface, style = BakiType.labelStrong)
                amount?.let { Text(it, color = baki.primary, style = BakiType.captionNumeric) }
            }
            LedgerRule(alpha = 0.12f)
        }
    } else {
        Row(
            Modifier.heightIn(min = Control.secondary)
                .clip(RoundedCornerShape(Radius.pill))
                .background(baki.surfaceHigh)
                .clickable { onClick() }
                .padding(horizontal = Space.l),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.s),
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = baki.primary, modifier = Modifier.size(IconSize.inline))
            }
            Text(label, color = baki.onSurface, style = BakiType.labelStrong)
            amount?.let { Text(it, color = baki.primary, style = BakiType.captionNumeric) }
        }
    }
}

@Composable
private fun DayPill(day: String, today: String, onChange: (String) -> Unit) {
    val baki = LocalBaki.current
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth()
                .then(
                    if (baki.paper) Modifier.padding(vertical = Space.s)
                    else Modifier.clip(RoundedCornerShape(Radius.pill)).background(baki.container).padding(vertical = Space.s)
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = { onChange(addDays(day, -1)) }) { Text("‹", color = baki.onSurface, style = BakiType.body ) }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (day == today) "Today" else prettyDate(day), color = baki.onSurface, style = BakiType.bodyStrong )
                Text(
                    if (day == today) prettyDate(day) else "${weekdayShort(day)} · ${prettyDate(day)}",
                    color = baki.muted, style = BakiType.caption )
            }
            TextButton(enabled = day < today, onClick = { onChange(addDays(day, 1)) }) {
                Text("›", color = if (day < today) baki.onSurface else baki.muted.copy(alpha = 0.3f), style = BakiType.body )
            }
        }
        if (baki.paper) LedgerRule(alpha = 0.16f)
    }
}

@Composable
fun PillButton(label: String, bg: Color, ink: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.clip(RoundedCornerShape(Radius.pill)).background(bg).tactileClickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = ink,
            modifier = Modifier.padding(horizontal = Space.l, vertical = 11.dp), style = BakiType.labelStrong )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun RowCardIcon(
    icon: ImageVector,
    title: String,
    sub: String,
    amount: String,
    tint: Color,
    sharedKey: String? = null,
    onEdit: () -> Unit = {},
    onDelete: (() -> Unit)? = null,
) {
    val baki = LocalBaki.current
    val gestureScope = rememberCoroutineScope()
    var acted by remember { mutableStateOf(false) }
    val sharedScope = LocalSharedTransition.current
    val visibilityScope = LocalAnimatedVisibility.current
    val sharedMod = if (sharedScope != null && visibilityScope != null && sharedKey != null) {
        with(sharedScope) {
            Modifier.sharedBounds(
                rememberSharedContentState(key = sharedKey),
                animatedVisibilityScope = visibilityScope,
            )
        }
    } else Modifier

    val swipe = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value != SwipeToDismissBoxValue.Settled && !acted) {
                acted = true
                when (value) {
                    SwipeToDismissBoxValue.StartToEnd -> onEdit()
                    SwipeToDismissBoxValue.EndToStart -> onDelete?.invoke()
                    else -> {}
                }
                // one gesture, one action; allow the next swipe after the spring-back
                gestureScope.launch { delay(650); acted = false }
            }
            false          // always spring back: the action is the swipe, not the removal
        },
    )

    SwipeToDismissBox(
        state = swipe,
        enableDismissFromStartToEnd = true,
        enableDismissFromEndToStart = onDelete != null,
        backgroundContent = {
            val editing = swipe.dismissDirection == SwipeToDismissBoxValue.StartToEnd
            Row(
                Modifier.fillMaxSize().clip(RoundedCornerShape(Radius.card))
                    .background(if (editing) baki.primaryContainer else baki.down.copy(alpha = 0.22f))
                    .padding(horizontal = Space.ml),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (editing) Arrangement.Start else Arrangement.End,
            ) {
                Text(
                    if (editing) "Edit" else "Delete",
                    color = if (editing) baki.onPrimaryContainer else baki.down, style = BakiType.labelStrong )
            }
        },
    ) {
    val a11yActions = buildList {
        add(CustomAccessibilityAction("Edit") { onEdit(); true })
        if (onDelete != null) add(CustomAccessibilityAction("Delete") { onDelete(); true })
    }
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().then(sharedMod)
                .then(
                    if (baki.paper) Modifier.background(baki.surface)
                    else Modifier.clip(RoundedCornerShape(Radius.card)).background(baki.container)
                )
                .pointerInput(title) { detectTapGestures(onLongPress = { onEdit() }) }
                .semantics { customActions = a11yActions }
                .padding(vertical = Space.m, horizontal = if (baki.paper) 0.dp else Space.l),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(34.dp).then(
                    if (baki.paper) Modifier
                    else Modifier.clip(RoundedCornerShape(Radius.input)).background(baki.surfaceHigh)
                ),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, contentDescription = null, tint = baki.primary, modifier = Modifier.size(IconSize.inline)) }
            Spacer(Modifier.width(Space.m))
            Column(Modifier.weight(1f)) {
                Text(title, color = baki.onSurface, style = BakiType.bodyStrong)
                if (sub.isNotEmpty()) Text(sub, color = baki.muted, style = BakiType.caption)
            }
            Text(amount, color = tint, style = BakiType.subtitleNumeric)
        }
        if (baki.paper) LedgerRule(Modifier.padding(start = 46.dp), alpha = 0.16f)
    }
    }
}
