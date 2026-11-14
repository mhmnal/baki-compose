package app.baki

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
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
 * A single day as a paper page: chronological entries with a running margin, shifts, receipt
 * attachments and one note. The page lives above the normal tab shell.
 */
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
    val baki = LocalBaki.current
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

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = Space.s, vertical = Space.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = { onDay(addDays(day, -1)) }) {
                    Text("‹", color = baki.onSurface, style = BakiType.body)
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (day == todayIso()) "Today" else "Day page", color = baki.onSurface, style = BakiType.bodyStrong)
                    Text(prettyDate(day), color = baki.muted, style = BakiType.caption)
                }
                TextButton(onClick = { onDay(addDays(day, 1)) }) {
                    Text("›", color = baki.onSurface, style = BakiType.body)
                }
                TextButton(onClick = onClose) {
                    Text("Close", color = baki.primary, style = BakiType.labelStrong)
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = Space.l),
                verticalArrangement = Arrangement.spacedBy(Space.s),
                contentPadding = PaddingValues(top = Space.s, bottom = 40.dp),
            ) {
                item {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Column(Modifier.weight(1f)) {
                            Text("DAY NET", color = baki.muted, style = BakiType.section)
                            EasedMoney(totals.net, baki.onSurface, BakiType.displaySmallNumeric)
                            Text(
                                "${entries.size} entries · ${shifts.size} shifts · in RM ${rm(totals.income)} · out RM ${rm(totals.expense)}",
                                color = baki.muted, style = BakiType.captionNumeric,
                            )
                        }
                        TextButton(onClick = onAddEntry) { Text("+ Entry", color = baki.primary, style = BakiType.labelStrong) }
                    }
                }

                item {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (closed) "CLOSED · ${dayClose?.updated?.take(16)?.replace('T', ' ').orEmpty()}" else "PAGE OPEN",
                            color = if (closed) baki.up else baki.muted, style = BakiType.captionStrong,
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
                            Text(if (closed) "Reopen day" else "Close the day", color = if (closed) baki.muted else baki.primary, style = BakiType.labelStrong)
                        }
                    }
                    if (!canWrite) Text("Read-only · enable writes in More → Allow writes", color = baki.warn, style = BakiType.caption)
                    else if (sealStatus.isNotEmpty()) Text(sealStatus, color = baki.warn, style = BakiType.caption)
                }

                item {
                    Text("ENTRIES · RUNNING", color = baki.muted, style = BakiType.section)
                    Spacer(Modifier.height(Space.xs))
                    LedgerRule(alpha = 0.14f)
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
                    Text(receiptStatus, color = baki.primary, style = BakiType.caption)
                }

                item {
                    Text("SHIFTS", color = baki.muted, style = BakiType.section)
                    Spacer(Modifier.height(Space.xs))
                    LedgerRule(alpha = 0.14f)
                }
                if (shifts.isEmpty()) {
                    item { Text("No shift recorded on this day.", color = baki.muted, style = BakiType.caption) }
                } else {
                    shifts.forEach { shift ->
                        val m = shiftMetrics(shift, store.settings.profit, overheadPerHour(store, shift.date.take(7)))
                        item(key = "shift-${shift.id}") {
                            DayShiftRow(shift = shift, net = m.net, km = m.km, onEdit = { onEditShift(shift) })
                        }
                    }
                }

                item {
                    Text("PAGE NOTE", color = baki.muted, style = BakiType.section)
                    Spacer(Modifier.height(Space.xs))
                    LedgerRule(alpha = 0.14f)
                    Spacer(Modifier.height(Space.s))
                    OutlinedTextField(
                        value = noteText,
                        onValueChange = { noteText = it.take(500) },
                        placeholder = { Text("A line for this day…", color = baki.muted, style = BakiType.label) },
                        minLines = 3,
                        enabled = canWrite,
                        modifier = Modifier.fillMaxWidth(),
                        colors = bakiFieldColorsPublic(),
                    )
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(noteStatus, color = baki.muted, style = BakiType.caption, modifier = Modifier.weight(1f))
                        TextButton(
                            enabled = canWrite && noteText != savedNote,
                            onClick = {
                                scope.launch {
                                    val ok = repo.setDayNote(day, noteText)
                                    noteStatus = if (ok) "Saved" else "Could not save"
                                    if (ok) savedNote = noteText
                                }
                            },
                        ) { Text("Save note", color = if (noteText != savedNote) baki.primary else baki.muted) }
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
                shape = RoundedCornerShape(Radius.dialog),
                title = { Text("Close this day?", color = baki.onSurface, style = BakiType.title) },
                text = {
                    Text(
                        "${entries.size} entries · ${shifts.size} shifts · net RM ${rm(totals.net)}. " +
                            "Closing marks the page reviewed. You can still edit it later.",
                        color = baki.muted, style = BakiType.body,
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        confirmClose = false
                        scope.launch {
                            sealStatus = if (repo.setDayClosed(day, true)) "Day closed" else "Could not close · ${repo.lastError ?: "write blocked"}"
                        }
                    }) { Text("Close the day", color = baki.primary, style = BakiType.labelStrong) }
                },
                dismissButton = { TextButton(onClick = { confirmClose = false }) { Text("Cancel", color = baki.muted) } },
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
    val baki = LocalBaki.current
    val bitmap = remember(entry.receipt) { entry.receipt?.takeIf { it.isNotBlank() }?.let(::decodeReceiptImage) }
    var menu by remember { mutableStateOf(false) }
    Surface(
        modifier = Modifier.fillMaxWidth().padding(vertical = Space.xs),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.padding(horizontal = Space.m)) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = Control.primary).clickable { onEdit() }
                .semantics { contentDescription = "${entry.note.ifEmpty { categoryLabel(store, entry.cat) }}, ${if (entry.type == "income") "income" else "expense"} RM ${rm(entry.amount)}" },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(categoryIcon(entry.cat), contentDescription = null, tint = baki.primary, modifier = Modifier.size(IconSize.inline))
            Spacer(Modifier.width(Space.s))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(entry.note.ifEmpty { categoryLabel(store, entry.cat) }, color = baki.onSurface, style = BakiType.bodyStrong)
                    if (bitmap != null) {
                        Spacer(Modifier.width(Space.xs))
                        Image(
                            bitmap = bitmap, contentDescription = "Receipt attached",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(24.dp).clip(RoundedCornerShape(Radius.small)).clickable { onPreview() },
                        )
                    }
                }
                Text(
                    listOfNotNull(categoryLabel(store, entry.cat), loggedTime(entry.loggedAt)?.let { "logged $it" }).joinToString(" · "),
                    color = baki.muted, style = BakiType.caption,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    (if (entry.type == "income") "+" else "−") + rm(entry.amount),
                    color = if (entry.type == "income") baki.up else baki.down, style = BakiType.labelNumeric,
                )
                Text("entry bal RM ${rm(runningCents)}", color = baki.muted, style = BakiType.nano)
            }
            Box {
                Text(
                    "⋮", color = baki.muted, style = BakiType.bodyStrong,
                    modifier = Modifier.size(48.dp).clickable { menu = true }
                        .semantics { contentDescription = "Entry actions" },
                    textAlign = TextAlign.Center,
                )
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text(if (bitmap == null) "Attach receipt" else "Replace receipt", color = baki.onSurface) },
                        onClick = { menu = false; onAttach() },
                    )
                    if (bitmap != null) {
                        DropdownMenuItem(
                            text = { Text("View receipt", color = baki.onSurface) },
                            onClick = { menu = false; onPreview() },
                        )
                        DropdownMenuItem(
                            text = { Text("Remove receipt", color = baki.down) },
                            onClick = { menu = false; onRemoveReceipt() },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Delete entry", color = baki.down) },
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
    val baki = LocalBaki.current
    Surface(
        modifier = Modifier.fillMaxWidth().padding(vertical = Space.xs),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = Control.primary).clickable { onEdit() }.padding(horizontal = Space.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(BakiIcons.Car, contentDescription = null, tint = baki.primary, modifier = Modifier.size(IconSize.inline))
        Spacer(Modifier.width(Space.s))
        Column(Modifier.weight(1f)) {
            Text("Shift ${shift.start.ifEmpty { shift.date }}", color = baki.onSurface, style = BakiType.bodyStrong)
            Text("${"%.1f".format(shift.hours)}h · ${"%.0f".format(km)} km · ${shift.payouts.size} payouts", color = baki.muted, style = BakiType.caption)
        }
        Text("RM ${rm(net)}", color = if (net < 0) baki.down else baki.up, style = BakiType.labelNumeric)
    }
    }
}

@Composable
private fun ReceiptPreview(receipt: String, onClose: () -> Unit) {
    val baki = LocalBaki.current
    val bitmap = remember(receipt) { decodeReceiptImage(receipt) }
    Box(Modifier.fillMaxSize().background(baki.surface).statusBarsPadding().padding(Space.l)) {
        Column(Modifier.fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("RECEIPT", color = baki.muted, style = BakiType.section, modifier = Modifier.weight(1f))
                TextButton(onClick = onClose) { Text("Close", color = baki.primary, style = BakiType.labelStrong) }
            }
            LedgerRule(alpha = 0.18f)
            Spacer(Modifier.height(Space.m))
            if (bitmap != null) {
                Image(bitmap, contentDescription = "Receipt", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth().weight(1f))
            } else {
                Text("Receipt could not be read.", color = baki.muted, style = BakiType.body)
            }
        }
    }
}
