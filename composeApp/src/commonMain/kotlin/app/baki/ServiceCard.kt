package app.baki

import androidx.compose.ui.text.font.FontWeight

import androidx.compose.material3.MaterialTheme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.datetime.LocalDate

@Composable
fun ServiceCard(repo: BakiRepo, onSetTarget: () -> Unit, onResetBaseline: () -> Unit = {}, onAddService: (() -> Unit)? = null) {
    val scheme = MaterialTheme.colorScheme
    val info = remember(repo.store) {
        serviceInfo(repo.store, repo.store.settings.serviceOdoBase, repo.store.settings.serviceOdoBaseOn)
    }
    val last = info.last

    Column(
        Modifier.fillMaxWidth().padding(vertical = Space.m),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("LAST SERVICE", color = scheme.onSurfaceVariant, letterSpacing = 1.sp, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold) )
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onSetTarget, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                Text("Set next", color = scheme.primary, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold) )
            }
            if (onAddService != null) {
                TextButton(onClick = onAddService, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                    Text("Add", color = scheme.primary, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                }
            }
        }
        if (last == null) {
            Text("No service logged yet", color = scheme.onSurface, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold) )
            Text("Add a Maintenance entry with the odometer.", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium )
        } else {
            Text(last.note.ifBlank { "Maintenance" }, color = scheme.onSurface, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold) )
            Text(
                prettyDate(last.date) +
                    (last.odo?.takeIf { it > 0 }?.let { " · $it km" } ?: " · odometer missing"),
                color = if (last.odo != null) scheme.onSurfaceVariant else scheme.secondary,
                style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
            )
        }

        Spacer(Modifier.height(Space.m))
        Text("NEXT SERVICE", color = scheme.onSurfaceVariant, letterSpacing = 1.sp, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold) )
        if (info.targetKm == null && info.targetOn == null) {
            Text("No target set — choose the km/date yourself.", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium )
        } else {
            Text(
                listOfNotNull(
                    info.targetKm?.let { "$it km" },
                    info.targetOn?.let { prettyDate(it) },
                ).joinToString(" · "),
                color = scheme.onSurface, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
            )
            val remaining = info.remainingKm
            if (remaining != null) {
                Text(
                    if (remaining > 0) "$remaining km to go" else "overdue by ${-remaining} km",
                    color = if (remaining > 0) scheme.onSurfaceVariant else scheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (info.overdue) Text("Target reached or date passed", color = scheme.error, style = MaterialTheme.typography.bodyMedium )
        }
        if (repo.store.settings.serviceOdoBase != null) {
            TextButton(onClick = onResetBaseline, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                Text("Reset baseline", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.labelLarge)
            }
            Text(
                "Baseline ${repo.store.settings.serviceOdoBase} km · ${repo.store.settings.serviceOdoBaseOn ?: "today"}",
                color = scheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall,
            )
        }
        Text("User-set target, not a guessed interval.", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)

        val history = remember(repo.store) { serviceHistory(repo.store) }
        if (history.size > 1) {
            Spacer(Modifier.height(Space.s))
            Text("SERVICE LOG", color = scheme.onSurfaceVariant, letterSpacing = 1.sp, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
            history.drop(1).forEach { entry ->
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        prettyDate(entry.date) + (entry.odo?.takeIf { it > 0 }?.let { " · $it km" } ?: ""),
                        color = scheme.onSurface, style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "RM ${rm(entry.amount / 100.0)}",
                        color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                    )
                }
            }
        }
        Text("Add a Maintenance entry in Spend to extend this log.", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
        Spacer(Modifier.height(Space.s))
    }
}

@Composable
fun ServiceTargetDialog(
    initialKm: Int?,
    initialDate: String?,
    onDismiss: () -> Unit,
    onSave: (Int?, String?) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    var km by remember { mutableStateOf(initialKm?.toString() ?: "") }
    var date by remember { mutableStateOf(initialDate ?: "") }
    var error by remember { mutableStateOf<String?>(null) }
    val cleanDate = date.trim()
    val validDate = cleanDate.isBlank() || runCatching { LocalDate.parse(cleanDate) }.isSuccess
    val cleanKm = km.trim().toIntOrNull()
    val validKm = km.isBlank() || (cleanKm != null && cleanKm > 0)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = scheme.surfaceContainerLow,
        title = { BakiDialogHeader("Next service", "when the car needs attention", "⌁") },
        text = {
            Column {
                Text(
                    "Set the odometer and/or date you want to aim for. Baki never guesses a service interval.",
                    color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(Space.m))
                OutlinedTextField(
                    value = km,
                    onValueChange = { km = it.filter { c -> c.isDigit() } },
                    label = { Text("Target odometer (km)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = !validKm,
                    modifier = Modifier.fillMaxWidth(),
                    colors = bakiFieldColorsPublic(),
                )
                Spacer(Modifier.height(Space.s))
                OutlinedTextField(
                    value = date,
                    onValueChange = { date = it },
                    label = { Text("Target date (YYYY-MM-DD)") },
                    singleLine = true,
                    isError = !validDate,
                    modifier = Modifier.fillMaxWidth(),
                    colors = bakiFieldColorsPublic(),
                )
                error?.let { Text(it, color = scheme.error, style = MaterialTheme.typography.bodyMedium ) }
                if (initialKm != null || initialDate != null) {
                    TextButton(
                        onClick = { onSave(null, null) },
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                    ) { Text("Clear target", color = scheme.error, style = MaterialTheme.typography.bodyMedium ) }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = validKm && validDate && (km.isNotBlank() || date.isNotBlank()),
                onClick = {
                    if (!validKm || !validDate) {
                        error = "Check the km and date."
                    } else {
                        onSave(cleanKm, cleanDate.ifBlank { null })
                    }
                },
            ) { Text("Save", color = scheme.primary) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = scheme.onSurfaceVariant) } },
    )
}

@Composable
fun ServiceBaselineDialog(
    initialKm: Int?,
    initialDate: String?,
    onSave: (Int?, String) -> Unit,
    onDismiss: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    var km by remember { mutableStateOf(initialKm?.toString() ?: "") }
    var date by remember { mutableStateOf(initialDate ?: todayIso()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = scheme.surfaceContainerHigh,
        title = { BakiDialogHeader("Service baseline", "new car or odometer reset", "◎") },
        text = {
            Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Space.s)) {
                Text(
                    "Set the current dashboard odometer and date. Older readings stay in history but stop driving the next target.",
                    color = scheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = km,
                    onValueChange = { km = it.filter { c -> c.isDigit() }.take(9) },
                    label = { Text("Current odometer (km)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    colors = bakiFieldColorsPublic(),
                )
                OutlinedTextField(
                    value = date,
                    onValueChange = { date = it.filter { c -> c.isDigit() || c == '-' }.take(10) },
                    label = { Text("Baseline date (YYYY-MM-DD)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = bakiFieldColorsPublic(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = (km.toIntOrNull() ?: 0) > 0 && date.length == 10,
                onClick = { onSave(km.toIntOrNull(), date) },
            ) { Text("Save baseline", color = scheme.primary, fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
