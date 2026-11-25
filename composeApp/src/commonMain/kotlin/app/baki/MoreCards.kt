package app.baki

import androidx.compose.foundation.shape.CircleShape

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch


/** All imported weekly statements, newest first; week-level reconciliation, no day stamps. */
@Composable
fun StatementHistorySheet(repo: BakiRepo, onDismiss: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val store = repo.store
    val scope = rememberCoroutineScope()
    val rows = store.statements.values.sortedByDescending { it.weekStart }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.extraLarge,
        title = { BakiDialogHeader("Statement history", "the paper trail", "▤") },
        text = {
        Column(Modifier.verticalScroll(rememberScrollState())) {
        Text(
            "${rows.size} imported week${if (rows.size == 1) "" else "s"} · income and insurance reconciliation",
            color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium,
        )
        rows.forEachIndexed { index, st ->
            val check = statementCheck(store, st)
            val gap = check.loggedGross / 100.0 - st.income
            Column(
                Modifier.fillMaxWidth().padding(vertical = Space.m),
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        st.rangeText.ifBlank { "Week of ${prettyDate(st.weekStart)}" },
                        color = scheme.onSurface, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.weight(1f),
                    )
                    Text("RM ${rm(st.income)}", color = scheme.onSurface, style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum") )
                }
                Spacer(Modifier.height(Space.xs))
                Text(
                    "gross RM ${rm(st.income)} − deductions RM ${rm(kotlin.math.abs(st.deductions))} = weekly RM ${rm(st.weekly)}",
                    color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                )
                Text(
                    when {
                        !check.insuranceKnown -> "insurance: app ${check.appInsurance} charges · statement count not recorded"
                        check.insuranceMatches -> "insurance: app ${check.appInsurance} · statement ${check.statementInsurance} ✓"
                        else -> "insurance: app ${check.appInsurance} vs statement ${check.statementInsurance} — check the cover rule"
                    },
                    color = if (check.insuranceKnown && check.insuranceMatches) scheme.tertiary else scheme.secondary,
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (check.loggedShifts == 0) {
                    Text("no shifts logged for this week", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                } else {
                    Text(
                        "gross logged RM ${rm(check.loggedGross / 100.0)} · statement RM ${rm(st.income)} · gap RM ${rm(gap)}",
                        color = if (kotlin.math.abs(gap) < 1.0) scheme.tertiary else scheme.secondary,
                        style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                    )
                }
                val mark = store.statementMarks[st.weekStart]
                val matches = check.loggedShifts > 0 && kotlin.math.abs(gap) < 1.0 && check.insuranceMatches
                Text(
                    when {
                        matches -> "MATCHED · gross and insurance counts agree"
                        check.loggedShifts == 0 -> "NOT LOGGED · import has no matching shifts yet"
                        !check.insuranceKnown -> "DIFFERENCE · compare gross above; re-import for insurance count"
                        else -> "DIFFERENCE · compare the gross and insurance lines above"
                    },
                    color = if (matches) scheme.tertiary else scheme.secondary,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                )
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (mark?.reconciled == true) "reconciled ${mark.updated.take(16).replace('T', ' ')}"
                        else "not reconciled",
                        color = if (mark?.reconciled == true) scheme.tertiary else scheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        enabled = !repo.readOnly,
                        onClick = { scope.launch { repo.setStatementReconciled(st.weekStart, mark?.reconciled != true) } },
                    ) {
                        Text(
                            if (mark?.reconciled != true) "Mark reconciled" else "Reopen",
                            color = if (mark?.reconciled != true) scheme.primary else scheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        )
                    }
                }
                Text(
                    "imported ${st.importedAt.take(16).replace('T', ' ')}",
                    color = scheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall,
                )
            }
        }
        }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done", color = scheme.primary, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)) } },
    )
}

/** Add or remove spending categories; a category still in use can't be deleted. */
@Composable
fun CategoriesCard(repo: BakiRepo, editable: Boolean = true) {
    val scheme = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    var adding by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }
    var renaming by remember { mutableStateOf<Cat?>(null) }
    var renameDraft by remember { mutableStateOf("") }
    val cats = repo.store.settings.cats.catsOut
    val usage = remember(repo.store) {
        repo.store.entries.filter { it.type != "income" }.groupingBy { it.cat }.eachCount()
    }

    Column(Modifier.fillMaxWidth().padding(vertical = Space.m)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Spending categories", color = scheme.onSurface, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold) )
                Text("${cats.size} categories · tap a name to rename, × to remove", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium )
            }
            TextButton(enabled = editable, onClick = { adding = true }) {
                Text("Add", color = if (editable) scheme.primary else scheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(Space.s))
        cats.forEachIndexed { index, c ->
            val used = usage[c.id] ?: 0
            Row(
                Modifier.fillMaxWidth().padding(vertical = Space.s),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(categoryIcon(c.id, c.name), contentDescription = null, tint = scheme.primary, modifier = Modifier.size(IconSize.inline))
                Spacer(Modifier.width(Space.s))
                Text(
                    c.name, color = scheme.onSurface,
                    modifier = Modifier.weight(1f).heightIn(min = Control.primary)
                        .clickable(enabled = editable) { renaming = c; renameDraft = c.name }
                        .semantics { if (editable) role = Role.Button },
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(if (used > 0) "$used" else "", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium )
                Spacer(Modifier.width(Space.s))
                Text(
                    "×",
                    color = if (used > 0 || !editable) scheme.onSurfaceVariant.copy(alpha = 0.35f) else scheme.error,
                    modifier = Modifier
                        .heightIn(min = Control.primary)
                        .clip(CircleShape)
                        .clickable(enabled = used == 0 && editable) {
                            scope.launch { repo.removeCategory(c.id) }
                        }
                        .semantics {
                            contentDescription = "Remove ${c.name}"
                            if (used == 0 && editable) role = Role.Button
                        }
                        .padding(horizontal = Space.m), style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold) )
            }
        }
    }

    renaming?.let { cat ->
        AlertDialog(
            onDismissRequest = { renaming = null },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = { BakiDialogHeader("Rename category", "keep the label personal", "A") },
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
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = { BakiDialogHeader("New category", "make it yours", "+") },
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
                ) { Text("Add", color = scheme.primary, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { adding = false }) { Text("Cancel", color = scheme.onSurfaceVariant) } },
        )
    }
}
