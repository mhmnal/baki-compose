package app.baki

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke

import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun App(repo: BakiRepo = remember { BakiRepo() }) {
    BakiTheme(matchWallpaper = repo.config.matchWallpaper, mode = repo.config.themeMode) {
        SharedTransitionLayout {
            androidx.compose.runtime.CompositionLocalProvider(LocalSharedTransition provides this) {
                AppContent(repo)
            }
        }
    }
}

@Composable
private fun AppContent(repo: BakiRepo) {
    // read the palette *inside* the theme provider, or every colour falls back to dark
    val baki = LocalBaki.current
    var tab by remember { mutableStateOf(0) }
    var adding by remember { mutableStateOf(0) }   // 0 none, 1 chooser, 2 entry, 3 shift, 4 payout
    var day by remember { mutableStateOf(todayIso()) }
    var month by remember { mutableStateOf(todayIso().take(7)) }
    var editingEntry by remember { mutableStateOf<Entry?>(null) }
    var editingShift by remember { mutableStateOf<Shift?>(null) }
    var entryType by remember { mutableStateOf("expense") }
    var presetCat by remember { mutableStateOf<String?>(null) }
    var editingBudgets by remember { mutableStateOf(false) }
    val glassTier = effectiveGlassTier(repo.config.glassMode, DeviceTier.level)
    val hazeState = rememberHazeState()
    val hazeMode = when (glassTier) {
        2 -> HazePerformanceMode.Quality
        1 -> HazePerformanceMode.Balanced
        else -> HazePerformanceMode.Performance
    }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { repo.start() }

    // notification actions: "+ Payout" and "End shift" come back as intents
    val pendingAction = BakiIntents.pending
    LaunchedEffect(pendingAction) {
        when (pendingAction) {
            "payout" -> if (repo.live != null) adding = 4
            "end" -> if (repo.live != null) { PlatformEffects.tapFeedback(); repo.endLive() }
        }
        if (pendingAction != null) BakiIntents.pending = null
    }

    PlatformBackHandler(enabled = adding != 0 || tab != 0) {
        if (adding != 0) { adding = 0; editingEntry = null; editingShift = null; presetCat = null }
        else if (tab != 0) tab = 0
    }


    BoxWithConstraints(Modifier.fillMaxSize()) {
        val barHeight = 74.dp
        val fabSize = 56.dp
        // sit above the gesture bar, not on top of it
        val gestureInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val barGap = gestureInset + 14.dp
        val barTop = maxHeight - barHeight - barGap
        val fabTop = barTop - fabSize - 6.dp
        val sheetOpen = adding != 0

        // ---- level 1: the app itself, blurred while a sheet is up ----
        Box(
            Modifier.fillMaxSize().then(
                if (sheetOpen) Modifier.blur(18.dp, BlurredEdgeTreatment.Unbounded) else Modifier,
            ),
        ) {

    Box(
        Modifier.fillMaxSize().then(if (glassTier > 0) Modifier.hazeSource(hazeState) else Modifier),
    ) {
    Scaffold(
        containerColor = baki.surface,
        snackbarHost = { SnackbarHost(snackbar, Modifier.padding(bottom = 108.dp)) },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            Header(repo.status)
            // expressive motion: the emphasised curve, content rises slightly as it fades in
            val expressive = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)
            AnimatedContent(
                targetState = tab,
                transitionSpec = {
                    (fadeIn(tween(260, easing = expressive)) +
                        slideInVertically(tween(260, easing = expressive)) { it / 60 })
                        .togetherWith(fadeOut(tween(140)))
                },
                modifier = Modifier.fillMaxSize(),
                label = "tab",
            ) { current ->
            androidx.compose.runtime.CompositionLocalProvider(LocalAnimatedVisibility provides this) {
            when (current) {
                0 -> TodayScreen(
                    repo = repo,
                    day = day,
                    onDay = { day = it },
                    onPayout = { adding = 4 },
                    onEditEntry = { editingEntry = it; entryType = it.type; adding = 2 },
                    onDeleteEntry = { entry ->
                        scope.launch {
                            repo.deleteEntry(entry.id)
                            val res = snackbar.showSnackbar(
                                "Deleted ${entry.note.ifEmpty { entry.cat }} · RM ${rm(entry.amount)}",
                                actionLabel = "Undo", duration = SnackbarDuration.Short,
                            )
                            if (res == SnackbarResult.ActionPerformed) {
                                repo.addEntry(entry.type, entry.amount, entry.cat, entry.note, entry.date)
                            }
                        }
                    },
                    onPreset = { catId ->
                        editingEntry = null
                        entryType = "expense"
                        presetCat = catId
                        adding = 2
                    },
                    onAddShift = { editingShift = null; adding = 3 },
                    onSeeAll = { tab = 1 },
                )
                1 -> SpendingScreen(
                    repo = repo,
                    month = month,
                    onMonth = { month = it },
                    onEditEntry = { editingEntry = it; entryType = it.type; adding = 2 },
                    onDeleteEntry = { entry ->
                        scope.launch {
                            repo.deleteEntry(entry.id)
                            val res = snackbar.showSnackbar(
                                "Deleted ${entry.note.ifEmpty { entry.cat }} · RM ${rm(entry.amount)}",
                                actionLabel = "Undo", duration = SnackbarDuration.Short,
                            )
                            if (res == SnackbarResult.ActionPerformed) {
                                repo.addEntry(entry.type, entry.amount, entry.cat, entry.note, entry.date)
                            }
                        }
                    },
                )
                2 -> ShiftsScreen(
                    repo = repo,
                    onEditShift = { editingShift = it; adding = 3 },
                    onDeleteShift = { shift ->
                        scope.launch {
                            repo.deleteShift(shift.id)
                            val res = snackbar.showSnackbar(
                                "Deleted shift ${shift.date} ${shift.start}",
                                actionLabel = "Undo", duration = SnackbarDuration.Short,
                            )
                            if (res == SnackbarResult.ActionPerformed) {
                                repo.addShift(
                                    shift.date, shift.start, shift.hours, shift.km, shift.gross,
                                    shift.extra, shift.note, shift.odoStart, shift.odoEnd,
                                )
                            }
                        }
                    },
                )
                3 -> SummaryScreen(store = repo.store, month = month, onMonth = { month = it })
                else -> MoreScreen(repo, onEditBudgets = { editingBudgets = true; adding = 5 })
            }
            }
            }
        }
    }
    }

    // ---- glass: the bar and the + float over a blurred copy of the content ----
    // hidden while a sheet is open: the sheet is modal, and Haze's layer would otherwise
    // composite above it
    if (!sheetOpen) {
    GlassSurface(
        hazeState = hazeState,
        shape = RoundedCornerShape(50),
        tier = glassTier,
        performanceMode = hazeMode,
        modifier = Modifier.align(Alignment.BottomCenter)
            .fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = barGap).height(barHeight),
    ) {
        BottomBar(tab) { tab = it }
    }

    if (tab == 0 && !repo.readOnly) {
        GlassSurface(
            hazeState = hazeState,
            shape = RoundedCornerShape(50),
            tier = glassTier,
            performanceMode = hazeMode,
            modifier = Modifier.align(Alignment.BottomEnd)
                .padding(end = 18.dp).padding(bottom = barHeight + barGap + 6.dp).size(fabSize)
                .shadow(10.dp, RoundedCornerShape(50)),
        ) {
            Box(
                Modifier.fillMaxSize().clickable { adding = 1 },
                contentAlignment = Alignment.Center,
            ) { Text("+", color = baki.primary, fontSize = 30.sp, fontWeight = FontWeight.Bold) }
        }
    }
    }
    }

    // ---- level 2: the sheet sits above everything, on a scrim over the blurred app ----
    when (adding) {
        1 -> AddChoiceDialog(
            onDismiss = { adding = 0 },
            onShift = { editingShift = null; adding = 3 },
            onExpense = { editingEntry = null; entryType = "expense"; adding = 2 },
            onIncome = { editingEntry = null; entryType = "income"; adding = 2 },
        )
        2 -> EntryDialog(
            existing = editingEntry,
            initialType = entryType,
            initialCat = presetCat,
            settings = repo.store.settings,
            day = day,
            onDismiss = { adding = 0; editingEntry = null; presetCat = null },
            onSave = { type, cents, cat, note, date, odoAtFill ->
                presetCat = null
                val target = editingEntry
                adding = 0; editingEntry = null
                scope.launch {
                    if (target == null) {
                        repo.addEntry(type, cents, cat, note, date, odoAtFill)
                    } else {
                        repo.updateEntry(target, type, cents, cat, note, date, odoAtFill)
                        val res = snackbar.showSnackbar(
                            "Saved · was ${target.note.ifEmpty { target.cat }} RM ${rm(target.amount)}",
                            actionLabel = "Undo", duration = SnackbarDuration.Long,
                        )
                        if (res == SnackbarResult.ActionPerformed) {
                            repo.updateEntry(target, target.type, target.amount, target.cat, target.note, target.date, target.odo)
                        }
                    }
                }
            },
            onDelete = { entry ->
                adding = 0; editingEntry = null
                scope.launch {
                    repo.deleteEntry(entry.id)
                    val res = snackbar.showSnackbar(
                        message = "Deleted ${entry.note.ifEmpty { entry.cat }} · RM ${rm(entry.amount)}",
                        actionLabel = "Undo",
                        duration = SnackbarDuration.Short,
                    )
                    if (res == SnackbarResult.ActionPerformed) {
                        repo.addEntry(entry.type, entry.amount, entry.cat, entry.note, entry.date)
                    }
                }
            },
        )
        3 -> ShiftDialog(
            store = repo.store,
            existing = editingShift,
            onDismiss = { adding = 0; editingShift = null },
            onSave = { date, start, hours, km, gross, extra, kmPerLitre, odoS, odoE ->
                val target = editingShift
                adding = 0; editingShift = null
                scope.launch {
                    if (target == null) {
                        repo.addShift(date, start, hours, km, gross, extra, odoStart = odoS, odoEnd = odoE)
                    } else {
                        repo.updateShift(target, date, start, hours, km, gross, extra, target.note, kmPerLitre, odoS, odoE)
                        val res = snackbar.showSnackbar(
                            "Shift saved · was RM ${rm(target.gross)}",
                            actionLabel = "Undo", duration = SnackbarDuration.Long,
                        )
                        if (res == SnackbarResult.ActionPerformed) {
                            repo.updateShift(
                                target, target.date, target.start, target.hours, target.km,
                                target.gross, target.extra, target.note, target.kmPerLitre,
                                target.odoStart, target.odoEnd,
                            )
                        }
                    }
                }
            },
            onCalibrate = { factor -> scope.launch { repo.setGpsCorrection(factor) } },
            onDelete = { shift ->
                adding = 0; editingShift = null
                scope.launch {
                    repo.deleteShift(shift.id)
                    val res = snackbar.showSnackbar(
                        message = "Deleted shift ${shift.date} ${shift.start}",
                        actionLabel = "Undo",
                        duration = SnackbarDuration.Short,
                    )
                    if (res == SnackbarResult.ActionPerformed) {
                        repo.addShift(
                            shift.date, shift.start, shift.hours, shift.km, shift.gross, shift.extra,
                            shift.note, shift.odoStart, shift.odoEnd,
                        )
                    }
                }
            },
        )
        5 -> BudgetSheet(repo, onDismiss = { adding = 0; editingBudgets = false })
        4 -> AddPayoutDialog(
            onDismiss = { adding = 0 },
            onAdd = { cents -> repo.addPayout(cents); adding = 0 },
        )
    }

    }


}

@Composable
private fun Header(status: String) {
    val baki = LocalBaki.current
    Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(baki.primary), contentAlignment = Alignment.Center) {
            Text("✓", color = baki.onPrimary, fontSize = 18.sp, fontWeight = FontWeight.Black)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("Baki", color = baki.onSurface, fontSize = 22.sp, fontWeight = FontWeight.Black)
            Text("what your shift really pays", color = baki.muted, fontSize = 11.sp)
        }
        val dot = when (status) {
            "synced" -> baki.up
            "offline" -> baki.down
            "cached" -> baki.warn
            "syncing" -> baki.primary
            else -> baki.muted
        }
        Box(Modifier.size(10.dp).clip(RoundedCornerShape(50)).background(dot))
    }
}

@Composable
private fun BottomBar(selected: Int, onSelect: (Int) -> Unit) {
    val baki = LocalBaki.current
    val items = listOf("Today", "Spending", "Shifts", "Summary", "More")
    Row(
        Modifier.fillMaxSize().padding(horizontal = 6.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEachIndexed { i, label ->
            val on = i == selected
            Box(
                Modifier.weight(1f).height(46.dp).clip(RoundedCornerShape(50))
                    .background(if (on) baki.primaryContainer else Color.Transparent)
                    .clickable { onSelect(i) },
                contentAlignment = Alignment.Center,
            ) {
                Text(label, color = if (on) baki.onPrimaryContainer else baki.muted, fontSize = 13.sp,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun Hero(label: String, amountCents: Long, tint: Color = Color.Unspecified, size: Int = 46) {
    val baki = LocalBaki.current
    val ink = if (tint == Color.Unspecified) baki.onSurface else tint
    Column {
        Text(label, color = baki.muted, fontSize = 12.sp)
        Text("RM ${rm(amountCents)}", color = ink, fontSize = size.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
fun Chip(text: String, tint: Color) {
    val baki = LocalBaki.current
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(baki.container).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(7.dp).clip(RoundedCornerShape(50)).background(tint))
        Spacer(Modifier.width(8.dp))
        Text(text, color = baki.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun RowCard(
    title: String,
    sub: String,
    amount: String,
    amountTint: Color = Color.Unspecified,
    onLongPress: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
) {
    val baki = LocalBaki.current
    val ink = if (amountTint == Color.Unspecified) baki.onSurface else amountTint
    val swipe = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> onLongPress?.invoke()
                SwipeToDismissBoxValue.EndToStart -> onDelete?.invoke()
                else -> {}
            }
            false
        },
    )
    SwipeToDismissBox(
        state = swipe,
        enableDismissFromStartToEnd = onLongPress != null,
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
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(baki.container)
            .then(
                if (onLongPress != null) Modifier.pointerInput(title) {
                    detectTapGestures(onLongPress = { onLongPress() })
                } else Modifier
            )
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = baki.onSurface, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            if (sub.isNotEmpty()) Text(sub, color = baki.muted, fontSize = 11.sp)
        }
        Text(amount, color = ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
    }
}

@Composable
private fun SectionTitle(text: String, trailing: String = "") {
    val baki = LocalBaki.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text, color = baki.muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        if (trailing.isNotEmpty()) Text(trailing, color = baki.muted, fontSize = 11.sp)
    }
}

@Composable
fun bakiFieldColorsPublic(): TextFieldColors = bakiFieldColors()

@Composable
private fun bakiFieldColors(): TextFieldColors {
    val baki = LocalBaki.current
    return OutlinedTextFieldDefaults.colors(
        focusedTextColor = baki.onSurface, unfocusedTextColor = baki.onSurface,
        focusedBorderColor = baki.primary, unfocusedBorderColor = baki.muted.copy(alpha = 0.45f),
        focusedLabelColor = baki.primary, unfocusedLabelColor = baki.muted,
        cursorColor = baki.primary,
        focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
    )
}

@Composable
fun LiveCard(repo: BakiRepo, live: LiveShift, onPayout: () -> Unit) {
    val baki = LocalBaki.current
    var now by remember { mutableStateOf(nowMillis()) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(live.start) {
        while (true) { now = nowMillis(); delay(1000) }
    }

    val worked = liveWorkedMs(live, now)
    val onBreak = isPaused(live)
    val breakMs = pausedMs(live, now)
    val total = liveTotalCents(live)
    val hours = worked / 3_600_000.0

    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(baki.surfaceHigh).padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(9.dp).clip(RoundedCornerShape(50)).background(if (onBreak) baki.warn else baki.primary))
            Spacer(Modifier.width(8.dp))
            Text(
                if (onBreak) "ON BREAK" else "LIVE SHIFT",
                color = if (onBreak) baki.warn else baki.primary,
                fontSize = 11.sp, fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.weight(1f))
            Text("since ${startClock(live.start)}", color = baki.muted, fontSize = 11.sp)
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                liveClock(worked),
                color = if (onBreak) baki.muted else baki.onSurface,
                fontSize = 30.sp, fontWeight = FontWeight.Black,
            )
            Spacer(Modifier.weight(1f))
            Text("RM ${rm(total)}", color = baki.onSurface, fontSize = 26.sp, fontWeight = FontWeight.Black)
        }
        // break-even: what the shift has cost so far, and how far the payouts are from covering it
        val be = liveBreakEven(repo.store, live, now)
        Text(
            when {
                be.covered -> "✓ costs covered (RM ${"%.2f".format(be.costs)})"
                be.minutesToGo != null -> "costs RM ${"%.2f".format(be.costs)} · break-even in ${be.minutesToGo.toInt()} min"
                else -> "costs so far RM ${"%.2f".format(be.costs)} · add a payout to start earning"
            },
            color = if (be.covered) baki.up else baki.muted,
            fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
        )
        Text(
            buildString {
                if (hours > 0.05) append("RM ${rm(total / 100.0 / hours)}/hr · ")
                append("${live.payouts.size} payout(s)")
                if (live.km > 0.0) append(" · ${"%.1f".format(live.km)} km tracked")
                if (breakMs > 0) append(" · ${liveClock(breakMs)} break")
            },
            color = baki.muted, fontSize = 12.sp,
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onPayout,
                colors = ButtonDefaults.buttonColors(containerColor = baki.primaryContainer, contentColor = baki.onPrimaryContainer),
                shape = RoundedCornerShape(50), modifier = Modifier.weight(1.1f),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 10.dp),
            ) { Text("+ Payout", fontSize = 12.sp, maxLines = 1, softWrap = false) }
            Button(
                enabled = !repo.readOnly,
                onClick = { if (onBreak) repo.resumeLive() else repo.pauseLive() },
                // tinted rather than filled: readable on every palette, including dynamic colour
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (onBreak) baki.warn.copy(alpha = 0.22f) else baki.surfaceHigh,
                    contentColor = if (onBreak) baki.warn else baki.onSurface,
                ),
                shape = RoundedCornerShape(50), modifier = Modifier.weight(1.1f),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 10.dp),
            ) {
                Text(
                    if (onBreak) "Resume" else "Pause",
                    fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    maxLines = 1, softWrap = false,
                )
            }
            Button(
                enabled = !repo.readOnly,
                onClick = { scope.launch { repo.endLive() } },
                colors = ButtonDefaults.buttonColors(containerColor = baki.primary, contentColor = baki.onPrimary),
                shape = RoundedCornerShape(50), modifier = Modifier.weight(0.9f),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 10.dp),
            ) { Text("End", fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1, softWrap = false) }
        }
    }
}

@Composable
private fun DayHeader(day: String, today: String, onChange: (String) -> Unit) {
    val baki = LocalBaki.current
    val atToday = day >= today
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(50)).background(baki.container).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = { onChange(addDays(day, -1)) }) {
            Text("‹", color = baki.onSurface, fontSize = 20.sp)
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (day == today) "Today" else day, color = baki.onSurface, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(if (day == today) day else "tap ‹ › to move", color = baki.muted, fontSize = 10.sp)
        }
        TextButton(enabled = !atToday, onClick = { onChange(addDays(day, 1)) }) {
            Text("›", color = if (atToday) baki.muted.copy(alpha = 0.35f) else baki.onSurface, fontSize = 20.sp)
        }
    }
}

@Composable
private fun MonthHeader(month: String, onChange: (String) -> Unit) {
    val baki = LocalBaki.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = { onChange(addDays("$month-01", -1).take(7)) }) {
            Text("‹", color = baki.onSurface, fontSize = 20.sp)
        }
        Text(monthLabel(month), color = baki.onSurface, fontSize = 15.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
        TextButton(onClick = { onChange(addDays(endOfMonth(month), 1).take(7)) }) {
            Text("›", color = baki.onSurface, fontSize = 20.sp)
        }
    }
}

fun monthLabel(month: String): String {
    val d = kotlinx.datetime.LocalDate.parse("$month-01")
    val name = d.month.name.lowercase().replaceFirstChar { it.uppercase() }
    return "$name ${d.year}"
}

fun endOfMonth(month: String): String = addDays(addDays("$month-01", 32).take(7) + "-01", -1)


@Composable
private fun SpendingScreen(
    repo: BakiRepo,
    month: String,
    onMonth: (String) -> Unit,
    onEditEntry: (Entry) -> Unit,
    onDeleteEntry: (Entry) -> Unit,
) {
    val baki = LocalBaki.current
    var query by remember { mutableStateOf("") }
    var catFilter by remember { mutableStateOf("all") }
    val t = monthTotals(repo.store, month)
    val all = repo.store.entries.filter { it.date.startsWith(month) }
    val entries = all
        .filter { catFilter == "all" || it.cat == catFilter }
        .filter { e ->
            query.isBlank() || (e.cat + " " + e.note + " " + (e.amount / 100.0)).lowercase().contains(query.lowercase())
        }
        .sortedByDescending { it.date }
    val cats = repo.store.settings.cats.catsOut

    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        Spacer(Modifier.height(10.dp))
        MonthHeader(month, onMonth)
        Spacer(Modifier.height(6.dp))
        Hero("spent this month", t.expense, baki.down, 40)
        Text("${t.entryCount} purchases · ${t.shiftCount} shifts · in ${rm(t.income)}", color = baki.muted, fontSize = 12.sp)
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = query, onValueChange = { query = it },
            placeholder = { Text("Search note, category, amount", color = baki.muted, fontSize = 12.sp) },
            singleLine = true, modifier = Modifier.fillMaxWidth(), colors = bakiFieldColors(),
        )
        Spacer(Modifier.height(8.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            val chips = listOf("all" to "All") + cats.map { it.id to it.name }
            chips.forEach { (id, label) ->
                val on = catFilter == id
                Box(
                    Modifier.clip(RoundedCornerShape(50))
                        .background(if (on) baki.primaryContainer else baki.container)
                        .clickable { catFilter = id }
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                ) { Text(label, color = if (on) baki.onPrimaryContainer else baki.muted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold) }
            }
        }
        Spacer(Modifier.height(12.dp))
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 132.dp),
        ) {
            // envelopes first: the categories with a cap are what you actually check
            val envelopes = budgetUsage(repo.store, month)
            if (envelopes.isNotEmpty()) {
                item {
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(baki.container).padding(14.dp)) {
                        Text("BUDGETS", color = baki.muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        Spacer(Modifier.height(8.dp))
                        envelopes.forEach { b ->
                            val name = repo.store.settings.cats.catsOut.firstOrNull { it.id == b.cat }?.name ?: b.cat
                            val ink = when {
                                b.over -> baki.down
                                b.fraction >= 0.8 -> baki.warn
                                else -> baki.up
                            }
                            Column(Modifier.padding(vertical = 5.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(name, color = baki.onSurface, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                    Text(
                                        "RM ${"%.0f".format(b.spent)} / ${"%.0f".format(b.limit)}" +
                                            if (b.over) " · RM ${"%.0f".format(-b.remaining)} over"
                                            else " · RM ${"%.0f".format(b.remaining)} left",
                                        color = ink, fontSize = 11.sp,
                                    )
                                }
                                Spacer(Modifier.height(3.dp))
                                Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)).background(baki.surfaceHigh)) {
                                    Box(
                                        Modifier.fillMaxWidth(b.fraction.toFloat().coerceIn(0.02f, 1f))
                                            .height(6.dp).clip(RoundedCornerShape(50)).background(ink),
                                    )
                                }
                            }
                        }
                    }
                }
            }
            items(entries) { e ->
                RowCard(
                    e.note.ifEmpty { e.cat },
                    e.date + " · swipe or hold to edit",
                    (if (e.type == "income") "+" else "−") + rm(e.amount),
                    if (e.type == "income") baki.up else baki.down,
                    onLongPress = { onEditEntry(e) },
                    onDelete = { onDeleteEntry(e) },
                )
            }
            if (entries.isEmpty()) item {
                Text(
                    if (query.isNotBlank() || catFilter != "all") "Nothing matches that filter."
                    else "Nothing recorded this month.",
                    color = baki.muted, fontSize = 13.sp,
                )
            }
        }
    }
}

@Composable
private fun ShiftsScreen(repo: BakiRepo, onEditShift: (Shift) -> Unit, onDeleteShift: (Shift) -> Unit) {
    val baki = LocalBaki.current
    val store = repo.store
    val cfg = store.settings.profit
    val rows = store.shifts.sortedByDescending { it.date }
    val hours = rows.sumOf { it.hours }
    val net = rows.sumOf { shiftMetrics(it, cfg, overheadPerHour(store, it.date.take(7))).net }

    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        Spacer(Modifier.height(18.dp))
        Hero("net after car cost", cents(net), baki.onSurface, 40)
        Text(
            "${rows.size} shifts · ${"%.1f".format(hours)}h · RM ${"%.2f".format(if (hours > 0) net / hours else 0.0)}/hr",
            color = baki.muted, fontSize = 12.sp,
        )
        Spacer(Modifier.height(16.dp))
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 132.dp),
        ) {
            items(rows) { sh ->
                val m = shiftMetrics(sh, cfg, overheadPerHour(store, sh.date.take(7)))
                RowCard(
                    "${sh.date} ${sh.start}".trim(),
                    "${"%.1f".format(m.km)} km · ${m.band} · swipe or hold to edit",
                    "RM ${"%.2f".format(m.net)}",
                    if (m.net < 0) baki.down else bandColor(m.band, baki),
                    onLongPress = { onEditShift(sh) },
                    onDelete = { onDeleteShift(sh) },
                )
            }
        }
    }
}

@Composable
private fun MoreScreen(repo: BakiRepo, onEditBudgets: () -> Unit) {
    val baki = LocalBaki.current
    var url by remember { mutableStateOf(repo.config.serverUrl) }
    var editingProfit by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val pickImport = rememberJsonImport { text ->
        if (text != null) scope.launch {
            val added = repo.importJson(text)
            if (added > 0) PlatformEffects.successFeedback()
        }
    }

    Column(
        Modifier.fillMaxSize().padding(horizontal = 18.dp).verticalScroll(rememberScrollState()),
    ) {
        Spacer(Modifier.height(18.dp))
        SectionTitle("SYNC")
        Spacer(Modifier.height(8.dp))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(baki.container).padding(14.dp)) {
            Text("Server address", color = baki.onSurface, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text("where the tracker server is running", color = baki.muted, fontSize = 11.sp)
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = url, onValueChange = { url = it }, singleLine = true,
                modifier = Modifier.fillMaxWidth(), colors = bakiFieldColorsPublic(),
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { repo.setServer(url) },
                    colors = ButtonDefaults.buttonColors(containerColor = baki.primary, contentColor = baki.onPrimary),
                ) { Text("Save & sync") }
                OutlinedButton(
                    onClick = { scope.launch { repo.sync() } },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = baki.onSurface),
                    border = BorderStroke(1.dp, baki.muted.copy(alpha = 0.45f)),
                ) { Text("Sync now") }
            }
            repo.lastError?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = baki.down, fontSize = 11.sp)
            }
        }
        Spacer(Modifier.height(16.dp))
        SectionTitle("SAFETY")
        Spacer(Modifier.height(8.dp))
        SwitchRow(
            title = "Allow writes",
            sub = if (repo.readOnly) "off — this app only reads your data" else "on — changes here reach the real ledger",
            checked = !repo.readOnly,
            warn = !repo.readOnly,
            onChange = { repo.setAllowWrites(it) },
        )
        Spacer(Modifier.height(16.dp))
        SectionTitle("LIQUID GLASS")
        Spacer(Modifier.height(8.dp))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(baki.container).padding(14.dp)) {
            Text("Nav bar & +", color = baki.onSurface, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(
                when (effectiveGlassTier(repo.config.glassMode, DeviceTier.level)) {
                    0 -> "Material surface (default) · ${DeviceTier.reason}"
                    else -> "Haze refraction · ${DeviceTier.reason}"
                },
                color = baki.muted, fontSize = 11.sp,
            )
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(50)).background(baki.surfaceHigh).padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                listOf("auto" to "Auto", "on" to "On", "off" to "Off").forEach { (value, label) ->
                    val on = repo.config.glassMode == value
                    Box(
                        Modifier.weight(1f).clip(RoundedCornerShape(50))
                            .background(if (on) baki.primaryContainer else Color.Transparent)
                            .clickable { repo.setGlassMode(value) }
                            .padding(vertical = 9.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(label, color = if (on) baki.onPrimaryContainer else baki.muted, fontSize = 13.sp,
                            fontWeight = if (on) FontWeight.Bold else FontWeight.Medium)
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        SectionTitle("APPEARANCE")
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(50)).background(baki.container).padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            listOf("auto" to "Auto", "light" to "Light", "dark" to "Dark").forEach { (value, label) ->
                val on = repo.config.themeMode == value
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(50))
                        .background(if (on) baki.primaryContainer else Color.Transparent)
                        .clickable { repo.setThemeMode(value) }
                        .padding(vertical = 9.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, color = if (on) baki.onPrimaryContainer else baki.muted, fontSize = 13.sp,
                        fontWeight = if (on) FontWeight.Bold else FontWeight.Medium)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        SwitchRow(
            title = "Match wallpaper",
            sub = "uses your Android theme colours on 12+",
            checked = repo.config.matchWallpaper,
            onChange = { repo.setMatchWallpaper(it) },
        )
        Spacer(Modifier.height(16.dp))
        SectionTitle("VEHICLE & TARGET")
        Spacer(Modifier.height(8.dp))
        val p = repo.store.settings.profit
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(baki.container).padding(14.dp)) {
            Text("RM ${"%.2f".format(p.fuelPrice)}/L · ${"%.1f".format(p.kmPerLitre)} km/L · wear RM ${"%.2f".format(p.wearPerKm)}/km",
                color = baki.onSurface, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text("target RM ${"%.0f".format(p.targetRate)}/hr · avg ${"%.0f".format(p.avgSpeed)} km/h · cover RM ${"%.2f".format(p.grabInsurance)}",
                color = baki.muted, fontSize = 11.sp)
            if (repo.store.settings.gpsCorrection != 1.0) {
                Text("GPS correction ×${"%.2f".format(repo.store.settings.gpsCorrection)} (from your odometer)",
                    color = baki.muted, fontSize = 10.sp)
            }
            // real economy, measured from fill-ups that carry an odometer reading
            measuredEconomy(repo.store)?.let { m ->
                Spacer(Modifier.height(6.dp))
                Text(
                    "measured ${"%.1f".format(m.kml)} km/L over ${"%.0f".format(m.km)} km · ${m.fills} fills (${m.from} → ${m.to})",
                    color = baki.muted, fontSize = 10.sp,
                )
                if (kotlin.math.abs(m.kml - p.kmPerLitre) > 0.3) {
                    TextButton(
                        onClick = { scope.launch { repo.updateProfit(p.copy(kmPerLitre = m.kml)) } },
                        contentPadding = PaddingValues(0.dp),
                    ) {
                        Text(
                            "Use ${"%.1f".format(m.kml)} km/L instead of ${"%.1f".format(p.kmPerLitre)}",
                            color = baki.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = { editingProfit = true },
                enabled = !repo.readOnly,
                colors = ButtonDefaults.buttonColors(containerColor = baki.primaryContainer, contentColor = baki.onPrimaryContainer),
                shape = RoundedCornerShape(50),
            ) { Text("Edit costs") }
        }
        Spacer(Modifier.height(16.dp))
        if (repo.store.statements.isNotEmpty()) {
            SectionTitle("STATEMENT")
            Spacer(Modifier.height(8.dp))
            StatementCard(repo.store)
            Spacer(Modifier.height(16.dp))
        }
        SectionTitle("CATEGORIES")
        Spacer(Modifier.height(8.dp))
        CategoriesCard(repo, editable = !repo.readOnly)
        Spacer(Modifier.height(16.dp))
        SectionTitle("BUDGETS")
        Spacer(Modifier.height(8.dp))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(baki.container).padding(14.dp)) {
            Text("Monthly limits", color = baki.onSurface, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            val n = repo.store.settings.budgets.size
            Text(
                if (n == 0) "none set — add caps for food, fuel, tolls"
                else "$n categories capped · shown on Spending",
                color = baki.muted, fontSize = 11.sp,
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { onEditBudgets() },
                enabled = !repo.readOnly,
                colors = ButtonDefaults.buttonColors(containerColor = baki.primaryContainer, contentColor = baki.onPrimaryContainer),
                shape = RoundedCornerShape(50),
            ) { Text("Set limits") }
        }
        Spacer(Modifier.height(16.dp))
        SectionTitle("BACKUP")
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { PlatformEffects.exportFile("baki-backup-${todayStamp()}.json", "application/json", backupJson(repo.store)) },
                colors = ButtonDefaults.buttonColors(containerColor = baki.primaryContainer, contentColor = baki.onPrimaryContainer),
                shape = RoundedCornerShape(50), modifier = Modifier.weight(1f),
            ) { Text("Backup (JSON)", fontSize = 12.sp) }
            Button(
                onClick = { PlatformEffects.exportFile("baki-${todayStamp()}.csv", "text/csv", exportCsv(repo.store)) },
                colors = ButtonDefaults.buttonColors(containerColor = baki.primaryContainer, contentColor = baki.onPrimaryContainer),
                shape = RoundedCornerShape(50), modifier = Modifier.weight(1f),
            ) { Text("Export (CSV)", fontSize = 12.sp) }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { pickImport() },
                enabled = !repo.readOnly,
                colors = ButtonDefaults.buttonColors(containerColor = baki.surfaceHigh, contentColor = baki.onSurface),
                shape = RoundedCornerShape(50), modifier = Modifier.weight(1f),
            ) { Text("Import JSON", fontSize = 12.sp) }
            Button(
                onClick = { confirmReset = true },
                colors = ButtonDefaults.buttonColors(containerColor = baki.surfaceHigh, contentColor = baki.down),
                shape = RoundedCornerShape(50), modifier = Modifier.weight(1f),
            ) { Text("Reset device", fontSize = 12.sp) }
        }
        Spacer(Modifier.height(16.dp))
        SectionTitle("ON THIS DEVICE")
        Spacer(Modifier.height(8.dp))
        RowCard("Store", LocalStore.path(), "", baki.muted)
        Spacer(Modifier.height(8.dp))
        RowCard("Records", "${repo.store.entries.size} entries · ${repo.store.shifts.size} shifts", "", baki.muted)
        Spacer(Modifier.height(8.dp))
        RowCard("Status", repo.status, "", baki.muted)
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            containerColor = baki.container,
            title = { Text("Reset this device?", color = baki.onSurface, fontWeight = FontWeight.Bold) },
            text = { Text("The local copy is forgotten and pulled again from the server. The server is not touched.", color = baki.muted, fontSize = 13.sp) },
            confirmButton = {
                TextButton(onClick = {
                    confirmReset = false
                    scope.launch { repo.resetLocal() }
                }) { Text("Reset", color = baki.down, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel", color = baki.muted) } },
        )
    }

    if (editingProfit) {
        ProfitDialog(
            profit = repo.store.settings.profit,
            onDismiss = { editingProfit = false },
            onSave = { updated ->
                editingProfit = false
                scope.launch { repo.updateProfit(updated) }
            },
        )
    }
}

@Composable
private fun SwitchRow(
    title: String,
    sub: String,
    checked: Boolean,
    warn: Boolean = false,
    onChange: (Boolean) -> Unit,
) {
    val baki = LocalBaki.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(baki.container).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = baki.onSurface, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(sub, color = if (warn) baki.warn else baki.muted, fontSize = 11.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = baki.onPrimary, checkedTrackColor = baki.primary,
                uncheckedThumbColor = baki.muted, uncheckedTrackColor = baki.container,
                uncheckedBorderColor = baki.muted,
            ),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddChoiceDialog(
    onDismiss: () -> Unit,
    onShift: () -> Unit,
    onExpense: () -> Unit,
    onIncome: () -> Unit,
) {
    val baki = LocalBaki.current
    BakiSheet(onDismiss = onDismiss) {
            Text("Add", color = baki.onSurface, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            ChoiceTile("Log shift", "payout + km + hours → your real rate", baki.accentStrong, baki.onAccent, onShift)
            ChoiceTile("Add spending", "petrol, toll, food, service", baki.surfaceHigh, baki.onSurface, onExpense)
            ChoiceTile("Add income", "tips, cash, bonus", baki.surfaceHigh, baki.up, onIncome)
        }
}

@Composable
private fun ChoiceTile(label: String, sub: String, bg: Color, ink: Color, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(bg).clickable { onClick() }.padding(16.dp),
    ) {
        Column {
            Text(label, color = ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(sub, color = ink.copy(alpha = 0.75f), fontSize = 11.sp)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
private fun EntryDialog(
    existing: Entry?,
    initialType: String,
    settings: Settings,
    day: String,
    initialCat: String? = null,
    onDismiss: () -> Unit,
    onSave: (String, Long, String, String, String, Int?) -> Unit,
    onDelete: (Entry) -> Unit,
) {
    val baki = LocalBaki.current
    var type by remember { mutableStateOf(existing?.type ?: initialType) }
    var amount by remember { mutableStateOf(existing?.let { "%.2f".format(it.amount / 100.0) } ?: "") }
    var note by remember { mutableStateOf(existing?.note ?: "") }
    var date by remember { mutableStateOf(existing?.date ?: day) }
    var odo by remember { mutableStateOf(existing?.odo?.toString() ?: "") }
    val cats = if (type == "income") settings.cats.catsIn else settings.cats.catsOut
    var cat by remember(type) { mutableStateOf(existing?.cat ?: initialCat ?: cats.firstOrNull()?.id ?: "other") }
    val cents = ((amount.toDoubleOrNull() ?: 0.0) * 100).toLong()

    // the sheet header shares its bounds with the row that opened it
    val sharedScope = LocalSharedTransition.current
    val visibilityScope = LocalAnimatedVisibility.current
    val sharedKey = existing?.let { "entry-${it.id}" }
    val headerMod = if (sharedScope != null && visibilityScope != null && sharedKey != null) {
        with(sharedScope) {
            Modifier.sharedBounds(
                rememberSharedContentState(key = sharedKey),
                animatedVisibilityScope = visibilityScope,
            )
        }
    } else Modifier

    BakiSheet(onDismiss = onDismiss) {
            Text(
                if (existing == null) "Add" else "Edit",
                color = baki.onSurface, fontSize = 22.sp, fontWeight = FontWeight.Bold,
                modifier = headerMod,
            )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SegButton("Spending", type == "expense", Modifier.weight(1f)) { type = "expense"; cat = settings.cats.catsOut.firstOrNull()?.id ?: "other" }
                    SegButton("Income", type == "income", Modifier.weight(1f)) { type = "income"; cat = settings.cats.catsIn.firstOrNull()?.id ?: "otherin" }
                }
                OutlinedTextField(
                    value = amount, onValueChange = { amount = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Amount (RM)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(), colors = bakiFieldColors(),
                )
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    cats.forEach { c ->
                        val on = c.id == cat
                        Box(
                            Modifier.clip(RoundedCornerShape(50))
                                .background(if (on) baki.primaryContainer else baki.surfaceHigh)
                                .clickable { cat = c.id }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        ) { Text(c.name, color = if (on) baki.onPrimaryContainer else baki.muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
                    }
                }
                OutlinedTextField(
                    value = note, onValueChange = { note = it },
                    label = { Text("Note (optional)") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(), colors = bakiFieldColors(),
                )
                // a fuel purchase with an odometer reading is what makes real km/L measurable
                if (cat == "fuel" || odo.isNotEmpty()) {
                    OutlinedTextField(
                        value = odo,
                        onValueChange = { odo = it.filter { c -> c.isDigit() } },
                        label = { Text("Odometer at fill-up (optional)") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(), colors = bakiFieldColors(),
                        supportingText = { Text("Add this and the app measures your real km/L", color = baki.muted, fontSize = 10.sp) },
                    )
                }
                OutlinedTextField(
                    value = date, onValueChange = { date = it },
                    label = { Text("Date (YYYY-MM-DD)") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(), colors = bakiFieldColors(),
                )
            if (existing != null) {
                TextButton(onClick = { onDelete(existing) }) {
                    Text("Delete", color = baki.down, fontWeight = FontWeight.Bold)
                }
            }
            Button(
                enabled = cents > 0,
                onClick = { onSave(type, cents, cat, note, date, odo.toIntOrNull()) },
                colors = ButtonDefaults.buttonColors(containerColor = baki.primary, contentColor = baki.onPrimary),
                shape = RoundedCornerShape(50),
                modifier = Modifier.fillMaxWidth().height(50.dp),
            ) {
                Text(
                    if (existing == null) "Add RM ${rm(cents)}" else "Save changes",
                    fontWeight = FontWeight.Bold, fontSize = 15.sp,
                )
            }
        }
}

@Composable
private fun SegButton(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val baki = LocalBaki.current
    Box(
        modifier.clip(RoundedCornerShape(50))
            .background(if (selected) baki.primaryContainer else baki.surfaceHigh)
            .clickable { onClick() }.padding(vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (selected) baki.onPrimaryContainer else baki.muted, fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShiftDialog(
    store: Store,
    existing: Shift?,
    onDismiss: () -> Unit,
    onSave: (String, String, Double, Double, Long, Long, Double?, Double?, Double?) -> Unit,
    onDelete: (Shift) -> Unit,
    onCalibrate: (Double) -> Unit = {},
) {
    val baki = LocalBaki.current
    var date by remember { mutableStateOf(existing?.date ?: todayIso()) }
    var start by remember { mutableStateOf(existing?.start ?: "") }
    var hours by remember { mutableStateOf(existing?.hours?.takeIf { it > 0 }?.let { "%.1f".format(it) } ?: "") }
    var km by remember { mutableStateOf(existing?.km?.takeIf { it > 0 }?.let { "%.1f".format(it) } ?: "") }
    var gross by remember { mutableStateOf(existing?.gross?.takeIf { it > 0 }?.let { "%.2f".format(it / 100.0) } ?: "") }
    var extra by remember { mutableStateOf(existing?.extra?.takeIf { it > 0 }?.let { "%.2f".format(it / 100.0) } ?: "") }
    var kml by remember { mutableStateOf(existing?.kmPerLitre?.let { "%.1f".format(it) } ?: "") }
    var odoA by remember { mutableStateOf(existing?.odoStart?.let { "%.0f".format(it) } ?: "") }
    var odoB by remember { mutableStateOf(existing?.odoEnd?.let { "%.0f".format(it) } ?: "") }
    val odoStart = odoA.toDoubleOrNull()
    val odoEnd = odoB.toDoubleOrNull()

    val grossCents = ((gross.toDoubleOrNull() ?: 0.0) * 100).toLong()
    val hoursValue = hours.toDoubleOrNull() ?: 0.0
    val kmValue = km.toDoubleOrNull() ?: 0.0
    val kmlValue = kml.toDoubleOrNull()?.takeIf { it > 0 }

    val preview = remember(date, start, hoursValue, kmValue, grossCents, kmlValue, odoStart, odoEnd) {
        val charge = existing?.grabFee ?: draftCoverCharge(store, date, start)
        val probe = Shift(
            id = "__preview", date = date, start = start, hours = hoursValue, km = kmValue,
            gross = grossCents, extra = ((extra.toDoubleOrNull() ?: 0.0) * 100).toLong(),
            grabFee = charge, kmPerLitre = kmlValue, odoStart = odoStart, odoEnd = odoEnd,
        )
        shiftMetrics(probe, store.settings.profit, overheadPerHour(store, date.take(7)))
    }

    BakiSheet(onDismiss = onDismiss) {
            Text(if (existing == null) "Log a shift" else "Edit shift", color = baki.onSurface, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                OutlinedTextField(value = date, onValueChange = { date = it }, label = { Text("Date (YYYY-MM-DD)") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(), colors = bakiFieldColors())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = start, onValueChange = { start = it }, label = { Text("Start HH:MM") },
                        singleLine = true, modifier = Modifier.weight(1f), colors = bakiFieldColors())
                    OutlinedTextField(value = hours, onValueChange = { hours = it.filter { c -> c.isDigit() || c == '.' } },
                        label = { Text("Hours") }, singleLine = true, modifier = Modifier.weight(1f), colors = bakiFieldColors())
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = km, onValueChange = { km = it.filter { c -> c.isDigit() || c == '.' } },
                        label = { Text("Km") }, singleLine = true, modifier = Modifier.weight(1f), colors = bakiFieldColors())
                    OutlinedTextField(value = gross, onValueChange = { gross = it.filter { c -> c.isDigit() || c == '.' } },
                        label = { Text("Payout (RM)") }, singleLine = true, modifier = Modifier.weight(1f), colors = bakiFieldColors())
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = extra, onValueChange = { extra = it.filter { c -> c.isDigit() || c == '.' } },
                        label = { Text("Tolls (RM)") }, singleLine = true, modifier = Modifier.weight(1f), colors = bakiFieldColors())
                    OutlinedTextField(value = kml, onValueChange = { kml = it.filter { c -> c.isDigit() || c == '.' } },
                        label = { Text("km/L (opt)") }, singleLine = true, modifier = Modifier.weight(1f), colors = bakiFieldColors())
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = odoA, onValueChange = { odoA = it.filter { c -> c.isDigit() || c == '.' } },
                        label = { Text("Odometer start") }, singleLine = true, modifier = Modifier.weight(1f), colors = bakiFieldColors())
                    OutlinedTextField(value = odoB, onValueChange = { odoB = it.filter { c -> c.isDigit() || c == '.' } },
                        label = { Text("Odometer end") }, singleLine = true, modifier = Modifier.weight(1f), colors = bakiFieldColors())
                }
                if (odoStart != null && odoEnd != null && odoEnd >= odoStart) {
                    Text("odometer says ${"%.1f".format(odoEnd - odoStart)} km — this wins over the Km field",
                        color = baki.muted, fontSize = 10.sp)
                }
                // reconciliation: GPS vs odometer on the same shift
                val tracked = existing?.km?.takeIf { it > 0.5 }
                if (tracked != null && odoStart != null && odoEnd != null && odoEnd > odoStart) {
                    val odo = odoEnd - odoStart
                    val ratio = odo / tracked
                    Text(
                        "GPS tracked ${"%.1f".format(tracked)} km · odometer ${"%.1f".format(odo)} km · ×${"%.2f".format(ratio)}",
                        color = baki.muted, fontSize = 10.sp,
                    )
                    if (ratio in 0.5..2.0) {
                        TextButton(onClick = { onCalibrate(ratio) }) {
                            Text(
                                "Use ×${"%.2f".format(ratio)} for future tracking",
                                color = baki.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
                Text(
                    "cover RM ${rm(preview.grabFee)} · net RM ${rm(preview.net)}" +
                        (preview.netHr?.let { " · RM ${rm(it)}/hr" } ?: ""),
                    color = baki.muted, fontSize = 11.sp,
                )
            if (existing != null) {
                TextButton(onClick = { onDelete(existing) }) {
                    Text("Delete shift", color = baki.down, fontWeight = FontWeight.Bold)
                }
            }
            Button(
                enabled = grossCents > 0 && hoursValue > 0,
                onClick = { onSave(date, start, hoursValue, kmValue, grossCents, ((extra.toDoubleOrNull() ?: 0.0) * 100).toLong(), kmlValue, odoStart, odoEnd) },
                colors = ButtonDefaults.buttonColors(containerColor = baki.primary, contentColor = baki.onPrimary),
                shape = RoundedCornerShape(50),
                modifier = Modifier.fillMaxWidth().height(50.dp),
            ) { Text(if (existing == null) "Save shift" else "Save changes", fontWeight = FontWeight.Bold, fontSize = 15.sp) }
        }
}

@Composable
private fun ProfitDialog(profit: Profit, onDismiss: () -> Unit, onSave: (Profit) -> Unit) {
    val baki = LocalBaki.current
    @Composable fun field(label: String, value: Double) = remember { mutableStateOf("%.2f".format(value)) }
    val fuel = field("fuel", profit.fuelPrice)
    val kml = field("kml", profit.kmPerLitre)
    val wear = field("wear", profit.wearPerKm)
    val target = field("target", profit.targetRate)
    val speed = field("speed", profit.avgSpeed)
    val cover = field("cover", profit.grabInsurance)
    val loan = field("loan", profit.loanMonthly)
    val roadTax = field("roadTax", profit.roadTaxYearly)
    val carIns = field("carIns", profit.carInsuranceYearly)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = baki.container,
        title = { Text("Vehicle costs & target", color = baki.onSurface, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ProfitField("Fuel price RM/L", fuel, baki)
                ProfitField("Fuel economy km/L", kml, baki)
                ProfitField("Wear RM/km", wear, baki)
                ProfitField("Target RM/hr", target, baki)
                ProfitField("Average speed km/h", speed, baki)
                ProfitField("Grab insurance RM/day", cover, baki)
                Spacer(Modifier.height(4.dp))
                Text("Fixed costs, spread over the hours you drive", color = baki.muted, fontSize = 10.sp)
                ProfitField("Car loan RM/month", loan, baki)
                ProfitField("Road tax RM/year", roadTax, baki)
                ProfitField("Car insurance RM/year", carIns, baki)
                Text("Saving re-derives every shift's cost, cover and overhead.", color = baki.muted, fontSize = 10.sp)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(
                    Profit(
                        fuelPrice = fuel.value.toDoubleOrNull() ?: profit.fuelPrice,
                        kmPerLitre = kml.value.toDoubleOrNull() ?: profit.kmPerLitre,
                        wearPerKm = wear.value.toDoubleOrNull() ?: profit.wearPerKm,
                        targetRate = target.value.toDoubleOrNull() ?: profit.targetRate,
                        avgSpeed = speed.value.toDoubleOrNull() ?: profit.avgSpeed,
                        grabInsurance = cover.value.toDoubleOrNull() ?: profit.grabInsurance,
                        loanMonthly = loan.value.toDoubleOrNull() ?: profit.loanMonthly,
                        roadTaxYearly = roadTax.value.toDoubleOrNull() ?: profit.roadTaxYearly,
                        carInsuranceYearly = carIns.value.toDoubleOrNull() ?: profit.carInsuranceYearly,
                    )
                )
            }) { Text("Save", color = baki.primary, fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = baki.muted) } },
    )
}

@Composable
private fun ProfitField(label: String, state: androidx.compose.runtime.MutableState<String>, baki: BakiPalette) {
    OutlinedTextField(
        value = state.value,
        onValueChange = { state.value = it.filter { c -> c.isDigit() || c == '.' } },
        label = { Text(label) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(), colors = bakiFieldColors(),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddPayoutDialog(onDismiss: () -> Unit, onAdd: (Long) -> Unit) {
    val baki = LocalBaki.current
    var amount by remember { mutableStateOf("") }
    val cents = ((amount.toDoubleOrNull() ?: 0.0) * 100).toLong()
    BakiSheet(onDismiss = onDismiss) {
            Text("Add a payout", color = baki.onSurface, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = amount, onValueChange = { amount = it.filter { c -> c.isDigit() || c == '.' } },
                label = { Text("Amount (RM)") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(), colors = bakiFieldColors(),
            )
            Button(
                enabled = cents > 0, onClick = { onAdd(cents) },
                colors = ButtonDefaults.buttonColors(containerColor = baki.primary, contentColor = baki.onPrimary),
                shape = RoundedCornerShape(50),
                modifier = Modifier.fillMaxWidth().height(50.dp),
            ) { Text("Add RM ${rm(cents)}", fontWeight = FontWeight.Bold) }
        }
}



@Composable
private fun BudgetSheet(repo: BakiRepo, onDismiss: () -> Unit) {
    val baki = LocalBaki.current
    val scope = rememberCoroutineScope()
    val cats = repo.store.settings.cats.catsOut.filter { it.id != "grabfee" }
    val draft = remember {
        mutableStateMapOf<String, String>().apply {
            repo.store.settings.budgets.forEach { (k, v) -> this[k] = "%.0f".format(v) }
        }
    }

    BakiSheet(
        onDismiss = onDismiss,
        footer = {
            Button(
                onClick = {
                    val limits = draft.mapNotNull { (k, v) -> v.toDoubleOrNull()?.takeIf { it > 0 }?.let { k to it } }.toMap()
                    scope.launch { repo.setBudgets(limits) }
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = baki.primary, contentColor = baki.onPrimary),
                shape = RoundedCornerShape(50),
                modifier = Modifier.fillMaxWidth().height(50.dp),
            ) { Text("Save limits", fontWeight = FontWeight.Bold) }
        },
    ) {
        Text("Monthly limits", color = baki.onSurface, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text("Leave a field blank for no cap. Applies to every month.", color = baki.muted, fontSize = 11.sp)
        Spacer(Modifier.height(4.dp))
        cats.forEach { c ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(c.icon, fontSize = 14.sp)
                Spacer(Modifier.width(8.dp))
                Text(c.name, color = baki.onSurface, fontSize = 13.sp, modifier = Modifier.weight(1f))
                OutlinedTextField(
                    value = draft[c.id] ?: "",
                    onValueChange = { v -> draft[c.id] = v.filter { it.isDigit() || it == '.' } },
                    placeholder = { Text("—", color = baki.muted, fontSize = 12.sp) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.width(96.dp),
                    colors = bakiFieldColors(),
                )
            }
        }
    }
}
