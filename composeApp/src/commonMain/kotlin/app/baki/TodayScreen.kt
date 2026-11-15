package app.baki

import androidx.compose.foundation.shape.CircleShape

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.toShape

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val BANDS = mapOf(
    "quiet" to "Quiet", "slow" to "Slow", "steady" to "Steady", "good" to "Good", "peak" to "Peak",
)

@Composable
fun bandColor(band: String, scheme: androidx.compose.material3.ColorScheme): Color = when (band) {
    "quiet" -> scheme.error
    "slow", "steady" -> scheme.secondary
    "good" -> scheme.tertiary
    else -> scheme.primary
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
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
    onHeaderCollapsed: (Boolean) -> Unit = {},
) {
    val scheme = MaterialTheme.colorScheme
    val listState = rememberLazyListState()
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collect { (index, offset) -> onHeaderCollapsed(index > 0 || offset > 56) }
    }
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
        state = listState,
        modifier = Modifier.fillMaxSize().padding(horizontal = Space.l),
        verticalArrangement = Arrangement.spacedBy(Space.s),
        // leave room for the floating bar and the + so nothing ends up underneath them
        contentPadding = PaddingValues(top = 6.dp, bottom = 190.dp),
    ) {
        item { DayPill(day, today, onDay) }

        item {
            BoxWithConstraints {
                val largeText = LocalDensity.current.fontScale >= 1.6f
                val heroInk = scheme.onPrimaryContainer
                val heroFont = bakiDisplayFontFamily()
                val amountSize = when {
                    largeText -> 72.sp
                    maxWidth < 340.dp -> 108.sp
                    else -> 132.sp
                }
                Surface(
                    shape = MaterialTheme.shapes.extraLarge,
                    color = scheme.primaryContainer,
                    contentColor = heroInk,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(Space.l)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                if (t.net >= 0) "TODAY'S TAKE" else "DAMAGE REPORT",
                                color = heroInk.copy(alpha = 0.72f),
                                letterSpacing = 1.6.sp,
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                modifier = Modifier.weight(1f),
                            )
                            val perHour = operating.perHour
                            if (operating.shiftCount > 0 && perHour != null) {
                                TargetBadge(
                                    beat = perHour >= cfg.targetRate,
                                    reducedMotion = repo.config.reducedMotion,
                                )
                            }
                        }
                        Spacer(Modifier.height(Space.s))
                        Text(
                            "RM",
                            color = heroInk.copy(alpha = 0.62f),
                            letterSpacing = 1.sp,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        )
                        EasedMoney(
                            t.net,
                            heroInk,
                            heroMoneyStyle(t.net, MaterialTheme.typography).copy(
                                fontFamily = heroFont,
                                fontFeatureSettings = "tnum",
                                fontSize = amountSize,
                                lineHeight = amountSize,
                                letterSpacing = (-3).sp,
                            ),
                            prefix = "",
                        )
                    }
                }
            }
        }

        // Supporting facts are deliberately quieter than the hero number.
        item {
            val largeText = LocalDensity.current.fontScale >= 1.6f
            Surface(
                shape = MaterialTheme.shapes.large,
                color = scheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(horizontal = Space.m, vertical = Space.m)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.l)) {
                        DayFact("IN", "RM ${rm(t.income)}", scheme.tertiary, Modifier.weight(1f))
                        DayFact("OUT", "RM ${rm(t.expense)}", scheme.error, Modifier.weight(1f))
                    }
                    if (operating.shiftCount > 0) {
                        Spacer(Modifier.height(Space.s))
                        val operatingValue = "RM ${rm(operating.net)}" + (operating.perHour?.let { " · ${rm(it)}/h" } ?: "")
                        if (largeText) {
                            Column {
                                Text(
                                    "OPERATING",
                                    color = scheme.onSurfaceVariant,
                                    letterSpacing = 1.2.sp,
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                )
                                Spacer(Modifier.height(Space.xxs))
                                Text(
                                    operatingValue,
                                    color = if (operating.net >= 0) scheme.onSurface else scheme.error,
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontFeatureSettings = "tnum",
                                        fontWeight = FontWeight.Bold,
                                    ),
                                )
                            }
                        } else {
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(
                                    "OPERATING",
                                    color = scheme.onSurfaceVariant,
                                    letterSpacing = 1.2.sp,
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    operatingValue,
                                    color = if (operating.net >= 0) scheme.onSurface else scheme.error,
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontFeatureSettings = "tnum",
                                        fontWeight = FontWeight.Bold,
                                    ),
                                )
                            }
                        }
                        Text(
                            "after running costs",
                            color = scheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
        }

        food?.let { f ->
            item {
                val fraction = if (f.cap > 0) (f.spent.toFloat() / f.cap).coerceIn(0f, 1f) else 0f
                Column(
                    Modifier.fillMaxWidth()
                        .clip(MaterialTheme.shapes.large)
                        .background(scheme.surfaceContainerLow)
                        .padding(horizontal = Space.m, vertical = Space.m),
                ) {
                    if (LocalDensity.current.fontScale >= 1.6f) {
                        Column {
                            Text(
                                "FOOD FUND",
                                color = scheme.onSurfaceVariant,
                                letterSpacing = 1.2.sp,
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            )
                            Spacer(Modifier.height(Space.xxs))
                            Text(
                                "RM ${rm(f.spent)} / ${rm(f.cap)}",
                                color = if (f.over) scheme.error else scheme.onSurface,
                                style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum", fontWeight = FontWeight.Bold),
                            )
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "FOOD FUND",
                                color = scheme.onSurfaceVariant,
                                letterSpacing = 1.2.sp,
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            )
                            Spacer(Modifier.weight(1f))
                            Text(
                                "RM ${rm(f.spent)} / ${rm(f.cap)}",
                                color = if (f.over) scheme.error else scheme.onSurface,
                                style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum", fontWeight = FontWeight.Bold),
                            )
                        }
                    }
                    Spacer(Modifier.height(Space.s))
                    LinearWavyProgressIndicator(
                        progress = { fraction },
                        modifier = Modifier.fillMaxWidth().height(12.dp),
                        color = if (f.over) scheme.error else scheme.tertiary,
                        trackColor = scheme.outlineVariant.copy(alpha = 0.55f),
                    )
                    Spacer(Modifier.height(Space.s))
                    Text(
                        if (f.over) "RM ${rm(-f.remaining)} over. The snacks won."
                        else "RM ${rm(f.remaining)} left for snacks.",
                        color = if (f.over) scheme.error else scheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    )
                }
            }
        }

        if (live != null && day == today) {
            item {
                // one-shot entrance: start from false so the first composition actually animates in
                val visible = remember {
                    MutableTransitionState(false).apply { targetState = true }
                }
                val motion = MaterialTheme.motionScheme
                AnimatedVisibility(
                    visibleState = visible,
                    enter = fadeIn(motion.defaultEffectsSpec<Float>()) +
                        scaleIn(initialScale = 0.97f, animationSpec = motion.defaultSpatialSpec<Float>()) +
                        slideInVertically(motion.defaultSpatialSpec<androidx.compose.ui.unit.IntOffset>()) { it / 12 },
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
                        BakiIcons.Shield, contentDescription = null, tint = scheme.tertiary,
                        modifier = Modifier.padding(top = 1.dp).size(14.dp),
                    )
                    Spacer(Modifier.width(Space.s))
                    // weight so a wrapped line stays beside the icon instead of under it
                    Text(
                        "Cover paid until ${clockOf(paidUntil)}",
                        color = scheme.tertiary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f),
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
                            Modifier.clip(MaterialTheme.shapes.large).background(scheme.surfaceContainerLow).padding(Space.l)
                        ),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "WHAT THE SHIFT PAID", color = scheme.onSurfaceVariant, letterSpacing = 1.1.sp,
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), modifier = Modifier.weight(1f),
                        )
                        
                            Box(
                                Modifier.clip(CircleShape)
                                    .background(bandColor(m.band, scheme).copy(alpha = 0.18f))
                                    .padding(horizontal = Space.m, vertical = 4.dp),
                            ) {
                                Text(BANDS[m.band] ?: "—", color = bandColor(m.band, scheme), style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                            }
                        
                    }
                    Spacer(Modifier.height(Space.xs))
                    BoxWithConstraints {
                        val compact = maxWidth < 340.dp || LocalDensity.current.fontScale > 1.15f
                        if (compact) {
                            Column {
                                Text("RM ${rm(m.net)}", color = scheme.onSurface, style = MaterialTheme.typography.headlineMedium.copy(fontFeatureSettings = "tnum"), maxLines = 1)
                                m.netHr?.let {
                                    Text("RM ${rm(it)}/hr", color = bandColor(m.band, scheme), style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold), maxLines = 1)
                                }
                            }
                        } else {
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text("RM ${rm(m.net)}", color = scheme.onSurface, style = MaterialTheme.typography.headlineMedium.copy(fontFeatureSettings = "tnum"))
                                Spacer(Modifier.weight(1f))
                                m.netHr?.let {
                                    Text("RM ${rm(it)}/hr", color = bandColor(m.band, scheme), style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold) )
                                }
                            }
                        }
                    }
                    Text(
                        "${"%.1f".format(shift.hours)}h · ${"%.0f".format(m.km)} km" +
                            (m.grossPerKm?.let { " · RM ${rm(it)}/km" } ?: "") +
                            " · fuel + wear RM ${rm(m.running)}",
                        color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium )
                    // what this shift had to beat
                    m.netHr?.let { rate ->
                        val delta = rate - cfg.targetRate
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                if (delta >= 0) "✓" else "✗",
                                color = if (delta >= 0) scheme.tertiary else scheme.error, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold) )
                            Spacer(Modifier.width(Space.xs))
                            Text(
                                if (delta >= 0) "RM ${rm(delta)} above target"
                                else "RM ${rm(-delta)} short of target",
                                color = if (delta >= 0) scheme.tertiary else scheme.error, style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    if (day == today && live == null && !repo.readOnly) {
                        Spacer(Modifier.height(Space.m))
                        FilledTonalButton(
                            onClick = { repo.startLive() },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Start the meter", style = MaterialTheme.typography.labelLargeEmphasized) }
                    }
                    
                }
            }
        } else if (live == null && !repo.readOnly && day == today) {
            item {
                FilledTonalButton(
                    onClick = { repo.startLive() },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                ) { Text("Start a live shift", style = MaterialTheme.typography.labelLargeEmphasized) }
            }
        }

        // the day's movement: between the shift card and the ledger
        item { TodayRouteCard(store = store, day = day, live = live) }

        // what you keep buying, one tap away
        if (quick.isNotEmpty()) item {
            Column {
                Text("QUICK HITS", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
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
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                Box(
                    Modifier
                        .size(12.dp)
                        .clip(MaterialShapes.SoftBurst.toShape())
                        .background(scheme.primary),
                )
                Text(
                    "${if (day == today) "TODAY" else day.uppercase()} · ${t.entryCount} ${if (t.entryCount == 1) "ENTRY" else "ENTRIES"}",
                    color = scheme.onSurfaceVariant,
                    letterSpacing = 1.sp,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                )
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
                    tint = scheme.tertiary,
                    onEdit = { onEditEntry(Entry(sh.id, sh.date, "shift", "shift", 0)) },
                )
            }
        }
        items(entries, key = { it.id }) { e ->
            Box(Modifier.animateItem()) {
                RowCardIcon(
                    icon = categoryIcon(e.cat),
                    title = e.note.ifEmpty { categoryLabel(store, e.cat) },
                    sub = listOfNotNull(
                        e.cat,
                        e.odo?.takeIf { it > 0 }?.let { "$it km" },
                        loggedTime(e.loggedAt)?.let { "logged $it" },
                    ).joinToString(" · "),
                    amount = (if (e.type == "income") "+" else "−") + rm(e.amount),
                    tint = if (e.type == "income") scheme.tertiary else scheme.error,
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
                    Modifier
                        .size(52.dp)
                        .clip(MaterialShapes.SoftBurst.toShape())
                        .background(scheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(BakiIcons.Receipt, contentDescription = null, tint = scheme.onPrimaryContainer, modifier = Modifier.size(IconSize.bar))
                }
                Spacer(Modifier.height(Space.m))
                Text("A clean page.", color = scheme.onSurface, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                Spacer(Modifier.height(3.dp))
                Text(
                    "Tap +. Your wallet isn't going to log itself.",
                    color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium,
                )
                
            }
        }
    }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun QuickAction(
    label: String,
    amount: String?,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    
        Row(
            Modifier.heightIn(min = Control.secondary)
                .clip(CircleShape)
                .background(scheme.surfaceContainerHigh)
                .clickable { onClick() }
                .padding(horizontal = Space.l),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.s),
        ) {
            if (icon != null) {
                Box(
                    Modifier
                        .size(26.dp)
                        .clip(MaterialShapes.SoftBurst.toShape())
                        .background(scheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, contentDescription = null, tint = scheme.onPrimaryContainer, modifier = Modifier.size(IconSize.inline))
                }
            }
            Text(label, color = scheme.onSurface, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
            amount?.let { Text(it, color = scheme.primary, style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum", fontWeight = FontWeight.Bold)) }
        }
    
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DayPill(day: String, today: String, onChange: (String) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val isToday = day == today
    Surface(
        color = scheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Space.s, vertical = Space.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.s),
        ) {
            ShapeNavButton(
                label = "‹",
                enabled = true,
                onClick = { onChange(addDays(day, -1)) },
                description = "Previous day",
            )
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Box(
                        Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(scheme.primary),
                    )
                    Text(
                        if (isToday) "TODAY" else weekdayShort(day).uppercase(),
                        color = scheme.primary,
                        letterSpacing = 1.4.sp,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    )
                }
                Text(
                    prettyDate(day),
                    color = scheme.onSurface,
                    maxLines = if (LocalDensity.current.fontScale >= 1.6f) 2 else 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    style = if (LocalDensity.current.fontScale >= 1.6f)
                        MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    else MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                )
                Text(
                    if (isToday) "a day in the life" else weekdayShort(day),
                    color = scheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            ShapeNavButton(
                label = "›",
                enabled = day < today,
                onClick = { onChange(addDays(day, 1)) },
                description = "Next day",
            )
        }
    }
}

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalSharedTransitionApi::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)
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
    val scheme = MaterialTheme.colorScheme
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
                Modifier.fillMaxSize().clip(MaterialTheme.shapes.large)
                    .background(if (editing) scheme.primaryContainer else scheme.error.copy(alpha = 0.22f))
                    .padding(horizontal = Space.ml),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (editing) Arrangement.Start else Arrangement.End,
            ) {
                Text(
                    if (editing) "Edit" else "Delete",
                    color = if (editing) scheme.onPrimaryContainer else scheme.error, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold) )
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
                    Modifier.clip(MaterialTheme.shapes.large).background(scheme.surfaceContainerLow)
                )
                .pointerInput(title) { detectTapGestures(onLongPress = { onEdit() }) }
                .semantics { customActions = a11yActions }
                .padding(vertical = Space.m, horizontal = Space.l),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(MaterialShapes.SoftBurst.toShape())
                    .background(tint.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(IconSize.inline)) }
            Spacer(Modifier.width(Space.m))
            Column(Modifier.weight(1f)) {
                Text(title, color = scheme.onSurface, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold))
                if (sub.isNotEmpty()) Text(sub, color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            }
            Text(amount, color = tint, style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"))
        }
    }
    }
}

@Composable
private fun DayFact(label: String, value: String, dot: Color, modifier: Modifier = Modifier) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(dot))
            Text(
                label,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.sp,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(
            value,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum", fontWeight = FontWeight.Bold),
        )
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TargetBadge(beat: Boolean, reducedMotion: Boolean) {
    val scheme = MaterialTheme.colorScheme
    var celebrate by remember { mutableStateOf(false) }
    LaunchedEffect(beat) {
        if (beat && !reducedMotion) {
            celebrate = true
            delay(1200)
            celebrate = false
        }
    }
    Box(contentAlignment = Alignment.Center) {
        if (celebrate) {
            listOf(
                (-54).dp to (-22).dp,
                48.dp to (-28).dp,
                54.dp to 18.dp,
                (-42).dp to 24.dp,
            ).forEachIndexed { index, (x, y) ->
                androidx.compose.animation.AnimatedVisibility(
                    visible = celebrate,
                    enter = fadeIn() + scaleIn(initialScale = 0.2f),
                    exit = fadeOut(),
                ) {
                    Box(
                        Modifier
                            .offset(x = x, y = y)
                            .size(if (index % 2 == 0) 9.dp else 6.dp)
                            .clip(MaterialShapes.SoftBurst.toShape())
                            .background(if (index % 2 == 0) scheme.tertiary else scheme.primary),
                    )
                }
            }
        }
        Surface(
            shape = RoundedCornerShape(50),
            color = if (beat) scheme.tertiaryContainer else scheme.surfaceContainerHigh,
            contentColor = if (beat) scheme.onTertiaryContainer else scheme.onSurface,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = Space.s, vertical = Space.xs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Box(
                    Modifier
                        .size(14.dp)
                        .clip(MaterialShapes.SoftBurst.toShape())
                        .background(if (beat) scheme.onTertiaryContainer else scheme.primary),
                )
                Text(
                    if (beat) "BEATEN" else "CHASING",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                )
            }
        }
    }
}
