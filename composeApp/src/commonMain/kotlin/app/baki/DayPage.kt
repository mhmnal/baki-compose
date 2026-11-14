package app.baki

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
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
    val pickReceipt = rememberReceiptPicker { data ->
        val target = pendingReceipt
        pendingReceipt = null
        if (data != null && target != null) {
            scope.launch {
                receiptStatus = if (repo.setEntryReceipt(target, data)) "Receipt attached" else "Receipt is too large"
            }
        }
    }

    Box(Modifier.fillMaxSize().background(baki.surface)) {
        PaperGrain(Modifier.fillMaxSize())
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
            LedgerRule(alpha = 0.18f)
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
                                runningCents = running,
                                onEdit = { onEditEntry(entry) },
                                onDelete = { onDeleteEntry(entry) },
                                onAttach = { pendingReceipt = entry.id; pickReceipt() },
                                onRemoveReceipt = {
                                    scope.launch {
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
                        modifier = Modifier.fillMaxWidth(),
                        colors = bakiFieldColorsPublic(),
                    )
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(noteStatus, color = baki.muted, style = BakiType.caption, modifier = Modifier.weight(1f))
                        TextButton(
                            enabled = noteText != savedNote,
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
    }
}

@Composable
private fun DayEntryRow(
    entry: Entry,
    runningCents: Long,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onAttach: () -> Unit,
    onRemoveReceipt: () -> Unit,
    onPreview: () -> Unit,
) {
    val baki = LocalBaki.current
    val bitmap = remember(entry.receipt) { entry.receipt?.takeIf { it.isNotBlank() }?.let(::decodeReceiptImage) }
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = Control.primary).clickable { onEdit() }
                .semantics { contentDescription = "${entry.note.ifEmpty { entry.cat }}, ${if (entry.type == "income") "income" else "expense"} RM ${rm(entry.amount)}" },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(categoryIcon(entry.cat), contentDescription = null, tint = baki.primary, modifier = Modifier.size(IconSize.inline))
            Spacer(Modifier.width(Space.s))
            Column(Modifier.weight(1f)) {
                Text(entry.note.ifEmpty { entry.cat }, color = baki.onSurface, style = BakiType.bodyStrong)
                Text(
                    listOfNotNull(entry.cat, loggedTime(entry.loggedAt)?.let { "logged $it" }).joinToString(" · "),
                    color = baki.muted, style = BakiType.caption,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    (if (entry.type == "income") "+" else "−") + rm(entry.amount),
                    color = if (entry.type == "income") baki.up else baki.down, style = BakiType.labelNumeric,
                )
                Text("bal RM ${rm(runningCents)}", color = baki.muted, style = BakiType.nano)
            }
        }
        if (bitmap != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    bitmap = bitmap, contentDescription = "Receipt for ${entry.note.ifEmpty { entry.cat }}",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.height(64.dp).width(92.dp).clip(RoundedCornerShape(Radius.small))
                        .background(baki.surfaceHigh).clickable { onPreview() },
                )
                Spacer(Modifier.width(Space.s))
                TextButton(onClick = onAttach) { Text("Replace", color = baki.primary, style = BakiType.caption) }
                TextButton(onClick = onRemoveReceipt) { Text("Remove", color = baki.down, style = BakiType.caption) }
            }
        } else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onAttach) { Text("Attach receipt", color = baki.muted, style = BakiType.caption) }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDelete) { Text("Delete", color = baki.down, style = BakiType.caption) }
            }
        }
        LedgerRule(alpha = 0.12f)
    }
}

@Composable
private fun DayShiftRow(shift: Shift, net: Double, km: Double, onEdit: () -> Unit) {
    val baki = LocalBaki.current
    Row(
        Modifier.fillMaxWidth().heightIn(min = Control.primary).clickable { onEdit() },
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
    LedgerRule(alpha = 0.12f)
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
