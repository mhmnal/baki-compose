package app.baki

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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(
    repo: BakiRepo,
    day: String,
    onDay: (String) -> Unit,
    onPayout: () -> Unit,
    onPayouts: () -> Unit,
    onQuickAdd: (String, Long) -> Unit,
    onQuickText: (String) -> Unit,
    onEditEntry: (Entry) -> Unit,
    onDeleteEntry: (Entry) -> Unit,
    onPreset: (String) -> Unit,
    onAddShift: () -> Unit,
    onSeeAll: () -> Unit,
) {
    val baki = LocalBaki.current
    val store = repo.store
    val today = todayIso()
    // memoised: a sync tick or the 1s live tick must not re-scan the whole store
    val t = remember(store, day) { dayTotals(store, day) }
    val entries = remember(store, day) { entriesOn(store, day).sortedByDescending { it.updated } }
    val shifts = remember(store, day) { shiftsOn(store, day) }
    val cfg = store.settings.profit
    val live = repo.live
    val shift = shifts.maxByOrNull { it.updated }
    val scope = rememberCoroutineScope()
    val quick = remember(store) { quickAdds(store) }

    // the hero number eases to its new value instead of snapping: the digits count up in place
    val shownNet by animateIntAsState(
        targetValue = t.net.toInt(),
        animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow),
        label = "net",
    )


    PullToRefreshBox(
        isRefreshing = repo.status == "syncing",
        onRefresh = { scope.launch { repo.sync() } },
        modifier = Modifier.fillMaxSize(),
        // the header dot already reports sync state, so no spinner over the content
        indicator = {},
    ) {
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        // leave room for the floating bar and the + so nothing ends up underneath them
        contentPadding = PaddingValues(top = 6.dp, bottom = 132.dp),
    ) {
        item { DayPill(day, today, onDay) }

        item {
            Column {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("RM", color = baki.muted, fontSize = 22.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp, end = 6.dp))
                    Text(rm(shownNet.toLong()), color = baki.onSurface, style = BakiType.displayNumeric)
                }
                Text(
                    if (t.net >= 0) "KEPT FOR THE DAY" else "OUT OF POCKET",
                    color = baki.muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp,
                )
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Chip("in ${rm(t.income)}", baki.up)
                Chip("out ${rm(t.expense)}", baki.down)
            }
        }

        if (live != null && day == today) {
            item { LiveCard(repo, live, onPayout, onPayouts) }
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
                    Spacer(Modifier.width(6.dp))
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
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.card)).background(baki.container).padding(16.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("SHIFT NET · AFTER CAR COST", color = baki.muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        Spacer(Modifier.weight(1f))
                        Box(
                            Modifier.clip(RoundedCornerShape(Radius.pill))
                                .background(bandColor(m.band, baki).copy(alpha = 0.18f))
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                        ) {
                            Text(BANDS[m.band] ?: "—", color = bandColor(m.band, baki), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("RM ${"%.2f".format(m.net)}", color = baki.onSurface, style = BakiType.headlineNumeric)
                        Spacer(Modifier.weight(1f))
                        m.netHr?.let {
                            Text("RM ${"%.0f".format(it)}/hr", color = bandColor(m.band, baki), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Text(
                        "${"%.1f".format(shift.hours)}h · ${"%.0f".format(m.km)} km · fuel + wear RM ${"%.2f".format(m.running)}",
                        color = baki.muted, fontSize = 11.sp,
                    )
                    // what this shift had to beat
                    m.netHr?.let { rate ->
                        val delta = rate - cfg.targetRate
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                if (delta >= 0) "✓" else "✗",
                                color = if (delta >= 0) baki.up else baki.down,
                                fontSize = 11.sp, fontWeight = FontWeight.Bold,
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                "target RM ${"%.0f".format(cfg.targetRate)}/hr · " +
                                    if (delta >= 0) "RM ${"%.0f".format(delta)} above"
                                    else "RM ${"%.0f".format(-delta)} short",
                                color = if (delta >= 0) baki.up else baki.down,
                                fontSize = 11.sp,
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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

        // one line of free text: "toll 4.20 parking 2"
        item { QuickEntryBar(onSubmit = onQuickText) }

        // what you keep buying, one tap away
        if (quick.isNotEmpty()) item {
            Column {
                Text("QUICK ADD", color = baki.muted, style = BakiType.section)
                Spacer(Modifier.height(6.dp))
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val cats = store.settings.cats.catsOut
                    quick.forEach { q ->
                        val name = cats.firstOrNull { it.id == q.cat }?.name ?: q.cat
                        Row(
                            Modifier.heightIn(min = 44.dp)
                                .clip(RoundedCornerShape(Radius.pill))
                                .background(baki.surfaceHigh)
                                .clickable { onQuickAdd(q.cat, q.cents) }
                                .padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(name, color = baki.onSurface, style = BakiType.labelStrong)
                            Text("RM ${rm(q.cents)}", color = baki.primary, style = BakiType.captionNumeric)
                        }
                    }
                }
            }
        }

        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val presets = store.settings.cats.catsOut.filter { it.id != "grabfee" }
                presets.forEach { c ->
                    Row(
                        Modifier.clip(RoundedCornerShape(Radius.pill)).background(baki.container)
                            .clickable { onPreset(c.id) }
                            // 48dp minimum touch target, per the accessibility guidance
                            .heightIn(min = 48.dp)
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            categoryIcon(c.id, c.name), contentDescription = null,
                            tint = baki.primary, modifier = Modifier.size(17.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(c.name, color = baki.onSurface, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${if (day == today) "TODAY" else day.uppercase()} · ${t.entryCount} ENTRIES",
                    color = baki.muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp,
                )
                Spacer(Modifier.weight(1f))
                Text("see all →", color = baki.muted, fontSize = 11.sp, modifier = Modifier.clickable { onSeeAll() })
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
                sub = e.cat,
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
                    Icon(BakiIcons.Receipt, contentDescription = null, tint = baki.muted, modifier = Modifier.size(24.dp))
                }
                Spacer(Modifier.height(12.dp))
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
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.pill)).background(baki.container).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = { onChange(addDays(day, -1)) }) { Text("‹", color = baki.onSurface, fontSize = 22.sp) }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (day == today) "Today" else prettyDate(day), color = baki.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(
                if (day == today) prettyDate(day) else "${weekdayShort(day)} · ${prettyDate(day)}",
                color = baki.muted, fontSize = 11.sp,
            )
        }
        TextButton(enabled = day < today, onClick = { onChange(addDays(day, 1)) }) {
            Text("›", color = if (day < today) baki.onSurface else baki.muted.copy(alpha = 0.3f), fontSize = 22.sp)
        }
    }
}

@Composable
private fun PillButton(label: String, bg: Color, ink: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.clip(RoundedCornerShape(Radius.pill)).background(bg).clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = ink, fontSize = 12.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp))
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
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> onEdit()
                SwipeToDismissBoxValue.EndToStart -> onDelete?.invoke()
                else -> {}
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
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (editing) Arrangement.Start else Arrangement.End,
            ) {
                Text(
                    if (editing) "✎  Edit" else "Delete  🗑",
                    color = if (editing) baki.onPrimaryContainer else baki.down,
                    fontSize = 12.sp, fontWeight = FontWeight.Bold,
                )
            }
        },
    ) {
    Row(
        Modifier.fillMaxWidth().then(sharedMod).clip(RoundedCornerShape(Radius.card)).background(baki.container)
            .pointerInput(title) { detectTapGestures(onLongPress = { onEdit() }) }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(34.dp).clip(RoundedCornerShape(Radius.input)).background(baki.surfaceHigh),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, contentDescription = null, tint = baki.primary, modifier = Modifier.size(18.dp)) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = baki.onSurface, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            if (sub.isNotEmpty()) Text(sub, color = baki.muted, fontSize = 11.sp)
        }
        Text(amount, color = tint, style = BakiType.labelNumeric.copy(fontSize = 16.sp))
    }
    }
}
