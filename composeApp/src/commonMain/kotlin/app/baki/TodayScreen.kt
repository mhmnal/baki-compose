package app.baki

import androidx.compose.foundation.shape.CircleShape

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.Surface
import androidx.compose.material3.toShape

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.text.TextAutoSize
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.sp
import kotlin.math.roundToLong
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
    val paidUntil = coverPaidUntil(store)
    var shiftExpanded by remember(day) { mutableStateOf(false) }
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
            val largeText = LocalDensity.current.fontScale >= 1.6f
            Column(Modifier.fillMaxWidth()) {
                Text(
                    if (day == today) "TODAY'S TAKE" else "THAT DAY'S TAKE",
                    color = scheme.onSurfaceVariant,
                    letterSpacing = 1.4.sp,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                )
                Spacer(Modifier.height(Space.xs))
                if (largeText) {
                    HeroAmount(
                        cents = t.net, reducedMotion = repo.config.reducedMotion,
                        fontFamily = archivoDisplayFontFamily(),
                        background = scheme.primary, ink = scheme.onPrimary,
                        decimalInk = scheme.tertiaryContainer, largeText = true,
                        description = if (day == today) "Today's take" else "Take for ${prettyDate(day)}",
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(Space.s))
                    FoodCard(food, paidUntil, largeText = true, animate = !repo.config.reducedMotion, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(Space.s))
                    DayCard(store, today, day, onDay, Modifier.fillMaxWidth().height(132.dp))
                } else {
                    Row(
                        Modifier.fillMaxWidth().height(206.dp),
                        horizontalArrangement = Arrangement.spacedBy(Space.s),
                    ) {
                        HeroAmount(
                            cents = t.net, reducedMotion = repo.config.reducedMotion,
                            fontFamily = archivoDisplayFontFamily(),
                            background = scheme.primary, ink = scheme.onPrimary,
                            decimalInk = scheme.tertiaryContainer, largeText = false,
                            description = if (day == today) "Today's take" else "Take for ${prettyDate(day)}",
                            modifier = Modifier.weight(0.6f).fillMaxHeight(),
                        )
                        Column(
                            Modifier.weight(0.4f).fillMaxHeight(),
                            verticalArrangement = Arrangement.spacedBy(Space.s),
                        ) {
                            FoodCard(food, paidUntil, largeText = false, animate = !repo.config.reducedMotion, modifier = Modifier.fillMaxWidth().height(120.dp))
                            DayCard(store, today, day, onDay, Modifier.fillMaxWidth().weight(1f))
                        }
                    }
                }
                if (operating.shiftCount > 0) {
                    Spacer(Modifier.height(Space.xs))
                    Text(
                        "OPERATING · RM ${rm(operating.net)}" + (operating.perHour?.let { " · ${rm(it)}/h" } ?: "") + " · after running costs",
                        color = if (operating.net >= 0) scheme.onSurfaceVariant else scheme.error,
                        style = MaterialTheme.typography.labelSmall,
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

        if (day == today && live == null && !repo.readOnly) {
            item { StartShiftCard(hasShift = shift != null, onStart = { repo.startLive() }) }
        }

        if (shift != null) {
            item {
                val m = shiftMetrics(shift, cfg, overheadPerHour(store, shift.date.take(7)))
                val largeText = LocalDensity.current.fontScale >= 1.6f
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = scheme.surfaceContainerLow,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { shiftExpanded = !shiftExpanded }
                        .semantics {
                            role = Role.Button
                            contentDescription = if (shiftExpanded) "Collapse shift details" else "Expand shift details"
                        }
                        .animateContentSize(),
                ) {
                    Column(Modifier.padding(Space.m)) {
                        if (largeText) {
                            Text(
                                "WHAT THE SHIFT PAID",
                                color = scheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            )
                            Text(
                                "RM ${rm(m.net)}",
                                color = scheme.onSurface,
                                style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum", fontWeight = FontWeight.Bold),
                            )
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                                ShiftBand(m.band, scheme)
                                m.netHr?.let {
                                    Text("RM ${rm(it)}/hr", color = bandColor(m.band, scheme), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                                }
                                Spacer(Modifier.weight(1f))
                                Text(if (shiftExpanded) "−" else "+", color = scheme.primary, style = MaterialTheme.typography.titleMedium)
                            }
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        "WHAT THE SHIFT PAID",
                                        color = scheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    )
                                    Text(
                                        "RM ${rm(m.net)}",
                                        color = scheme.onSurface,
                                        style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum", fontWeight = FontWeight.Bold),
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    ShiftBand(m.band, scheme)
                                    m.netHr?.let {
                                        Text("RM ${rm(it)}/hr", color = bandColor(m.band, scheme), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                                    }
                                }
                                Spacer(Modifier.width(Space.s))
                                Text(if (shiftExpanded) "−" else "+", color = scheme.primary, style = MaterialTheme.typography.titleMedium)
                            }
                        }
                        if (shiftExpanded) {
                            Spacer(Modifier.height(Space.s))
                            Text(
                                "${"%.1f".format(shift.hours)}h · ${"%.0f".format(m.km)} km" +
                                    (m.grossPerKm?.let { " · RM ${rm(it)}/km" } ?: "") +
                                    " · fuel + wear RM ${rm(m.running)}",
                                color = scheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            m.netHr?.let { rate ->
                                val delta = rate - cfg.targetRate
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        if (delta >= 0) "✓" else "✗",
                                        color = if (delta >= 0) scheme.tertiary else scheme.error,
                                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                    )
                                    Spacer(Modifier.width(Space.xs))
                                    Text(
                                        if (delta >= 0) "RM ${rm(delta)} above target" else "RM ${rm(-delta)} short of target",
                                        color = if (delta >= 0) scheme.tertiary else scheme.error,
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // the day's movement: between the shift card and the ledger
        item { TodayRouteCard(store = store, day = day, live = live) }

        // what you keep buying, one tap away
        if (day == today && !repo.readOnly && quick.isNotEmpty()) item {
            Column {
                Text("QUICK HITS", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                Spacer(Modifier.height(Space.s))
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(Space.s),
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

        if (day == today && !repo.readOnly && quick.isEmpty()) item {
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
                        .clip(CircleShape)
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
                        .clip(CircleShape)
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
                Icon(icon, contentDescription = null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(IconSize.row))
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
    val largeText = LocalDensity.current.fontScale >= 1.6f
    Surface(
        color = scheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(Space.s),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.s),
        ) {
            ShapeNavButton(
                label = "‹", enabled = true, onClick = { onChange(addDays(day, -1)) },
                description = "Previous day",
            )
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Box(Modifier.size(7.dp).clip(CircleShape).background(scheme.primary))
                    Text(
                        if (isToday) "DAY IN PLAY" else weekdayShort(day).uppercase(),
                        color = scheme.primary,
                        letterSpacing = 1.3.sp,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    )
                }
                Text(
                    prettyDate(day),
                    color = scheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = (if (largeText) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge)
                        .copy(fontWeight = FontWeight.Bold),
                )
                Text(
                    if (isToday) "a day in the life" else "tap ‹ › to move",
                    color = scheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            ShapeNavButton(
                label = "›", enabled = day < today, onClick = { onChange(addDays(day, 1)) },
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
    editable: Boolean = true,
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
            if (editable && value != SwipeToDismissBoxValue.Settled && !acted) {
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
        enableDismissFromStartToEnd = editable,
        enableDismissFromEndToStart = editable && onDelete != null,
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
        if (editable) {
            add(CustomAccessibilityAction("Edit") { onEdit(); true })
            if (onDelete != null) add(CustomAccessibilityAction("Delete") { onDelete(); true })
        }
    }
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().then(sharedMod)
                .then(
                    Modifier.clip(MaterialTheme.shapes.large).background(scheme.surfaceContainerLow)
                )
                .then(if (editable) Modifier.pointerInput(title) { detectTapGestures(onLongPress = { onEdit() }) } else Modifier)
                .semantics { customActions = a11yActions }
                .padding(vertical = Space.m, horizontal = Space.l),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
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
private fun HeroAmount(
    cents: Long,
    reducedMotion: Boolean,
    fontFamily: FontFamily,
    background: Color,
    ink: Color,
    decimalInk: Color,
    largeText: Boolean,
    description: String,
    modifier: Modifier = Modifier,
) {
    val motion = MaterialTheme.motionScheme
    val shown = remember { Animatable(0f) }
    LaunchedEffect(cents, reducedMotion) {
        if (reducedMotion) shown.snapTo(cents.toFloat())
        else shown.animateTo(cents.toFloat(), motion.defaultSpatialSpec())
    }
    val raw = rm(shown.value.roundToLong())
    val negative = raw.startsWith("-")
    val unsigned = raw.removePrefix("-")
    val dot = unsigned.lastIndexOf('.')
    val whole = if (dot >= 0) unsigned.substring(0, dot) else unsigned
    val decimal = if (dot >= 0) unsigned.substring(dot) else ""
    val style = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = BakiHeroFontSize,
        fontFeatureSettings = "tnum",
        letterSpacing = (-4).sp,
    )
    val decimalStyle = style.copy(
        fontSize = (BakiHeroFontSize.value * 0.33f).sp,
        letterSpacing = (-1).sp,
    )
    val cardHeight = if (largeText) 175.dp else 206.dp
    val measurer = rememberTextMeasurer()
    Box(
        modifier
            .fillMaxWidth()
            .height(cardHeight)
            .clip(BakiHeroShape)
            .drawBehind { drawRect(background) }
            .semantics { contentDescription = "$description, RM ${rm(cents)}" },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val edge = 10.dp.toPx()
            val available = (size.width - edge * 2f).coerceAtLeast(1f)
            val gap = 3.dp.toPx()
            val int = measurer.measure(
                whole, style, maxLines = 1, softWrap = false,
            )
            val dec = decimal.takeIf { it.isNotEmpty() }?.let {
                measurer.measure(it, decimalStyle, maxLines = 1, softWrap = false)
            }
            val signWidth = if (negative) (size.width * 0.12f).coerceIn(16.dp.toPx(), 34.dp.toPx()) else 0f
            val numberWidth = int.size.width + (dec?.size?.width ?: 0) + if (dec != null) gap else 0f
            val numberAvailable = (available - signWidth - if (negative) gap else 0f).coerceAtLeast(1f)
            val scaleX = (numberAvailable / numberWidth).coerceIn(0.20f, 1.18f)
            val targetHeight = size.height * 1.16f
            val scaleY = (targetHeight / int.size.height.toFloat()).coerceIn(1f, 2.6f)
            val left = edge + signWidth + (if (negative) gap else 0f) +
                (numberAvailable - numberWidth * scaleX) / 2f
            val top = (size.height - int.size.height * scaleY) / 2f
            val intBaseline = int.getLineBaseline(0)
            val decTop = dec?.let { intBaseline - it.getLineBaseline(0) - int.size.height * 0.08f } ?: 0f
            if (negative) {
                drawRoundRect(
                    color = ink,
                    topLeft = androidx.compose.ui.geometry.Offset(edge, size.height * 0.49f),
                    size = androidx.compose.ui.geometry.Size(signWidth, 4.dp.toPx()),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()),
                )
            }
            withTransform({
                translate(left, top)
                scale(scaleX, scaleY, pivot = androidx.compose.ui.geometry.Offset.Zero)
            }) {
                drawText(int, color = ink, topLeft = androidx.compose.ui.geometry.Offset(0f, 0f))
                dec?.let { drawText(it, color = decimalInk, topLeft = androidx.compose.ui.geometry.Offset(int.size.width + gap, decTop)) }
            }
        }
    }
}

@Composable
private fun StartShiftCard(hasShift: Boolean, onStart: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = MaterialTheme.shapes.large,
        color = scheme.primaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(Space.m), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "LIVE SHIFT",
                    color = scheme.onPrimaryContainer.copy(alpha = 0.7f),
                    letterSpacing = 1.2.sp,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                )
                Text(
                    if (hasShift) "Start another live shift" else "Ready when you are",
                    color = scheme.onPrimaryContainer,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                )
            }
            FilledTonalButton(
                onClick = onStart,
                modifier = Modifier.height(48.dp),
                colors = androidx.compose.material3.ButtonDefaults.filledTonalButtonColors(
                    containerColor = scheme.primary,
                    contentColor = scheme.onPrimary,
                ),
            ) {
                Text("Start", style = MaterialTheme.typography.labelLargeEmphasized)
            }
        }
    }
}

@Composable
private fun FoodCard(food: DailyFood?, paidUntil: Long?, largeText: Boolean, animate: Boolean = true, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val cover = paidUntil?.takeIf { it > nowMillis() }
    val status = food?.let { if (it.over) "RM ${rm(-it.remaining)} over" else "RM ${rm(it.remaining)} left" } ?: "no cap set"
    Surface(modifier, shape = MaterialTheme.shapes.extraLarge, color = scheme.surfaceContainerLow) {
        if (largeText) {
            Row(Modifier.fillMaxWidth().padding(Space.m), verticalAlignment = Alignment.CenterVertically) {
                FoodRing(food, Modifier.size(88.dp), showCenter = false, animate = animate)
                Spacer(Modifier.width(Space.m))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("FOOD FUND", color = scheme.onSurfaceVariant, letterSpacing = 1.2.sp, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                        Spacer(Modifier.weight(1f))
                        CoverMark(cover != null, paidUntil)
                    }
                    Text(status, color = if (food?.over == true) scheme.error else scheme.onSurface, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                }
            }
        } else {
            Column(Modifier.fillMaxSize().padding(horizontal = Space.m, vertical = Space.s), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("FOOD", color = scheme.onSurfaceVariant, letterSpacing = 1.1.sp, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                    Spacer(Modifier.weight(1f))
                    CoverMark(cover != null, paidUntil)
                }
                Spacer(Modifier.height(2.dp))
                FoodRing(food, Modifier.size(70.dp), animate = animate)
            }
        }
    }
}

@Composable
private fun CoverMark(active: Boolean, paidUntil: Long?) {
    val scheme = MaterialTheme.colorScheme
    Text(
        if (active) "✓" else "×",
        color = if (active) scheme.tertiary else scheme.onSurfaceVariant,
        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
        modifier = Modifier.semantics {
            contentDescription = if (active) {
                paidUntil?.let { "Cover active until ${clockOf(it)}" } ?: "Cover active"
            } else "Cover not active"
        },
    )
}

@Composable
private fun FoodRing(food: DailyFood?, modifier: Modifier = Modifier, showCenter: Boolean = true, animate: Boolean = true) {
    val scheme = MaterialTheme.colorScheme
    val targetFraction = if (food != null && food.cap > 0) (food.spent.toFloat() / food.cap).coerceIn(0f, 1f) else 0f
    val fraction = if (animate) {
        animateFloatAsState(
            targetValue = targetFraction,
            animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
            label = "foodProgress",
        ).value
    } else targetFraction
    val over = food?.over == true
    val color = if (over) scheme.error else scheme.tertiary
    val description = food?.let {
        "Food fund, RM ${rm(it.spent)} spent of RM ${rm(it.cap)}, " +
            if (it.over) "RM ${rm(-it.remaining)} over" else "RM ${rm(it.remaining)} left"
    } ?: "Food fund not set"
    Box(modifier.semantics { contentDescription = description }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 3.4.dp.toPx()
            val amplitude = 0.9.dp.toPx()
            val baseR = size.minDimension / 2f - stroke - amplitude
            // Crisp tonal track, wavy gold progress. The wave is on the data, not the container.
            drawArc(
                color = scheme.surfaceContainerHigh,
                startAngle = -90f, sweepAngle = 360f, useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(stroke / 2f, stroke / 2f),
                size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke),
                style = androidx.compose.ui.graphics.drawscope.Stroke(stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round),
            )
            if (fraction > 0f) {
                fun point(angle: Double): androidx.compose.ui.geometry.Offset {
                    val r = baseR + amplitude * kotlin.math.sin(angle * 12.0).toFloat()
                    return androidx.compose.ui.geometry.Offset(
                        center.x + (r * kotlin.math.cos(angle)).toFloat(),
                        center.y + (r * kotlin.math.sin(angle)).toFloat(),
                    )
                }
                val steps = (96 * fraction).toInt().coerceAtLeast(2)
                val progressPath = Path().apply {
                    val p0 = point(Math.toRadians(-90.0))
                    moveTo(p0.x, p0.y)
                    for (i in 1..steps) {
                        val p = point(Math.toRadians(-90.0 + 360.0 * fraction * i / steps))
                        lineTo(p.x, p.y)
                    }
                }
                drawPath(
                    progressPath, color,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round),
                )
                val end = point(Math.toRadians(-90.0 + 360.0 * fraction))
                drawCircle(color, radius = 3.2.dp.toPx(), center = end)
            }
        }
        if (showCenter) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clearAndSetSemantics {}) {
                Text(
                    food?.let { "RM ${rm(kotlin.math.abs(it.remaining))}" } ?: "—",
                    color = if (over) scheme.error else scheme.onSurface,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                )
                Text(
                    if (over) "over" else "left",
                    color = scheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

@Composable
private fun DayCard(
    store: Store,
    today: String,
    selectedDay: String,
    onDay: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val previous = addDays(today, -1)
    Surface(modifier, shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(
            Modifier.fillMaxSize().padding(Space.s),
            horizontalArrangement = Arrangement.spacedBy(Space.s),
        ) {
            MiniDay(store, previous, selectedDay == previous, { onDay(previous) }, Modifier.weight(1f).fillMaxHeight())
            MiniDay(store, today, selectedDay == today, { onDay(today) }, Modifier.weight(1f).fillMaxHeight())
        }
    }
}

@Composable
private fun MiniDay(store: Store, iso: String, selected: Boolean, onDay: (String) -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val t = dayTotals(store, iso)
    val shape = MaterialTheme.shapes.medium
    val empty = t.entryCount == 0 && t.shiftCount == 0
    val description = buildString {
        append(prettyDate(iso))
        if (empty) append(", no entries") else append(", net RM ${rm(t.net)}")
        if (selected) append(", selected")
    }
    Column(
        modifier
            .clip(shape)
            .background(if (selected) scheme.primaryContainer else Color.Transparent)
            .clickable { onDay(iso) }
            .semantics {
                role = Role.Button
                contentDescription = description
            }
            .padding(horizontal = 4.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            iso.takeLast(2),
            color = if (selected) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
        )
        Spacer(Modifier.height(2.dp))
        Text(
            if (empty) "–" else (t.net / 100).toInt().toString(),
            color = if (selected) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum", fontWeight = FontWeight.Bold),
            maxLines = 1,
        )
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ShiftBand(band: String, scheme: androidx.compose.material3.ColorScheme) {
    Box(
        Modifier.clip(MaterialShapes.Cookie9Sided.toShape())
            .background(bandColor(band, scheme).copy(alpha = 0.18f))
            .padding(horizontal = Space.m, vertical = 2.dp),
    ) {
        Text(
            BANDS[band] ?: "—",
            color = bandColor(band, scheme),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
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
                            .clip(CircleShape)
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
                        .clip(CircleShape)
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
