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
import androidx.compose.ui.text.font.FontWeight
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
    onDay: (String) -> Unit,
    onPayout: () -> Unit,
    onPayouts: () -> Unit,
    onQuickAdd: (String, Long) -> Unit,
    onEditEntry: (Entry) -> Unit,
    onDeleteEntry: (Entry) -> Unit,
    onPreset: (String) -> Unit,
    onAddShift: () -> Unit,
    onSeeAll: () -> Unit,
    onAdd: () -> Unit,
) {
    val baki = LocalBaki.current
    val store = repo.store
    val today = todayIso()
    // memoised: a sync tick or the 1s live tick must not re-scan the whole store
    val t = remember(store, day) { dayTotals(store, day) }
    val operating = remember(store, day) { dayOperating(store, day) }
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
            Column {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("RM", color = baki.muted,
                        modifier = Modifier.padding(bottom = Space.s, end = 6.dp), style = BakiType.title )
                    EasedMoney(t.net, baki.onSurface, BakiType.displayNumeric, prefix = "")
                }
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
                                    (operating.perHour?.let { " · ${"%.2f".format(it)}/h" } ?: ""),
                                color = if (operating.net >= 0) baki.onSurface else baki.down,
                                style = BakiType.labelNumeric,
                            )
                            Text("after running costs", color = baki.muted, style = BakiType.nano)
                        }
                    }
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(Space.m)) {
                Chip("in RM ${rm(t.income)}", baki.up)
                Chip("out RM ${rm(t.expense)}", baki.down)
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
                        "Cover paid until ${clockOf(paidUntil)} — starting before then adds no RM ${"%.2f".format(cfg.grabInsurance)}",
                        color = baki.up, style = BakiType.caption, modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        if (shift != null) {
            item {
                val m = shiftMetrics(shift, cfg, overheadPerHour(store, shift.date.take(7)))
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.card)).background(baki.container).padding(Space.l),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("SHIFT NET · AFTER CAR COST", color = baki.muted, letterSpacing = 1.sp, style = BakiType.captionStrong )
                        Spacer(Modifier.weight(1f))
                        Box(
                            Modifier.clip(RoundedCornerShape(Radius.pill))
                                .background(bandColor(m.band, baki).copy(alpha = 0.18f))
                                .padding(horizontal = Space.m, vertical = 4.dp),
                        ) {
                            Text(BANDS[m.band] ?: "—", color = bandColor(m.band, baki), style = BakiType.captionStrong )
                        }
                    }
                    Spacer(Modifier.height(Space.xs))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("RM ${"%.2f".format(m.net)}", color = baki.onSurface, style = BakiType.headlineNumeric)
                        Spacer(Modifier.weight(1f))
                        m.netHr?.let {
                            Text("RM ${"%.0f".format(it)}/hr", color = bandColor(m.band, baki), style = BakiType.bodyStrong )
                        }
                    }
                    Text(
                        "${"%.1f".format(shift.hours)}h · ${"%.0f".format(m.km)} km · fuel + wear RM ${"%.2f".format(m.running)}",
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
                                "target RM ${"%.0f".format(cfg.targetRate)}/hr · " +
                                    if (delta >= 0) "RM ${"%.0f".format(delta)} above"
                                    else "RM ${"%.0f".format(-delta)} short",
                                color = if (delta >= 0) baki.up else baki.down, style = BakiType.caption )
                        }
                    }
                    Spacer(Modifier.height(Space.m))
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                        PillButton("+ Add shift", baki.surfaceHigh, baki.onSurface, Modifier.weight(1f), onAddShift)
                        if (live == null) PillButton("▶ Live", baki.surfaceHigh, baki.onSurface, Modifier.weight(1f)) { repo.startLive() }
                        PillButton("All →", baki.surfaceHigh, baki.onSurface, Modifier.weight(1f), onSeeAll)
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
                        Row(
                            Modifier.heightIn(min = Control.secondary)
                                .clip(RoundedCornerShape(Radius.pill))
                                .background(baki.surfaceHigh)
                                .clickable { onQuickAdd(q.cat, q.cents) }
                                .padding(horizontal = Space.l),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Space.s),
                        ) {
                            Text(name, color = baki.onSurface, style = BakiType.labelStrong)
                            Text("RM ${rm(q.cents)}", color = baki.primary, style = BakiType.captionNumeric)
                        }
                    }
                }
            }
        }

        item {
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
                    Row(
                        Modifier.clip(RoundedCornerShape(Radius.pill)).background(baki.container)
                            .clickable { onPreset(c.id) }
                            // 48dp minimum touch target, per the accessibility guidance
                            .heightIn(min = Control.primary)
                            .padding(horizontal = Space.l),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            categoryIcon(c.id, c.name), contentDescription = null,
                            tint = baki.primary, modifier = Modifier.size(IconSize.inline),
                        )
                        Spacer(Modifier.width(Space.s))
                        Text(c.name, color = baki.onSurface, style = BakiType.bodySmallStrong )
                    }
                }
            }
        }

        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${if (day == today) "TODAY" else day.uppercase()} · ${t.entryCount} ENTRIES",
                    color = baki.muted, letterSpacing = 1.sp, style = BakiType.captionStrong )
                Spacer(Modifier.weight(1f))
                Text(
                    "+ Add", color = baki.primary,
                    modifier = Modifier.clickable { onAdd() }.padding(horizontal = Space.s, vertical = Space.xs),
                    style = BakiType.captionStrong,
                )
                Spacer(Modifier.width(Space.s))
                Text("see all →", color = baki.muted, modifier = Modifier.clickable { onSeeAll() }, style = BakiType.caption )
            }
        }

        items(shifts) { sh ->
            val m = shiftMetrics(sh, cfg, overheadPerHour(store, sh.date.take(7)))
            RowCardIcon(
                icon = BakiIcons.Car,
                title = "Shift ${sh.start.ifEmpty { sh.date }}",
                sub = "${m.band} · ${"%.1f".format(sh.hours)}h · ${"%.0f".format(m.km)} km",
                amount = "RM ${"%.2f".format(m.net)}",
                tint = baki.up,
                onEdit = { onEditEntry(Entry(sh.id, sh.date, "shift", "shift", 0)) },
            )
        }
        items(entries, key = { it.id }) { e ->
            RowCardIcon(
                icon = categoryIcon(e.cat),
                title = e.note.ifEmpty { e.cat },
                sub = listOfNotNull(e.cat, loggedTime(e.loggedAt)?.let { "logged $it" }).joinToString(" · "),
                amount = (if (e.type == "income") "+" else "−") + rm(e.amount),
                tint = if (e.type == "income") baki.up else baki.down,
                sharedKey = "entry-${e.id}",
                onEdit = { onEditEntry(e) },
                onDelete = { onDeleteEntry(e) },
            )
        }
        if (shifts.isEmpty() && entries.isEmpty()) item {
            Column(
                Modifier.fillMaxWidth().padding(top = 26.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier.size(52.dp).clip(RoundedCornerShape(Radius.card)).background(baki.container),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(BakiIcons.Receipt, contentDescription = null, tint = baki.muted, modifier = Modifier.size(IconSize.bar))
                }
                Spacer(Modifier.height(Space.m))
                Text("Nothing recorded yet", color = baki.onSurface, style = BakiType.bodyStrong)
                Spacer(Modifier.height(3.dp))
                Text(
                    "Tap a category above, or + to log something.",
                    color = baki.muted, style = BakiType.caption,
                )
            }
        }
    }
    }
}

@Composable
private fun DayPill(day: String, today: String, onChange: (String) -> Unit) {
    val baki = LocalBaki.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.pill)).background(baki.container).padding(vertical = Space.s),
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
}

@Composable
private fun PillButton(label: String, bg: Color, ink: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.clip(RoundedCornerShape(Radius.pill)).background(bg).clickable { onClick() },
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
                    if (editing) "✎  Edit" else "Delete  🗑",
                    color = if (editing) baki.onPrimaryContainer else baki.down, style = BakiType.labelStrong )
            }
        },
    ) {
    Row(
        Modifier.fillMaxWidth().then(sharedMod).clip(RoundedCornerShape(Radius.card)).background(baki.container)
            .pointerInput(title) { detectTapGestures(onLongPress = { onEdit() }) }
            .padding(Space.l),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(34.dp).clip(RoundedCornerShape(Radius.input)).background(baki.surfaceHigh),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, contentDescription = null, tint = baki.primary, modifier = Modifier.size(IconSize.inline)) }
        Spacer(Modifier.width(Space.m))
        Column(Modifier.weight(1f)) {
            Text(title, color = baki.onSurface, style = BakiType.bodyStrong )
            if (sub.isNotEmpty()) Text(sub, color = baki.muted, style = BakiType.caption )
        }
        Text(amount, color = tint, style = BakiType.subtitleNumeric)
    }
    }
}
