package app.baki

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.IconButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * A single day: chronological entries with a running margin, shifts, receipt
 * attachments and one note. The page lives above the normal tab shell.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayPage(
    repo: BakiRepo,
    day: String,
    onDay: (String) -> Unit,
    onClose: () -> Unit,
    onEditEntry: (Entry) -> Unit,
    onDeleteEntry: (Entry) -> Unit,
    onAddEntry: () -> Unit,
    onEditShift: (Shift) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val store = repo.store
    val scope = rememberCoroutineScope()
    val entries = remember(store, day) {
        entriesOn(store, day).sortedWith(compareBy<Entry> { it.loggedAt.ifBlank { it.updated } }.thenBy { it.id })
    }
    val shifts = remember(store, day) { shiftsOn(store, day).sortedBy { it.start } }
    val entriesWithRunning = remember(entries) {
        var balance = 0L
        entries.map { e ->
            balance += if (e.type == "income") e.amount else -e.amount
            e to balance
        }
    }
    val totals = remember(store, day) { dayTotals(store, day) }
    val note = remember(day) { store.dayNotes[day]?.text.orEmpty() }
    var noteText by remember(day) { mutableStateOf(note) }
    var savedNote by remember(day) { mutableStateOf(note) }
    var noteStatus by remember(day) { mutableStateOf("") }
    var pendingReceipt by remember { mutableStateOf<String?>(null) }
    var receiptStatus by remember { mutableStateOf("") }
    var preview by remember { mutableStateOf<String?>(null) }
    var confirmClose by remember(day) { mutableStateOf(false) }
    var sealStatus by remember(day) { mutableStateOf("") }
    val dayClose = store.dayCloses[day]
    val closed = dayClose?.closed == true
    val canWrite = !repo.readOnly
    val pickReceipt = rememberReceiptPicker { data ->
        val target = pendingReceipt
        pendingReceipt = null
        if (data != null && target != null) {
            scope.launch {
                receiptStatus = if (repo.setEntryReceipt(target, data)) "Receipt attached" else "Receipt is too large"
            }
        }
    }

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
        ) {
            MediumTopAppBar(
                title = {
                    Column {
                        Text(
                            if (day == todayIso()) "Today" else "Day page",
                            color = scheme.onSurface,
                            style = MaterialTheme.typography.titleLargeEmphasized,
                        )
                        Text(prettyDate(day), color = scheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { onDay(addDays(day, -1)) }) {
                        Text("‹", color = scheme.onSurface, style = MaterialTheme.typography.titleLarge)
                    }
                },
                actions = {
                    IconButton(onClick = { onDay(addDays(day, 1)) }) {
                        Text("›", color = scheme.onSurface, style = MaterialTheme.typography.titleLarge)
                    }
                    TextButton(onClick = onClose) {
                        Text("Close", color = scheme.primary, style = MaterialTheme.typography.labelLargeEmphasized)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
                scrollBehavior = scrollBehavior,
                windowInsets = WindowInsets(0, 0, 0, 0),
            )
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = Space.l),
                verticalArrangement = Arrangement.spacedBy(Space.s),
                contentPadding = PaddingValues(top = Space.s, bottom = 40.dp),
            ) {
                item {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Column(Modifier.weight(1f)) {
                            Text("DAY NET", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.titleSmall)
                            EasedMoney(totals.net, scheme.onSurface, MaterialTheme.typography.displaySmall.copy(fontFeatureSettings = "tnum"))
                            Text(
                                "${entries.size} entries · ${shifts.size} shifts · in RM ${rm(totals.income)} · out RM ${rm(totals.expense)}",
                                color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                            )
                        }
                        TextButton(onClick = onAddEntry) { Text("+ Entry", color = scheme.primary, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)) }
                    }
                }

                item {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (closed) "CLOSED · ${dayClose?.updated?.take(16)?.replace('T', ' ').orEmpty()}" else "PAGE OPEN",
                            color = if (closed) scheme.tertiary else scheme.onSurfaceVariant, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(
                            enabled = canWrite,
                            onClick = {
                                if (closed) scope.launch {
                                    sealStatus = if (repo.setDayClosed(day, false)) "Day reopened" else "Could not reopen · ${repo.lastError ?: "write blocked"}"
                                } else confirmClose = true
                            },
                        ) {
                            Text(if (closed) "Reopen day" else "Close the day", color = if (closed) scheme.onSurfaceVariant else scheme.primary, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                        }
                    }
                    if (!canWrite) Text("Read-only · enable writes in More → Allow writes", color = scheme.secondary, style = MaterialTheme.typography.bodyMedium)
                    else if (sealStatus.isNotEmpty()) Text(sealStatus, color = scheme.secondary, style = MaterialTheme.typography.bodyMedium)
                }

                item {
                    Text("ENTRIES · RUNNING", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(Space.xs))
                }
                if (entries.isEmpty()) {
                    item { EmptyState("No entries on this page", "Add a spending or income entry.") }
                } else {
                    entriesWithRunning.forEach { (entry, running) ->
                        item(key = "entry-${entry.id}") {
                            DayEntryRow(
                                entry = entry,
                                store = store,
                                runningCents = running,
                                onEdit = { onEditEntry(entry) },
                                onDelete = { onDeleteEntry(entry) },
                                onAttach = { if (canWrite) { pendingReceipt = entry.id; pickReceipt() } },
                                onRemoveReceipt = {
                                    if (canWrite) scope.launch {
                                        receiptStatus = if (repo.setEntryReceipt(entry.id, "")) "Receipt removed" else "Could not remove receipt"
                                    }
                                },
                                onPreview = { preview = entry.receipt },
                            )
                        }
                    }
                }
                if (receiptStatus.isNotEmpty()) item {
                    Text(receiptStatus, color = scheme.primary, style = MaterialTheme.typography.bodyMedium)
                }

                item {
                    Text("SHIFTS", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(Space.xs))
                }
                if (shifts.isEmpty()) {
                    item { Text("No shift recorded on this day.", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium) }
                } else {
                    shifts.forEach { shift ->
                        val m = shiftMetrics(shift, store.settings.profit, overheadPerHour(store, shift.date.take(7)))
                        item(key = "shift-${shift.id}") {
                            DayShiftRow(shift = shift, net = m.net, km = m.km, onEdit = { onEditShift(shift) })
                        }
                    }
                }

                item {
                    Text("PAGE NOTE", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(Space.xs))
                    Spacer(Modifier.height(Space.s))
                    OutlinedTextField(
                        value = noteText,
                        onValueChange = { noteText = it.take(500) },
                        placeholder = { Text("A line for this day…", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.labelLarge) },
                        minLines = 3,
                        enabled = canWrite,
                        modifier = Modifier.fillMaxWidth(),
                        colors = bakiFieldColorsPublic(),
                    )
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(noteStatus, color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        TextButton(
                            enabled = canWrite && noteText != savedNote,
                            onClick = {
                                scope.launch {
                                    val ok = repo.setDayNote(day, noteText)
                                    noteStatus = if (ok) "Saved" else "Could not save"
                                    if (ok) savedNote = noteText
                                }
                            },
                        ) { Text("Save note", color = if (noteText != savedNote) scheme.primary else scheme.onSurfaceVariant) }
                    }
                }
            }
        }

        preview?.let { receipt ->
            ReceiptPreview(receipt, onClose = { preview = null })
        }

        if (confirmClose) {
            AlertDialog(
                onDismissRequest = { confirmClose = false },
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = MaterialTheme.shapes.extraLarge,
                title = { Text("Close this day?", color = scheme.onSurface, style = MaterialTheme.typography.titleLarge) },
                text = {
                    Text(
                        "${entries.size} entries · ${shifts.size} shifts · net RM ${rm(totals.net)}. " +
                            "Closing marks the page reviewed. You can still edit it later.",
                        color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge,
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        confirmClose = false
                        scope.launch {
                            sealStatus = if (repo.setDayClosed(day, true)) "Day closed" else "Could not close · ${repo.lastError ?: "write blocked"}"
                        }
                    }) { Text("Close the day", color = scheme.primary, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)) }
                },
                dismissButton = { TextButton(onClick = { confirmClose = false }) { Text("Cancel", color = scheme.onSurfaceVariant) } },
            )
        }
    }
}

@Composable
private fun DayEntryRow(
    entry: Entry,
    store: Store,
    runningCents: Long,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onAttach: () -> Unit,
    onRemoveReceipt: () -> Unit,
    onPreview: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val bitmap = remember(entry.receipt) { entry.receipt?.takeIf { it.isNotBlank() }?.let(::decodeReceiptImage) }
    var menu by remember { mutableStateOf(false) }
    val receiptPress = remember { MutableInteractionSource() }
    val receiptPressed by receiptPress.collectIsPressedAsState()
    val receiptShape = morphChipShape(receiptPressed)
    Surface(
        modifier = Modifier.fillMaxWidth().padding(vertical = Space.xs),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.padding(horizontal = Space.m)) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = Control.primary)
                .combinedClickable(onClick = onEdit, onLongClick = { menu = true })
                .semantics { contentDescription = "${entry.note.ifEmpty { categoryLabel(store, entry.cat) }}, ${if (entry.type == "income") "income" else "expense"} RM ${rm(entry.amount)}" },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(categoryIcon(entry.cat), contentDescription = null, tint = scheme.primary, modifier = Modifier.size(IconSize.inline))
            Spacer(Modifier.width(Space.s))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(entry.note.ifEmpty { categoryLabel(store, entry.cat) }, color = scheme.onSurface, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold))
                    if (bitmap != null) {
                        Spacer(Modifier.width(Space.xs))
                        Surface(
                            shape = receiptShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.padding(2.dp),
                        ) {
                            Image(
                                bitmap = bitmap, contentDescription = "Receipt attached",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.padding(2.dp).size(22.dp)
                                    .clip(MaterialTheme.shapes.extraSmall)
                                    .clickable(
                                        interactionSource = receiptPress,
                                        indication = LocalIndication.current,
                                        onClick = onPreview,
                                    ),
                            )
                        }
                    }
                }
                Text(
                    listOfNotNull(categoryLabel(store, entry.cat), loggedTime(entry.loggedAt)?.let { "logged $it" }).joinToString(" · "),
                    color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    (if (entry.type == "income") "+" else "−") + rm(entry.amount),
                    color = if (entry.type == "income") scheme.tertiary else scheme.error, style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum", fontWeight = FontWeight.Bold),
                )
                Text("entry bal RM ${rm(runningCents)}", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
            }
            Box {
                Text(
                    "⋮", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.size(48.dp).clickable { menu = true }
                        .semantics { contentDescription = "Entry actions" },
                    textAlign = TextAlign.Center,
                )
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text(if (bitmap == null) "Attach receipt" else "Replace receipt", color = scheme.onSurface) },
                        onClick = { menu = false; onAttach() },
                    )
                    if (bitmap != null) {
                        DropdownMenuItem(
                            text = { Text("View receipt", color = scheme.onSurface) },
                            onClick = { menu = false; onPreview() },
                        )
                        DropdownMenuItem(
                            text = { Text("Remove receipt", color = scheme.error) },
                            onClick = { menu = false; onRemoveReceipt() },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Delete entry", color = scheme.error) },
                        onClick = { menu = false; onDelete() },
                    )
                }
            }
        }
        }
    }
}

@Composable
private fun DayShiftRow(shift: Shift, net: Double, km: Double, onEdit: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth().padding(vertical = Space.xs),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = Control.primary).clickable { onEdit() }.padding(horizontal = Space.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(BakiIcons.Car, contentDescription = null, tint = scheme.primary, modifier = Modifier.size(IconSize.inline))
        Spacer(Modifier.width(Space.s))
        Column(Modifier.weight(1f)) {
            Text("Shift ${shift.start.ifEmpty { shift.date }}", color = scheme.onSurface, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold))
            Text("${"%.1f".format(shift.hours)}h · ${"%.0f".format(km)} km · ${shift.payouts.size} payouts", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        }
        Text("RM ${rm(net)}", color = if (net < 0) scheme.error else scheme.tertiary, style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum", fontWeight = FontWeight.Bold))
    }
    }
}

@Composable
private fun ReceiptPreview(receipt: String, onClose: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val bitmap = remember(receipt) { decodeReceiptImage(receipt) }
    Box(Modifier.fillMaxSize().background(scheme.surface).statusBarsPadding().padding(Space.l)) {
        Column(Modifier.fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("RECEIPT", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                TextButton(onClick = onClose) { Text("Close", color = scheme.primary, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)) }
            }
            Spacer(Modifier.height(Space.m))
            if (bitmap != null) {
                Image(bitmap, contentDescription = "Receipt", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth().weight(1f))
            } else {
                Text("Receipt could not be read.", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}
