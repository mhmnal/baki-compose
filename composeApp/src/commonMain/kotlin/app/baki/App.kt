package app.baki

import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.LaunchedEffect
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import androidx.compose.animation.animateColorAsState
import kotlin.math.abs
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
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith

import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.shadow
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
import com.kyant.backdrop.backdrops.layerBackdrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun App(repo: BakiRepo = remember { BakiRepo() }) {
    // install the typefaces before anything composes text
    val (textFace, displayFace, readoutFace) = bakiFontFamilies()
    BakiType.install(textFace, displayFace, readoutFace, paper = false)

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
    // keep the Wednesday nudge in sync with the device setting
    LaunchedEffect(repo.config.statementReminder) {
        PlatformEffects.setStatementReminder(repo.config.statementReminder)
    }
    BakiPrivacy.discreet = repo.config.discreet
    LaunchedEffect(repo.config.paperThemeMigrated) {
        repo.ensurePaperTheme()
    }
    BakiTheme(matchWallpaper = repo.config.matchWallpaper, mode = repo.config.themeMode, pack = repo.config.palette) {
        // a desktop gets a desktop: rail, wide canvas, tables — not a stretched phone
        if (!PhonePreview.active && Caps.isDesktop && DesktopRoot(repo)) return@BakiTheme
        SharedTransitionLayout {
            androidx.compose.runtime.CompositionLocalProvider(LocalSharedTransition provides this) {
                AppContent(repo)
            }
        }
    }
}

/** Which shift's payouts the sheet is showing. */
private data class FuelPrompt(val entryId: String, val label: String, val cents: Long)

private sealed interface PayoutsTarget {
    data object Live : PayoutsTarget
    data class Stored(val shiftId: String) : PayoutsTarget
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AppContent(repo: BakiRepo) {
    // read the palette *inside* the theme provider, or every colour falls back to dark
    val baki = LocalBaki.current
    var tab by remember { mutableStateOf(PhonePreview.initialTab) }
    var adding by remember { mutableStateOf(0) }   // 0 none, 2 entry, 3 shift, 4 payout
    var addMenuOpen by remember { mutableStateOf(false) }
    var dayPage by remember { mutableStateOf<String?>(null) }
    var day by remember { mutableStateOf(todayIso()) }
    var month by remember { mutableStateOf(todayIso().take(7)) }
    var todayTick by remember { mutableStateOf(todayIso()) }
    var editingEntry by remember { mutableStateOf<Entry?>(null) }
    var editingShift by remember { mutableStateOf<Shift?>(null) }
    var entryType by remember { mutableStateOf("expense") }
    var presetCat by remember { mutableStateOf<String?>(null) }
    var editingBudgets by remember { mutableStateOf(false) }
    var showDiagnostics by remember { mutableStateOf(false) }
    var payoutsTarget by remember { mutableStateOf<PayoutsTarget?>(null) }
    var fuelPrompt by remember { mutableStateOf<FuelPrompt?>(null) }
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
        if (type != "income" && isFuelCat(cat) && odo == null) {
            fuelPrompt = FuelPrompt(id, name, cents)
        } else {
            scope.launch { announceAdded(id, name, cents) }
        }
    }

    // A swipe can report the same confirmation twice; never queue two Undo bars for one row.
    val deletingIds = remember { mutableSetOf<String>() }
    fun deleteEntryWithUndo(entry: Entry) {
        if (!deletingIds.add(entry.id)) return
        scope.launch {
            try {
                if (repo.store.entries.none { it.id == entry.id }) return@launch
                repo.deleteEntry(entry.id)
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

    PlatformBackHandler(enabled = adding != 0 || dayPage != null || tab != 0) {
        if (adding != 0) { adding = 0; editingEntry = null; editingShift = null; presetCat = null }
        else if (dayPage != null) dayPage = null
        else if (tab != 0) tab = 0
    }


    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 600.dp
        val barHeight = 80.dp
        val fabSize = 56.dp
        // sit above the gesture bar, not on top of it
        val gestureInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val barGap = gestureInset
        val hasFab = tab == 0 && !repo.readOnly
        // keep Undo clear of the floating +, not underneath it
        val snackbarBottom = if (hasFab) barHeight + barGap + fabSize + 10.dp
        else barHeight + barGap + 10.dp
        val sheetOpen = adding != 0 || payoutsTarget != null
        var headerCollapsed by remember { mutableStateOf(false) }
        LaunchedEffect(tab) { headerCollapsed = false }

        // ---- level 1: the app itself. The modal sheet owns the standard MD3 scrim. ----
        Box(Modifier.fillMaxSize()) {

    Box(Modifier.fillMaxSize()) {
    Box(Modifier.fillMaxSize().background(baki.surface)) {}
    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost = {
            BakiSnackbarHost(snackbar, Modifier.padding(horizontal = Space.m).padding(bottom = snackbarBottom))
        },
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize()
                .padding(start = if (wide) 80.dp else 0.dp)
                .widthIn(max = 960.dp),
        ) {
            Header(repo)
            val motionScheme = MaterialTheme.motionScheme
            AnimatedContent(
                targetState = tab,
                transitionSpec = {
                    val spatial = motionScheme.defaultSpatialSpec<androidx.compose.ui.unit.IntOffset>()
                    val effects = motionScheme.defaultEffectsSpec<Float>()
                    (fadeIn(effects) + slideInVertically(spatial) { it / 12 })
                        .togetherWith(fadeOut(effects))
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
                    onAdd = { adding = 1 },
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
                    onOpenDayPage = { dayPage = it },
                )
                1 -> SpendingScreen(
                    repo = repo,
                    month = month,
                    onMonth = { month = it },
                    onEditEntry = { editingEntry = it; entryType = it.type; adding = 2 },
                    onDeleteEntry = { entry -> deleteEntryWithUndo(entry) },
                    onHeaderCollapsed = { headerCollapsed = it },
                )
                2 -> ShiftsScreen(
                    repo = repo,
                    onAddShift = { editingShift = null; adding = 3 },
                    onEditShift = { editingShift = it; adding = 3 },
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
                else -> MoreScreen(repo, onHeaderCollapsed = { headerCollapsed = it }, onEditBudgets = { editingBudgets = true; adding = 5 })
            }
            }
            }
        }
    }
    }

    // Adaptive navigation: rail on tablets, bar on phones.
    if (wide && !sheetOpen && dayPage == null) {
        NavigationRail(
            modifier = Modifier.align(Alignment.CenterStart).fillMaxHeight(),
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Spacer(Modifier.height(Space.l))
            val labels = listOf("Today", "Spending", "Shifts", "Summary", "More")
            val icons = listOf(BakiIcons.Today, BakiIcons.Receipt, BakiIcons.Car, BakiIcons.Insights, BakiIcons.More)
            labels.forEachIndexed { i, label ->
                NavigationRailItem(
                    selected = tab == i,
                    onClick = { PlatformEffects.haptic(Haptic.Tick); tab = i },
                    icon = { Icon(icons[i], contentDescription = null) },
                    label = { Text(label) },
                )
            }
            Spacer(Modifier.weight(1f))
            if (tab == 0 && !repo.readOnly) {
                FloatingActionButton(
                    onClick = { editingEntry = null; entryType = "expense"; presetCat = null; adding = 2 },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ) { Text("+") }
            }
            Spacer(Modifier.height(Space.l))
        }
    }

    // MD3 navigation bar; hidden while a sheet or day page is modal.
    if (!wide && !sheetOpen && dayPage == null) {
        NavigationBar(
            modifier = Modifier.align(Alignment.BottomCenter),
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 3.dp,
        ) {
            val labels = listOf("Today", "Spend", "Shifts", "Stats", "More")
            val icons = listOf(BakiIcons.Today, BakiIcons.Receipt, BakiIcons.Car, BakiIcons.Insights, BakiIcons.More)
            labels.forEachIndexed { i, label ->
                NavigationBarItem(
                    selected = tab == i,
                    onClick = { PlatformEffects.haptic(Haptic.Tick); tab = i },
                    icon = { Icon(icons[i], contentDescription = null) },
                    label = {
                        Text(label, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                    },
                )
            }
        }
    }

    // M3 Expressive primary action menu.
    if (!wide && tab == 0 && !repo.readOnly && dayPage == null) {
        FloatingActionButtonMenu(
            expanded = addMenuOpen,
            modifier = Modifier.align(Alignment.BottomEnd)
                .padding(end = Space.l, bottom = barHeight + barGap + Space.l),
            button = {
                FloatingActionButton(
                    onClick = { addMenuOpen = !addMenuOpen },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.semantics { contentDescription = if (addMenuOpen) "Close quick actions" else "Open quick actions" },
                ) { Text("+", style = MaterialTheme.typography.headlineSmall) }
            },
        ) {
            FloatingActionButtonMenuItem(
                onClick = {
                    addMenuOpen = false
                    editingEntry = null
                    entryType = "expense"
                    presetCat = null
                    adding = 2
                },
                text = { Text("Add spending") },
                icon = { Icon(BakiIcons.Receipt, contentDescription = null) },
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
            )
            FloatingActionButtonMenuItem(
                onClick = {
                    addMenuOpen = false
                    editingEntry = null
                    entryType = "income"
                    presetCat = null
                    adding = 2
                },
                text = { Text("Add income") },
                icon = { Icon(BakiIcons.Wallet, contentDescription = null) },
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            )
            FloatingActionButtonMenuItem(
                onClick = {
                    addMenuOpen = false
                    editingShift = null
                    adding = 3
                },
                text = { Text("Log shift") },
                icon = { Icon(BakiIcons.Car, contentDescription = null) },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            )
        }
    }

    dayPage?.let { pageDate ->
        DayPage(
            repo = repo,
            day = pageDate,
            onDay = { dayPage = it },
            onClose = { dayPage = null },
            onEditEntry = { editingEntry = it; entryType = it.type; adding = 2 },
            onDeleteEntry = { entry -> deleteEntryWithUndo(entry) },
            onAddEntry = { day = pageDate; editingEntry = null; adding = 2 },
            onEditShift = { editingShift = it; adding = 3 },
        )
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
            editable = !repo.readOnly,
            onEdit = { index, cents ->
                scope.launch {
                    when (target) {
                        PayoutsTarget.Live -> repo.updatePayout(index, cents)
                        is PayoutsTarget.Stored -> repo.updateShiftPayout(target.shiftId, index, cents)
                    }
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
                }
            },
            onDismiss = { payoutsTarget = null },
        )
    }

    fuelPrompt?.let { p ->
        OdometerPromptDialog(
            label = p.label,
            cents = p.cents,
            lastOdo = repo.store.entries
                .filter { isFuelCat(it.cat) && (it.odo ?: 0) > 0 }
                .maxOfOrNull { it.odo ?: 0 },
            onSave = { odo ->
                fuelPrompt = null
                scope.launch {
                    repo.setEntryOdometer(p.entryId, odo)
                    announceAdded(p.entryId, p.label, p.cents)
                }
            },
            onSkip = {
                fuelPrompt = null
                scope.launch { announceAdded(p.entryId, p.label, p.cents) }
            },
        )
    }

    when (adding) {
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
        5 -> BudgetSheet(repo, onDismiss = { adding = 0; editingBudgets = false })
        4 -> AddPayoutDialog(
            onDismiss = { adding = 0 },
            onAdd = { cents -> repo.addPayout(cents); adding = 0 },
        )
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
    TopAppBar(
        title = {
            Column {
                Text("Baki", style = MaterialTheme.typography.titleLarge)
                Text(
                    "what your shift really pays",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        actions = {
            IconButton(onClick = { repo.setDiscreet(!repo.config.discreet) }) {
                Text(
                    if (repo.config.discreet) "◉" else "◎",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (status == "syncing") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(end = Space.l),
                ) {
                    LoadingIndicator(
                        modifier = Modifier.size(18.dp),
                        color = ink,
                    )
                    Spacer(Modifier.width(Space.xs))
                    Text("Syncing", color = ink, style = MaterialTheme.typography.labelSmall)
                }
            } else {
                Text(
                    status.replaceFirstChar { it.titlecase() },
                    color = ink,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(end = Space.l),
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
        ),
        windowInsets = WindowInsets(0, 0, 0, 0),
    )
}

@Composable
private fun Hero(label: String, amountCents: Long, tint: Color = Color.Unspecified, size: Int = 46) {
    val baki = LocalBaki.current
    val ink = if (tint == Color.Unspecified) baki.onSurface else tint
    Column {
        Text(label, color = baki.muted, style = BakiType.label)
        EasedMoney(
            cents = amountCents, color = ink,
            // tabular figures: the hero number keeps its width as digits change
            style = if (size >= 40) heroMoneyStyle(amountCents) else BakiType.headlineNumeric,
        )
    }
}

@Composable
fun Chip(text: String, tint: Color, modifier: Modifier = Modifier) {
    val baki = LocalBaki.current
    if (baki.paper) {
        Column(modifier) {
            Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(7.dp).clip(RoundedCornerShape(Radius.pill)).background(tint))
                Spacer(Modifier.width(Space.s))
                Text(text, color = baki.onSurface, style = BakiType.labelStrong)
            }
            LedgerRule(alpha = 0.14f)
        }
    } else {
        Row(
            Modifier.clip(RoundedCornerShape(Radius.pill)).background(baki.container).padding(horizontal = Space.l, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(7.dp).clip(RoundedCornerShape(Radius.pill)).background(tint))
            Spacer(Modifier.width(Space.s))
            Text(text, color = baki.onSurface, style = BakiType.labelStrong)
        }
    }
}

@Composable
fun RowCard(
    title: String,
    sub: String,
    amount: String,
    amountTint: Color = Color.Unspecified,
    icon: ImageVector? = null,
    onLongPress: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    running: String? = null,
) {
    val baki = LocalBaki.current
    val ink = if (amountTint == Color.Unspecified) baki.onSurface else amountTint
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
                        .background(if (editing) baki.primaryContainer else baki.down.copy(alpha = 0.22f))
                        .padding(horizontal = Space.ml),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = if (editing) Arrangement.Start else Arrangement.End,
                ) {
                    Text(
                        if (editing) "Edit" else "Delete",
                        color = if (editing) baki.onPrimaryContainer else baki.down,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        },
    ) {
    val a11yActions = buildList {
        if (onLongPress != null) add(CustomAccessibilityAction("Edit") { onLongPress(); true })
        if (onDelete != null) add(CustomAccessibilityAction("Delete") { onDelete(); true })
    }
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth()
                .then(
                    if (baki.paper) Modifier.background(baki.surface)
                    else Modifier.clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surfaceContainerLow)
                )
                .then(
                    if (onLongPress != null) Modifier.pointerInput(title) {
                        detectTapGestures(onLongPress = { onLongPress() })
                    } else Modifier
                )
                .semantics { customActions = a11yActions }
                .padding(vertical = Space.l, horizontal = if (baki.paper) 0.dp else Space.l),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Box(
                    Modifier.size(32.dp).then(
                        if (baki.paper) Modifier
                        else Modifier.clip(RoundedCornerShape(Radius.small)).background(baki.surfaceHigh)
                    ),
                    contentAlignment = Alignment.Center,
                ) { Icon(icon, contentDescription = null, tint = baki.primary, modifier = Modifier.size(IconSize.inline)) }
                Spacer(Modifier.width(Space.m))
            }
            Column(Modifier.weight(1f)) {
                Text(title, color = baki.onSurface, style = BakiType.bodyStrong)
                if (sub.isNotEmpty()) Text(sub, color = baki.muted, style = BakiType.caption)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(amount, color = ink, style = BakiType.subtitleNumeric)
                if (running != null) Text(running, color = baki.muted, style = BakiType.nano)
            }
        }
        if (baki.paper) LedgerRule(Modifier.padding(start = if (icon != null) 44.dp else 0.dp), alpha = 0.16f)
    }
    }
}

@Composable
private fun SectionTitle(text: String, trailing: String = "") {
    val baki = LocalBaki.current
    Column(Modifier.fillMaxWidth()) {
        if (baki.paper) {
            LedgerRule()
            Spacer(Modifier.height(Space.s))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text.lowercase().replaceFirstChar { it.titlecase() }, color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.semantics { heading() },
            )
            if (trailing.isNotEmpty()) Text(trailing, color = baki.muted, style = BakiType.caption)
        }
    }
}

@Composable
fun bakiFieldColorsPublic(): TextFieldColors = bakiFieldColors()

@Composable
private fun bakiFieldColors(): TextFieldColors {
    val baki = LocalBaki.current
    return OutlinedTextFieldDefaults.colors(
        focusedTextColor = baki.onSurface, unfocusedTextColor = baki.onSurface,
        focusedBorderColor = baki.primary, unfocusedBorderColor = baki.muted,
        focusedLabelColor = baki.primary, unfocusedLabelColor = baki.muted,
        cursorColor = baki.primary,
        focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LiveCard(repo: BakiRepo, live: LiveShift, onPayout: () -> Unit, onPayouts: () -> Unit = {}) {
    val baki = LocalBaki.current
    var now by remember { mutableStateOf(nowMillis()) }
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
    val accent = if (onBreak) baki.warn else baki.primary

    Surface(
        modifier = Modifier.fillMaxWidth().padding(vertical = Space.s),
        shape = RoundedCornerShape(Radius.chart),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(Modifier.fillMaxWidth().padding(Space.l)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(30.dp)
                        .clip(MaterialShapes.Cookie9Sided.toShape())
                        .background(
                            if (onBreak) MaterialTheme.colorScheme.tertiaryContainer
                            else MaterialTheme.colorScheme.secondaryContainer,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.size(8.dp).clip(RoundedCornerShape(50)).background(accent))
                }
                Spacer(Modifier.width(Space.s))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (onBreak) "On break" else "Live shift",
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text("since ${startClock(live.start)}", color = baki.muted, style = MaterialTheme.typography.labelMedium)
                }
                Text(
                    if (onBreak) "PAUSED" else "ACTIVE",
                    color = accent,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            Spacer(Modifier.height(Space.l))
            Text("Net so far", color = baki.muted, style = MaterialTheme.typography.labelLarge)
            Text(
                "RM ${rm(netCents)}",
                color = if (net < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontFeatureSettings = "tnum",
                    fontWeight = FontWeight.Bold,
                ),
            )
            Spacer(Modifier.height(Space.s))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                ) {
                    Text(
                        "${live.payouts.size} payouts",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = Space.m, vertical = Space.xs),
                    )
                }
                Spacer(Modifier.weight(1f))
                Text("gross RM ${rm(total)}", color = baki.muted, style = MaterialTheme.typography.labelLarge)
            }
            Spacer(Modifier.height(Space.m))
            Surface(
                shape = RoundedCornerShape(Radius.large),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(horizontal = Space.m, vertical = Space.xs)) {
                    LiveMetric("On clock", liveClock(worked), MaterialTheme.colorScheme.onSurface)
                    LiveMetric("Costs so far", "RM ${rm(be.costs)}", baki.muted)
                    LiveMetric(
                        if (be.covered) "Break-even" else "Break-even in",
                        if (be.covered) "covered" else be.minutesToGo?.let { "${it.toInt()} min" } ?: "add a payout",
                        if (be.covered) baki.up else baki.muted,
                    )
                    if (be.netPerHour != null) {
                        LiveMetric(
                            "Yield now",
                            buildString {
                                append("RM ${rm(be.netPerHour)}/h")
                                be.grossPerKm?.let { append(" · RM ${rm(it)}/km") }
                            },
                            if ((be.netPerHour ?: 0.0) >= repo.store.settings.profit.targetRate) baki.up else baki.down,
                        )
                    }
                    LiveMetric(
                        "Tracked",
                        buildString {
                            append("${"%.1f".format(live.km)} km")
                            if (breakMs > 0) append(" · ${liveClock(breakMs)} break")
                        },
                        baki.muted,
                    )
                }
            }
            Spacer(Modifier.height(Space.l))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                FilledTonalButton(
                    onClick = onPayout,
                    modifier = Modifier.weight(1f),
                ) { Text("+ Payout") }
                Button(
                    enabled = !repo.readOnly,
                    onClick = { if (onBreak) repo.resumeLive() else repo.pauseLive() },
                    modifier = Modifier.weight(1f),
                ) { Text(if (onBreak) "Resume" else "Pause") }
                Button(
                    enabled = !repo.readOnly,
                    onClick = { scope.launch { repo.endLive() } },
                    modifier = Modifier.weight(0.82f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
                ) { Text("End") }
            }
        }
    }
}

@Composable
private fun LiveMetric(label: String, value: String, tint: Color) {
    val baki = LocalBaki.current
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = baki.muted, style = BakiType.caption, modifier = Modifier.weight(1f))
        Text(value, color = tint, style = BakiType.labelNumeric)
    }
}

@Composable
private fun DayHeader(day: String, today: String, onChange: (String) -> Unit) {
    val baki = LocalBaki.current
    val atToday = day >= today
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.pill)).background(baki.container).padding(vertical = Space.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = { onChange(addDays(day, -1)) }) {
            Text("‹", color = baki.onSurface, style = BakiType.body )
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (day == today) "Today" else day, color = baki.onSurface, style = BakiType.bodyStrong )
            Text(if (day == today) day else "tap ‹ › to move", color = baki.muted, style = BakiType.caption )
        }
        TextButton(enabled = !atToday, onClick = { onChange(addDays(day, 1)) }) {
            Text("›", color = if (atToday) baki.muted.copy(alpha = 0.35f) else baki.onSurface, style = BakiType.body )
        }
    }
}

@Composable
private fun MonthHeader(month: String, onChange: (String) -> Unit) {
    val baki = LocalBaki.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = { onChange(addDays("$month-01", -1).take(7)) }) {
            Text("‹", color = baki.onSurface, style = BakiType.body )
        }
        Text(monthLabel(month), color = baki.onSurface,
            modifier = Modifier.weight(1f), textAlign = TextAlign.Center, style = BakiType.bodyStrong )
        TextButton(onClick = { onChange(addDays(endOfMonth(month), 1).take(7)) }) {
            Text("›", color = baki.onSurface, style = BakiType.body )
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
    onDeleteEntry: (Entry) -> Unit,
    onHeaderCollapsed: (Boolean) -> Unit = {},
) {
    val baki = LocalBaki.current
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
        else -> "SPENT THIS MONTH"
    }

    val envelopes = remember(repo.store, month) { budgetUsage(repo.store, month) }
    val runningById = remember(entries) {
        var balance = 0L
        entries.sortedWith(
            compareBy<Entry> { it.date }.thenBy { it.loggedAt.ifBlank { it.updated } },
        ).associate { e ->
            balance += if (e.type == "income") e.amount else -e.amount
            e.id to balance
        }
    }
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
                Hero(shownLabel, shown, baki.down, 40)
                Text(
                    (if (filtering) {
                        "${entries.size} purchase(s)${catName?.let { " · $it" } ?: ""} of ${t.entryCount} this month"
                    } else {
                        "${t.entryCount} purchases · ${t.shiftCount} shifts · in RM ${rm(t.income)}"
                    }),
                    color = baki.muted, style = BakiType.captionNumeric,
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
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Space.s),
                    verticalArrangement = Arrangement.spacedBy(Space.s),
                ) {
                    val usedCats = all.map { it.cat }.toSet()
                    val chips = listOf("all" to "All") +
                        cats.filter { it.id in usedCats || it.id == catFilter }.map { it.id to it.name }
                    chips.forEach { (id, label) ->
                        FilterChip(
                            selected = catFilter == id,
                            onClick = { catFilter = id },
                            label = { Text(label) },
                            leadingIcon = if (id != "all") {
                                { Icon(categoryIcon(id), contentDescription = null, modifier = Modifier.size(IconSize.inline)) }
                            } else null,
                            shape = MaterialTheme.shapes.small,
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
            }
        }

            if (envelopes.isNotEmpty()) {
                item {
                    Column(Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("ENVELOPES", color = baki.muted, style = BakiType.section, modifier = Modifier.weight(1f))
                            Text("base + carry + moves", color = baki.muted, style = BakiType.nano)
                        }
                        LedgerRule(alpha = 0.14f)
                        Spacer(Modifier.height(Space.s))
                        envelopes.forEach { b ->
                            val name = repo.store.settings.cats.catsOut.firstOrNull { it.id == b.cat }?.name ?: b.cat
                            val ink = when {
                                b.over -> baki.down
                                b.fraction >= 0.8 -> baki.warn
                                else -> baki.up
                            }
                            Column(Modifier.fillMaxWidth().padding(vertical = Space.s)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(name, color = baki.onSurface, style = BakiType.labelStrong)
                                    Text(
                                        "RM ${rm(b.spent)} / ${rm(b.limit)}" +
                                            if (b.over) " · RM ${rm(-b.remaining)} over"
                                            else " · RM ${rm(b.remaining)} left",
                                        color = ink, style = BakiType.captionNumeric,
                                    )
                                }
                                if (b.carried != 0.0 || b.moved != 0.0) {
                                    Text(
                                        "base RM ${rm(b.base)}" +
                                            (if (b.carried != 0.0) " · carry +RM ${rm(b.carried)}" else "") +
                                            (if (b.moved != 0.0) " · move ${if (b.moved > 0) "+" else "−"}RM ${rm(kotlin.math.abs(b.moved))}" else ""),
                                        color = baki.muted, style = BakiType.nano,
                                    )
                                }
                                Spacer(Modifier.height(Space.s))
                                Box(
                                    Modifier.fillMaxWidth().height(3.dp).background(baki.surfaceHigh),
                                ) {
                                    Box(
                                        Modifier.fillMaxWidth(b.fraction.toFloat().coerceIn(0f, 1f))
                                            .fillMaxHeight().background(ink),
                                    )
                                }
                            }
                            LedgerRule(alpha = 0.14f)
                        }
                    }
                }
            }
            items(entries, key = { it.id }) { e ->
                RowCard(
                    e.note.ifEmpty { categoryLabel(repo.store, e.cat) },
                    buildString {
                        append(prettyDate(e.date))
                        e.odo?.takeIf { it > 0 }?.let { append(" · ${it} km") }
                        loggedTime(e.loggedAt)?.let { append(" · logged $it") }
                    },
                    (if (e.type == "income") "+" else "−") + rm(e.amount),
                    if (e.type == "income") baki.up else baki.down,
                    icon = categoryIcon(e.cat),
                    onLongPress = { onEditEntry(e) },
                    onDelete = { onDeleteEntry(e) },
                    running = runningById[e.id]?.let { "entry bal RM ${rm(it)}" },
                )
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
    onDeleteShift: (Shift) -> Unit,
    onHeaderCollapsed: (Boolean) -> Unit = {},
) {
    val baki = LocalBaki.current
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
                Hero("NET AFTER CAR COST", cents(net), baki.onSurface, 40)
                Text(
                    "${rows.size} shifts · ${"%.1f".format(hours)}h · RM ${rm(if (hours > 0) net / hours else 0.0)}/hr · hold to edit",
                    color = baki.muted, style = BakiType.label)
                Spacer(Modifier.height(Space.m))
                FilledTonalButton(
                    onClick = onAddShift,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                ) { Text("Add shift") }
                Spacer(Modifier.height(Space.l))
            }
        }
        if (rows.isEmpty()) item {
            EmptyState("No shifts yet", "Add a shift to see what it really paid after running costs.")
        }
        items(rows, key = { it.first.id }) { row ->
            val sh = row.first
            val m = row.second
            RowCard(
                "${prettyDate(sh.date)} · ${sh.start}".trim().trimEnd('·'),
                "${"%.1f".format(m.km)} km" +
                    (m.grossPerKm?.let { " · RM ${rm(it)}/km" } ?: "") +
                    " · ${m.band}",
                "RM ${rm(m.net)}",
                if (m.net < 0) baki.down else bandColor(m.band, baki),
                onLongPress = { onEditShift(sh) },
                onDelete = { onDeleteShift(sh) },
            )
        }
    }
}

@Composable
private fun BillEditorDialog(
    existing: Bill?,
    categories: List<Cat>,
    baki: BakiPalette,
    canDelete: Boolean,
    onSave: (Bill) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var amount by remember { mutableStateOf(existing?.let { "%.2f".format(it.amount / 100.0) } ?: "") }
    var annual by remember { mutableStateOf(existing?.cadence == "annual") }
    var category by remember { mutableStateOf(existing?.category ?: categories.firstOrNull()?.id ?: "other") }
    var business by remember { mutableStateOf(existing?.business ?: false) }
    var optional by remember { mutableStateOf(existing?.optional ?: false) }
    var anchor by remember { mutableStateOf(existing?.anchorMonth?.toString() ?: "1") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(Radius.dialog),
        title = { Text(if (existing == null) "New bill" else "Edit bill", color = baki.onSurface, style = BakiType.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it }, singleLine = true,
                    label = { Text("Name") }, modifier = Modifier.fillMaxWidth(), colors = bakiFieldColorsPublic(),
                )
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it.filter { c -> c.isDigit() || c == '.' } },
                    singleLine = true, label = { Text("Amount RM") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(), colors = bakiFieldColorsPublic(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Monthly", color = baki.onSurface, style = BakiType.label)
                    Switch(checked = annual, onCheckedChange = { annual = it })
                    Text("Annual", color = baki.onSurface, style = BakiType.label)
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
                Text("Category", color = baki.muted, style = BakiType.caption)
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(Space.xs),
                ) {
                    categories.forEach { c ->
                        val on = c.id == category
                        Box(
                            Modifier.clip(RoundedCornerShape(Radius.pill))
                                .background(if (on) baki.primaryContainer else baki.container)
                                .clickable { category = c.id }
                                .padding(horizontal = Space.m, vertical = Space.xs),
                        ) {
                            Text(c.name, color = if (on) baki.onPrimaryContainer else baki.onSurface, style = BakiType.label)
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Business (tax deductible)", color = baki.onSurface, style = BakiType.label)
                    Spacer(Modifier.weight(1f))
                    Switch(checked = business, onCheckedChange = { business = it })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Optional (pay from leftovers)", color = baki.onSurface, style = BakiType.label)
                    Spacer(Modifier.weight(1f))
                    Switch(checked = optional, onCheckedChange = { optional = it })
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val cents = ((amount.toDoubleOrNull() ?: 0.0) * 100).toLong()
                    onSave(
                        Bill(
                            id = existing?.id ?: "",
                            name = name,
                            amount = cents,
                            cadence = if (annual) "annual" else "monthly",
                            category = category,
                            business = business,
                            optional = optional,
                            anchorMonth = if (annual) anchor.toIntOrNull()?.coerceIn(1, 12) ?: 1 else null,
                        ),
                    )
                },
                enabled = name.isNotBlank() && (amount.toDoubleOrNull() ?: 0.0) > 0.0,
            ) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (existing != null) {
                    TextButton(onClick = onDelete, enabled = canDelete) { Text("Delete", color = if (canDelete) baki.down else baki.muted) }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

private enum class MoreTab(val label: String) { MONEY("Money"), SHIFT("Shift"), APP("App") }

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MoreTabs(selected: MoreTab, onSelect: (MoreTab) -> Unit) {
    ButtonGroup(
        overflowIndicator = { state -> ButtonGroupDefaults.OverflowIndicator(state) },
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        MoreTab.entries.forEach { tab ->
            toggleableItem(
                checked = tab == selected,
                label = tab.label,
                onCheckedChange = { if (it) onSelect(tab) },
                weight = 1f,
            )
        }
    }
}
@Composable
private fun MoreScreen(repo: BakiRepo, onEditBudgets: () -> Unit, onHeaderCollapsed: (Boolean) -> Unit = {}) {
    val baki = LocalBaki.current
    var url by remember { mutableStateOf(repo.config.serverUrl) }
    var finding by remember { mutableStateOf(false) }
    var foundMsg by remember { mutableStateOf<String?>(null) }
    var foundOk by remember { mutableStateOf(false) }
    var routeKey by remember { mutableStateOf(repo.config.routeSecret) }
    var routeSync by remember { mutableStateOf(repo.config.syncRoutes) }
    var editingProfit by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    var linkTarget by remember { mutableStateOf<Pair<Bill, String>?>(null) }
    var editingBill by remember { mutableStateOf<Bill?>(null) }
    var addingBill by remember { mutableStateOf(false) }
    var unlinkTarget by remember { mutableStateOf<Pair<Bill, String>?>(null) }
    var historyOpen by remember { mutableStateOf(false) }
    var statementHistoryOpen by remember { mutableStateOf(false) }
    var notifRefresh by remember { mutableStateOf(0) }
    var serviceTargetOpen by remember { mutableStateOf(false) }
    var moreTab by remember { mutableStateOf(MoreTab.MONEY) }
    val moreScroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(moreTab) { moreScroll.scrollTo(0) }
    // More is a settings page: keep the tabs reachable while its sections scroll.
    LaunchedEffect(Unit) { onHeaderCollapsed(false) }
    val pickImport = rememberJsonImport { text ->
        if (text != null) scope.launch {
            val added = repo.importJson(text)
            if (added > 0) PlatformEffects.successFeedback()
        }
    }

    Column(
        Modifier.fillMaxSize().padding(horizontal = Space.l),
    ) {
        // The More header and its tabs stay put; only the settings body scrolls.
        MoreTabs(moreTab, onSelect = { moreTab = it })
        Spacer(Modifier.height(Space.s))
        Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(moreScroll)) {
        if (moreTab == MoreTab.SHIFT) {
        SectionTitle("PAYMENT QR")
        Spacer(Modifier.height(Space.s))
        PaymentQrSection()
        Spacer(Modifier.height(Space.l))
        SectionTitle("SERVICE")
        Spacer(Modifier.height(Space.s))
        ServiceCard(repo, onSetTarget = { serviceTargetOpen = true })
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
        }

        if (moreTab == MoreTab.APP) {
        SectionTitle("SYNC")
        Spacer(Modifier.height(Space.s))
        Column(Modifier.fillMaxWidth().padding(vertical = Space.m)) {
            Text("Server address", color = baki.onSurface, style = BakiType.bodyStrong )
            Text("where the tracker server is running", color = baki.muted, style = BakiType.caption )
            Spacer(Modifier.height(Space.m))
            OutlinedTextField(
                value = url, onValueChange = { url = it }, singleLine = true,
                modifier = Modifier.fillMaxWidth(), colors = bakiFieldColorsPublic(),
            )
            Spacer(Modifier.height(Space.m))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                Button( shape = androidx.compose.foundation.shape.RoundedCornerShape(Radius.pill),
                    onClick = { repo.setServer(url) },
                    colors = ButtonDefaults.buttonColors(containerColor = baki.primary, contentColor = baki.onPrimary),
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
                            repo.setServer(found.url)
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
                Text(
                    if (finding) "Searching this Wi-Fi…" else "Find server on this Wi-Fi",
                    color = if (finding) baki.muted else baki.primary,
                    style = BakiType.captionStrong,
                )
            }
            foundMsg?.let {
                Text(it, color = if (foundOk) baki.up else baki.down, style = BakiType.caption )
            }
            repo.lastError?.let {
                Spacer(Modifier.height(Space.s))
                Text(it, color = baki.down, style = BakiType.caption )
            }
        }
        Spacer(Modifier.height(Space.m))
        Column(Modifier.fillMaxWidth().padding(vertical = Space.m)) {
            Text("Route traces", color = baki.onSurface, style = BakiType.bodyStrong)
            Text(
                "Sealed with a key only your devices know, so the desk can review your drives. The server never sees coordinates.",
                color = baki.muted, style = BakiType.caption,
            )
            Spacer(Modifier.height(Space.m))
            SwitchRow(
                title = "Sync route traces",
                sub = if (routeSync) "on — recordings are uploaded sealed" else "off — recordings stay on this phone",
                checked = routeSync,
            ) {
                routeSync = it
                repo.setRouteSync(it, routeKey)
            }
            Spacer(Modifier.height(Space.m))
            OutlinedTextField(
                value = routeKey, onValueChange = { routeKey = it }, singleLine = true,
                label = { Text("shared route key") },
                modifier = Modifier.fillMaxWidth(), colors = bakiFieldColorsPublic(),
            )
            Spacer(Modifier.height(Space.m))
            Button( shape = androidx.compose.foundation.shape.RoundedCornerShape(Radius.pill),
                onClick = { repo.setRouteSync(routeSync, routeKey) },
                colors = ButtonDefaults.buttonColors(containerColor = baki.primary, contentColor = baki.onPrimary),
            ) { Text("Save route key") }
        }
        }

        if (moreTab == MoreTab.MONEY) {
        SectionTitle("BILLS")
        Spacer(Modifier.height(Space.s))
        Column(Modifier.fillMaxWidth().padding(vertical = Space.m)) {
            val current = Bills.monthPeriod(todayIso())
            val target = Bills.nextOpenMonth(repo.store, current)
            val committed = Bills.due(repo.store, current)
            val covered = committed.count { Bills.isCovered(repo.store, it, Bills.periodOf(it, current)) }
            if (repo.store.bills.isEmpty()) {
                Text("No bills loaded yet", color = baki.onSurface, style = BakiType.bodyStrong)
                Text(
                    "Bills sync from the server. Tap Sync above if they stay empty.",
                    color = baki.muted, style = BakiType.caption,
                )
            } else {
                val committedTotal = Bills.committed(repo.store, current)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "${monthLabel(current)} · RM ${rm(committedTotal / 100.0)} committed",
                            color = baki.onSurface, style = BakiType.bodyStrong,
                        )
                        Text(
                            "$covered of ${committed.size} covered · average RM ${rm(Bills.averageMonthly(repo.store) / 100.0)}/month incl. annual",
                            color = baki.muted, style = BakiType.caption,
                        )
                    }
                    TextButton(onClick = { historyOpen = true }) { Text("History", color = baki.muted, style = BakiType.label) }
                    TextButton(onClick = { addingBill = true }) { Text("Add", color = baki.primary, style = BakiType.label) }
                }
            }
            Text(
                "Cover before month end; covered months roll to the next.",
                color = baki.muted, style = BakiType.caption,
            )
            Spacer(Modifier.height(Space.m))
            committed.forEach { bill ->
                val period = Bills.periodOf(bill, current)
                val isPaid = Bills.isCovered(repo.store, bill, period)
                Row(Modifier.fillMaxWidth().padding(vertical = Space.s), verticalAlignment = Alignment.CenterVertically) {
                    Column(
                        Modifier.weight(1f).heightIn(min = Control.primary).clickable { editingBill = bill },
                    ) {
                        Text(bill.name, color = baki.onSurface, style = BakiType.label)
                        Text(
                            "RM ${rm(bill.amount / 100.0)} · ${if (bill.cadence == "annual") "annual" else "monthly"}",
                            color = baki.muted, style = BakiType.captionNumeric,
                        )
                    }
                    if (isPaid) {
                        Text(
                            "covered ${Bills.paidOn(repo.store, bill, period) ?: ""}",
                            color = baki.up, style = BakiType.caption,
                            modifier = Modifier.heightIn(min = Control.primary).clickable { unlinkTarget = bill to period },
                        )
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                            FilledTonalButton(
                                onClick = { scope.launch { repo.payBill(bill.id, period) } },
                                contentPadding = PaddingValues(horizontal = Space.m, vertical = Space.xs),
                            ) { Text("Mark paid") }
                            TextButton(
                                onClick = { linkTarget = bill to period },
                                contentPadding = PaddingValues(horizontal = Space.m, vertical = Space.xs),
                            ) { Text("Link") }
                        }
                    }
                }
            }
            Spacer(Modifier.height(Space.s))
            if (repo.store.bills.isNotEmpty()) FilledTonalButton(
                onClick = { scope.launch { repo.settleBills(target) } },
                enabled = Bills.unpaid(repo.store, target).isNotEmpty(),
            ) { Text("Settle all for ${monthLabel(target)}") }

            // optional goals: only when the month actually leaves something over
            val net = monthTotals(repo.store, current).net
            repo.store.bills.filter { it.optional }.forEach { bill ->
                Spacer(Modifier.height(Space.m))
                val paid = Bills.isCovered(repo.store, bill, Bills.periodOf(bill, current))
                Text(
                    "leftover this month: RM ${rm(net / 100.0)}",
                    color = baki.muted, style = BakiType.captionNumeric,
                )
                if (paid) {
                    Text("${bill.name} covered", color = baki.up, style = BakiType.caption)
                } else {
                    FilledTonalButton(
                        onClick = { scope.launch { repo.payBill(bill.id, Bills.periodOf(bill, current)) } },
                        enabled = net >= bill.amount,
                    ) { Text("Pay ${bill.name} · RM ${rm(bill.amount / 100.0)}") }
                }
            }
        }
            if (historyOpen) {
                val rows = Bills.history(repo.store)
                AlertDialog(
                    onDismissRequest = { historyOpen = false },
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    title = { Text("Bill payments", color = baki.onSurface, fontWeight = FontWeight.Bold) },
                    text = {
                        Column(
                            Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                        ) {
                            if (rows.isEmpty()) {
                                Text("Nothing paid yet. Marking a bill paid records a payment here.", color = baki.muted, style = BakiType.caption)
                            }
                            rows.forEach { p ->
                                Row(Modifier.fillMaxWidth().padding(vertical = Space.xs), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text("${p.billName} · ${p.period}", color = baki.onSurface, style = BakiType.label)
                                        Text(
                                            "paid ${p.paidAt}" +
                                                when {
                                                    !p.active -> " · unlinked"
                                                    !p.entryExists -> " · expense deleted"
                                                    else -> ""
                                                },
                                            color = if (p.active && p.entryExists) baki.muted else baki.down,
                                            style = BakiType.caption,
                                        )
                                    }
                                    Text("RM ${rm(p.amount / 100.0)}", color = baki.onSurface, style = BakiType.labelNumeric)
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
                    title = { Text("Unlink ${bill.name} · $period", color = baki.onSurface, fontWeight = FontWeight.Bold) },
                    text = {
                        Text(
                            "Only the covered mark is removed. The RM ${rm((link?.amount ?: bill.amount) / 100.0)} expense stays in your ledger.",
                            color = baki.muted, style = BakiType.caption,
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            scope.launch { repo.unlinkBillPayment(bill.id, period) }
                            unlinkTarget = null
                        }) { Text("Unlink", color = baki.down) }
                    },
                    dismissButton = { TextButton(onClick = { unlinkTarget = null }) { Text("Cancel") } },
                )
            }
            if (addingBill || editingBill != null) {
                val existing = editingBill
                BillEditorDialog(
                    existing = existing,
                    categories = repo.store.settings.cats.catsOut,
                    baki = baki,
                    canDelete = existing != null && repo.store.billPayments.none { it.billId == existing.id },
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
                    title = { Text("Link ${bill.name} · $period", color = baki.onSurface, fontWeight = FontWeight.Bold) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                            if (candidates.isEmpty()) {
                                Text(
                                    "No entry in that month matches this bill's amount or category. Use Mark paid to record a new one.",
                                    color = baki.muted, style = BakiType.caption,
                                )
                            } else {
                                Text("Choose the entry that already paid this bill:", color = baki.muted, style = BakiType.caption)
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
                                            color = baki.onSurface, style = BakiType.label,
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
            Text("Import the Wednesday PDF", color = baki.onSurface, style = BakiType.bodyStrong)
            Text(
                "Import the Wednesday PDF to reconcile income and insurance.",
                color = baki.muted, style = BakiType.caption,
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
            FilledTonalButton(onClick = { pickStatement() }) { Text("Import statement PDFs") }
            Spacer(Modifier.height(Space.xs))
            val weeks = repo.store.statements.size
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (weeks == 1) "1 week on file" else "$weeks weeks on file",
                    color = baki.muted, style = BakiType.captionNumeric,
                )
                if (weeks > 0) {
                    TextButton(onClick = { statementHistoryOpen = true }, contentPadding = PaddingValues(0.dp)) {
                        Text(" · View all weeks", color = baki.primary, style = BakiType.captionStrong )
                    }
                }
            }
            importMsg?.let {
                Spacer(Modifier.height(Space.s))
                Text(it, color = if (it.startsWith("Imported")) baki.up else baki.down, style = BakiType.caption)
            }

            Spacer(Modifier.height(Space.m))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Wednesday reminder", color = baki.onSurface, style = BakiType.label)
                    Text("10:00 nudge to import last week's PDF", color = baki.muted, style = BakiType.caption)
                }
                Switch(
                    checked = repo.config.statementReminder,
                    onCheckedChange = { repo.setStatementReminder(it) },
                )
            }
            val notificationsBlocked = remember(repo.config.statementReminder, notifRefresh) {
                repo.config.statementReminder && PlatformEffects.notificationsBlocked()
            }
            if (notificationsBlocked) {
                Spacer(Modifier.height(Space.s))
                Text(
                    "Notifications are blocked, so the Wednesday reminder cannot fire.",
                    color = baki.down, style = BakiType.caption,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                    TextButton(onClick = { PlatformEffects.openNotificationSettings() }, contentPadding = PaddingValues(0.dp)) {
                        Text("Open settings", color = baki.primary, style = BakiType.captionStrong )
                    }
                    TextButton(onClick = { notifRefresh++ }, contentPadding = PaddingValues(0.dp)) {
                        Text("Check again", color = baki.primary, style = BakiType.captionStrong )
                    }
                }
            }

            val latest = repo.store.statements.values.maxByOrNull { it.weekStart }
            if (latest != null) {
                Spacer(Modifier.height(Space.m))
                Text(
                    "${latest.rangeText.ifBlank { latest.weekStart }}",
                    color = baki.onSurface, style = BakiType.label,
                )
                Text(
                    "income RM ${rm(latest.income)} · deductions RM ${rm(latest.deductions)} · weekly RM ${rm(latest.weekly)}",
                    color = baki.muted, style = BakiType.captionNumeric,
                )
                val check = statementCheck(repo.store, latest)
                val gap = check.loggedGross / 100.0 - latest.income
                if (check.loggedShifts == 0) {
                    Text(
                        "no shifts logged for this week (Grab RM ${rm(latest.income)})",
                        color = baki.muted, style = BakiType.captionNumeric,
                    )
                } else {
                    Text(
                        "logged ${check.loggedShifts} shifts · RM ${rm(check.loggedGross / 100.0)} · Grab RM ${rm(latest.income)} · gap RM ${rm(gap)}",
                        color = if (kotlin.math.abs(gap) < 1.0) baki.up else baki.warn,
                        style = BakiType.captionNumeric,
                    )
                }
                Text(
                    if (check.insuranceMatches) "insurance: ${latest.insuranceCount} charges match"
                    else "insurance: app ${check.appInsurance} vs statement ${latest.insuranceCount} — check the cover rule",
                    color = if (check.insuranceMatches) baki.up else baki.down,
                    style = BakiType.caption,
                )
                Text(
                    if (repo.store.statementMarks[latest.weekStart]?.reconciled == true) "statement reconciled"
                    else "statement not reconciled · open history to review",
                    color = if (repo.store.statementMarks[latest.weekStart]?.reconciled == true) baki.up else baki.warn,
                    style = BakiType.caption,
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
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            listOf("auto" to "Auto", "light" to "Light", "dark" to "Dark").forEachIndexed { index, (value, label) ->
                SegmentedButton(
                    selected = repo.config.themeMode == value,
                    onClick = { repo.setThemeMode(value) },
                    shape = SegmentedButtonDefaults.itemShape(index, 3),
                ) { Text(label) }
            }
        }
        Spacer(Modifier.height(Space.s))
        Text(
            "Dynamic colour follows your wallpaper on Android 12+.",
            color = baki.muted, style = BakiType.caption,
        )
        }

        if (moreTab == MoreTab.MONEY) {
        SectionTitle("VEHICLE & TARGET")
        Spacer(Modifier.height(Space.s))
        val p = repo.store.settings.profit
        Column(Modifier.fillMaxWidth().padding(vertical = Space.m)) {
            Text("RM ${rm(p.fuelPrice)}/L · ${"%.1f".format(p.kmPerLitre)} km/L · wear RM ${rm(p.wearPerKm)}/km",
                color = baki.onSurface, style = BakiType.labelStrong )
            Text("target RM ${rm(p.targetRate)}/hr · avg ${"%.0f".format(p.avgSpeed)} km/h · cover RM ${rm(p.grabInsurance)}",
                color = baki.muted, style = BakiType.caption )
            if (repo.store.settings.gpsCorrection != 1.0) {
                Text("GPS correction ×${"%.2f".format(repo.store.settings.gpsCorrection)} (from your odometer)",
                    color = baki.muted, style = BakiType.caption )
            }
            // real economy, measured from fill-ups that carry an odometer reading
            measuredEconomy(repo.store)?.let { m ->
                Spacer(Modifier.height(Space.s))
                Text(
                    "measured ${"%.1f".format(m.kml)} km/L over ${"%.0f".format(m.km)} km · ${m.fills} fills " +
                        "(${m.from} → ${m.to}) · budget uses ${"%.2f".format(p.kmPerLitre)}",
                    color = baki.muted, style = BakiType.caption )
                if (kotlin.math.abs(m.kml - p.kmPerLitre) > 0.3) {
                    TextButton(
                        onClick = { scope.launch { repo.updateProfit(p.copy(kmPerLitre = m.kml)) } },
                        contentPadding = PaddingValues(0.dp),
                    ) {
                        Text(
                            "Use ${"%.1f".format(m.kml)} km/L instead of ${"%.1f".format(p.kmPerLitre)}",
                            color = baki.primary, style = BakiType.captionStrong )
                    }
                }
            }
            Spacer(Modifier.height(Space.m))
            Button(
                onClick = { editingProfit = true },
                enabled = !repo.readOnly,
                colors = ButtonDefaults.buttonColors(containerColor = baki.primaryContainer, contentColor = baki.onPrimaryContainer),
                shape = RoundedCornerShape(Radius.pill),
            ) { Text("Edit costs") }
        }
        SectionTitle("CATEGORIES")
        Spacer(Modifier.height(Space.s))
        CategoriesCard(repo, editable = !repo.readOnly)
        Spacer(Modifier.height(Space.l))
        SectionTitle("BUDGETS")
        Spacer(Modifier.height(Space.s))
        Column(Modifier.fillMaxWidth().padding(vertical = Space.m)) {
            Text("Monthly limits", color = baki.onSurface, style = BakiType.bodyStrong )
            val n = repo.store.settings.budgets.size
            Text(
                if (n == 0) "none set — add caps for food, fuel, tolls"
                else "$n categories capped · shown on Spending",
                color = baki.muted, style = BakiType.caption )
            Spacer(Modifier.height(Space.s))
            Button(
                onClick = { onEditBudgets() },
                enabled = !repo.readOnly,
                colors = ButtonDefaults.buttonColors(containerColor = baki.primaryContainer, contentColor = baki.onPrimaryContainer),
                shape = RoundedCornerShape(Radius.pill),
            ) { Text("Set limits") }
        }
        }

        if (moreTab == MoreTab.APP) {
        SectionTitle("DIAGNOSTICS")
        Spacer(Modifier.height(Space.s))
        Column(Modifier.fillMaxWidth().padding(vertical = Space.m)) {
            val h = healthOf(repo)
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
                    Text(k, color = baki.muted, style = BakiType.label)
                    Text(v, color = baki.onSurface, style = BakiType.labelNumeric, modifier = Modifier.weight(1f).padding(start = Space.m), textAlign = TextAlign.End)
                }
                if (index != rows.lastIndex) LedgerRule(alpha = 0.14f)
            }
            h.lastError?.let { err ->
                Spacer(Modifier.height(Space.s))
                Text("last error: $err", color = baki.down, style = BakiType.caption, maxLines = 3)
            }
            Spacer(Modifier.height(Space.m))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                val clipboard = LocalClipboardManager.current
                TextButton(
                    onClick = {
                        val report = buildString {
                            appendLine("Baki 0.1.9 diagnostics")
                            appendLine("status: ${h.status}")
                            appendLine("sync: ${ago(now, h.lastSyncAt)}  upload: ${ago(now, h.lastPushAt)} pending=${h.pendingPush}")
                            appendLine("gps: ${h.tracker.provider} fixes=${h.tracker.fixes} rejected=${h.tracker.rejected}")
                            appendLine("live: ${if (h.live != null) "yes" else "no"}  correction=${h.gpsCorrection}")
                            appendLine("records: ${h.entries} entries, ${h.shifts} shifts, readOnly=${h.readOnly}")
                            appendLine("error: ${h.lastError ?: "none"}")
                        }
                        clipboard.setText(AnnotatedString(report))
                    },
                    contentPadding = PaddingValues(0.dp),
                ) { Text("Copy report", color = baki.primary, style = BakiType.labelStrong) }
                TextButton(onClick = { scope.launch { repo.sync() } }, contentPadding = PaddingValues(0.dp)) {
                    Text("Sync now", color = baki.primary, style = BakiType.labelStrong)
                }
            }
        }
        }

        if (moreTab == MoreTab.SHIFT) {
        SectionTitle("HOME SCREEN")
        Spacer(Modifier.height(Space.s))
        Button(
            onClick = { PlatformEffects.requestLiveWidget() },
            colors = ButtonDefaults.buttonColors(containerColor = baki.primaryContainer, contentColor = baki.onPrimaryContainer),
            shape = RoundedCornerShape(Radius.pill), modifier = Modifier.fillMaxWidth(),
        ) { Text("Add the live widget", style = BakiType.label ) }
        }

        if (moreTab == MoreTab.APP) {
        SectionTitle("BACKUP")
        Spacer(Modifier.height(Space.s))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            Button(
                onClick = { PlatformEffects.exportFile("baki-backup-${todayStamp()}.json", "application/json", backupJson(repo.store)) },
                colors = ButtonDefaults.buttonColors(containerColor = baki.primaryContainer, contentColor = baki.onPrimaryContainer),
                shape = RoundedCornerShape(Radius.pill), modifier = Modifier.weight(1f),
            ) { Text("Backup (JSON)", style = BakiType.label ) }
            Button(
                onClick = { PlatformEffects.exportFile("baki-${todayStamp()}.csv", "text/csv", exportCsv(repo.store)) },
                colors = ButtonDefaults.buttonColors(containerColor = baki.primaryContainer, contentColor = baki.onPrimaryContainer),
                shape = RoundedCornerShape(Radius.pill), modifier = Modifier.weight(1f),
            ) { Text("Export (CSV)", style = BakiType.label ) }
        }
        Spacer(Modifier.height(Space.s))
        Button(
            onClick = {
                scope.launch {
                    val from = addDays(todayIso(), -30)
                    val routes = runCatching { RouteStore.routesSince(from) }.getOrDefault(emptyList())
                    if (routes.isNotEmpty()) {
                        PlatformEffects.exportFile(
                            "baki-routes-${todayStamp()}.gpx",
                            "application/gpx+xml",
                            exportGpx(from, routes),
                        )
                    }
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = baki.primaryContainer, contentColor = baki.onPrimaryContainer),
            shape = RoundedCornerShape(Radius.pill), modifier = Modifier.fillMaxWidth(),
        ) { Text("Export routes (GPX · 30 days)", style = BakiType.label ) }
        Spacer(Modifier.height(Space.s))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            Button(
                onClick = { pickImport() },
                enabled = !repo.readOnly,
                colors = ButtonDefaults.buttonColors(containerColor = baki.surfaceHigh, contentColor = baki.onSurface),
                shape = RoundedCornerShape(Radius.pill), modifier = Modifier.weight(1f),
            ) { Text("Import JSON", style = BakiType.label ) }
            Button(
                onClick = { confirmReset = true },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh, contentColor = baki.down),
                shape = RoundedCornerShape(Radius.pill), modifier = Modifier.weight(1f),
            ) { Text("Reset device", style = BakiType.label ) }
        }
        Spacer(Modifier.height(Space.l))
        SectionTitle("ON THIS DEVICE")
        Spacer(Modifier.height(Space.s))
        RowCard("Store", LocalStore.path(), "", baki.muted)
        Spacer(Modifier.height(Space.s))
        RowCard("Records", "${repo.store.entries.size} entries · ${repo.store.shifts.size} shifts", "", baki.muted)
        Spacer(Modifier.height(Space.s))
        RowCard("Status", repo.status, "", baki.muted)
        }
        // keep the last card of every tab clear of the bottom bar / overlay
        Spacer(Modifier.height(140.dp))
        }
    }

    if (statementHistoryOpen) {
        StatementHistorySheet(repo = repo, onDismiss = { statementHistoryOpen = false })
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = { Text("Reset this device?", color = baki.onSurface, fontWeight = FontWeight.Bold) },
            text = { Text("The local copy is forgotten and pulled again from the server. The server is not touched.", color = baki.muted, style = BakiType.label ) },
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
        Modifier.fillMaxWidth().padding(vertical = Space.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = baki.onSurface, style = BakiType.bodyStrong )
            Text(sub, color = if (warn) baki.warn else baki.muted, style = BakiType.caption )
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
    // Grab insurance is derived from the shift timeline; manual entry would double-charge it.
    val cats = (if (type == "income") settings.cats.catsIn else settings.cats.catsOut)
        .filterNot { isDerivedCategory(it.id) }
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
                color = baki.onSurface,
                modifier = headerMod, style = BakiType.title )
                Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                    SegButton("Spending", type == "expense", Modifier.weight(1f)) { type = "expense"; cat = settings.cats.catsOut.firstOrNull()?.id ?: "other" }
                    SegButton("Income", type == "income", Modifier.weight(1f)) { type = "income"; cat = settings.cats.catsIn.firstOrNull()?.id ?: "otherin" }
                }
                if (baki.paper) LedgerRule(alpha = 0.16f)
                OutlinedTextField(
                    value = amount, onValueChange = { amount = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Amount (RM)") }, singleLine = true,
                    textStyle = if (baki.paper) BakiType.headlineNumeric else androidx.compose.material3.LocalTextStyle.current,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(), colors = bakiFieldColors(),
                )
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                    cats.forEach { c ->
                        val on = c.id == cat
                        if (baki.paper) {
                            Column(
                                Modifier.width(IntrinsicSize.Max).clickable { cat = c.id }
                                    .heightIn(min = Control.secondary)
                                    .padding(horizontal = Space.s),
                                verticalArrangement = Arrangement.Center,
                            ) {
                                Text(
                                    c.name, color = if (on) baki.primary else baki.muted,
                                    textDecoration = if (on) TextDecoration.Underline else null,
                                    style = BakiType.labelStrong,
                                )
                            }
                        } else {
                            Box(
                                Modifier.clip(RoundedCornerShape(Radius.pill))
                                    .background(if (on) baki.primaryContainer else baki.surfaceHigh)
                                    .clickable { cat = c.id }
                                    .padding(horizontal = Space.m, vertical = 8.dp),
                            ) { Text(c.name, color = if (on) baki.onPrimaryContainer else baki.muted, style = BakiType.labelStrong ) }
                        }
                    }
                }
                if (baki.paper) LedgerRule(alpha = 0.12f)
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
                                color = baki.muted, style = BakiType.caption,
                            )
                        },
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
                shape = RoundedCornerShape(Radius.pill),
                modifier = Modifier.fillMaxWidth().height(50.dp),
            ) {
                Text(
                    if (existing == null) "Add RM ${rm(cents)}" else "Save changes", style = BakiType.bodyStrong )
            }
        }
}

@Composable
private fun SegButton(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val baki = LocalBaki.current
    if (baki.paper) {
        Column(
            modifier.heightIn(min = Control.secondary).clickable { onClick() }
                .semantics { this.selected = selected; role = Role.Tab },
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                label, color = if (selected) baki.primary else baki.muted,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                style = BakiType.label,
            )
            if (selected) Box(Modifier.fillMaxWidth().height(2.dp).background(baki.primary))
        }
    } else {
        Box(
            modifier.clip(RoundedCornerShape(Radius.pill))
                .background(if (selected) baki.primaryContainer else baki.surfaceHigh)
                .clickable { onClick() }.padding(vertical = Space.s),
            contentAlignment = Alignment.Center,
        ) {
            Text(label, color = if (selected) baki.onPrimaryContainer else baki.muted,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, style = BakiType.label )
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

@Composable
private fun FormSection(label: String) {
    val baki = LocalBaki.current
    Spacer(Modifier.height(Space.s))
    Text(label, color = baki.muted, style = BakiType.section)
    LedgerRule(alpha = 0.14f)
    Spacer(Modifier.height(Space.s))
}

@OptIn(ExperimentalMaterial3Api::class)
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
    val baki = LocalBaki.current
    var date by remember { mutableStateOf(existing?.date ?: todayIso()) }
    var start by remember { mutableStateOf(existing?.start ?: "") }
    var hours by remember { mutableStateOf(existing?.hours?.takeIf { it > 0 }?.let { "%.1f".format(it) } ?: "") }
    var km by remember { mutableStateOf(existing?.km?.takeIf { it > 0 }?.let { "%.1f".format(it) } ?: "") }
    var gross by remember { mutableStateOf(existing?.gross?.takeIf { it > 0 }?.let { "%.2f".format(it / 100.0) } ?: "") }
    var extra by remember { mutableStateOf(existing?.extra?.takeIf { it > 0 }?.let { "%.2f".format(it / 100.0) } ?: "") }
    var reimbursed by remember { mutableStateOf(existing?.reimbursedTolls ?: false) }
    var kml by remember { mutableStateOf(existing?.kmPerLitre?.let { "%.1f".format(it) } ?: "") }
    var odoA by remember { mutableStateOf(existing?.odoStart?.let { "%.0f".format(it) } ?: "") }
    var odoB by remember { mutableStateOf(existing?.odoEnd?.let { "%.0f".format(it) } ?: "") }
    val odoStart = odoA.toDoubleOrNull()
    val odoEnd = odoB.toDoubleOrNull()

    val grossCents = ((gross.toDoubleOrNull() ?: 0.0) * 100).toLong()
    val hoursValue = hours.toDoubleOrNull() ?: 0.0
    val kmValue = km.toDoubleOrNull() ?: 0.0
    val kmlValue = kml.toDoubleOrNull()?.takeIf { it > 0 }

    val preview = remember(date, start, hoursValue, kmValue, grossCents, kmlValue, odoStart, odoEnd, extra, reimbursed) {
        val charge = existing?.grabFee ?: draftCoverCharge(store, date, start)
        val probe = Shift(
            id = "__preview", date = date, start = start, hours = hoursValue, km = kmValue,
            gross = grossCents, extra = ((extra.toDoubleOrNull() ?: 0.0) * 100).toLong(),
            reimbursedTolls = reimbursed,
            grabFee = charge, kmPerLitre = kmlValue, odoStart = odoStart, odoEnd = odoEnd,
        )
        shiftMetrics(probe, store.settings.profit, overheadPerHour(store, date.take(7)))
    }

    BakiSheet(onDismiss = onDismiss) {
            Text(if (existing == null) "Log a shift" else "Edit shift", color = baki.onSurface, style = BakiType.title )
                FormSection("WHEN")
                OutlinedTextField(value = date, onValueChange = { date = it }, label = { Text("Date (YYYY-MM-DD)") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(), colors = bakiFieldColors())
                PairFields(
                    first = { m -> OutlinedTextField(value = start, onValueChange = { start = it }, label = { Text("Start HH:MM") },
                        singleLine = true, modifier = m, colors = bakiFieldColors()) },
                    second = { m -> OutlinedTextField(value = hours, onValueChange = { hours = it.filter { c -> c.isDigit() || c == '.' } },
                        label = { Text("Hours") }, singleLine = true, modifier = m, colors = bakiFieldColors()) },
                )
                FormSection("PAY & DISTANCE")
                PairFields(
                    first = { m -> OutlinedTextField(value = km, onValueChange = { km = it.filter { c -> c.isDigit() || c == '.' } },
                        label = { Text("Km") }, singleLine = true, modifier = m, colors = bakiFieldColors()) },
                    second = { m -> OutlinedTextField(value = gross, onValueChange = { gross = it.filter { c -> c.isDigit() || c == '.' } },
                        label = { Text("Payout (RM)") }, singleLine = true, modifier = m, colors = bakiFieldColors()) },
                )
                PairFields(
                    first = { m -> OutlinedTextField(value = extra, onValueChange = { extra = it.filter { c -> c.isDigit() || c == '.' } },
                        label = { Text("Tolls (RM)") }, singleLine = true, modifier = m, colors = bakiFieldColors()) },
                    second = { m -> OutlinedTextField(value = kml, onValueChange = { kml = it.filter { c -> c.isDigit() || c == '.' } },
                        label = { Text("km/L (opt)") }, singleLine = true, modifier = m, colors = bakiFieldColors()) },
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Tolls reimbursed", color = baki.onSurface, style = BakiType.bodyStrong )
                        Text("Grab pays these back; excluded from running cost",
                            color = baki.muted, style = BakiType.caption )
                    }
                    Switch(checked = reimbursed, onCheckedChange = { reimbursed = it })
                }
                FormSection("ODOMETER")
                PairFields(
                    first = { m -> OutlinedTextField(value = odoA, onValueChange = { odoA = it.filter { c -> c.isDigit() || c == '.' } },
                        label = { Text("Odometer start") }, singleLine = true, modifier = m, colors = bakiFieldColors()) },
                    second = { m -> OutlinedTextField(value = odoB, onValueChange = { odoB = it.filter { c -> c.isDigit() || c == '.' } },
                        label = { Text("Odometer end") }, singleLine = true, modifier = m, colors = bakiFieldColors()) },
                )
                if (odoStart != null && odoEnd != null && odoEnd >= odoStart) {
                    Text("odometer says ${"%.1f".format(odoEnd - odoStart)} km — this wins over the Km field",
                        color = baki.muted, style = BakiType.caption )
                }
                // reconciliation: GPS vs odometer on the same shift
                val tracked = existing?.km?.takeIf { it > 0.5 }
                if (tracked != null && odoStart != null && odoEnd != null && odoEnd > odoStart) {
                    val odo = odoEnd - odoStart
                    val ratio = odo / tracked
                    Text(
                        "GPS tracked ${"%.1f".format(tracked)} km · odometer ${"%.1f".format(odo)} km · ×${"%.2f".format(ratio)}",
                        color = baki.muted, style = BakiType.caption )
                    if (ratio in 0.5..2.0) {
                        TextButton(onClick = { onCalibrate(ratio) }) {
                            Text(
                                "Use ×${"%.2f".format(ratio)} for future tracking",
                                color = baki.primary, style = BakiType.captionStrong )
                        }
                    }
                }
                Spacer(Modifier.height(Space.s))
                LedgerRule(alpha = 0.18f)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Cover", color = baki.muted, style = BakiType.caption, modifier = Modifier.weight(1f))
                    Text("RM ${rm(preview.grabFee)}", color = baki.onSurface, style = BakiType.captionNumeric)
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Estimated net", color = baki.muted, style = BakiType.caption, modifier = Modifier.weight(1f))
                    Text("RM ${rm(preview.net)}", color = baki.onSurface, style = BakiType.labelNumeric)
                }
                preview.netHr?.let { rate ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Net rate", color = baki.muted, style = BakiType.caption, modifier = Modifier.weight(1f))
                        Text("RM ${rm(rate)}/hr", color = baki.onSurface, style = BakiType.captionNumeric)
                    }
                }
                preview.grossPerKm?.let { perKm ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Gross per km", color = baki.muted, style = BakiType.caption, modifier = Modifier.weight(1f))
                        Text("RM ${rm(perKm)}/km", color = baki.onSurface, style = BakiType.captionNumeric)
                    }
                }
                LedgerRule(alpha = 0.18f)
                val rate = preview.netHr
                if (rate != null && hoursValue > 0 && rate < store.settings.profit.targetRate) {
                    val needed = grossToClearTarget(preview, store.settings.profit.targetRate)
                    Text(
                        "Below RM ${rm(store.settings.profit.targetRate)}/hr" +
                            (needed?.let { " · need RM ${rm(it)} gross" } ?: ""),
                        color = baki.down, style = BakiType.captionStrong,
                    )
                }
            if (existing != null) {
                if (existing.payouts.isNotEmpty()) {
                    TextButton(onClick = { onPayouts(existing) }) {
                        Text(
                            "Payouts (${existing.payouts.size})",
                            color = baki.primary, fontWeight = FontWeight.Bold,
                        )
                    }
                }
                TextButton(onClick = { onDelete(existing) }) {
                    Text("Delete shift", color = baki.down, fontWeight = FontWeight.Bold)
                }
            }
            Button(
                enabled = grossCents > 0 && hoursValue > 0,
                onClick = { onSave(date, start, hoursValue, kmValue, grossCents, ((extra.toDoubleOrNull() ?: 0.0) * 100).toLong(), kmlValue, odoStart, odoEnd, reimbursed) },
                colors = ButtonDefaults.buttonColors(containerColor = baki.primary, contentColor = baki.onPrimary),
                shape = RoundedCornerShape(Radius.pill),
                modifier = Modifier.fillMaxWidth().height(50.dp),
            ) { Text(if (existing == null) "Save shift" else "Save changes", style = BakiType.bodyStrong ) }
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
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(Radius.dialog),
        title = { Text("Vehicle costs & target", color = baki.onSurface, style = BakiType.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
                ProfitField("Fuel price RM/L", fuel, baki)
                ProfitField("Fuel economy km/L", kml, baki)
                ProfitField("Wear RM/km", wear, baki)
                ProfitField("Target RM/hr", target, baki)
                ProfitField("Average speed km/h", speed, baki)
                ProfitField("Grab insurance RM/day", cover, baki)
                Spacer(Modifier.height(Space.xs))
                Text("Fixed costs, spread over the hours you drive", color = baki.muted, style = BakiType.caption )
                ProfitField("Car loan RM/month", loan, baki)
                ProfitField("Road tax RM/year", roadTax, baki)
                ProfitField("Car insurance RM/year", carIns, baki)
                Text("Saving re-derives every shift's cost, cover and overhead.", color = baki.muted, style = BakiType.caption )
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
            Text("Add a payout", color = baki.onSurface, style = BakiType.title )
            OutlinedTextField(
                value = amount, onValueChange = { amount = it.filter { c -> c.isDigit() || c == '.' } },
                label = { Text("Amount (RM)") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(), colors = bakiFieldColors(),
            )
            Button(
                enabled = cents > 0, onClick = { onAdd(cents) },
                colors = ButtonDefaults.buttonColors(containerColor = baki.primary, contentColor = baki.onPrimary),
                shape = RoundedCornerShape(Radius.pill),
                modifier = Modifier.fillMaxWidth().height(50.dp),
            ) { Text("Add RM ${rm(cents)}", fontWeight = FontWeight.Bold) }
        }
}



@Composable
private fun BudgetSheet(repo: BakiRepo, onDismiss: () -> Unit) {
    val baki = LocalBaki.current
    val scope = rememberCoroutineScope()
    val cats = remember(repo.store) { repo.store.settings.cats.catsOut }.filter { it.id != "grabfee" }
    val draft = remember {
        mutableStateMapOf<String, String>().apply {
            repo.store.settings.budgets.forEach { (k, v) -> this[k] = "%.0f".format(v) }
        }
    }
    var foodCap by remember {
        mutableStateOf(repo.store.settings.dailyFoodCapCents?.let { "%.2f".format(it / 100.0) } ?: "")
    }
    var carry by remember { mutableStateOf(repo.store.settings.envelopeCarry) }
    var moveOpen by remember { mutableStateOf(false) }
    val canWrite = !repo.readOnly

    BakiSheet(
        onDismiss = onDismiss,
        footer = {
            Button(
                enabled = canWrite,
                onClick = {
                    val limits = draft.mapNotNull { (k, v) -> v.toDoubleOrNull()?.takeIf { it > 0 }?.let { k to it } }.toMap()
                    val cap = foodCap.toDoubleOrNull()?.takeIf { it > 0 }?.let { (it * 100).toLong() }
                    scope.launch {
                        repo.setDailyFoodCap(cap)
                        repo.setBudgets(limits)
                        repo.setEnvelopeCarry(carry)
                    }
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = baki.primary, contentColor = baki.onPrimary),
                shape = RoundedCornerShape(Radius.pill),
                modifier = Modifier.fillMaxWidth().height(50.dp),
            ) { Text("Save limits", fontWeight = FontWeight.Bold) }
        },
    ) {
        Text("Monthly envelopes", color = baki.onSurface, style = BakiType.title)
        if (!canWrite) Text("Read-only · enable writes in More → Allow writes", color = baki.warn, style = BakiType.caption)
        Text("Leave a field blank for no envelope. Base allocation applies every month.", color = baki.muted, style = BakiType.caption)
        Spacer(Modifier.height(Space.s))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Carry unspent forward", color = baki.onSurface, style = BakiType.labelStrong)
                Text("Positive remainder rolls into next month.", color = baki.muted, style = BakiType.caption)
            }
            Switch(checked = carry, onCheckedChange = { carry = it })
        }
        if (baki.paper) LedgerRule(alpha = 0.16f)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Daily food cap", color = baki.onSurface, modifier = Modifier.weight(1f), style = BakiType.labelStrong )
            OutlinedTextField(
                value = foodCap,
                onValueChange = { v -> foodCap = v.filter { it.isDigit() || it == '.' } },
                placeholder = { Text("—", color = baki.muted, style = BakiType.label ) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.width(110.dp),
                colors = bakiFieldColors(),
            )
        }
        Text("Optional daily guardrail for the Food category; clear it for none.", color = baki.muted, style = BakiType.caption)
        Spacer(Modifier.height(Space.s))
        if (baki.paper) LedgerRule(alpha = 0.16f)
        cats.forEachIndexed { index, c ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = Space.s)) {
                Icon(categoryIcon(c.id, c.name), contentDescription = null, tint = baki.primary, modifier = Modifier.size(IconSize.inline))
                Spacer(Modifier.width(Space.s))
                Text(c.name, color = baki.onSurface, modifier = Modifier.weight(1f), style = BakiType.label )
                OutlinedTextField(
                    value = draft[c.id] ?: "",
                    onValueChange = { v -> draft[c.id] = v.filter { it.isDigit() || it == '.' } },
                    placeholder = { Text("—", color = baki.muted, style = BakiType.label ) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.width(96.dp),
                    colors = bakiFieldColors(),
                )
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Move money between envelopes for this month.", color = baki.muted, style = BakiType.caption, modifier = Modifier.weight(1f))
            TextButton(onClick = { moveOpen = true }, enabled = cats.size > 1 && canWrite) {
                Text("Move", color = baki.primary, style = BakiType.labelStrong)
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
    val baki = LocalBaki.current
    val scope = rememberCoroutineScope()
    var from by remember { mutableStateOf(cats.firstOrNull()?.first ?: "") }
    var to by remember { mutableStateOf(cats.getOrNull(1)?.first ?: "") }
    var amount by remember { mutableStateOf("") }
    val value = amount.toDoubleOrNull() ?: 0.0
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(Radius.dialog),
        title = { Text("Move envelope money", color = baki.onSurface, style = BakiType.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
                Text("From", color = baki.muted, style = BakiType.section)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                    cats.forEach { (id, name) ->
                        val on = from == id
                        Text(
                            name, color = if (on) baki.primary else baki.muted,
                            modifier = Modifier.heightIn(min = Control.primary).clickable { from = id }.semantics { selected = on; role = Role.Tab },
                            style = BakiType.label,
                        )
                    }
                }
                Text("To", color = baki.muted, style = BakiType.section)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                    cats.forEach { (id, name) ->
                        val on = to == id
                        Text(
                            name, color = if (on) baki.primary else baki.muted,
                            modifier = Modifier.heightIn(min = Control.primary).clickable { to = id }.semantics { selected = on; role = Role.Tab },
                            style = BakiType.label,
                        )
                    }
                }
                OutlinedTextField(
                    value = amount, onValueChange = { amount = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Amount (RM)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(), colors = bakiFieldColors(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = from.isNotBlank() && to.isNotBlank() && from != to && value > 0.0,
                onClick = {
                    scope.launch { repo.moveEnvelope(month, from, to, value) }
                    onDismiss()
                },
            ) { Text("Move", color = baki.primary, style = BakiType.labelStrong) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = baki.muted) } },
    )
}
