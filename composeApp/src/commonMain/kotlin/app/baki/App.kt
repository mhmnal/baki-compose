package app.baki

import androidx.compose.ui.platform.testTag

import androidx.compose.ui.input.nestedscroll.nestedScroll

import androidx.compose.ui.layout.onSizeChanged

import androidx.compose.foundation.shape.CircleShape

import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.animateColorAsState
import kotlin.math.abs
import kotlin.math.roundToLong
import androidx.compose.ui.unit.round
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.draw.scale
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith

import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun App(repo: BakiRepo = remember { BakiRepo() }) {

    // A PDF shared into the app is a statement import; the effect keeps it off the composition thread.
    val shared = SharedStatement.pending
    LaunchedEffect(shared) {
        val bytes = shared ?: return@LaunchedEffect
        SharedStatement.pending = null
        val parsed = withContext(Dispatchers.Default) {
            PdfText.extract(bytes)?.let { StatementParse.parse(it) }
        }
        if (parsed == null) {
            PlatformEffects.notice("That PDF doesn't look like a Grab statement")
        } else {
            repo.saveStatement(parsed)
            PlatformEffects.notice("Imported week of ${parsed.weekStart}")
        }
    }
    BakiPrivacy.discreet = repo.config.discreet
    BakiTheme(
        mode = repo.config.themeMode,
        colorSource = repo.config.colorSource,
        highContrast = repo.config.highContrast,
        reducedMotion = repo.config.reducedMotion,
    ) {
        androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
            if (Caps.isDesktop && !PhonePreview.active) {
                // Desktop is a separate read-only review surface, not a stretched phone.
                DeskApp(repo)
            } else {
                SharedTransitionLayout {
                    androidx.compose.runtime.CompositionLocalProvider(LocalSharedTransition provides this) {
                        AppContent(repo)
                    }
                }
            }
            ActionFeedbackOverlay(Modifier.align(Alignment.BottomCenter).padding(bottom = 96.dp))
        }
    }
}

/** Which shift's payouts the sheet is showing. */
private sealed interface PayoutsTarget {
    data object Live : PayoutsTarget
    data class Stored(val shiftId: String) : PayoutsTarget
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AppContent(repo: BakiRepo) {
    // read the palette *inside* the theme provider, or every colour falls back to dark
    val scheme = MaterialTheme.colorScheme
    var tab by remember { mutableStateOf(PhonePreview.initialTab) }
    var adding by remember { mutableStateOf(0) }   // 0 none, 2 entry, 3 shift, 4 payout
    var addMenuOpen by remember { mutableStateOf(false) }
    var day by remember { mutableStateOf(todayIso()) }
    var month by remember { mutableStateOf(todayIso().take(7)) }
    var todayTick by remember { mutableStateOf(todayIso()) }
    var editingEntry by remember { mutableStateOf<Entry?>(null) }
    var editingShift by remember { mutableStateOf<Shift?>(null) }
    var entryType by remember { mutableStateOf("expense") }
    var presetCat by remember { mutableStateOf<String?>(null) }
    var presetAmount by remember { mutableStateOf<String?>(null) }
    var presetNote by remember { mutableStateOf<String?>(null) }
    var editingBudgets by remember { mutableStateOf(false) }
    var payoutsTarget by remember { mutableStateOf<PayoutsTarget?>(null) }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    suspend fun announceAdded(id: String, label: String, cents: Long) {
        val res = snackbar.showSnackbar(
            "Added $label RM ${rm(cents)}",
            actionLabel = "Undo", duration = SnackbarDuration.Short,
        )
        if (res == SnackbarResult.ActionPerformed) repo.deleteEntry(id)
    }
    fun routeAdded(id: String, type: String, cat: String, cents: Long, odo: Int?) {
        val name = (repo.store.settings.cats.catsOut + repo.store.settings.cats.catsIn)
            .firstOrNull { it.id == cat }?.name ?: cat
        scope.launch { announceAdded(id, name, cents) }
    }

    // A swipe can report the same confirmation twice; never queue two Undo bars for one row.
    val deletingIds = remember { mutableSetOf<String>() }
    fun deleteEntryWithUndo(entry: Entry) {
        if (!deletingIds.add(entry.id)) return
        scope.launch {
            try {
                if (repo.store.entries.none { it.id == entry.id }) return@launch
                if (!repo.deleteEntry(entry.id)) {
                    ActionFeedback.show("Could not delete — enable Allow writes")
                    return@launch
                }
                val res = snackbar.showSnackbar(
                    "Deleted ${entry.note.ifEmpty { entry.cat }} · RM ${rm(entry.amount)}",
                    actionLabel = "Undo", duration = SnackbarDuration.Short,
                )
                if (res == SnackbarResult.ActionPerformed) {
                    repo.addEntry(entry.type, entry.amount, entry.cat, entry.note, entry.date)
                }
            } finally {
                deletingIds.remove(entry.id)
            }
        }
    }
    LaunchedEffect(Unit) {
        repo.start()
        // roll Today over at midnight; if the user deliberately browsed a past day, leave it there
        while (true) {
            val now = todayIso()
            if (now != todayTick) {
                if (day == todayTick) day = now
                if (month == todayTick.take(7)) month = now.take(7)
                todayTick = now
            }
            delay(30_000)
        }
    }

    // notification actions: "+ Payout" and "End shift" come back as intents
    val pendingAction = BakiIntents.pending
    LaunchedEffect(pendingAction) {
        when (pendingAction) {
            "payout" -> if (repo.live != null) adding = 4
            "qr" -> tab = 4
            "end" -> if (repo.live != null) { PlatformEffects.tapFeedback(); repo.endLive() }
            "pause" -> repo.live?.let { live ->
                PlatformEffects.tapFeedback()
                if (isPaused(live)) repo.resumeLive() else repo.pauseLive()
            }
        }
        if (pendingAction != null) BakiIntents.pending = null
    }

    PlatformBackHandler(enabled = adding != 0 || tab != 0) {
        if (adding != 0) { adding = 0; editingEntry = null; editingShift = null; presetCat = null }
        else if (tab != 0) tab = 0
    }


    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 600.dp
        var wideRailExpanded by remember { mutableStateOf(maxWidth >= 840.dp) }
        var railWidthPx by remember { mutableIntStateOf(0) }
        var navHeightPx by remember { mutableIntStateOf(0) }
        val railStart = if (wide && railWidthPx > 0) {
            with(LocalDensity.current) { railWidthPx.toDp() }
        } else if (wide) 88.dp else 0.dp
        val contentStart = if (wide) {
            railStart + ((maxWidth - railStart - 960.dp).coerceAtLeast(0.dp) / 2)
        } else 0.dp
        val barHeight = 80.dp
        val measuredNavHeight = if (navHeightPx > 0) {
            with(LocalDensity.current) { navHeightPx.toDp() }
        } else barHeight
        val fabSize = 56.dp
        // sit above the gesture bar, not on top of it
        val gestureInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val barGap = gestureInset
        val hasFab = tab == 0 && !repo.readOnly && repo.live == null
        // keep Undo clear of the floating +, not underneath it
        val snackbarBottom = if (hasFab) barHeight + barGap + fabSize + 10.dp
        else barHeight + barGap + 10.dp
        val sheetOpen = adding != 0 || payoutsTarget != null
        var headerCollapsed by remember { mutableStateOf(false) }
        LaunchedEffect(tab) { headerCollapsed = false }

        // ---- level 1: the app itself. The modal sheet owns the standard MD3 scrim. ----
        Box(Modifier.fillMaxSize()) {

    Box(Modifier.fillMaxSize()) {
    Box(Modifier.fillMaxSize().background(scheme.surface)) {}
    // The system status bar is transparent under edge-to-edge. Paint its inset with the
    // same surface as the app bar so the two never split into different bands.
    Box(
        Modifier
            .fillMaxWidth()
            .height(WindowInsets.statusBars.asPaddingValues().calculateTopPadding())
            .align(Alignment.TopCenter)
            .background(scheme.surface),
    )
    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost = {
            BakiSnackbarHost(snackbar, Modifier.padding(horizontal = Space.m).padding(bottom = snackbarBottom))
        },
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize()
                .padding(start = contentStart)
                .widthIn(max = 960.dp),
        ) {
            Header(repo)
            if (repo.store.entries.isEmpty() && repo.store.shifts.isEmpty() && repo.status == "offline") {
                FirstRunCard(onOpenMore = { tab = 4 })
            }
            val motionScheme = MaterialTheme.motionScheme
            AnimatedContent(
                targetState = tab,
                transitionSpec = {
                    val spatial = motionScheme.defaultSpatialSpec<androidx.compose.ui.unit.IntOffset>()
                    val effects = motionScheme.defaultEffectsSpec<Float>()
                    (fadeIn(effects) + scaleIn(initialScale = 0.985f) + slideInVertically(spatial) { it / 12 })
                        .togetherWith(fadeOut(effects) + scaleOut(targetScale = 1.01f))
                },
                modifier = Modifier.fillMaxSize(),
                label = "tab",
            ) { current ->
            androidx.compose.runtime.CompositionLocalProvider(LocalAnimatedVisibility provides this) {
            when (current) {
                0 -> TodayScreen(
                    repo = repo,
                    day = day,
                    today = todayTick,
                    onDay = { day = it },
                    onAdd = {
                        editingEntry = null
                        entryType = "expense"
                        presetCat = null
                        adding = 2
                    },
                    onPayout = { adding = 4 },
                    onPayouts = { payoutsTarget = PayoutsTarget.Live },
                    onQuickAdd = { cat, cents ->
                        scope.launch {
                            val id = repo.addExpense(cents, cat, "", todayIso())
                            if (id != null) routeAdded(id, "expense", cat, cents, null)
                        }
                    },
                    onEditEntry = { editingEntry = it; entryType = it.type; adding = 2 },
                    onDeleteEntry = { entry -> deleteEntryWithUndo(entry) },
                    onPreset = { catId ->
                        editingEntry = null
                        entryType = "expense"
                        presetCat = catId
                        adding = 2
                    },
                    onSeeAll = { tab = 1 },
                    onHeaderCollapsed = { headerCollapsed = it },
                )
                1 -> SpendingScreen(
                    repo = repo,
                    month = month,
                    onMonth = { month = it },
                    onEditEntry = { editingEntry = it; entryType = it.type; adding = 2 },
                    onRepeatEntry = { entry ->
                        editingEntry = null
                        entryType = entry.type
                        presetCat = entry.cat
                        presetAmount = "%.2f".format(entry.amount / 100.0)
                        presetNote = entry.note
                        adding = 2
                    },
                    onDeleteEntry = { entry -> deleteEntryWithUndo(entry) },
                    onHeaderCollapsed = { headerCollapsed = it },
                )
                2 -> ShiftsScreen(
                    repo = repo,
                    onAddShift = { editingShift = null; adding = 3 },
                    onEditShift = { editingShift = it; adding = 3 },
                    onPayouts = { payoutsTarget = PayoutsTarget.Stored(it.id) },
                    onHeaderCollapsed = { headerCollapsed = it },
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
                3 -> SummaryScreen(repo = repo, store = repo.store, month = month, onMonth = { month = it }, onHeaderCollapsed = { headerCollapsed = it })
                else -> MoreScreen(
                    repo,
                    onHeaderCollapsed = { headerCollapsed = it },
                    onEditBudgets = { editingBudgets = true; adding = 5 },
                    onAddService = {
                        editingEntry = null
                        entryType = "expense"
                        presetCat = "maintenance"
                        presetAmount = null
                        presetNote = null
                        adding = 2
                    },
                )
            }
            }
            }
        }
    }
    }

    // Adaptive navigation: floating rail on tablets, custom dock on phones.
    if (wide && !sheetOpen) {
        BakiWideNavigation(
            labels = listOf("Today", "Spending", "Shifts", "Summary", "More"),
            icons = listOf(BakiIcons.Today, BakiIcons.Receipt, BakiIcons.Car, BakiIcons.Insights, BakiIcons.More),
            selected = tab,
            expanded = wideRailExpanded,
            onToggle = { wideRailExpanded = !wideRailExpanded },
            onSelect = { i -> PlatformEffects.haptic(Haptic.Tick); tab = i },
            showAdd = tab == 0 && !repo.readOnly && repo.live == null,
            onAdd = { editingEntry = null; entryType = "expense"; presetCat = null; adding = 2 },
            modifier = Modifier
                .align(Alignment.CenterStart)
                .onSizeChanged { railWidthPx = it.width }
                .testTag("wide-navigation"),
        )
    }

    // Baki floating dock on compact screens.
    if (!wide && !sheetOpen) {
        BakiBottomNavigation(
            labels = listOf("Today", "Spend", "Shifts", "Stats", "More"),
            icons = listOf(BakiIcons.Today, BakiIcons.Receipt, BakiIcons.Car, BakiIcons.Insights, BakiIcons.More),
            selected = tab,
            onSelect = { i -> PlatformEffects.haptic(Haptic.Tick); tab = i },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .onSizeChanged { navHeightPx = it.height }
                .testTag("bottom-navigation"),
        )
    }

    // M3 Expressive primary action menu.
    if (!wide && tab == 0 && day == todayTick && !repo.readOnly && repo.live == null) {
        Box(
            modifier = Modifier.align(Alignment.BottomEnd).testTag("quick-actions")
                .padding(
                    end = 16.dp,
                    // No internal menu inset now: place the visible FAB itself 8dp above the dock.
                    bottom = measuredNavHeight + 8.dp,
                ),
            contentAlignment = Alignment.BottomEnd,
        ) {
            Column(horizontalAlignment = Alignment.End) {
                AnimatedVisibility(
                    visible = addMenuOpen,
                    enter = fadeIn() + scaleIn(initialScale = 0.82f) + slideInVertically { it / 5 },
                    exit = fadeOut() + scaleOut(targetScale = 0.88f),
                ) {
                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(Space.m),
                        modifier = Modifier.padding(bottom = Space.m),
                    ) {
                        QuickMenuItem("Add spending", BakiIcons.Receipt) {
                            addMenuOpen = false
                            editingEntry = null
                            entryType = "expense"
                            presetCat = null
                            adding = 2
                        }
                        QuickMenuItem("Add income", BakiIcons.Wallet) {
                            addMenuOpen = false
                            editingEntry = null
                            entryType = "income"
                            presetCat = null
                            adding = 2
                        }
                        QuickMenuItem("Log shift", BakiIcons.Car) {
                            addMenuOpen = false
                            editingShift = null
                            adding = 3
                        }
                    }
                }
                val fabScale by animateFloatAsState(
                    targetValue = if (addMenuOpen) 1.04f else 1f,
                    animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                    label = "fabScale",
                )
                ToggleFloatingActionButton(
                    checked = addMenuOpen,
                    onCheckedChange = { addMenuOpen = it },
                    modifier = Modifier
                        .size(96.dp)
                        .graphicsLayer { scaleX = fabScale; scaleY = fabScale }
                        .semantics { contentDescription = if (addMenuOpen) "Close quick actions" else "Open quick actions" },
                    contentAlignment = Alignment.Center,
                    containerColor = ToggleFloatingActionButtonDefaults.containerColor(
                        initialColor = MaterialTheme.colorScheme.primaryContainer,
                        finalColor = MaterialTheme.colorScheme.primary,
                    ),
                ) {
                    val rotation by animateFloatAsState(
                        targetValue = if (addMenuOpen) 45f else 0f,
                        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                        label = "addMenuRotation",
                    )
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "+",
                            color = if (addMenuOpen) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onPrimaryContainer,
                            style = MaterialTheme.typography.displaySmall,
                            modifier = Modifier.rotate(rotation),
                        )
                    }
                }
            }
        }
    }

    LaunchedEffect(repo.live?.start) {
        if (repo.live != null) addMenuOpen = false
    }


    // ---- level 2: the sheets sit above everything, on a scrim over the blurred app ----
    payoutsTarget?.let { target ->
        val liveShift = repo.live
        val stored = (target as? PayoutsTarget.Stored)
            ?.let { t -> repo.store.shifts.firstOrNull { it.id == t.shiftId } }
        val payouts = when (target) {
            PayoutsTarget.Live -> liveShift?.payouts.orEmpty()
            is PayoutsTarget.Stored -> stored?.payouts.orEmpty()
        }
        val title = when (target) {
            PayoutsTarget.Live -> "Shift payouts"
            is PayoutsTarget.Stored -> "Payouts · ${prettyDate(stored?.date.orEmpty())}"
        }
        PayoutsSheet(
            title = title,
            payouts = payouts,
            recordedCents = stored?.let { (it.gross + it.incentive).coerceAtLeast(0L) },
            editable = !repo.readOnly,
            onEdit = { index, cents ->
                scope.launch {
                    when (target) {
                        PayoutsTarget.Live -> repo.updatePayout(index, cents)
                        is PayoutsTarget.Stored -> repo.updateShiftPayout(target.shiftId, index, cents)
                    }
                    ActionFeedback.show("Payout updated")
                }
            },
            onDelete = { index ->
                scope.launch {
                    val removed = when (target) {
                        PayoutsTarget.Live -> repo.removePayout(index)
                        is PayoutsTarget.Stored -> repo.removeShiftPayout(target.shiftId, index)
                    }
                    if (removed != null) {
                        val res = snackbar.showSnackbar(
                            "Payout removed · RM ${rm(removed.c)}",
                            actionLabel = "Undo",
                            duration = SnackbarDuration.Long,
                        )
                        if (res == SnackbarResult.ActionPerformed) {
                            when (target) {
                                PayoutsTarget.Live -> repo.insertPayout(index, removed)
                                is PayoutsTarget.Stored -> repo.insertShiftPayout(target.shiftId, index, removed)
                            }
                        }
                    }
                }
            },
            onAdd = { cents ->
                scope.launch {
                    when (target) {
                        PayoutsTarget.Live -> repo.addPayout(cents)
                        is PayoutsTarget.Stored -> repo.addShiftPayout(target.shiftId, cents)
                    }
                    ActionFeedback.show("Payout added")
                }
            },
            onDismiss = { payoutsTarget = null },
        )
    }

    when (adding) {
        2 -> EntryDialog(
            existing = editingEntry,
            initialType = entryType,
            initialCat = presetCat,
            initialAmount = presetAmount,
            initialNote = presetNote,
            settings = repo.store.settings,
            day = day,
            onDismiss = { adding = 0; editingEntry = null; presetCat = null; presetAmount = null; presetNote = null },
            onSave = { type, cents, cat, note, date, odoAtFill ->
                presetCat = null
                presetAmount = null
                presetNote = null
                val target = editingEntry
                adding = 0; editingEntry = null
                scope.launch {
                    if (target == null) {
                        val id = repo.addEntry(type, cents, cat, note, date, odoAtFill)
                        if (id != null) routeAdded(id, type, cat, cents, odoAtFill)
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
                deleteEntryWithUndo(entry)
            },
        )
        3 -> ShiftDialog(
            store = repo.store,
            existing = editingShift,
            onPayouts = { shift ->
                adding = 0; editingShift = null
                payoutsTarget = PayoutsTarget.Stored(shift.id)
            },
            onDismiss = { adding = 0; editingShift = null },
            onSave = { date, start, hours, km, gross, extra, kmPerLitre, odoS, odoE, reimbursed ->
                val target = editingShift
                adding = 0; editingShift = null
                scope.launch {
                    if (target == null) {
                        repo.addShift(date, start, hours, km, gross, extra, odoStart = odoS, odoEnd = odoE, reimbursedTolls = reimbursed)
                    } else {
                        repo.updateShift(target, date, start, hours, km, gross, extra, target.note, kmPerLitre, odoS, odoE, reimbursed)
                        val res = snackbar.showSnackbar(
                            "Shift saved · was RM ${rm(target.gross)}",
                            actionLabel = "Undo", duration = SnackbarDuration.Long,
                        )
                        if (res == SnackbarResult.ActionPerformed) {
                            repo.updateShift(
                                target, target.date, target.start, target.hours, target.km,
                                target.gross, target.extra, target.note, target.kmPerLitre,
                                target.odoStart, target.odoEnd, target.reimbursedTolls,
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
        5 -> BudgetSheet(
            repo = repo,
            onDismiss = { adding = 0; editingBudgets = false },
            onSave = { cap, limits, carry ->
                // App-level scope: the sheet may disappear while these writes are still in flight.
                scope.launch {
                    try {
                        repo.setDailyFoodCap(cap)
                        repo.setBudgets(limits)
                        repo.setEnvelopeCarry(carry)
                    } finally {
                        adding = 0
                        editingBudgets = false
                    }
                }
            },
        )
        4 -> AddPayoutDialog(
            onDismiss = { adding = 0 },
            onAdd = { cents -> repo.addPayout(cents); adding = 0 },
        )
    }

    }
    }


}

@OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FirstRunCard(onOpenMore: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        color = scheme.primaryContainer,
        contentColor = scheme.onPrimaryContainer,
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier.fillMaxWidth().padding(bottom = Space.s),
    ) {
        Row(Modifier.padding(Space.ml), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(scheme.onPrimaryContainer.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Text("↗", color = scheme.onPrimaryContainer, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
            }
            Spacer(Modifier.width(Space.m))
            Column(Modifier.weight(1f)) {
                Text("Let's get connected", style = MaterialTheme.typography.titleMediumEmphasized)
                Spacer(Modifier.height(Space.xs))
                Text(
                    "Baki is empty and offline. Point it at the server in More → App, then enable writes.",
                    color = scheme.onPrimaryContainer.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(Space.s))
                FilledTonalButton(onClick = onOpenMore) {
                    Text("Open setup", style = MaterialTheme.typography.labelLargeEmphasized)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun Header(repo: BakiRepo) {
    val status = repo.status
    val ink = when (status) {
        "synced" -> MaterialTheme.colorScheme.tertiary
        "offline" -> MaterialTheme.colorScheme.error
        "cached" -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.primary
    }
    Row(
        Modifier.fillMaxWidth().height(64.dp).padding(start = Space.l, end = Space.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(24.dp)
                .clip(MaterialShapes.SoftBurst.toShape())
                .background(MaterialTheme.colorScheme.primary),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            "Baki",
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
        )
        Spacer(Modifier.weight(1f))
        Surface(
            shape = CircleShape,
            color = ink.copy(alpha = 0.13f),
            contentColor = ink,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                if (status == "syncing") {
                    LoadingIndicator(modifier = Modifier.size(13.dp), color = ink)
                } else {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(ink))
                }
                Text(
                    if (status == "syncing") "Syncing" else status.replaceFirstChar { it.titlecase() },
                    color = ink,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .clickable { repo.setDiscreet(!repo.config.discreet) }
                .semantics {
                    role = Role.Button
                    contentDescription = if (repo.config.discreet) "Show ledger amounts" else "Hide ledger amounts"
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (repo.config.discreet) "◉" else "◎",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun QuickMenuItem(label: String, icon: ImageVector, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = MaterialTheme.shapes.large,
        color = scheme.primaryContainer,
        contentColor = scheme.onPrimaryContainer,
        shadowElevation = 4.dp,
    ) {
        Row(
            Modifier
                .clickable(onClick = onClick)
                .heightIn(min = 56.dp)
                .padding(start = Space.m, end = Space.xl, top = Space.s, bottom = Space.s),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.m),
        ) {
            Icon(icon, contentDescription = null, tint = scheme.onPrimaryContainer, modifier = Modifier.size(IconSize.bar))
            Text(label, color = scheme.onPrimaryContainer, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Hero(label: String, amountCents: Long, tint: Color = Color.Unspecified, size: Int = 46) {
    val scheme = MaterialTheme.colorScheme
    val ink = if (tint == Color.Unspecified) scheme.onSurface else tint
    Column {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            Box(
                Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(if (tint == Color.Unspecified) scheme.primary else tint),
            )
            Text(
                label,
                color = scheme.onSurfaceVariant,
                letterSpacing = 1.1.sp,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            )
        }
        EasedMoney(
            cents = amountCents, color = ink,
            // tabular figures: the hero number keeps its width as digits change
            style = if (size >= 40) heroMoneyStyle(amountCents, MaterialTheme.typography) else MaterialTheme.typography.headlineMedium.copy(fontFeatureSettings = "tnum"),
        )
    }
}

@Composable
@OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
fun RowCard(
    title: String,
    sub: String,
    amount: String,
    amountTint: Color = Color.Unspecified,
    dotTint: Color = Color.Unspecified,
    icon: ImageVector? = null,
    onLongPress: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    running: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val ink = if (amountTint == Color.Unspecified) scheme.onSurface else amountTint
    val dot = if (dotTint == Color.Unspecified) ink else dotTint
    val gestureScope = rememberCoroutineScope()
    var acted by remember { mutableStateOf(false) }
    val swipe = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value != SwipeToDismissBoxValue.Settled && !acted) {
                acted = true
                when (value) {
                    SwipeToDismissBoxValue.StartToEnd -> onLongPress?.invoke()
                    SwipeToDismissBoxValue.EndToStart -> onDelete?.invoke()
                    else -> {}
                }
                gestureScope.launch { delay(650); acted = false }
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
            if (swipe.dismissDirection != SwipeToDismissBoxValue.Settled) {
                Row(
                    Modifier.fillMaxSize().clip(MaterialTheme.shapes.large)
                        .background(if (editing) scheme.primaryContainer else scheme.error.copy(alpha = 0.22f))
                        .padding(horizontal = Space.ml),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = if (editing) Arrangement.Start else Arrangement.End,
                ) {
                    Text(
                        if (editing) "Edit" else "Delete",
                        color = if (editing) scheme.onPrimaryContainer else scheme.error,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        },
    ) {
    val a11yActions = buildList {
        if (onClick != null) add(CustomAccessibilityAction("Open") { onClick(); true })
        if (onLongPress != null) add(CustomAccessibilityAction("Edit") { onLongPress(); true })
        if (onAction != null && actionLabel != null) add(CustomAccessibilityAction(actionLabel) { onAction(); true })
        if (onDelete != null) add(CustomAccessibilityAction("Delete") { onDelete(); true })
    }
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth()
                .then(
                    Modifier.clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surfaceContainerLow)
                )
                .then(
                    if (onClick != null || onLongPress != null) Modifier.pointerInput(title) {
                        detectTapGestures(
                            onTap = { onClick?.invoke() },
                            onLongPress = { onLongPress?.invoke() },
                        )
                    } else Modifier
                )
                .semantics {
                    customActions = a11yActions
                    contentDescription = buildString {
                        append(title)
                        if (amount.isNotBlank()) append(", $amount")
                        actionLabel?.let { append(", $it available") }
                    }
                }
                .padding(vertical = Space.l, horizontal = Space.l),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Box(
                    Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(scheme.primaryContainer.copy(alpha = 0.75f)),
                    contentAlignment = Alignment.Center,
                ) { Icon(icon, contentDescription = null, tint = scheme.onPrimaryContainer, modifier = Modifier.size(IconSize.inline)) }
                Spacer(Modifier.width(Space.m))
            } else {
                Box(
                    Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(dot.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center,
                ) { Box(Modifier.size(7.dp).clip(CircleShape).background(dot)) }
                Spacer(Modifier.width(Space.m))
            }
            Column(Modifier.weight(1f)) {
                Text(title, color = scheme.onSurface, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold))
                if (sub.isNotEmpty()) Text(sub, color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(amount, color = ink, style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"))
                if (running != null) Text(running, color = scheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                if (actionLabel != null && onAction != null) {
                    TextButton(
                        onClick = onAction,
                        contentPadding = PaddingValues(horizontal = Space.xs),
                    ) {
                        Text(
                            actionLabel,
                            color = scheme.primary,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        )
                    }
                }
            }
        }
    }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SectionTitle(text: String, trailing: String = "") {
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                Box(
                    Modifier
                        .size(13.dp)
                        .clip(CircleShape)
                        .background(scheme.primary),
                )
                Text(
                    text.lowercase().replaceFirstChar { it.titlecase() }, color = scheme.onSurfaceVariant,
                    style = MaterialTheme.typography.titleSmallEmphasized,
                    modifier = Modifier.semantics { heading() },
                )
            }
            if (trailing.isNotEmpty()) Text(trailing, color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun bakiFieldColorsPublic(): TextFieldColors = bakiFieldColors()

@Composable
private fun bakiFieldColors(): TextFieldColors {
    val scheme = MaterialTheme.colorScheme
    return OutlinedTextFieldDefaults.colors(
        focusedTextColor = scheme.onSurface, unfocusedTextColor = scheme.onSurface,
        focusedBorderColor = scheme.primary, unfocusedBorderColor = scheme.onSurfaceVariant,
        focusedLabelColor = scheme.primary, unfocusedLabelColor = scheme.onSurfaceVariant,
        cursorColor = scheme.primary,
        focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LiveCard(repo: BakiRepo, live: LiveShift, onPayout: () -> Unit, onPayouts: () -> Unit = {}) {
    val scheme = MaterialTheme.colorScheme
    var now by remember { mutableStateOf(nowMillis()) }
    var ending by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(live.start) {
        while (true) {
            now = nowMillis()
            repo.ensureLiveCover(now)
            delay(1000)
        }
    }

    val worked = liveWorkedMs(live, now)
    val onBreak = isPaused(live)
    val breakMs = pausedMs(live, now)
    val total = liveTotalCents(live)
    val be = liveBreakEven(repo.store, live, now)
    val net = be.earned - be.costs
    val netCents = (net * 100).toLong()
    val accent = if (onBreak) scheme.secondary else scheme.primary

    Surface(
        modifier = Modifier.fillMaxWidth().padding(vertical = Space.s),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Box {
            Column(Modifier.fillMaxWidth().padding(Space.l)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LiveTrace(
                    active = !onBreak,
                    color = accent,
                    reducedMotion = repo.config.reducedMotion,
                    modifier = Modifier.width(72.dp).height(23.dp),
                )
                Spacer(Modifier.width(Space.s))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (onBreak) "BREAK TIME" else "ON THE CLOCK",
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                    )
                    Text("since ${startClock(live.start)}", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                }
                Surface(
                    shape = RoundedCornerShape(50),
                    color = accent.copy(alpha = 0.14f),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = Space.s, vertical = Space.xs),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(accent))
                        Text(
                            if (onBreak) "PAUSED" else "LIVE",
                            color = accent,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        )
                    }
                }
            }
            Spacer(Modifier.height(Space.l))
            Text("Net so far", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.labelLarge)
            Text(
                "RM ${rm(netCents)}",
                color = if (net < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.headlineLargeEmphasized.copy(fontFeatureSettings = "tnum"),
            )
            Text(
                if (onBreak) "Recharging. The road can wait." else "The meter is running.",
                color = scheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(Space.s))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier
                        .clickable(onClick = onPayouts)
                        .semantics {
                            role = Role.Button
                            contentDescription = "Open payouts; ${live.payouts.size} recorded"
                        },
                ) {
                    Text(
                        "${live.payouts.size} ${if (live.payouts.size == 1) "payout" else "payouts"} · view",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = Space.m, vertical = Space.xs),
                    )
                }
                Spacer(Modifier.weight(1f))
                Text("gross RM ${rm(total)}", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.labelLarge)
            }
            Spacer(Modifier.height(Space.m))
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(horizontal = Space.m, vertical = Space.xs)) {
                    LiveMetric("On clock", liveClock(worked), MaterialTheme.colorScheme.onSurface)
                    LiveMetric("Costs so far", "RM ${rm(be.costs)}", scheme.onSurfaceVariant)
                    LiveMetric(
                        if (be.covered) "Break-even" else "Break-even in",
                        if (be.covered) "covered" else be.minutesToGo?.let { "${it.toInt()} min" } ?: "add a payout",
                        if (be.covered) scheme.tertiary else scheme.onSurfaceVariant,
                    )
                    if (be.netPerHour != null) {
                        LiveMetric(
                            "Yield now",
                            buildString {
                                append("RM ${rm(be.netPerHour)}/h")
                                be.grossPerKm?.let { append(" · RM ${rm(it)}/km") }
                            },
                            if ((be.netPerHour ?: 0.0) >= repo.store.settings.profit.targetRate) scheme.tertiary else scheme.error,
                        )
                    }
                    LiveMetric(
                        "Tracked",
                        buildString {
                            append("${"%.1f".format(live.km)} km")
                            if (breakMs > 0) append(" · ${liveClock(breakMs)} break")
                        },
                        scheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(Space.l))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                FilledTonalButton(
                    onClick = onPayout,
                    modifier = Modifier.weight(1f),
                ) { Text("Payout", style = MaterialTheme.typography.labelLargeEmphasized, maxLines = 1, softWrap = false) }
                Button(
                    enabled = !repo.readOnly,
                    onClick = { if (onBreak) repo.resumeLive() else repo.pauseLive() },
                    modifier = Modifier.weight(1f),
                ) { Text(if (onBreak) "Resume" else "Pause", style = MaterialTheme.typography.labelLargeEmphasized) }
                Button(
                    enabled = !repo.readOnly && !ending,
                    onClick = {
                        if (ending) return@Button
                        ending = true
                        scope.launch {
                            try { repo.endLive() } finally { ending = false }
                        }
                    },
                    modifier = Modifier.weight(0.82f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
                ) { Text(if (ending) "Ending…" else "End", style = MaterialTheme.typography.labelLargeEmphasized) }
            }
            }
        }
    }
}

@Composable
private fun LiveMetric(label: String, value: String, tint: Color) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(value, color = tint, style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum", fontWeight = FontWeight.Bold))
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MonthHeader(month: String, onChange: (String) -> Unit) {
    val scheme = MaterialTheme.colorScheme
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
                label = "‹",
                enabled = true,
                onClick = { onChange(addDays("$month-01", -1).take(7)) },
                description = "Previous month",
            )
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Box(Modifier.size(7.dp).clip(CircleShape).background(scheme.primary))
                    Text(
                        "MONTH IN PLAY",
                        color = scheme.primary,
                        letterSpacing = 1.3.sp,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    )
                }
                Text(
                    monthLabel(month),
                    color = scheme.onSurface,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                )
                Text("spending, monthly", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
            }
            ShapeNavButton(
                label = "›",
                enabled = true,
                onClick = { onChange(addDays(endOfMonth(month), 1).take(7)) },
                description = "Next month",
            )
        }
    }
}

fun monthLabel(month: String): String {
    val d = kotlinx.datetime.LocalDate.parse("$month-01")
    val name = d.month.name.lowercase().replaceFirstChar { it.uppercase() }
    return "$name ${d.year}"
}

fun endOfMonth(month: String): String = addDays(addDays("$month-01", 32).take(7) + "-01", -1)


@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SpendingScreen(
    repo: BakiRepo,
    month: String,
    onMonth: (String) -> Unit,
    onEditEntry: (Entry) -> Unit,
    onRepeatEntry: (Entry) -> Unit,
    onDeleteEntry: (Entry) -> Unit,
    onHeaderCollapsed: (Boolean) -> Unit = {},
) {
    val scheme = MaterialTheme.colorScheme
    val listState = rememberLazyListState()
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collect { (index, offset) -> onHeaderCollapsed(index > 0 || offset > 56) }
    }
    var query by remember { mutableStateOf("") }
    var catFilter by remember { mutableStateOf("all") }
    val t = monthTotals(repo.store, month)
    val all = repo.store.entries.filter { it.date.startsWith(month) }
    val entries = all
        .filter { catFilter == "all" || it.cat == catFilter }
        .filter { e ->
            query.isBlank() || (e.cat + " " + e.note + " " + (e.amount / 100.0)).lowercase().contains(query.lowercase())
        }
        .sortedWith(
            compareByDescending<Entry> { it.date }
                .thenByDescending { it.loggedAt.ifBlank { it.updated } },
        )
    val cats = remember(repo.store) { repo.store.settings.cats.catsOut }

    // the headline follows the filter: a category view answers "how much on this?" not "how much at all"
    val filtering = catFilter != "all" || query.isNotBlank()
    val catName = cats.firstOrNull { it.id == catFilter }?.name
    val shown = if (filtering) entries.filter { it.type != "income" }.sumOf { it.amount } else t.expense
    val shownLabel = when {
        catFilter != "all" -> "SPENT · ${(catName ?: catFilter).uppercase()}"
        query.isNotBlank() -> "SPENT · ${entries.size} MATCH(ES)"
        else -> "MONEY OUT"
    }
    val expenseCount = all.count { it.type != "income" }
    val entriesByDay = remember(entries) {
        entries.groupBy { it.date }.toSortedMap(compareByDescending { it })
    }

    val envelopes = remember(repo.store, month) { budgetUsage(repo.store, month) }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().padding(horizontal = Space.l),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 148.dp),
    ) {
        item {
            Column {
                Spacer(Modifier.height(Space.m))
                MonthHeader(month, onMonth)
                Spacer(Modifier.height(Space.s))
                Hero(shownLabel, shown, scheme.secondary, 40)
                Text(
                    (if (filtering) {
                        "${entries.size} ${if (entries.size == 1) "purchase" else "purchases"}${catName?.let { " · $it" } ?: ""} of ${t.entryCount} this month"
                    } else {
                        "$expenseCount ${if (expenseCount == 1) "purchase" else "purchases"} this month · no judgement"
                    }),
                    color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                )
                Spacer(Modifier.height(Space.m))
                TextField(
                    value = query, onValueChange = { query = it },
                    placeholder = { Text("Search note, category, amount") },
                    leadingIcon = { Text("⌕", style = MaterialTheme.typography.titleMedium) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                )
                Spacer(Modifier.height(Space.s))
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(Space.s),
                ) {
                    val usedCats = all.map { it.cat }.toSet()
                    val chips = listOf("all" to "All") +
                        cats.filter { it.id in usedCats || it.id == catFilter }.map { it.id to it.name }
                    chips.forEach { (id, label) ->
                        val chipShape = morphChipShape(catFilter == id)
                        FilterChip(
                            selected = catFilter == id,
                            onClick = { catFilter = id },
                            label = { Text(label) },
                            leadingIcon = if (id != "all") {
                                { Icon(categoryIcon(id), contentDescription = null, modifier = Modifier.size(IconSize.inline)) }
                            } else null,
                            shape = chipShape,
                            border = null,
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                            modifier = Modifier.heightIn(min = Control.primary),
                        )
                    }
                }
                Spacer(Modifier.height(Space.m))
                val repeats = remember(all) {
                    all.filter { it.type == "expense" }
                        .distinctBy { it.cat to (it.note.trim().lowercase() to it.amount) }
                        .sortedByDescending { it.loggedAt.ifBlank { it.updated } }
                        .take(3)
                }
                if (repeats.isNotEmpty()) {
                    Text("Repeat last", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.height(Space.xs))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                        repeats.forEach { entry ->
                            val label = (entry.note.ifBlank { categoryLabel(repo.store, entry.cat) }) + " · RM " + rm(entry.amount)
                            Surface(
                                shape = MaterialTheme.shapes.large,
                                color = scheme.surfaceContainerHigh,
                                modifier = Modifier.clickable { onRepeatEntry(entry) },
                            ) {
                                Text(label, color = scheme.onSurface, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = Space.m, vertical = Space.s))
                            }
                        }
                    }
                }
                Spacer(Modifier.height(Space.m))
            }
        }

            if (envelopes.isNotEmpty()) {
                item {
                    Column(Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("ENVELOPES", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            Text("base + carry + moves", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                        }
                        Spacer(Modifier.height(Space.s))
                        envelopes.forEach { b ->
                            val name = repo.store.settings.cats.catsOut.firstOrNull { it.id == b.cat }?.name ?: b.cat
                            val ink = when {
                                b.over -> scheme.error
                                b.fraction >= 0.8 -> scheme.secondary
                                else -> scheme.tertiary
                            }
                            Column(Modifier.fillMaxWidth().padding(vertical = Space.s)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(name, color = scheme.onSurface, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                                    Text(
                                        "RM ${rm(b.spent)} / ${rm(b.limit)}" +
                                            if (b.over) " · RM ${rm(-b.remaining)} over"
                                            else " · RM ${rm(b.remaining)} left",
                                        color = ink, style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                                    )
                                }
                                if (b.carried != 0.0 || b.moved != 0.0) {
                                    Text(
                                        "base RM ${rm(b.base)}" +
                                            (if (b.carried != 0.0) " · carry +RM ${rm(b.carried)}" else "") +
                                            (if (b.moved != 0.0) " · move ${if (b.moved > 0) "+" else "−"}RM ${rm(kotlin.math.abs(b.moved))}" else ""),
                                        color = scheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall,
                                    )
                                }
                                Spacer(Modifier.height(Space.s))
                                Box(
                                    Modifier.fillMaxWidth().height(3.dp).background(scheme.surfaceContainerHigh),
                                ) {
                                    Box(
                                        Modifier.fillMaxWidth(b.fraction.toFloat().coerceIn(0f, 1f))
                                            .fillMaxHeight().background(ink),
                                    )
                                }
                            }
                        }
                    }
                }
            }
            entriesByDay.forEach { (date, dayEntries) ->
                item(key = "day-$date") {
                    val out = dayEntries.filter { it.type != "income" }.sumOf { it.amount }
                    Row(Modifier.fillMaxWidth().padding(top = Space.s), verticalAlignment = Alignment.CenterVertically) {
                        Text("${weekdayShort(date)} ${prettyDate(date)}", color = scheme.onSurface, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                        Spacer(Modifier.weight(1f))
                        Text("RM ${rm(out)} out", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"))
                    }
                }
                items(dayEntries, key = { it.id }) { e ->
                    RowCard(
                        e.note.ifEmpty { categoryLabel(repo.store, e.cat) },
                        buildString {
                            e.odo?.takeIf { it > 0 }?.let { append("${it} km") }
                            loggedTime(e.loggedAt)?.let { if (isNotEmpty()) append(" · "); append("logged $it") }
                        },
                        (if (e.type == "income") "+" else "−") + rm(e.amount),
                        if (e.type == "income") scheme.tertiary else scheme.error,
                        icon = categoryIcon(e.cat),
                        onLongPress = { onEditEntry(e) },
                        onDelete = { onDeleteEntry(e) },
                    )
                }
            }
            if (entries.isEmpty()) item {
                if (query.isNotBlank() || catFilter != "all") {
                    EmptyState("Nothing matches that filter", "Clear the search or choose another category.")
                } else {
                    EmptyState("Nothing recorded this month", "Expenses you log will appear here.")
                }
            }
        }
}

@Composable
private fun ShiftsScreen(
    repo: BakiRepo,
    onAddShift: () -> Unit,
    onEditShift: (Shift) -> Unit,
    onPayouts: (Shift) -> Unit,
    onDeleteShift: (Shift) -> Unit,
    onHeaderCollapsed: (Boolean) -> Unit = {},
) {
    val scheme = MaterialTheme.colorScheme
    val listState = rememberLazyListState()
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collect { (index, offset) -> onHeaderCollapsed(index > 0 || offset > 56) }
    }
    val store = repo.store
    val cfg = store.settings.profit
    val rows = remember(store) {
        val overheadByMonth = store.shifts.groupBy { it.date.take(7) }
            .mapValues { (m, _) -> overheadPerHour(store, m) }
        store.shifts.sortedByDescending { it.date }.map { it to shiftMetrics(it, cfg, overheadByMonth[it.date.take(7)] ?: 0.0) }
    }
    val hours = remember(store) { rows.sumOf { it.first.hours } }
    val net = remember(store) { rows.sumOf { it.second.net } }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().padding(horizontal = Space.l),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 148.dp),
    ) {
        item {
            Column {
                Spacer(Modifier.height(Space.l))
                Hero("ALL-TIME SHIFT NET", cents(net), scheme.onSurface, 40)
                Text(
                    "${rows.size} ${if (rows.size == 1) "shift" else "shifts"} · ${"%.1f".format(hours)}h · RM ${rm(if (hours > 0) net / hours else 0.0)}/hr · all time · tap a shift to edit",
                    color = scheme.onSurfaceVariant, style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(Space.m))
                FilledTonalButton(
                    onClick = onAddShift,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = scheme.primary,
                        contentColor = scheme.onPrimary,
                    ),
                ) { Text("Add shift", style = MaterialTheme.typography.labelLargeEmphasized) }
                Spacer(Modifier.height(Space.l))
            }
        }
        if (rows.isEmpty()) item {
            EmptyState("No shifts yet", "The road is waiting. Add a shift and see what it really paid.")
        }
        items(rows, key = { it.first.id }) { row ->
            val sh = row.first
            val m = row.second
            RowCard(
                "${weekdayShort(sh.date)} ${prettyDate(sh.date)} · ${sh.start}",
                "${"%.1f".format(m.km)} km · ${"%.1f".format(m.hrs)}h" +
                    (m.netHr?.let { " · RM ${rm(it)}/hr" } ?: ""),
                "RM ${rm(m.net)}",
                scheme.onSurface,
                onClick = { onEditShift(sh) },
                onLongPress = { onEditShift(sh) },
                onDelete = { onDeleteShift(sh) },
                actionLabel = if (sh.payouts.isEmpty()) "Add payout" else "Payouts",
                onAction = { onPayouts(sh) },
            )
        }
    }
}

@Composable
private fun BillEditorDialog(
    existing: Bill?,
    categories: List<Cat>,
    scheme: androidx.compose.material3.ColorScheme,
    canDelete: Boolean,
    onSave: (Bill) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var amountDigits by remember { mutableStateOf(existing?.amount?.let(::centsDigits).orEmpty()) }
    var annual by remember { mutableStateOf(existing?.cadence == "annual") }
    var category by remember { mutableStateOf(existing?.category ?: categories.firstOrNull()?.id ?: "other") }
    var optional by remember { mutableStateOf(existing?.optional ?: false) }
    var anchor by remember { mutableStateOf(existing?.anchorMonth?.toString() ?: "1") }
    var dueDay by remember { mutableStateOf(existing?.dueDay?.toString() ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.extraLarge,
        title = { BakiDialogHeader(if (existing == null) "New bill" else "Edit bill", "recurring money", "▣") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it }, singleLine = true,
                    label = { Text("Name") }, modifier = Modifier.fillMaxWidth(), colors = bakiFieldColorsPublic(),
                )
                CentsField(
                    digits = amountDigits,
                    onDigitsChange = { amountDigits = it },
                    label = "Amount RM",
                    prefix = "",
                    colors = bakiFieldColorsPublic(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Monthly", color = scheme.onSurface, style = MaterialTheme.typography.labelLarge)
                    Switch(checked = annual, onCheckedChange = { annual = it })
                    Text("Annual", color = scheme.onSurface, style = MaterialTheme.typography.labelLarge)
                }
                if (annual) {
                    OutlinedTextField(
                        value = anchor,
                        onValueChange = { anchor = it.filter { c -> c.isDigit() }.take(2) },
                        singleLine = true, label = { Text("Renews in month (1-12)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(), colors = bakiFieldColorsPublic(),
                    )
                }
                OutlinedTextField(
                    value = dueDay,
                    onValueChange = { dueDay = it.filter { c -> c.isDigit() }.take(2) },
                    singleLine = true, label = { Text("Due day (optional, 1-31)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(), colors = bakiFieldColorsPublic(),
                )
                Text("Category", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(Space.xs),
                ) {
                    categories.forEach { c ->
                        val on = c.id == category
                        val chipShape = morphChipShape(on)
                        Box(
                            Modifier.clip(chipShape)
                                .background(if (on) scheme.primaryContainer else scheme.surfaceContainerLow)
                                .clickable { category = c.id }
                                .padding(horizontal = Space.m, vertical = Space.xs),
                        ) {
                            Text(c.name, color = if (on) scheme.onPrimaryContainer else scheme.onSurface, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Optional (pay from leftovers)", color = scheme.onSurface, style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.weight(1f))
                    Switch(checked = optional, onCheckedChange = { optional = it })
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val cents = centsFromDigits(amountDigits)
                    onSave(
                        Bill(
                            id = existing?.id ?: "",
                            name = name,
                            amount = cents,
                            cadence = if (annual) "annual" else "monthly",
                            category = category,
                            optional = optional,
                            anchorMonth = if (annual) anchor.toIntOrNull()?.coerceIn(1, 12) ?: 1 else null,
                            dueDay = dueDay.toIntOrNull()?.takeIf { it in 1..31 },
                        ),
                    )
                },
                enabled = name.isNotBlank() && centsFromDigits(amountDigits) > 0L,
            ) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (existing != null) {
                    TextButton(onClick = onDelete, enabled = canDelete) { Text("Delete", color = if (canDelete) scheme.error else scheme.onSurfaceVariant) }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

private enum class MoreTab(val label: String) { MONEY("Money"), SHIFT("Work"), APP("App") }

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MoreTabs(selected: MoreTab, onSelect: (MoreTab) -> Unit) {
    ConnectedChoiceGroup(
        options = MoreTab.entries.map { it.label },
        selected = selected.label,
        onSelect = { label -> MoreTab.entries.firstOrNull { it.label == label }?.let(onSelect) },
        modifier = Modifier.fillMaxWidth(),
    )
}
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MoreHero(tab: MoreTab) {
    val scheme = MaterialTheme.colorScheme
    val (label, title, sub, icon) = when (tab) {
        MoreTab.MONEY -> Quad("MONEY DESK", "Bills, statement, car", "The desk side of the ledger.", BakiIcons.Wallet)
        MoreTab.SHIFT -> Quad("WORK KIT", "Payment QR & service", "Keep the car on the road.", BakiIcons.Car)
        MoreTab.APP -> Quad("BAKI SETUP", "Sync & appearance", "Make Baki feel like yours.", BakiIcons.Phone)
    }
    Surface(shape = MaterialTheme.shapes.extraLarge, color = scheme.primaryContainer) {
        Row(Modifier.fillMaxWidth().padding(Space.m), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(scheme.primary),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, contentDescription = null, tint = scheme.onPrimary, modifier = Modifier.size(IconSize.row)) }
            Spacer(Modifier.width(Space.m))
            Column(Modifier.weight(1f)) {
                Text(label, color = scheme.onPrimaryContainer.copy(alpha = 0.68f), letterSpacing = 1.3.sp, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                Text(title, color = scheme.onPrimaryContainer, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                Text(sub, color = scheme.onPrimaryContainer.copy(alpha = 0.78f), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MoreScreen(repo: BakiRepo, onEditBudgets: () -> Unit, onAddService: () -> Unit = {}, onHeaderCollapsed: (Boolean) -> Unit = {}) {
    val scheme = MaterialTheme.colorScheme
    var url by remember { mutableStateOf(repo.config.serverUrl) }
    var token by remember { mutableStateOf(repo.config.serverToken) }
    var finding by remember { mutableStateOf(false) }
    var foundMsg by remember { mutableStateOf<String?>(null) }
    var foundOk by remember { mutableStateOf(false) }
    var routeKey by remember { mutableStateOf(repo.config.routeSecret) }
    var routeSync by remember { mutableStateOf(repo.config.syncRoutes) }
    var routeSaved by remember { mutableStateOf(false) }
    var editingProfit by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    var linkTarget by remember { mutableStateOf<Pair<Bill, String>?>(null) }
    var editingBill by remember { mutableStateOf<Bill?>(null) }
    var addingBill by remember { mutableStateOf(false) }
    var unlinkTarget by remember { mutableStateOf<Pair<Bill, String>?>(null) }
    var historyOpen by remember { mutableStateOf(false) }
    var statementHistoryOpen by remember { mutableStateOf(false) }
    var diagnosticsOpen by remember { mutableStateOf(false) }
    var serviceTargetOpen by remember { mutableStateOf(false) }
    var serviceBaselineOpen by remember { mutableStateOf(false) }
    var manualOdometerOpen by remember { mutableStateOf(false) }
    var prefillStatement by remember { mutableStateOf<ImportedTxn?>(null) }
    var moreTab by remember { mutableStateOf(MoreTab.MONEY) }
    var pendingRestore by remember { mutableStateOf<String?>(null) }
    var restoreSummary by remember { mutableStateOf<BackupSummary?>(null) }
    var restoreError by remember { mutableStateOf<String?>(null) }
    var backupPassMode by remember { mutableStateOf<String?>(null) }
    var encryptedRestore by remember { mutableStateOf<String?>(null) }
    val moreScroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val undoSnackbar = remember { SnackbarHostState() }
    LaunchedEffect(moreTab) { moreScroll.scrollTo(0) }
    LaunchedEffect(routeSaved) { if (routeSaved) { delay(1800); routeSaved = false } }
    // More is a settings page: keep the tabs reachable while its sections scroll.
    LaunchedEffect(Unit) { onHeaderCollapsed(false) }
    val pickRestore = rememberJsonImport { text ->
        if (text == null) return@rememberJsonImport
        if (isEncryptedBackup(text)) {
            encryptedRestore = text
            backupPassMode = "restore"
            restoreError = null
        } else {
            val summary = summarizeBackup(text)
            if (summary == null) {
                restoreError = "That file is not a Baki backup."
                PlatformEffects.haptic(Haptic.Warn)
            } else {
                restoreError = null
                restoreSummary = summary
                pendingRestore = text
            }
        }
    }
    fun createBackup() {
        scope.launch {
            val json = runCatching { backupBundleJson(repo) }.getOrNull()
            if (json == null) {
                ActionFeedback.show("Backup could not be created")
                return@launch
            }
            repo.markBackupExported(json)
            PlatformEffects.exportFile("baki-${todayStamp()}-backup.json", "application/json", json)
            PlatformEffects.successFeedback()
            ActionFeedback.show("Backup ready")
        }
    }

    fun autoBillPayment(bill: Bill, period: String): Boolean {
        val p = Bills.payment(repo.store, bill, period) ?: return false
        return repo.store.entries.firstOrNull { it.id == p.entryId }?.note == "${bill.name} · $period"
    }

    fun offerBillUndo(message: String, items: List<Triple<String, String, Boolean>>) {
        if (items.isEmpty()) return
        scope.launch {
            val result = undoSnackbar.showSnackbar(message, actionLabel = "Undo", duration = SnackbarDuration.Long)
            if (result == SnackbarResult.ActionPerformed) {
                items.forEach { (billId, period, auto) -> repo.undoBillPayment(billId, period, auto) }
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = Space.l),
    ) {
        // The More header and its tabs stay put; only the settings body scrolls.
        MoreTabs(moreTab, onSelect = { moreTab = it })
        Spacer(Modifier.height(Space.s))
        MoreHero(moreTab)
        Spacer(Modifier.height(Space.s))
        Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(moreScroll)) {
        if (moreTab == MoreTab.SHIFT) {
        SectionTitle("PAYMENT QR")
        Spacer(Modifier.height(Space.s))
        PaymentQrSection()
        Spacer(Modifier.height(Space.l))
        SectionTitle("SERVICE")
        Spacer(Modifier.height(Space.s))
        ServiceCard(
            repo,
            onSetTarget = { serviceTargetOpen = true },
            onResetBaseline = { serviceBaselineOpen = true },
            onAddService = onAddService,
        )
        Spacer(Modifier.height(Space.l))
        SectionTitle("VEHICLE ECONOMY")
        Spacer(Modifier.height(Space.s))
        VehicleEconomyCard(repo.store)
        Spacer(Modifier.height(Space.l))
        SectionTitle("VEHICLE WEAR")
        Spacer(Modifier.height(Space.s))
        VehicleWearCard(repo.store, onAddReading = { manualOdometerOpen = true })
        if (manualOdometerOpen) {
            ManualOdometerDialog(
                onSave = { date, km ->
                    manualOdometerOpen = false
                    scope.launch {
                        if (repo.addOdometerReading(date, km)) ActionFeedback.show("Odometer reading saved")
                    }
                },
                onDismiss = { manualOdometerOpen = false },
            )
        }
        if (serviceTargetOpen) {
            val info = remember(repo.store) { serviceInfo(repo.store) }
            ServiceTargetDialog(
                initialKm = info.targetKm,
                initialDate = info.targetOn,
                onDismiss = { serviceTargetOpen = false },
                onSave = { km, date ->
                    serviceTargetOpen = false
                    scope.launch { repo.setServiceTarget(km, date) }
                },
            )
        }
        if (serviceBaselineOpen) {
            ServiceBaselineDialog(
                initialKm = repo.store.settings.serviceOdoBase,
                initialDate = repo.store.settings.serviceOdoBaseOn,
                onSave = { km, date ->
                    serviceBaselineOpen = false
                    scope.launch { repo.setServiceBaseline(km, date) }
                },
                onDismiss = { serviceBaselineOpen = false },
            )
        }
        }

        if (moreTab == MoreTab.APP) {
        SectionTitle("SYNC")
        Spacer(Modifier.height(Space.s))
        Column(Modifier.fillMaxWidth().padding(vertical = Space.m)) {
            Text("Server address", color = scheme.onSurface, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold) )
            Text("where the tracker server is running", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium )
            Spacer(Modifier.height(Space.m))
            OutlinedTextField(
                value = url, onValueChange = { url = it }, singleLine = true,
                modifier = Modifier.fillMaxWidth(), colors = bakiFieldColorsPublic(),
            )
            Spacer(Modifier.height(Space.s))
            OutlinedTextField(
                value = token, onValueChange = { token = it }, singleLine = true,
                label = { Text("API token (optional)") },
                supportingText = { Text("Only needed when the server runs with BAKI_TOKEN set.", color = scheme.onSurfaceVariant) },
                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(), colors = bakiFieldColorsPublic(),
            )
            Spacer(Modifier.height(Space.m))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                Button( shape = CircleShape,
                    onClick = { repo.setServer(url, token) },
                    colors = ButtonDefaults.buttonColors(containerColor = scheme.primary, contentColor = scheme.onPrimary),
                ) { Text("Save & sync") }
                FilledTonalButton(onClick = { scope.launch { repo.sync() } }) { Text("Sync now") }
            }
            TextButton(
                onClick = {
                    if (finding) return@TextButton
                    foundMsg = null
                    finding = true
                    scope.launch {
                        val found = discoverBakiServer()
                        finding = false
                        if (found != null) {
                            url = found.url
                            foundOk = true
                            foundMsg = "Found ${found.host}:${found.port}"
                            repo.setServer(found.url, token)
                            PlatformEffects.successFeedback()
                        } else {
                            foundOk = false
                            foundMsg = "No Baki server found on this Wi-Fi"
                        }
                    }
                },
                enabled = !finding,
                contentPadding = PaddingValues(horizontal = 0.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (finding) {
                        LoadingIndicator(
                            modifier = Modifier.size(18.dp),
                            color = scheme.primary,
                        )
                        Spacer(Modifier.width(Space.xs))
                    }
                    Text(
                        if (finding) "Searching this Wi-Fi…" else "Find server on this Wi-Fi",
                        color = if (finding) scheme.onSurfaceVariant else scheme.primary,
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    )
                }
            }
            foundMsg?.let {
                Text(it, color = if (foundOk) scheme.tertiary else scheme.error, style = MaterialTheme.typography.bodyMedium )
            }
            repo.lastError?.let {
                Spacer(Modifier.height(Space.s))
                Text(it, color = scheme.error, style = MaterialTheme.typography.bodyMedium )
            }
        }
        Spacer(Modifier.height(Space.m))
        Column(Modifier.fillMaxWidth().padding(vertical = Space.m)) {
            Text("SYNC HEALTH", color = scheme.primary, letterSpacing = 1.4.sp, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
            Spacer(Modifier.height(Space.s))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.m)) {
                Box(
                    Modifier.size(9.dp).clip(CircleShape)
                        .background(if (repo.pendingPush) scheme.secondary else if (repo.status == "synced") scheme.tertiary else scheme.onSurfaceVariant),
                )
                Text(
                    when {
                        repo.readOnly -> "read-only · no uploads"
                        repo.pendingPush -> "pending upload · retries automatically"
                        repo.status == "synced" -> "synced"
                        repo.status == "offline" -> "offline · local copy"
                        else -> repo.status
                    },
                    color = scheme.onSurface,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                )
            }
            Text(
                "Last pull ${repo.lastSyncAt?.let { ago(nowMillis(), it) } ?: "never"} · " +
                    "last upload ${repo.lastPushAt?.let { ago(nowMillis(), it) } ?: "never"}",
                color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium,
            )
            if (repo.lastMergeConflicts > 0) {
                Spacer(Modifier.height(Space.s))
                Text(
                    "${repo.lastMergeConflicts} record(s) changed on both sides. Newest edit wins; deletions win. Review the affected month after syncing.",
                    color = scheme.secondary, style = MaterialTheme.typography.bodyMedium,
                )
            }
            Text(
                "Conflict policy: newest edit per record wins, tombstones stay deleted, and the phone remains the writer. Pull always runs before a push. Pending local settings are uploaded before a remote settings block can replace them.",
                color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(Space.s))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                TextButton(
                    onClick = { scope.launch { repo.sync() } },
                    contentPadding = PaddingValues(horizontal = 0.dp),
                ) { Text("Pull & merge", color = scheme.primary, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)) }
                if (repo.pendingPush && !repo.readOnly) TextButton(
                    onClick = { scope.launch { repo.sync() } },
                    contentPadding = PaddingValues(horizontal = 0.dp),
                ) { Text("Retry upload", color = scheme.secondary, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)) }
            }
        }
        Spacer(Modifier.height(Space.m))
        Column(Modifier.fillMaxWidth().padding(vertical = Space.m)) {
            Text("Route traces", color = scheme.onSurface, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold))
            Text(
                "Sealed with a key only your devices know, so the desk can review your drives. The server never sees coordinates.",
                color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(Space.m))
            SwitchRow(
                title = "Sync route traces",
                sub = if (routeSync) "on — recordings are uploaded sealed" else "off — recordings stay on this phone",
                checked = routeSync,
            ) {
                routeSync = it
                repo.setRouteSync(it, routeKey)
                routeSaved = true
                PlatformEffects.successFeedback()
            }
            Spacer(Modifier.height(Space.m))
            OutlinedTextField(
                value = routeKey, onValueChange = { routeKey = it }, singleLine = true,
                label = { Text("shared route key") },
                modifier = Modifier.fillMaxWidth(), colors = bakiFieldColorsPublic(),
            )
            Spacer(Modifier.height(Space.m))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                Button( shape = CircleShape,
                    onClick = {
                        repo.setRouteSync(routeSync, routeKey)
                        routeSaved = true
                        PlatformEffects.successFeedback()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = scheme.primary, contentColor = scheme.onPrimary),
                ) { Text("Save route key") }
                if (routeSaved) Text("Saved ✓", color = scheme.tertiary, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
            }
        }
        }

        if (moreTab == MoreTab.MONEY) {
        val statementRows = repo.store.importedTxns
        val matchedRows = statementRows.count { it.status == "matched" }
        val pendingRows = statementRows.count { it.status == "pending" }
        SectionTitle("STATEMENT RECONCILIATION")
        Spacer(Modifier.height(Space.s))
        Text(
            "Statements are imported and matched on Desk. The phone ledger is unchanged; only the reconciliation summary appears here.",
            color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium,
        )
        if (statementRows.isEmpty()) {
            Spacer(Modifier.height(Space.s))
            Text("No statement batches yet — open Desk → Statements.", color = scheme.onSurface, style = MaterialTheme.typography.bodyMedium)
        } else {
            Spacer(Modifier.height(Space.m))
            val currentMonth = todayIso().take(7)
            if (repo.store.monthCloses.containsKey(currentMonth)) {
                Text("✓ ${monthLabel(currentMonth)} closed", color = scheme.tertiary, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
            }
            Text(
                "$matchedRows matched · $pendingRows unrecorded",
                color = if (pendingRows > 0) scheme.secondary else scheme.tertiary,
                style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum", fontWeight = FontWeight.Bold),
            )
            repo.store.accounts.filter { acc -> !acc.archived && statementRows.any { it.accountId == acc.id } }.forEach { acc ->
                val rows = statementRows.filter { it.accountId == acc.id }
                val open = rows.count { it.status == "pending" }
                Text(
                    "${acc.name}: ${rows.count { it.status == "matched" }} matched · $open open",
                    color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall,
                )
            }
            statementRows.filter { it.status == "pending" && !it.transfer }.take(5).forEach { row ->
                Row(Modifier.fillMaxWidth().padding(vertical = Space.xs), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${row.date} · ${if (row.direction == "in") "+" else "−"}RM ${rm(row.amount / 100.0)}",
                        color = scheme.onSurface, style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"), modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = { prefillStatement = row },
                        contentPadding = PaddingValues(0.dp),
                        modifier = Modifier.testTag("statement-prefill"),
                    ) { Text("Add", color = scheme.primary) }
                }
            }
            Spacer(Modifier.height(Space.xs))
            Text(
                "Review and match the full list on Desk. Missing rows are still added manually on the phone.",
                color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall,
            )
        }
        Spacer(Modifier.height(Space.l))
        SectionTitle("BILLS")
        Spacer(Modifier.height(Space.s))
        Column(Modifier.fillMaxWidth().padding(vertical = Space.m)) {
            val current = Bills.monthPeriod(todayIso())
            val target = Bills.nextOpenMonth(repo.store, current)
            val committed = Bills.due(repo.store, current)
            val covered = committed.count { Bills.isCovered(repo.store, it, Bills.periodOf(it, current)) }
            if (repo.store.bills.isEmpty()) {
                Text("No bills loaded yet", color = scheme.onSurface, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold))
                Text(
                    "Bills sync from the server. Tap Sync above if they stay empty.",
                    color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                val committedTotal = Bills.committed(repo.store, current)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "${monthLabel(current)} · RM ${rm(committedTotal / 100.0)} committed",
                            color = scheme.onSurface, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                        )
                        Text(
                            "$covered of ${committed.size} covered · average RM ${rm(Bills.averageMonthly(repo.store) / 100.0)}/month incl. annual",
                            color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    TextButton(onClick = { historyOpen = true }) { Text("History", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.labelLarge) }
                    TextButton(onClick = { addingBill = true }) { Text("Add", color = scheme.primary, style = MaterialTheme.typography.labelLarge) }
                }
            }
            Text(
                "Cover before month end; covered months roll to the next.",
                color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(Space.m))
            committed.forEach { bill ->
                val period = Bills.periodOf(bill, current)
                val isPaid = Bills.isCovered(repo.store, bill, period)
                Row(Modifier.fillMaxWidth().padding(vertical = Space.s), verticalAlignment = Alignment.CenterVertically) {
                    Column(
                        Modifier.weight(1f).heightIn(min = Control.primary).clickable { editingBill = bill },
                    ) {
                        Text(bill.name, color = scheme.onSurface, style = MaterialTheme.typography.labelLarge)
                        Text(
                            "RM ${rm(bill.amount / 100.0)} · ${if (bill.cadence == "annual") "annual" else "monthly"}",
                            color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                        )
                    }
                    if (isPaid) {
                        Text(
                            "covered ${Bills.paidOn(repo.store, bill, period) ?: ""}",
                            color = scheme.tertiary, style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.heightIn(min = Control.primary).clickable { unlinkTarget = bill to period },
                        )
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                            FilledTonalButton(
                                enabled = !repo.readOnly,
                                onClick = {
                                    scope.launch {
                                        if (repo.payBill(bill.id, period)) {
                                            offerBillUndo(
                                                "Marked ${bill.name} paid",
                                                listOf(Triple(bill.id, period, autoBillPayment(bill, period))),
                                            )
                                        } else {
                                            ActionFeedback.show("Could not mark paid — check writes and sync")
                                        }
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = Space.m, vertical = Space.xs),
                                modifier = Modifier.testTag("bill-mark-paid-${bill.id}"),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = scheme.primary,
                                    contentColor = scheme.onPrimary,
                                ),
                            ) { Text("Mark paid") }
                            TextButton(
                                enabled = !repo.readOnly,
                                onClick = { linkTarget = bill to period },
                                contentPadding = PaddingValues(horizontal = Space.m, vertical = Space.xs),
                            ) { Text("Link") }
                        }
                    }
                }
            }
            Spacer(Modifier.height(Space.s))
            if (repo.readOnly) {
                Text(
                    "Read-only mode. Enable writes in More → App to mark bills paid.",
                    color = scheme.secondary, style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(Space.s))
            }
            if (repo.store.bills.isNotEmpty()) FilledTonalButton(
                onClick = {
                    scope.launch {
                        val pending = Bills.unpaid(repo.store, target).map { it.id to Bills.periodOf(it, target) }
                        val n = repo.settleBills(target)
                        if (n > 0) offerBillUndo("Settled $n bill${if (n == 1) "" else "s"}", pending.map { Triple(it.first, it.second, true) })
                    }
                },
                enabled = !repo.readOnly && Bills.unpaid(repo.store, target).isNotEmpty(),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = scheme.primaryContainer,
                    contentColor = scheme.onPrimaryContainer,
                ),
            ) { Text("Settle all for ${monthLabel(target)}") }

            // optional goals: only when the month actually leaves something over
            val net = monthTotals(repo.store, current).net
            repo.store.bills.filter { it.optional }.forEach { bill ->
                Spacer(Modifier.height(Space.m))
                val paid = Bills.isCovered(repo.store, bill, Bills.periodOf(bill, current))
                Text(
                    "leftover this month: RM ${rm(net / 100.0)}",
                    color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                )
                if (paid) {
                    Text("${bill.name} covered", color = scheme.tertiary, style = MaterialTheme.typography.bodyMedium)
                } else {
                    FilledTonalButton(
                        onClick = {
                            scope.launch {
                                val period = Bills.periodOf(bill, current)
                                if (repo.payBill(bill.id, period)) {
                                    offerBillUndo("Marked ${bill.name} paid", listOf(Triple(bill.id, period, autoBillPayment(bill, period))))
                                }
                            }
                        },
                        enabled = !repo.readOnly && net >= bill.amount,
                    ) { Text("Pay ${bill.name} · RM ${rm(bill.amount / 100.0)}") }
                }
            }
        }
            if (historyOpen) {
                val rows = Bills.history(repo.store)
                AlertDialog(
                    onDismissRequest = { historyOpen = false },
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    title = { BakiDialogHeader("Bill payments", "settle the month", "✓") },
                    text = {
                        Column(
                            Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                        ) {
                            if (rows.isEmpty()) {
                                Text("Nothing paid yet. Marking a bill paid records a payment here.", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                            }
                            rows.forEach { p ->
                                Row(Modifier.fillMaxWidth().padding(vertical = Space.xs), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text("${p.billName} · ${p.period}", color = scheme.onSurface, style = MaterialTheme.typography.labelLarge)
                                        Text(
                                            "paid ${p.paidAt}" +
                                                when {
                                                    !p.active -> " · unlinked"
                                                    !p.entryExists -> " · expense deleted"
                                                    else -> ""
                                                },
                                            color = if (p.active && p.entryExists) scheme.onSurfaceVariant else scheme.error,
                                            style = MaterialTheme.typography.bodyMedium,
                                        )
                                    }
                                    Text("RM ${rm(p.amount / 100.0)}", color = scheme.onSurface, style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum", fontWeight = FontWeight.Bold))
                                    if (p.entryExists) {
                                        TextButton(
                                            onClick = {
                                                scope.launch {
                                                    if (repo.deleteEntry(p.entryId)) {
                                                        historyOpen = false
                                                        ActionFeedback.show("Deleted ${p.billName} expense")
                                                    } else {
                                                        ActionFeedback.show("Could not delete — enable Allow writes")
                                                    }
                                                }
                                            },
                                            contentPadding = PaddingValues(horizontal = Space.xs),
                                        ) { Text("Delete", color = scheme.error, style = MaterialTheme.typography.labelMedium) }
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = { TextButton(onClick = { historyOpen = false }) { Text("Close") } },
                )
            }
            unlinkTarget?.let { (bill, period) ->
                val link = Bills.payment(repo.store, bill, period)
                AlertDialog(
                    onDismissRequest = { unlinkTarget = null },
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    title = { BakiDialogHeader("Unlink ${bill.name} · $period", "keep the ledger honest", "−") },
                    text = {
                        Text(
                            "Only the covered mark is removed. The RM ${rm((link?.amount ?: bill.amount) / 100.0)} expense stays in your ledger.",
                            color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium,
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            scope.launch { repo.unlinkBillPayment(bill.id, period) }
                            unlinkTarget = null
                        }) { Text("Unlink", color = scheme.error) }
                    },
                    dismissButton = { TextButton(onClick = { unlinkTarget = null }) { Text("Cancel") } },
                )
            }
            if (addingBill || editingBill != null) {
                val existing = editingBill
                BillEditorDialog(
                    existing = existing,
                    categories = repo.store.settings.cats.catsOut,
                    scheme = scheme,
                    canDelete = existing != null && !repo.readOnly,
                    onSave = { bill ->
                        scope.launch { repo.saveBill(bill) }
                        addingBill = false
                        editingBill = null
                    },
                    onDelete = {
                        existing?.let { b -> scope.launch { repo.deleteBill(b.id) } }
                        editingBill = null
                    },
                    onDismiss = { addingBill = false; editingBill = null },
                )
            }
            linkTarget?.let { (bill, period) ->
                val candidates = Bills.linkCandidates(repo.store, bill, period)
                AlertDialog(
                    onDismissRequest = { linkTarget = null },
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    title = { BakiDialogHeader("Link ${bill.name} · $period", "attach a payment", "+") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                            if (candidates.isEmpty()) {
                                Text(
                                    "No entry in that month matches this bill's amount or category. Use Mark paid to record a new one.",
                                    color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium,
                                )
                            } else {
                                Text("Choose the entry that already paid this bill:", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                                candidates.take(6).forEach { entry ->
                                    TextButton(
                                        onClick = {
                                            scope.launch {
                                                repo.linkBillPayment(bill.id, period, entry.id)
                                                linkTarget = null
                                            }
                                        },
                                        contentPadding = PaddingValues(0.dp),
                                    ) {
                                        Text(
                                            "${entry.date} · RM ${rm(entry.amount / 100.0)} · ${entry.note.ifBlank { entry.cat }}",
                                            color = scheme.onSurface, style = MaterialTheme.typography.labelLarge,
                                        )
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = { TextButton(onClick = { linkTarget = null }) { Text("Cancel") } },
                )
            }
        Spacer(Modifier.height(Space.l))
        SectionTitle("WEEKLY STATEMENT")
        Spacer(Modifier.height(Space.s))
        Column(Modifier.fillMaxWidth().padding(vertical = Space.m)) {
            Text("Import the weekly PDF", color = scheme.onSurface, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold))
            Text(
                "Import the weekly PDF to reconcile income and insurance.",
                color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(Space.m))
            var importMsg by remember { mutableStateOf<String?>(null) }
            val pickStatement = rememberStatementImport { files ->
                if (files.isNullOrEmpty()) return@rememberStatementImport
                scope.launch {
                    var imported = 0
                    var skipped = 0
                    files.forEach { bytes ->
                        val parsed = withContext(Dispatchers.Default) {
                            PdfText.extract(bytes)?.let { StatementParse.parse(it) }
                        }
                        if (parsed == null) skipped++ else {
                            repo.saveStatement(parsed)
                            imported++
                        }
                    }
                    importMsg = when {
                        imported == 0 -> "None of those looked like Grab statements."
                        skipped == 0 -> "Imported $imported statement${if (imported == 1) "" else "s"}."
                        else -> "Imported $imported · skipped $skipped."
                    }
                }
            }
            FilledTonalButton(onClick = { pickStatement() }) { Text("Import statement PDFs", style = MaterialTheme.typography.labelLargeEmphasized) }
            Spacer(Modifier.height(Space.xs))
            val weeks = repo.store.statements.size
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (weeks == 1) "1 week on file" else "$weeks weeks on file",
                    color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                )
                if (weeks > 0) {
                    TextButton(onClick = { statementHistoryOpen = true }, contentPadding = PaddingValues(0.dp)) {
                        Text(" · View all weeks", color = scheme.primary, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold) )
                    }
                }
            }
            importMsg?.let {
                Spacer(Modifier.height(Space.s))
                Text(it, color = if (it.startsWith("Imported")) scheme.tertiary else scheme.error, style = MaterialTheme.typography.bodyMedium)
            }

            val latest = repo.store.statements.values.maxByOrNull { it.weekStart }
            if (latest != null) {
                Spacer(Modifier.height(Space.m))
                Text(
                    "${latest.rangeText.ifBlank { latest.weekStart }}",
                    color = scheme.onSurface, style = MaterialTheme.typography.labelLarge,
                )
                Text(
                    "income RM ${rm(latest.income)} · deductions RM ${rm(latest.deductions)} · weekly RM ${rm(latest.weekly)}",
                    color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                )
                val check = statementCheck(repo.store, latest)
                val gap = check.loggedGross / 100.0 - latest.income
                if (check.loggedShifts == 0) {
                    Text(
                        "no shifts logged for this week (Grab RM ${rm(latest.income)})",
                        color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                    )
                } else {
                    Text(
                        "logged ${check.loggedShifts} shifts · RM ${rm(check.loggedGross / 100.0)} · Grab RM ${rm(latest.income)} · gap RM ${rm(gap)}",
                        color = if (kotlin.math.abs(gap) < 1.0) scheme.tertiary else scheme.secondary,
                        style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                    )
                }
                Text(
                    when {
                        !check.insuranceKnown -> "insurance: statement count not recorded — re-import the PDF"
                        check.insuranceMatches -> "insurance: ${check.statementInsurance} charges match"
                        else -> "insurance: app ${check.appInsurance} vs statement ${check.statementInsurance} — check the cover rule"
                    },
                    color = if (check.insuranceKnown && check.insuranceMatches) scheme.tertiary else scheme.secondary,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    if (repo.store.statementMarks[latest.weekStart]?.reconciled == true) "statement reconciled"
                    else "statement not reconciled · open history to review",
                    color = if (repo.store.statementMarks[latest.weekStart]?.reconciled == true) scheme.tertiary else scheme.secondary,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        }

        if (moreTab == MoreTab.APP) {
        SectionTitle("SAFETY")
        Spacer(Modifier.height(Space.s))
        SwitchRow(
            title = "Allow writes",
            sub = if (repo.readOnly) "off — this app only reads your data" else "on — changes here reach the real ledger",
            checked = !repo.readOnly,
            warn = !repo.readOnly,
            onChange = { repo.setAllowWrites(it) },
        )
        Spacer(Modifier.height(Space.l))
        SectionTitle("APPEARANCE")
        Spacer(Modifier.height(Space.s))
        ConnectedChoiceGroup(
            options = listOf("Auto", "Light", "Dark"),
            selected = when (repo.config.themeMode) {
                "light" -> "Light"
                "dark" -> "Dark"
                else -> "Auto"
            },
            onSelect = { choice ->
                repo.setThemeMode(when (choice) {
                    "Light" -> "light"
                    "Dark" -> "dark"
                    else -> "auto"
                })
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(Space.s))
        if (supportsDynamicColor()) {
            Text(
                "Colour source",
                color = scheme.onSurface, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            )
            Spacer(Modifier.height(Space.xs))
            ConnectedChoiceGroup(
                options = listOf("System", "Baki"),
                selected = if (repo.config.colorSource == "baki") "Baki" else "System",
                onSelect = { repo.setColorSource(if (it == "Baki") "baki" else "wallpaper") },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                if (repo.config.colorSource == "baki") "Roast & Pine: espresso, matcha and terracotta."
                else "System uses Material You. Roast & Pine is the fixed fallback.",
                color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium,
            )
            if (repo.config.colorSource == "baki") {
                Spacer(Modifier.height(Space.s))
                SwitchRow(
                    title = "High contrast",
                    sub = if (repo.config.highContrast) "stronger text and outlines" else "standard contrast",
                    checked = repo.config.highContrast,
                ) { repo.setHighContrast(it) }
            }
        } else {
            Text(
                "Baki fixed colours are in use — dynamic colour isn't available on this device.",
                color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium,
            )
        }
        Spacer(Modifier.height(Space.l))
        SectionTitle("MOTION")
        Spacer(Modifier.height(Space.s))
        SwitchRow(
            title = "Reduce motion",
            sub = if (repo.config.reducedMotion) "on — standard springs, less overshoot"
            else "off — expressive springs and shape changes",
            checked = repo.config.reducedMotion,
            onChange = { repo.setReducedMotion(it) },
        )
        Text(
            "Android also applies the system animation scale.",
            color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium,
        )
        }

        if (moreTab == MoreTab.MONEY) {
        SectionTitle("VEHICLE & TARGET")
        Spacer(Modifier.height(Space.s))
        val p = repo.store.settings.profit
        Column(Modifier.fillMaxWidth().padding(vertical = Space.m)) {
            Text("RM ${rm(p.fuelPrice)}/L · ${"%.1f".format(p.kmPerLitre)} km/L · wear RM ${rm(p.wearPerKm)}/km",
                color = scheme.onSurface, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold) )
            Text("target RM ${rm(p.targetRate)}/hr · avg ${"%.0f".format(p.avgSpeed)} km/h · cover RM ${rm(p.grabInsurance)}",
                color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium )
            if (repo.store.settings.gpsCorrection != 1.0) {
                Text("GPS correction ×${"%.2f".format(repo.store.settings.gpsCorrection)} (from your odometer)",
                    color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium )
            }
            // real economy, measured from fill-ups that carry an odometer reading
            measuredEconomy(repo.store)?.let { m ->
                Spacer(Modifier.height(Space.s))
                Text(
                    "measured ${"%.1f".format(m.kml)} km/L over ${"%.0f".format(m.km)} km · ${m.fills} fills " +
                        "(${m.from} → ${m.to}) · budget uses ${"%.2f".format(p.kmPerLitre)}",
                    color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium )
                if (kotlin.math.abs(m.kml - p.kmPerLitre) > 0.3) {
                    TextButton(
                        onClick = { scope.launch { repo.updateProfit(p.copy(kmPerLitre = m.kml)) } },
                        contentPadding = PaddingValues(0.dp),
                    ) {
                        Text(
                            "Use ${"%.1f".format(m.kml)} km/L instead of ${"%.1f".format(p.kmPerLitre)}",
                            color = scheme.primary, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold) )
                    }
                }
            }
            Spacer(Modifier.height(Space.m))
            Button(
                onClick = { editingProfit = true },
                enabled = !repo.readOnly,
                colors = ButtonDefaults.buttonColors(containerColor = scheme.primaryContainer, contentColor = scheme.onPrimaryContainer),
                shape = CircleShape,
            ) { Text("Edit costs") }
        }
        SectionTitle("CATEGORIES")
        Spacer(Modifier.height(Space.s))
        CategoriesCard(repo, editable = !repo.readOnly)
        Spacer(Modifier.height(Space.l))
        SectionTitle("BUDGETS")
        Spacer(Modifier.height(Space.s))
        Column(Modifier.fillMaxWidth().padding(vertical = Space.m)) {
            Text("Monthly limits", color = scheme.onSurface, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold) )
            val n = repo.store.settings.budgets.size
            Text(
                if (n == 0) "none set — add caps for food, fuel, tolls"
                else "$n categories capped · shown on Spending",
                color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium )
            Spacer(Modifier.height(Space.s))
            Button(
                onClick = { onEditBudgets() },
                enabled = !repo.readOnly,
                colors = ButtonDefaults.buttonColors(containerColor = scheme.primaryContainer, contentColor = scheme.onPrimaryContainer),
                shape = CircleShape,
            ) { Text("Set limits") }
        }
        }

        if (moreTab == MoreTab.APP) {
        SectionTitle("ABOUT & DEVELOPER")
        Spacer(Modifier.height(Space.s))
        Text("Baki $APP_VERSION · the ledger stays yours.", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        val healthWear = remember(repo.store) { odometerWear(repo.store) }
        Text(
            "Odometer coverage: ${healthWear.anchors} anchors / ${healthWear.intervals} intervals · last backup ${repo.config.lastBackupAt?.let { ago(nowMillis(), it) } ?: "never"} · last sync ${repo.lastSyncAt?.let { ago(nowMillis(), it) } ?: "never"}",
            color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall,
        )
        TextButton(onClick = { diagnosticsOpen = !diagnosticsOpen }, contentPadding = PaddingValues(0.dp)) {
            Text(if (diagnosticsOpen) "Hide diagnostics" else "Diagnostics & support", color = scheme.primary, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
        }
        if (diagnosticsOpen) {
        Spacer(Modifier.height(Space.s))
        Column(Modifier.fillMaxWidth().padding(vertical = Space.m)) {
            val h = healthOf(repo)
            val reliability = remember(diagnosticsOpen) { PlatformEffects.reliabilityStatus() }
            var routeStats by remember { mutableStateOf<Pair<Int, Int>?>(null) }
            LaunchedEffect(h.entries, h.shifts) {
                routeStats = runCatching { RouteStore.stats() }.getOrNull()
            }
            val now = nowMillis()
            val rows = buildList {
                add("Sync" to when {
                    h.status == "syncing" -> "syncing…"
                    h.lastSyncAt == null -> "${h.status} · never"
                    else -> "${h.status} · ${ago(now, h.lastSyncAt)}"
                })
                add("Upload" to when {
                    h.readOnly -> "read-only"
                    h.pendingPush -> "pending — retries on next sync"
                    h.lastPushAt == null -> "never"
                    else -> "ok · ${ago(now, h.lastPushAt)}"
                })
                StoreHealth.loadError?.let { add("Local store" to it) }
                StoreHealth.saveError?.let { add("Local save" to it) }
                add("Notifications" to when {
                    !reliability.notificationsAllowed -> "blocked — enable Baki notifications"
                    reliability.notificationActive -> "allowed · live notification active"
                    else -> "allowed · no live notification"
                })
                add("Battery" to if (reliability.batteryUnrestricted) "unrestricted"
                    else "restricted — long shifts may be killed")
                add("GPS" to when {
                    h.tracker.provider.contains("desktop") -> "unavailable on desktop"
                    h.tracker.provider == "—" || h.tracker.fixes == 0 ->
                        "idle — tracking starts with a live shift"
                    else -> "${h.tracker.provider} · ${h.tracker.fixes} fixes · " +
                        "${h.tracker.rejected} rejected · ${ago(now, h.tracker.lastFixAt)}"
                })
                if (h.live != null) {
                    val paused = isPaused(h.live)
                    add("Live" to (if (paused) "paused" else "running") + " · ${"%.1f".format(h.live.km)} km · ${ago(now, h.live.start)}")
                }
                if (h.gpsCorrection != 1.0) add("Correction" to "×${"%.2f".format(h.gpsCorrection)}")
                routeStats?.let { stats ->
                    add(
                        "Routes" to if (stats.first == 0) "none recorded"
                        else "${stats.first} path(s) · ${stats.second} points",
                    )
                }
            }
            rows.forEachIndexed { index, (k, v) ->
                Row(Modifier.fillMaxWidth().padding(vertical = Space.s), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(k, color = scheme.onSurfaceVariant, style = MaterialTheme.typography.labelLarge)
                    Text(v, color = scheme.onSurface, style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum", fontWeight = FontWeight.Bold), modifier = Modifier.weight(1f).padding(start = Space.m), textAlign = TextAlign.End)
                }
            }
            if (!reliability.batteryUnrestricted) {
                TextButton(onClick = { PlatformEffects.openBatterySettings() }, contentPadding = PaddingValues(0.dp)) {
                    Text("Open battery settings", color = scheme.primary, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                }
            }
            h.lastError?.let { err ->
                Spacer(Modifier.height(Space.s))
                Text("last error: $err", color = scheme.error, style = MaterialTheme.typography.bodyMedium, maxLines = 3)
            }
            Spacer(Modifier.height(Space.m))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                val clipboard = LocalClipboardManager.current
                TextButton(
                    onClick = {
                        val report = buildString {
                            appendLine("Baki $APP_VERSION diagnostics")
                            appendLine("status: ${h.status}")
                            appendLine("sync: ${ago(now, h.lastSyncAt)}  upload: ${ago(now, h.lastPushAt)} pending=${h.pendingPush}")
                            appendLine("notifications: allowed=${reliability.notificationsAllowed} active=${reliability.notificationActive}")
                            appendLine("battery unrestricted=${reliability.batteryUnrestricted}")
                            appendLine("gps: ${h.tracker.provider} fixes=${h.tracker.fixes} rejected=${h.tracker.rejected}")
                            appendLine("live: ${if (h.live != null) "yes" else "no"}  correction=${h.gpsCorrection}")
                            appendLine("records: ${h.entries} entries, ${h.shifts} shifts, readOnly=${h.readOnly}")
                            appendLine("error: ${h.lastError ?: "none"}")
                        }
                        clipboard.setText(AnnotatedString(report))
                    },
                    contentPadding = PaddingValues(0.dp),
                ) { Text("Copy report", color = scheme.primary, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)) }
                TextButton(onClick = { scope.launch { repo.sync() } }, contentPadding = PaddingValues(0.dp)) {
                    Text("Sync now", color = scheme.primary, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                }
            }
        }
        }
        }

        if (moreTab == MoreTab.APP) {
        SectionTitle("BACKUP")
        Spacer(Modifier.height(Space.s))
        Column(Modifier.fillMaxWidth().padding(vertical = Space.s)) {
            val last = repo.config.lastBackupAt
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(9.dp).clip(CircleShape).background(if (last == null) scheme.error else scheme.tertiary))
                Spacer(Modifier.width(Space.s))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (last == null) "No portable backup yet" else "Last backup ${ago(nowMillis(), last)}",
                        color = scheme.onSurface,
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                    )
                    Text(
                        if (last == null) "Create one before changing phones or resetting this device."
                        else "${repo.config.lastBackupEntries} entries · ${repo.config.lastBackupShifts} shifts · ${repo.config.lastBackupBytes / 1024} KB",
                        color = scheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            Spacer(Modifier.height(Space.m))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                Button(
                    onClick = { createBackup() },
                    colors = ButtonDefaults.buttonColors(containerColor = scheme.primary, contentColor = scheme.onPrimary),
                    shape = CircleShape, modifier = Modifier.weight(1f),
                ) { Text("Backup now", style = MaterialTheme.typography.labelLarge ) }
                Button(
                    onClick = { pickRestore() },
                    colors = ButtonDefaults.buttonColors(containerColor = scheme.primaryContainer, contentColor = scheme.onPrimaryContainer),
                    shape = CircleShape, modifier = Modifier.weight(1f),
                ) { Text("Restore backup", style = MaterialTheme.typography.labelLarge ) }
            }
            Spacer(Modifier.height(Space.s))
            Button(
                onClick = { backupPassMode = "create" },
                colors = ButtonDefaults.buttonColors(containerColor = scheme.surfaceContainerHigh, contentColor = scheme.onSurface),
                shape = CircleShape, modifier = Modifier.fillMaxWidth(),
            ) { Text("Encrypted backup", style = MaterialTheme.typography.labelLarge) }
            Spacer(Modifier.height(Space.s))
            Text(
                "Backup includes the ledger, device settings, calibration and local routes. Restore merges into this device, never deletes, and keeps writes off. The file contains private server and route-key data; store it securely.",
                color = scheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
            restoreError?.let {
                Spacer(Modifier.height(Space.s))
                Text(it, color = scheme.error, style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(Space.s))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                Button(
                    onClick = { PlatformEffects.exportFile("baki-${todayStamp()}.csv", "text/csv", exportCsv(repo.store)) },
                    colors = ButtonDefaults.buttonColors(containerColor = scheme.surfaceContainerHigh, contentColor = scheme.onSurface),
                    shape = CircleShape, modifier = Modifier.weight(1f),
                ) { Text("Export CSV", style = MaterialTheme.typography.labelLarge ) }
            }
        }
        Spacer(Modifier.height(Space.s))
        Button(
            onClick = {
                scope.launch {
                    val from = addDays(todayIso(), -30)
                    val routes = runCatching { RouteStore.routesSince(from) }.getOrDefault(emptyList())
                    if (routes.isNotEmpty()) {
                        PlatformEffects.exportFile(
                            "scheme-routes-${todayStamp()}.gpx",
                            "application/gpx+xml",
                            exportGpx(from, routes),
                        )
                    }
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = scheme.primaryContainer, contentColor = scheme.onPrimaryContainer),
            shape = CircleShape, modifier = Modifier.fillMaxWidth(),
        ) { Text("Export routes (GPX · 30 days)", style = MaterialTheme.typography.labelLarge ) }
        Spacer(Modifier.height(Space.l))
        SectionTitle("HOME SCREEN")
        Spacer(Modifier.height(Space.s))
        Text(
            "The live shift widget mirrors the clock and payouts. Add it from the launcher; it never writes ledger records.",
            color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(Space.s))
        Button(
            onClick = { PlatformEffects.requestLiveWidget() },
            colors = ButtonDefaults.buttonColors(containerColor = scheme.primaryContainer, contentColor = scheme.onPrimaryContainer),
            shape = CircleShape, modifier = Modifier.fillMaxWidth(),
        ) { Text("Add the live widget", style = MaterialTheme.typography.labelLarge ) }
        Spacer(Modifier.height(Space.s))
        Button(
            onClick = { confirmReset = true },
            colors = ButtonDefaults.buttonColors(containerColor = scheme.surfaceContainerHigh, contentColor = scheme.error),
            shape = CircleShape, modifier = Modifier.fillMaxWidth(),
        ) { Text("Reset local device", style = MaterialTheme.typography.labelLarge ) }
        Spacer(Modifier.height(Space.l))
        SectionTitle("ON THIS DEVICE")
        Spacer(Modifier.height(Space.s))
        RowCard("Store", LocalStore.path(), "", scheme.onSurfaceVariant)
        Spacer(Modifier.height(Space.s))
        RowCard("Records", "${repo.store.entries.size} entries · ${repo.store.shifts.size} shifts", "", scheme.onSurfaceVariant)
        Spacer(Modifier.height(Space.s))
        RowCard("Status", repo.status, "", scheme.onSurfaceVariant)
        }
        // keep the last card of every tab clear of the bottom bar / overlay
        Spacer(Modifier.height(140.dp))
        }
    }
    SnackbarHost(undoSnackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 96.dp))
    }

    prefillStatement?.let { row ->
        val account = repo.store.accounts.firstOrNull { it.id == row.accountId }?.name ?: "Statement"
        var amount by remember(row.id) { mutableStateOf(centsDigits(row.amount)) }
        var date by remember(row.id) { mutableStateOf(row.date) }
        var category by remember(row.id) { mutableStateOf(statementCategoryHint(row.description)) }
        var note by remember(row.id) { mutableStateOf("$account · ${row.description}".take(160)) }
        AlertDialog(
            onDismissRequest = { prefillStatement = null },
            title = { Text(if (row.direction == "in") "Add statement income" else "Add statement expense") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
                    CentsField(amount, { amount = it }, label = "Amount (RM)", prefix = "", modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(date, { date = it }, label = { Text("Date (YYYY-MM-DD)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(category, { category = it }, label = { Text("Category") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(note, { note = it }, label = { Text("Note") }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val cents = centsFromDigits(amount)
                    if (cents > 0) {
                        scope.launch {
                            val entryId = repo.addEntry(if (row.direction == "in") "income" else "expense", cents, category, note, date)
                            if (entryId != null) repo.matchImported(row.id, entryId)
                            prefillStatement = null
                        }
                    }
                }) { Text("Save on phone") }
            },
            dismissButton = { TextButton(onClick = { prefillStatement = null }) { Text("Cancel") } },
        )
    }

    if (statementHistoryOpen) {
        StatementHistorySheet(repo = repo, onDismiss = { statementHistoryOpen = false })
    }

    backupPassMode?.let { mode ->
        BackupPassphraseDialog(
            title = if (mode == "create") "Create encrypted backup" else "Open encrypted backup",
            confirmLabel = if (mode == "create") "Create" else "Open",
            onConfirm = { passphrase ->
                backupPassMode = null
                if (mode == "create") {
                    scope.launch {
                        val json = runCatching { backupBundleJson(repo) }.getOrNull()
                        val sealed = json?.let { encryptBackup(it, passphrase) }
                        if (json != null && sealed != null) {
                            repo.markBackupExported(json)
                            PlatformEffects.exportFile("baki-${todayStamp()}-backup.bak", "application/octet-stream", sealed)
                            PlatformEffects.successFeedback()
                            ActionFeedback.show("Encrypted backup ready")
                        } else {
                            restoreError = "Could not create encrypted backup"
                        }
                    }
                } else {
                    val text = encryptedRestore
                    val decoded = text?.let { decryptBackup(it, passphrase) }
                    val summary = decoded?.let { summarizeBackup(it) }
                    if (text == null || decoded == null || summary == null) {
                        restoreError = "Wrong passphrase or damaged encrypted backup"
                        encryptedRestore = null
                        PlatformEffects.haptic(Haptic.Warn)
                    } else {
                        encryptedRestore = null
                        restoreSummary = summary
                        pendingRestore = decoded
                    }
                }
            },
            onDismiss = { backupPassMode = null; encryptedRestore = null },
        )
    }

    restoreSummary?.let { summary ->
        AlertDialog(
            onDismissRequest = { restoreSummary = null; pendingRestore = null },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = MaterialTheme.shapes.extraLarge,
            title = { BakiDialogHeader("Restore this backup?", "merge · never delete", "↺") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
                    Text(
                        "Exported ${summary.exportedAt.ifBlank { "unknown time" }}",
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                    )
                    Text(
                        "${summary.entries} entries · ${summary.shifts} shifts · ${summary.bills} bills · ${summary.statements} statements · ${summary.routes} routes",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "This merges into this device. Existing records with the same id follow the newest edit; nothing is deleted. " +
                            (if (summary.hasDeviceConfig) "The backup also carries device settings, but writes stay off. " else "") +
                            "A backup copy of the current file is still recommended.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val text = pendingRestore
                        restoreSummary = null
                        pendingRestore = null
                        if (text != null) scope.launch {
                            val added = repo.importJson(text)
                            PlatformEffects.successFeedback()
                            ActionFeedback.show(if (added > 0) "Restored · $added new records" else "Backup merged · no new records")
                        }
                    },
                ) { Text("Restore", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { restoreSummary = null; pendingRestore = null }) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
        )
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = {
                BakiDialogHeader(
                    "Reset this device?",
                    "local data only",
                    "!",
                    background = scheme.errorContainer,
                    ink = scheme.onErrorContainer,
                )
            },
            text = { Text("The local copy is forgotten and pulled again from the server. The server is not touched.", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.labelLarge ) },
            confirmButton = {
                TextButton(onClick = {
                    confirmReset = false
                    scope.launch { repo.resetLocal() }
                }) { Text("Reset", color = scheme.error, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel", color = scheme.onSurfaceVariant) } },
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

@OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SwitchRow(
    title: String,
    sub: String,
    checked: Boolean,
    warn: Boolean = false,
    onChange: (Boolean) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().padding(vertical = Space.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(if (warn) scheme.secondary else scheme.primary),
            )
            Spacer(Modifier.width(Space.s))
            Column {
                Text(title, color = scheme.onSurface, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold) )
                Text(sub, color = if (warn) scheme.secondary else scheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = scheme.onPrimary, checkedTrackColor = scheme.primary,
                uncheckedThumbColor = scheme.onSurfaceVariant, uncheckedTrackColor = scheme.surfaceContainerLow,
                uncheckedBorderColor = scheme.onSurfaceVariant,
            ),
        )
    }
}

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalSharedTransitionApi::class,
    ExperimentalLayoutApi::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)
@Composable
private fun EntryDialog(
    existing: Entry?,
    initialType: String,
    settings: Settings,
    day: String,
    initialCat: String? = null,
    initialAmount: String? = null,
    initialNote: String? = null,
    onDismiss: () -> Unit,
    onSave: (String, Long, String, String, String, Int?) -> Unit,
    onDelete: (Entry) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    var type by remember { mutableStateOf(existing?.type ?: initialType) }
    var amountDigits by remember { mutableStateOf(existing?.amount?.let(::centsDigits) ?: initialCentsDigits(initialAmount)) }
    var note by remember { mutableStateOf(existing?.note ?: initialNote ?: "") }
    var date by remember { mutableStateOf(existing?.date ?: day) }
    val dateValid = remember(date) { isValidIsoDate(date) }
    var odo by remember { mutableStateOf(existing?.odo?.toString() ?: "") }
    // Grab insurance is derived from the shift timeline; manual entry would double-charge it.
    val cats = (if (type == "income") settings.cats.catsIn else settings.cats.catsOut)
        .filterNot { isDerivedCategory(it.id) }
    var cat by remember(type) { mutableStateOf(existing?.cat ?: initialCat ?: cats.firstOrNull()?.id ?: "other") }
    val cents = centsFromDigits(amountDigits)

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
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(scheme.tertiaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (type == "income") "+" else "−",
                        color = scheme.onTertiaryContainer,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    )
                }
                Spacer(Modifier.width(Space.m))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (existing == null) "LOG MONEY" else "EDIT MONEY",
                        color = scheme.onSurface,
                        modifier = headerMod,
                        letterSpacing = 1.sp,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    )
                    Text(
                        if (type == "income") "money in. nice." else "money out. it happens.",
                        color = scheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
                ConnectedChoiceGroup(
                    options = listOf("Spending", "Income"),
                    selected = if (type == "expense") "Spending" else "Income",
                    onSelect = { choice ->
                        type = if (choice == "Spending") "expense" else "income"
                        cat = if (type == "expense") {
                            settings.cats.catsOut.firstOrNull()?.id ?: "other"
                        } else {
                            settings.cats.catsIn.firstOrNull()?.id ?: "otherin"
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                CentsField(
                    digits = amountDigits,
                    onDigitsChange = { amountDigits = it },
                    label = "HOW MUCH?",
                    isError = amountDigits.isNotBlank() && cents <= 0,
                    supportingText = if (amountDigits.isNotBlank() && cents <= 0) "Enter an amount greater than zero" else null,
                    textStyle = MaterialTheme.typography.displaySmallEmphasized.copy(fontFeatureSettings = "tnum"),
                    shape = MaterialTheme.shapes.extraLarge,
                    filled = true,
                    contentDescription = "Amount in Malaysian ringgit",
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = scheme.secondaryContainer,
                        unfocusedContainerColor = scheme.secondaryContainer,
                        focusedTextColor = scheme.onSecondaryContainer,
                        unfocusedTextColor = scheme.onSecondaryContainer,
                        focusedLabelColor = scheme.onSecondaryContainer.copy(alpha = 0.72f),
                        unfocusedLabelColor = scheme.onSecondaryContainer.copy(alpha = 0.72f),
                        cursorColor = scheme.onSecondaryContainer,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Space.s),
                    verticalArrangement = Arrangement.spacedBy(Space.s),
                ) {
                    cats.forEach { c ->
                        val on = c.id == cat
                        val chipShape = morphChipShape(on)
                        Row(
                            Modifier
                                .heightIn(min = Control.primary)
                                .clip(chipShape)
                                .background(if (on) scheme.primaryContainer else scheme.surfaceContainerHigh)
                                .clickable { cat = c.id }
                                .padding(horizontal = Space.m, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Space.xs),
                        ) {
                            Icon(
                                categoryIcon(c.id),
                                contentDescription = null,
                                tint = if (on) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
                                modifier = Modifier.size(IconSize.inline),
                            )
                            Text(
                                c.name,
                                color = if (on) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = note, onValueChange = { note = it },
                    label = { Text("Note (optional)") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(), colors = bakiFieldColors(),
                )
                // fuel needs it for km/L; maintenance needs it to tie the service to the car
                if (cat == "fuel" || cat == "maintenance" || odo.isNotEmpty()) {
                    val service = cat == "maintenance"
                    OutlinedTextField(
                        value = odo,
                        onValueChange = { odo = it.filter { c -> c.isDigit() } },
                        label = {
                            Text(if (service) "Odometer at service (optional)" else "Odometer at fill-up (optional)")
                        }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(), colors = bakiFieldColors(),
                        supportingText = {
                            Text(
                                if (service) "Keeps the service tied to the car's mileage"
                                else "Add this and the app measures your real km/L",
                                color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium,
                            )
                        },
                    )
                }
                OutlinedTextField(
                    value = date, onValueChange = { date = it.filter { c -> c.isDigit() || c == '-' }.take(10) },
                    label = { Text("Date (YYYY-MM-DD)") }, singleLine = true,
                    isError = date.isNotEmpty() && !dateValid,
                    supportingText = if (date.isNotEmpty() && !dateValid) {
                        { Text("Use a real date in YYYY-MM-DD format", color = scheme.error) }
                    } else null,
                    modifier = Modifier.fillMaxWidth(), colors = bakiFieldColors(),
                )
            if (existing != null) {
                TextButton(onClick = { onDelete(existing) }) {
                    Text("Delete", color = scheme.error, fontWeight = FontWeight.Bold)
                }
            }
            Button(
                enabled = cents > 0 && dateValid,
                onClick = { onSave(type, cents, cat, note, date, odo.toIntOrNull()) },
                colors = ButtonDefaults.buttonColors(containerColor = scheme.primary, contentColor = scheme.onPrimary),
                shape = CircleShape,
                modifier = Modifier.fillMaxWidth().height(50.dp),
            ) {
                Text(
                    if (existing == null) "Log it · RM ${rm(cents)}" else "Save it", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold) )
            }
        }
}


@Composable
private fun PairFields(
    first: @Composable (Modifier) -> Unit,
    second: @Composable (Modifier) -> Unit,
) {
    BoxWithConstraints {
        val compact = maxWidth < 340.dp || LocalDensity.current.fontScale > 1.15f
        if (compact) {
            Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
                first(Modifier.fillMaxWidth())
                second(Modifier.fillMaxWidth())
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                first(Modifier.weight(1f))
                second(Modifier.weight(1f))
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FormSection(label: String) {
    val scheme = MaterialTheme.colorScheme
    Spacer(Modifier.height(Space.s))
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s)) {
        Box(
            Modifier
                .size(14.dp)
                .clip(CircleShape)
                .background(scheme.primary),
        )
        Text(label, color = scheme.onSurfaceVariant, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
    }
    Spacer(Modifier.height(Space.s))
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ShiftDialog(
    store: Store,
    existing: Shift?,
    onDismiss: () -> Unit,
    onPayouts: (Shift) -> Unit = {},
    onSave: (String, String, Double, Double, Long, Long, Double?, Double?, Double?, Boolean) -> Unit,
    onDelete: (Shift) -> Unit,
    onCalibrate: (Double) -> Unit = {},
) {
    val scheme = MaterialTheme.colorScheme
    var date by remember { mutableStateOf(existing?.date ?: todayIso()) }
    var start by remember { mutableStateOf(existing?.start ?: "") }
    var hours by remember { mutableStateOf(existing?.hours?.takeIf { it > 0 }?.let { "%.1f".format(it) } ?: "") }
    var km by remember { mutableStateOf(existing?.km?.takeIf { it > 0 }?.let { "%.1f".format(it) } ?: "") }
    var gross by remember { mutableStateOf(existing?.gross?.takeIf { it > 0 }?.let(::centsDigits).orEmpty()) }
    var extra by remember { mutableStateOf(existing?.extra?.takeIf { it > 0 }?.let(::centsDigits).orEmpty()) }
    var reimbursed by remember { mutableStateOf(existing?.reimbursedTolls ?: false) }
    var kml by remember { mutableStateOf(existing?.kmPerLitre?.let { "%.1f".format(it) } ?: "") }
    var odoA by remember { mutableStateOf(existing?.odoStart?.let { "%.0f".format(it) } ?: "") }
    var odoB by remember { mutableStateOf(existing?.odoEnd?.let { "%.0f".format(it) } ?: "") }
    val odoStart = odoA.toDoubleOrNull()
    val odoEnd = odoB.toDoubleOrNull()
    val dateValid = remember(date) { isValidIsoDate(date) }
    val startValid = remember(start) { isValidClock(start) }

    val grossCents = centsFromDigits(gross)
    val hoursValue = hours.toDoubleOrNull() ?: 0.0
    val kmValue = km.toDoubleOrNull() ?: 0.0
    val kmlValue = kml.toDoubleOrNull()?.takeIf { it > 0 }

    val preview = remember(date, start, hoursValue, kmValue, grossCents, kmlValue, odoStart, odoEnd, extra, reimbursed) {
        val charge = existing?.grabFee ?: draftCoverCharge(store, date, start)
        val probe = Shift(
            id = "__preview", date = date, start = start, hours = hoursValue, km = kmValue,
            gross = grossCents, extra = centsFromDigits(extra),
            reimbursedTolls = reimbursed,
            grabFee = charge, kmPerLitre = kmlValue, odoStart = odoStart, odoEnd = odoEnd,
        )
        shiftMetrics(probe, store.settings.profit, overheadPerHour(store, date.take(7)))
    }

    BakiSheet(onDismiss = onDismiss) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(scheme.tertiaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(BakiIcons.Car, contentDescription = null, tint = scheme.onTertiaryContainer, modifier = Modifier.size(IconSize.bar))
                }
                Spacer(Modifier.width(Space.m))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (existing == null) "LOG A SHIFT" else "EDIT SHIFT",
                        color = scheme.onSurface,
                        letterSpacing = 1.sp,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    )
                    Text("count the real cost.", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                }
            }
                FormSection("WHEN")
                OutlinedTextField(
                    value = date,
                    onValueChange = { date = it.filter { c -> c.isDigit() || c == '-' }.take(10) },
                    label = { Text("Date (YYYY-MM-DD)") },
                    singleLine = true,
                    isError = date.isNotEmpty() && !dateValid,
                    supportingText = if (date.isNotEmpty() && !dateValid) {
                        { Text("Use a real date in YYYY-MM-DD format", color = scheme.error) }
                    } else null,
                    modifier = Modifier.fillMaxWidth(), colors = bakiFieldColors(),
                )
                PairFields(
                    first = { m -> OutlinedTextField(
                        value = start,
                        onValueChange = { start = it.filter { c -> c.isDigit() || c == ':' }.take(5) },
                        label = { Text("Start HH:MM") },
                        singleLine = true,
                        isError = start.isNotEmpty() && !startValid,
                        supportingText = if (start.isNotEmpty() && !startValid) {
                            { Text("Use 00:00–23:59", color = scheme.error) }
                        } else null,
                        modifier = m, colors = bakiFieldColors(),
                    ) },
                    second = { m -> OutlinedTextField(value = hours, onValueChange = { hours = it.filter { c -> c.isDigit() || c == '.' } },
                        label = { Text("Hours") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = m, colors = bakiFieldColors()) },
                )
                FormSection("PAY & DISTANCE")
                PairFields(
                    first = { m -> OutlinedTextField(value = km, onValueChange = { km = it.filter { c -> c.isDigit() || c == '.' } },
                        label = { Text("Km") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = m, colors = bakiFieldColors()) },
                    second = { m -> CentsField(digits = gross, onDigitsChange = { gross = it }, label = "Payout (RM)", prefix = "", modifier = m, colors = bakiFieldColors()) },
                )
                PairFields(
                    first = { m -> CentsField(digits = extra, onDigitsChange = { extra = it }, label = "Tolls (RM)", prefix = "", modifier = m, colors = bakiFieldColors()) },
                    second = { m -> OutlinedTextField(value = kml, onValueChange = { kml = it.filter { c -> c.isDigit() || c == '.' } },
                        label = { Text("km/L (opt)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = m, colors = bakiFieldColors()) },
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Tolls reimbursed", color = scheme.onSurface, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold) )
                        Text("Grab pays these back; excluded from running cost",
                            color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium )
                    }
                    Switch(checked = reimbursed, onCheckedChange = { reimbursed = it })
                }
                FormSection("ODOMETER")
                PairFields(
                    first = { m -> OutlinedTextField(value = odoA, onValueChange = { odoA = it.filter { c -> c.isDigit() || c == '.' } },
                        label = { Text("Odometer start") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = m, colors = bakiFieldColors()) },
                    second = { m -> OutlinedTextField(value = odoB, onValueChange = { odoB = it.filter { c -> c.isDigit() || c == '.' } },
                        label = { Text("Odometer end") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = m, colors = bakiFieldColors()) },
                )
                if (odoStart != null && odoEnd != null && odoEnd >= odoStart) {
                    Text("odometer says ${"%.1f".format(odoEnd - odoStart)} km — this wins over the Km field",
                        color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium )
                }
                // reconciliation: GPS vs odometer on the same shift
                val tracked = existing?.km?.takeIf { it > 0.5 }
                if (tracked != null && odoStart != null && odoEnd != null && odoEnd > odoStart) {
                    val odo = odoEnd - odoStart
                    val ratio = odo / tracked
                    Text(
                        "GPS tracked ${"%.1f".format(tracked)} km · odometer ${"%.1f".format(odo)} km · ×${"%.2f".format(ratio)}",
                        color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium )
                    if (ratio in 0.5..2.0) {
                        TextButton(onClick = { onCalibrate(ratio) }) {
                            Text(
                                "Use ×${"%.2f".format(ratio)} for future tracking",
                                color = scheme.primary, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold) )
                        }
                    }
                }
                Spacer(Modifier.height(Space.s))
                Surface(
                    shape = MaterialTheme.shapes.extraLarge,
                    color = scheme.secondaryContainer,
                    contentColor = scheme.onSecondaryContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Box {
                        Column(Modifier.padding(Space.m)) {
                            Text(
                                "ESTIMATED TAKE",
                                color = scheme.onSecondaryContainer.copy(alpha = 0.76f),
                                letterSpacing = 1.2.sp,
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            )
                            Text(
                                "RM ${rm(preview.net)}",
                                color = scheme.onSecondaryContainer,
                                style = MaterialTheme.typography.displaySmallEmphasized.copy(fontFeatureSettings = "tnum"),
                            )
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text("Cover RM ${rm(preview.grabFee)}", color = scheme.onSecondaryContainer.copy(alpha = 0.72f), style = MaterialTheme.typography.bodyMedium)
                                Spacer(Modifier.weight(1f))
                                preview.netHr?.let { rate ->
                                    Text("RM ${rm(rate)}/hr", color = scheme.onSecondaryContainer, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                                }
                            }
                            preview.grossPerKm?.let { perKm ->
                                Text("RM ${rm(perKm)}/km gross", color = scheme.onSecondaryContainer.copy(alpha = 0.72f), style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
                val rate = preview.netHr
                if (rate != null && hoursValue > 0 && rate < store.settings.profit.targetRate) {
                    val needed = grossToClearTarget(preview, store.settings.profit.targetRate)
                    Text(
                        "Below RM ${rm(store.settings.profit.targetRate)}/hr" +
                            (needed?.let { " · need RM ${rm(it)} gross" } ?: ""),
                        color = scheme.error, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    )
                }
            if (existing != null) {
                val truth = payoutTruth(existing)
                TextButton(onClick = { onPayouts(existing) }) {
                    Text(
                        if (truth.count == 0) "Add payout"
                        else "Payouts (${truth.count}) · RM ${rm(truth.received)} received",
                        color = scheme.primary, fontWeight = FontWeight.Bold,
                    )
                }
                TextButton(onClick = { onDelete(existing) }) {
                    Text("Delete shift", color = scheme.error, fontWeight = FontWeight.Bold)
                }
            }
            Button(
                enabled = grossCents > 0 && hoursValue > 0 && dateValid && startValid,
                onClick = { onSave(date, start, hoursValue, kmValue, grossCents, centsFromDigits(extra), kmlValue, odoStart, odoEnd, reimbursed) },
                colors = ButtonDefaults.buttonColors(containerColor = scheme.primary, contentColor = scheme.onPrimary),
                shape = CircleShape,
                modifier = Modifier.fillMaxWidth().height(50.dp),
            ) { Text(if (existing == null) "Save shift" else "Save changes", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold) ) }
        }
}

@Composable
private fun ProfitDialog(profit: Profit, onDismiss: () -> Unit, onSave: (Profit) -> Unit) {
    val scheme = MaterialTheme.colorScheme
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
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.extraLarge,
        title = { BakiDialogHeader("Vehicle costs & target", "the car's real numbers", "◎") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
                ProfitField("Fuel price RM/L", fuel, scheme)
                ProfitField("Fuel economy km/L", kml, scheme)
                ProfitField("Wear RM/km", wear, scheme)
                ProfitField("Target RM/hr", target, scheme)
                ProfitField("Average speed km/h", speed, scheme)
                ProfitField("Grab insurance RM/day", cover, scheme)
                Spacer(Modifier.height(Space.xs))
                Text("Fixed costs, spread over the hours you drive", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium )
                ProfitField("Car loan RM/month", loan, scheme)
                ProfitField("Road tax RM/year", roadTax, scheme)
                ProfitField("Car insurance RM/year", carIns, scheme)
                Text("Saving re-derives every shift's cost, cover and overhead.", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium )
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
            }) { Text("Save", color = scheme.primary, fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = scheme.onSurfaceVariant) } },
    )
}

@Composable
private fun ProfitField(label: String, state: androidx.compose.runtime.MutableState<String>, scheme: androidx.compose.material3.ColorScheme) {
    OutlinedTextField(
        value = state.value,
        onValueChange = { state.value = it.filter { c -> c.isDigit() || c == '.' } },
        label = { Text(label) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(), colors = bakiFieldColors(),
    )
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AddPayoutDialog(onDismiss: () -> Unit, onAdd: (Long) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    var amountDigits by remember { mutableStateOf("") }
    val cents = centsFromDigits(amountDigits)
    BakiSheet(onDismiss = onDismiss) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(scheme.tertiaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("+", color = scheme.onTertiaryContainer, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                }
                Spacer(Modifier.width(Space.m))
                Column {
                    Text("PAYOUT", color = scheme.onSurface, letterSpacing = 1.sp, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                    Text("log the cash.", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                }
            }
            CentsField(
                digits = amountDigits,
                onDigitsChange = { amountDigits = it },
                label = "HOW MUCH?",
                isError = amountDigits.isNotBlank() && cents <= 0,
                supportingText = if (amountDigits.isNotBlank() && cents <= 0) "Enter an amount greater than zero" else null,
                textStyle = MaterialTheme.typography.displaySmallEmphasized.copy(fontFeatureSettings = "tnum"),
                shape = MaterialTheme.shapes.extraLarge,
                filled = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = scheme.secondaryContainer,
                    unfocusedContainerColor = scheme.secondaryContainer,
                    focusedTextColor = scheme.onSecondaryContainer,
                    unfocusedTextColor = scheme.onSecondaryContainer,
                    focusedLabelColor = scheme.onSecondaryContainer.copy(alpha = 0.72f),
                    unfocusedLabelColor = scheme.onSecondaryContainer.copy(alpha = 0.72f),
                    cursorColor = scheme.onSecondaryContainer,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
            )
            Button(
                enabled = cents > 0, onClick = { onAdd(cents) },
                colors = ButtonDefaults.buttonColors(containerColor = scheme.primary, contentColor = scheme.onPrimary),
                shape = CircleShape,
                modifier = Modifier.fillMaxWidth().height(50.dp),
            ) { Text("Add RM ${rm(cents)}", fontWeight = FontWeight.Bold) }
        }
}



@Composable
private fun BudgetSheet(
    repo: BakiRepo,
    onDismiss: () -> Unit,
    onSave: (Long?, Map<String, Double>, Boolean) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val cats = remember(repo.store) { repo.store.settings.cats.catsOut }.filter { it.id != "grabfee" }
    val draft = remember {
        mutableStateMapOf<String, String>().apply {
            repo.store.settings.budgets.forEach { (k, v) -> this[k] = centsDigits((v * 100).roundToLong()) }
        }
    }
    var foodCap by remember {
        mutableStateOf(repo.store.settings.dailyFoodCapCents?.let(::centsDigits) ?: "")
    }
    var carry by remember { mutableStateOf(repo.store.settings.envelopeCarry) }
    var moveOpen by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    val canWrite = !repo.readOnly

    BakiSheet(
        onDismiss = onDismiss,
        footer = {
            Button(
                enabled = canWrite && !saving,
                onClick = {
                    if (saving) return@Button
                    val limits = draft.mapNotNull { (k, v) ->
                        centsFromDigits(v).takeIf { it > 0L }?.let { k to it / 100.0 }
                    }.toMap()
                    val cap = centsFromDigits(foodCap).takeIf { it > 0L }
                    saving = true
                    onSave(cap, limits, carry)
                },
                colors = ButtonDefaults.buttonColors(containerColor = scheme.primary, contentColor = scheme.onPrimary),
                shape = CircleShape,
                modifier = Modifier.fillMaxWidth().height(50.dp),
            ) { Text(if (saving) "Saving…" else "Save limits", fontWeight = FontWeight.Bold) }
        },
    ) {
        BakiDialogHeader("Monthly envelopes", "give every ringgit a job", "▦")
        if (!canWrite) Text("Read-only · enable writes in More → Allow writes", color = scheme.secondary, style = MaterialTheme.typography.bodyMedium)
        Text("Leave a field blank for no envelope. Base allocation applies every month.", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(Space.s))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Carry unspent forward", color = scheme.onSurface, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                Text("Positive remainder rolls into next month.", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            }
            Switch(checked = carry, onCheckedChange = { carry = it })
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Daily food fund", color = scheme.onSurface, modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold) )
            CentsField(
                digits = foodCap,
                onDigitsChange = { foodCap = it },
                placeholder = "—",
                prefix = "",
                modifier = Modifier.width(110.dp),
                colors = bakiFieldColors(),
            )
        }
        Text(
            "This is the Food fund shown on Today. The Food row below is a separate monthly envelope; " +
                "setting one does not set the other.",
            color = scheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(Space.s))
        cats.forEachIndexed { index, c ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = Space.s)) {
                Icon(categoryIcon(c.id, c.name), contentDescription = null, tint = scheme.primary, modifier = Modifier.size(IconSize.inline))
                Spacer(Modifier.width(Space.s))
                Text(c.name, color = scheme.onSurface, modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelLarge )
                CentsField(
                    digits = draft[c.id] ?: "",
                    onDigitsChange = { draft[c.id] = it },
                    placeholder = "—",
                    prefix = "",
                    modifier = Modifier.width(96.dp),
                    colors = bakiFieldColors(),
                )
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Move money between envelopes for this month.", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = { moveOpen = true }, enabled = cats.size > 1 && canWrite) {
                Text("Move", color = scheme.primary, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
            }
        }
    }
    if (moveOpen) {
        EnvelopeMoveDialog(repo, todayIso().take(7), cats.map { it.id to it.name }, onDismiss = { moveOpen = false })
    }
}

@Composable
private fun EnvelopeMoveDialog(
    repo: BakiRepo,
    month: String,
    cats: List<Pair<String, String>>,
    onDismiss: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    var from by remember { mutableStateOf(cats.firstOrNull()?.first ?: "") }
    var to by remember { mutableStateOf(cats.getOrNull(1)?.first ?: "") }
    var amount by remember { mutableStateOf("") }
    val value = centsFromDigits(amount) / 100.0
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.extraLarge,
        title = { BakiDialogHeader("Move envelope money", "virtual buckets", "↔") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
                Text("From", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.titleSmall)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                    cats.forEach { (id, name) ->
                        val on = from == id
                        Text(
                            name, color = if (on) scheme.primary else scheme.onSurfaceVariant,
                            modifier = Modifier.heightIn(min = Control.primary).clickable { from = id }.semantics { selected = on; role = Role.Tab },
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
                Text("To", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.titleSmall)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                    cats.forEach { (id, name) ->
                        val on = to == id
                        Text(
                            name, color = if (on) scheme.primary else scheme.onSurfaceVariant,
                            modifier = Modifier.heightIn(min = Control.primary).clickable { to = id }.semantics { selected = on; role = Role.Tab },
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
                CentsField(
                    digits = amount,
                    onDigitsChange = { amount = it },
                    label = "Amount (RM)",
                    prefix = "",
                    modifier = Modifier.fillMaxWidth(),
                    colors = bakiFieldColors(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = from.isNotBlank() && to.isNotBlank() && from != to && value > 0.0,
                onClick = {
                    scope.launch {
                        try {
                            repo.moveEnvelope(month, from, to, value)
                        } finally {
                            onDismiss()
                        }
                    }
                },
            ) { Text("Move", color = scheme.primary, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = scheme.onSurfaceVariant) } },
    )
}


@Composable
private fun ActionFeedbackOverlay(modifier: Modifier = Modifier) {
    val message = ActionFeedback.message
    LaunchedEffect(message) {
        if (message != null) {
            delay(1800)
            ActionFeedback.clear(message)
        }
    }
    AnimatedVisibility(
        visible = message != null,
        enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.slideInVertically { it / 3 },
        exit = androidx.compose.animation.fadeOut(),
        modifier = modifier.padding(horizontal = Space.xl, vertical = Space.s),
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            shadowElevation = 3.dp,
        ) {
            Row(
                Modifier.padding(horizontal = Space.m, vertical = Space.s),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.s),
            ) {
                Text("✓", color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                Text(message.orEmpty(), color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
