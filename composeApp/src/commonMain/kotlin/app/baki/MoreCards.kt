package app.baki

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/** Read-only view of the last imported Grab statement. */
@Composable
fun StatementCard(store: Store, onViewHistory: (() -> Unit)? = null) {
    val baki = LocalBaki.current
    val st = store.statements.values.maxByOrNull { it.weekStart } ?: return
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.card)).background(baki.container).padding(Space.l),
    ) {
        Text("LAST STATEMENT · ${st.weekStart}", color = baki.muted, style = BakiType.captionStrong )
        Spacer(Modifier.height(Space.s))
        Text("RM ${rm(st.income)} gross", color = baki.onSurface, style = BakiType.title )
        Text(
            "${st.insuranceCount} insurance charge(s) · RM ${rm(st.deductions)} deducted",
            color = baki.muted, style = BakiType.caption )
        if (st.rangeText.isNotEmpty()) {
            Text(st.rangeText, color = baki.muted, style = BakiType.caption )
        }
        Text("imported ${st.importedAt.take(16).replace('T', ' ')}", color = baki.muted, style = BakiType.caption )
        if (onViewHistory != null && store.statements.size > 1) {
            TextButton(onClick = onViewHistory, contentPadding = PaddingValues(0.dp)) {
                Text("View all ${store.statements.size} weeks", color = baki.primary, style = BakiType.captionStrong )
            }
        }
    }
}

/** All imported weekly statements, newest first; week-level reconciliation, no day stamps. */
@Composable
fun StatementHistorySheet(store: Store, onDismiss: () -> Unit) {
    val baki = LocalBaki.current
    val rows = store.statements.values.sortedByDescending { it.weekStart }
    BakiSheet(onDismiss = onDismiss) {
        Text("Statement history", color = baki.onSurface, style = BakiType.title )
        Text(
            "${rows.size} imported week${if (rows.size == 1) "" else "s"} · income and insurance reconciliation",
            color = baki.muted, style = BakiType.caption,
        )
        rows.forEach { st ->
            val check = statementCheck(store, st)
            val gap = check.loggedGross / 100.0 - st.income
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.card)).background(baki.surfaceHigh).padding(Space.l),
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        st.rangeText.ifBlank { "Week of ${prettyDate(st.weekStart)}" },
                        color = baki.onSurface, style = BakiType.labelStrong,
                        modifier = Modifier.weight(1f),
                    )
                    Text("RM ${rm(st.income)}", color = baki.onSurface, style = BakiType.titleNumeric )
                }
                Spacer(Modifier.height(Space.xs))
                Text(
                    "deductions RM ${rm(st.deductions)} · weekly RM ${rm(st.weekly)}",
                    color = baki.muted, style = BakiType.captionNumeric,
                )
                Text(
                    "insurance: app ${check.appInsurance} vs statement ${st.insuranceCount}" +
                        if (check.insuranceMatches) " ✓" else " — check the cover rule",
                    color = if (check.insuranceMatches) baki.up else baki.down,
                    style = BakiType.caption,
                )
                if (check.loggedShifts == 0) {
                    Text("no shifts logged for this week", color = baki.muted, style = BakiType.caption )
                } else {
                    Text(
                        "logged ${check.loggedShifts} shifts · RM ${rm(check.loggedGross / 100.0)} · gap RM ${rm(gap)}",
                        color = if (kotlin.math.abs(gap) < 1.0) baki.up else baki.warn,
                        style = BakiType.captionNumeric,
                    )
                }
                Text(
                    "imported ${st.importedAt.take(16).replace('T', ' ')}",
                    color = baki.muted, style = BakiType.nano,
                )
            }
        }
    }
}

/** Add or remove spending categories; a category still in use can't be deleted. */
@Composable
fun CategoriesCard(repo: BakiRepo, editable: Boolean = true) {
    val baki = LocalBaki.current
    val scope = rememberCoroutineScope()
    var adding by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }
    var renaming by remember { mutableStateOf<Cat?>(null) }
    var renameDraft by remember { mutableStateOf("") }
    val cats = repo.store.settings.cats.catsOut
    val usage = remember(repo.store) {
        repo.store.entries.filter { it.type != "income" }.groupingBy { it.cat }.eachCount()
    }

    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.card)).background(baki.container).padding(Space.l)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Spending categories", color = baki.onSurface, style = BakiType.bodyStrong )
                Text("${cats.size} categories · tap a name to rename, × to remove", color = baki.muted, style = BakiType.caption )
            }
            TextButton(enabled = editable, onClick = { adding = true }) {
                Text("Add", color = if (editable) baki.primary else baki.muted, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(Space.s))
        cats.forEach { c ->
            val used = usage[c.id] ?: 0
            Row(
                Modifier.fillMaxWidth().padding(vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(categoryIcon(c.id, c.name), contentDescription = null, tint = baki.primary, modifier = Modifier.size(IconSize.inline))
                Spacer(Modifier.width(Space.s))
                Text(
                    c.name, color = baki.onSurface,
                    modifier = Modifier.weight(1f)
                        .clickable(enabled = editable) { renaming = c; renameDraft = c.name },
                    style = BakiType.label,
                )
                Text(if (used > 0) "$used" else "", color = baki.muted, style = BakiType.caption )
                Spacer(Modifier.width(Space.s))
                Text(
                    "×",
                    color = if (used > 0 || !editable) baki.muted.copy(alpha = 0.35f) else baki.down,
                    modifier = Modifier
                        .clip(RoundedCornerShape(Radius.pill))
                        .clickable(enabled = used == 0 && editable) {
                            scope.launch { repo.removeCategory(c.id) }
                        }
                        .padding(horizontal = Space.m), style = BakiType.bodyStrong )
            }
        }
    }

    renaming?.let { cat ->
        AlertDialog(
            onDismissRequest = { renaming = null },
            containerColor = baki.container,
            title = { Text("Rename category", color = baki.onSurface, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = renameDraft, onValueChange = { renameDraft = it },
                    label = { Text("Name") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(), colors = bakiFieldColorsPublic(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val clean = renameDraft.trim()
                        if (clean.isNotBlank()) scope.launch { repo.renameCategory(cat.id, clean) }
                        renaming = null
                    },
                    enabled = renameDraft.isNotBlank(),
                ) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("Cancel") } },
        )
    }

    if (adding) {
        AlertDialog(
            onDismissRequest = { adding = false },
            containerColor = baki.container,
            title = { Text("New category", color = baki.onSurface, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = draft, onValueChange = { draft = it },
                    label = { Text("Name") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(), colors = bakiFieldColorsPublic(),
                )
            },
            confirmButton = {
                TextButton(
                    enabled = draft.isNotBlank(),
                    onClick = {
                        scope.launch { repo.addCategory(draft.trim()) }
                        draft = ""
                        adding = false
                    },
                ) { Text("Add", color = baki.primary, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { adding = false }) { Text("Cancel", color = baki.muted) } },
        )
    }
}
