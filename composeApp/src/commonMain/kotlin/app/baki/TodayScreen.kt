package app.baki

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
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

/**
 * Today, reduced: one number, one line to add, the ledger as typography. Cards, chips, icon tiles and
 * section boxes are gone; structure comes from scale, alignment and space. Glass remains on the nav and
 * on sheets only.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(
    repo: BakiRepo,
    day: String,
    onDay: (String) -> Unit,
    onPayout: () -> Unit,
    onPayouts: () -> Unit,
    onQuickText: (String) -> Unit,
    onEditEntry: (Entry) -> Unit,
    onDeleteEntry: (Entry) -> Unit,
    onAddShift: () -> Unit,
    onSeeAll: () -> Unit,
    onAdd: () -> Unit,
) {
    val baki = LocalBaki.current
    val store = repo.store
    val today = todayIso()
    val t = remember(store, day) { dayTotals(store, day) }
    val entries = remember(store, day) { entriesOn(store, day).sortedByDescending { it.updated } }
    val shifts = remember(store, day) { shiftsOn(store, day) }
    val cats = remember(store) { store.settings.cats.catsOut.associateBy { it.id } }
    val cfg = store.settings.profit
    val live = repo.live
    val scope = rememberCoroutineScope()
    val paidUntil = coverPaidUntil(store)

    PullToRefreshBox(
        isRefreshing = repo.status == "syncing",
        onRefresh = { scope.launch { repo.sync() } },
        modifier = Modifier.fillMaxSize(),
        indicator = {},
    ) {
        LazyColumn(
            Modifier.fillMaxSize().padding(horizontal = Space.xl),
            contentPadding = PaddingValues(top = Space.s, bottom = 132.dp),
        ) {
            item { DateLine(day, today, onDay) }

            item {
                Spacer(Modifier.height(Space.xxl))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        "RM", color = baki.muted,
                        modifier = Modifier.padding(bottom = Space.s, end = 6.dp),
                        style = BakiType.title,
                    )
                    EasedMoney(t.net, baki.onSurface, BakiType.displayNumeric, prefix = "")
                }
                Spacer(Modifier.height(Space.xs))
                Text(
                    if (t.net >= 0) "kept for the day" else "out of pocket",
                    color = baki.muted, style = BakiType.caption,
                )
                Spacer(Modifier.height(Space.s))
                Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                    Text("in ${rm(t.income)}", color = baki.muted, style = BakiType.captionNumeric)
                    Text("·", color = baki.muted.copy(alpha = 0.6f), style = BakiType.captionNumeric)
                    Text("out ${rm(t.expense)}", color = baki.muted, style = BakiType.captionNumeric)
                }
            }

            if (live != null && day == today) {
                item {
                    Spacer(Modifier.height(Space.xl))
                    LiveInline(repo, live, onPayout, onPayouts)
                }
            } else if (day == today && !repo.readOnly) {
                item {
                    Spacer(Modifier.height(Space.m))
                    FlatAction("Start a live shift") { repo.startLive() }
                }
            }

            item {
                Spacer(Modifier.height(Space.xxl))
                QuickEntryBar(onSubmit = onQuickText)
            }

            if (entries.isNotEmpty() || shifts.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(Space.xxl))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("ENTRIES", color = baki.muted, letterSpacing = 1.2.sp, style = BakiType.captionStrong)
                        Spacer(Modifier.width(Space.s))
                        Text("${t.entryCount}", color = baki.muted, style = BakiType.captionNumeric)
                        Spacer(Modifier.weight(1f))
                        Text(
                            "Add", color = baki.primary,
                            modifier = Modifier.clickable { onAdd() }.padding(Space.s),
                            style = BakiType.captionStrong,
                        )
                    }
                }
                items(entries, key = { it.id }) { entry ->
                    MinimalLedgerRow(
                        title = entry.note.ifBlank { cats[entry.cat]?.name ?: entry.cat },
                        sub = if (entry.note.isBlank()) "" else cats[entry.cat]?.name ?: entry.cat,
                        amount = (if (entry.type == "income") "+" else "−") + rm(entry.amount),
                        tint = if (entry.type == "income") baki.up else baki.onSurface,
                        onEdit = { onEditEntry(entry) },
                        onDelete = { onDeleteEntry(entry) },
                    )
                }
                if (shifts.isNotEmpty()) {
                    items(shifts, key = { it.id }) { shift ->
                        val m = shiftMetrics(shift, cfg, overheadPerHour(store, shift.date.take(7)))
                        MinimalLedgerRow(
                            title = "Shift ${shift.start.ifEmpty { shift.date }}",
                            sub = "${"%.1f".format(shift.hours)} h · ${"%.0f".format(m.km)} km · ${BANDS[m.band] ?: "—"}",
                            amount = "RM ${"%.2f".format(m.net)}",
                            tint = baki.onSurface,
                            onEdit = { onEditEntry(Entry(shift.id, shift.date, "shift", "shift", 0)) },
                        )
                    }
                }
                if (shifts.isNotEmpty()) {
                    item {
                        Row(Modifier.fillMaxWidth().padding(top = Space.s), horizontalArrangement = Arrangement.spacedBy(Space.l)) {
                            Text(
                                "Add shift", color = baki.muted,
                                modifier = Modifier.clickable { onAddShift() }.padding(vertical = Space.s),
                                style = BakiType.caption,
                            )
                            Text("All shifts →", color = baki.muted, modifier = Modifier.clickable { onSeeAll() }, style = BakiType.caption)
                        }
                    }
                }
                item {
                    Text(
                        "All entries →", color = baki.muted,
                        modifier = Modifier.padding(top = Space.s).clickable { onSeeAll() },
                        style = BakiType.caption,
                    )
                }
            } else {
                item {
                    Spacer(Modifier.height(Space.xxl))
                    Text("Nothing recorded yet", color = baki.onSurface, style = BakiType.bodyStrong)
                    Spacer(Modifier.height(Space.xs))
                    Text(
                        "Type a line above — “toll 4.20”, “petrol 40” — or Add for the full form.",
                        color = baki.muted, style = BakiType.caption,
                    )
                }
            }

            item {
                Spacer(Modifier.height(56.dp))
                if (paidUntil != null && nowMillis() < paidUntil) {
                    Text(
                        "Cover until ${clockOf(paidUntil)} · starting before then adds no RM ${"%.2f".format(cfg.grabInsurance)}",
                        color = baki.up, style = BakiType.caption,
                    )
                    Spacer(Modifier.height(Space.xl))
                }
                Text("LAST 7 DAYS", color = baki.muted, letterSpacing = 1.2.sp, style = BakiType.captionStrong)
                Spacer(Modifier.height(Space.m))
                WeekStrip(store, day)
            }

            item {
                Spacer(Modifier.height(Space.xxl))
                TodayRouteCard(store = store, day = day, live = live)
            }
        }
    }
}

@Composable
private fun DateLine(day: String, today: String, onChange: (String) -> Unit) {
    val baki = LocalBaki.current
    Row(Modifier.fillMaxWidth().padding(top = Space.m), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                if (day == today) "TODAY" else weekdayShort(day).uppercase(),
                color = baki.muted, letterSpacing = 1.5.sp, style = BakiType.captionStrong,
            )
            Text(
                if (day == today) prettyDate(day) else "${prettyDate(day)}",
                color = baki.onSurface, style = BakiType.bodyStrong,
            )
        }
        Text(
            "‹", color = baki.onSurface, style = BakiType.title,
            modifier = Modifier.clickable { onChange(addDays(day, -1)) }.padding(horizontal = Space.ml, vertical = Space.xs),
        )
        if (day < today) {
            Text(
                "›", color = baki.onSurface, style = BakiType.title,
                modifier = Modifier
                    .clickable { onChange(addDays(day, 1)) }
                    .padding(horizontal = Space.ml, vertical = Space.xs),
            )
        }
    }
}

@Composable
private fun FlatAction(label: String, onClick: () -> Unit) {
    val baki = LocalBaki.current
    Row(
        Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = Space.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = baki.primary, style = BakiType.bodyStrong)
        Spacer(Modifier.weight(1f))
        Text("→", color = baki.primary, style = BakiType.body)
    }
}

@Composable
private fun LiveInline(repo: BakiRepo, live: LiveShift, onPayout: () -> Unit, onPayouts: () -> Unit) {
    val baki = LocalBaki.current
    val scope = rememberCoroutineScope()
    val now = nowMillis()
    val worked = liveWorkedMs(live, now)
    val onBreak = isPaused(live)
    val total = liveTotalCents(live)
    val hours = worked / 3_600_000.0

    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).clip(RoundedCornerShape(Radius.pill)).background(if (onBreak) baki.warn else baki.primary))
            Spacer(Modifier.width(Space.s))
            Text(if (onBreak) "ON BREAK" else "LIVE", color = if (onBreak) baki.warn else baki.primary, letterSpacing = 1.2.sp, style = BakiType.captionStrong)
            Spacer(Modifier.weight(1f))
            Text("since ${startClock(live.start)}", color = baki.muted, style = BakiType.captionNumeric)
        }
        Spacer(Modifier.height(Space.s))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(liveClock(worked), color = baki.onSurface, style = BakiType.headline)
            Spacer(Modifier.weight(1f))
            Text("RM ${rm(total)}", color = baki.onSurface, style = BakiType.headlineNumeric)
        }
        if (hours > 0.05) {
            Text("RM ${rm(total / 100.0 / hours)}/hr", color = baki.muted, style = BakiType.captionNumeric)
        }
        Spacer(Modifier.height(Space.s))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.xl)) {
            Text(
                "+ Payout", color = baki.primary,
                modifier = Modifier.clickable { onPayout() }.padding(vertical = Space.s),
                style = BakiType.labelStrong,
            )
            Text(
                if (onBreak) "Resume" else "Pause", color = baki.onSurface,
                modifier = Modifier
                    .clickable(enabled = !repo.readOnly) { if (onBreak) repo.resumeLive() else repo.pauseLive() }
                    .padding(vertical = Space.s),
                style = BakiType.label,
            )
            Text(
                "End", color = baki.muted,
                modifier = Modifier
                    .clickable(enabled = !repo.readOnly) { scope.launch { repo.endLive() } }
                    .padding(vertical = Space.s),
                style = BakiType.label,
            )
            Spacer(Modifier.weight(1f))
            Text(
                "${live.payouts.size} payouts →", color = baki.muted,
                modifier = Modifier.clickable { onPayouts() }.padding(vertical = Space.s),
                style = BakiType.caption,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MinimalLedgerRow(
    title: String,
    sub: String,
    amount: String,
    tint: Color,
    onEdit: () -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    val baki = LocalBaki.current
    val swipe = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> onEdit()
                SwipeToDismissBoxValue.EndToStart -> onDelete?.invoke()
                else -> {}
            }
            false
        },
    )
    SwipeToDismissBox(
        state = swipe,
        enableDismissFromStartToEnd = true,
        enableDismissFromEndToStart = onDelete != null,
        backgroundContent = {
            val editing = swipe.dismissDirection == SwipeToDismissBoxValue.StartToEnd
            Row(
                Modifier.fillMaxSize().background(if (editing) baki.primaryContainer else baki.down.copy(alpha = 0.18f))
                    .padding(horizontal = Space.l),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (editing) Arrangement.Start else Arrangement.End,
            ) {
                Text(if (editing) "Edit" else "Delete", color = if (editing) baki.onPrimaryContainer else baki.down, style = BakiType.labelStrong)
            }
        },
    ) {
        Row(
            // opaque canvas face: otherwise the swipe actions show through a flat row
            Modifier.fillMaxWidth().background(baki.surface)
                .pointerInput(title) { detectTapGestures(onLongPress = { onEdit() }) }
                .padding(vertical = Space.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, color = baki.onSurface, style = BakiType.body)
                if (sub.isNotEmpty()) Text(sub, color = baki.muted, style = BakiType.caption)
            }
            Text(amount, color = tint, style = BakiType.subtitleNumeric)
        }
    }
}
