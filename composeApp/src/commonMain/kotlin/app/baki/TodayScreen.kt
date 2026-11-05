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
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
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
    onEditEntry: (Entry) -> Unit,
    onDeleteEntry: (Entry) -> Unit,
    onPreset: (String) -> Unit,
    onAddShift: () -> Unit,
    onSeeAll: () -> Unit,
) {
    val baki = LocalBaki.current
    val store = repo.store
    val today = todayIso()
    val t = dayTotals(store, day)
    val entries = entriesOn(store, day).sortedByDescending { it.updated }
    val shifts = shiftsOn(store, day)
    val cfg = store.settings.profit
    val live = repo.live
    val shift = shifts.maxByOrNull { it.updated }
    val scope = rememberCoroutineScope()

    // the hero number eases to its new value instead of snapping
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
                    Text("RM", color = baki.muted, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp, end = 6.dp))
                    Text(rm(shownNet.toLong()), color = baki.onSurface, fontSize = 58.sp, fontWeight = FontWeight.Black)
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
            item { LiveCard(repo, live, onPayout) }
        }

        item { WeekStrip(store, day) }

        val paidUntil = coverPaidUntil(store)
        if (paidUntil != null && nowMillis() < paidUntil) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🛡", fontSize = 12.sp)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Cover paid until ${clockOf(paidUntil)} — starting before then adds no RM ${"%.2f".format(cfg.grabInsurance)}",
                        color = baki.up, fontSize = 11.sp,
                    )
                }
            }
        }

        if (shift != null) {
            item {
                val m = shiftMetrics(shift, cfg)
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(baki.container).padding(16.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("SHIFT NET · AFTER CAR COST", color = baki.muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        Spacer(Modifier.weight(1f))
                        Box(
                            Modifier.clip(RoundedCornerShape(50))
                                .background(bandColor(m.band, baki).copy(alpha = 0.18f))
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                        ) {
                            Text(BANDS[m.band] ?: "—", color = bandColor(m.band, baki), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("RM ${"%.2f".format(m.net)}", color = baki.onSurface, fontSize = 30.sp, fontWeight = FontWeight.Black)
                        Spacer(Modifier.weight(1f))
                        m.netHr?.let {
                            Text("RM ${"%.0f".format(it)}/hr", color = bandColor(m.band, baki), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Text(
                        "${"%.1f".format(shift.hours)}h · ${"%.0f".format(m.km)} km · fuel + wear RM ${"%.2f".format(m.running)}",
                        color = baki.muted, fontSize = 11.sp,
                    )
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

        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val presets = store.settings.cats.catsOut.filter { it.id != "grabfee" }
                presets.forEach { c ->
                    Row(
                        Modifier.clip(RoundedCornerShape(50)).background(baki.container)
                            .clickable { onPreset(c.id) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(c.icon, fontSize = 15.sp)
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
            val m = shiftMetrics(sh, cfg)
            RowCardIcon(
                icon = "🚗",
                title = "Shift ${sh.start.ifEmpty { sh.date }}",
                sub = "${m.band} · ${"%.1f".format(sh.hours)}h · ${"%.0f".format(m.km)} km",
                amount = "RM ${"%.2f".format(m.net)}",
                tint = baki.up,
                onEdit = { onEditEntry(Entry(sh.id, sh.date, "shift", "shift", 0)) },
            )
        }
        items(entries) { e ->
            RowCardIcon(
                icon = store.settings.cats.catsOut.firstOrNull { it.id == e.cat }?.icon
                    ?: store.settings.cats.catsIn.firstOrNull { it.id == e.cat }?.icon ?: "🏷",
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
            Text("Nothing recorded for this day. Tap a category above or + to start.", color = baki.muted, fontSize = 13.sp)
        }
    }
    }
}

@Composable
private fun DayPill(day: String, today: String, onChange: (String) -> Unit) {
    val baki = LocalBaki.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(50)).background(baki.container).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = { onChange(addDays(day, -1)) }) { Text("‹", color = baki.onSurface, fontSize = 20.sp) }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (day == today) "Today" else prettyDate(day), color = baki.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(
                if (day == today) prettyDate(day) else "${weekdayShort(day)} · ${prettyDate(day)}",
                color = baki.muted, fontSize = 11.sp,
            )
        }
        TextButton(enabled = day < today, onClick = { onChange(addDays(day, 1)) }) {
            Text("›", color = if (day < today) baki.onSurface else baki.muted.copy(alpha = 0.3f), fontSize = 20.sp)
        }
    }
}

@Composable
private fun PillButton(label: String, bg: Color, ink: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.clip(RoundedCornerShape(50)).background(bg).clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = ink, fontSize = 13.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun RowCardIcon(
    icon: String,
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
                Modifier.fillMaxSize().clip(RoundedCornerShape(18.dp))
                    .background(if (editing) baki.primaryContainer else baki.down.copy(alpha = 0.22f))
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (editing) Arrangement.Start else Arrangement.End,
            ) {
                Text(
                    if (editing) "✎  Edit" else "Delete  🗑",
                    color = if (editing) baki.onPrimaryContainer else baki.down,
                    fontSize = 13.sp, fontWeight = FontWeight.Bold,
                )
            }
        },
    ) {
    Row(
        Modifier.fillMaxWidth().then(sharedMod).clip(RoundedCornerShape(18.dp)).background(baki.container)
            .pointerInput(title) { detectTapGestures(onLongPress = { onEdit() }) }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(baki.surfaceHigh),
            contentAlignment = Alignment.Center,
        ) { Text(icon, fontSize = 15.sp) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = baki.onSurface, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            if (sub.isNotEmpty()) Text(sub, color = baki.muted, fontSize = 11.sp)
        }
        Text(amount, color = tint, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
    }
}
