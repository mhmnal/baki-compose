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
fun ServiceCard(repo: BakiRepo, onSetTarget: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val info = remember(repo.store) { serviceInfo(repo.store) }
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
        Text("User-set target, not a guessed interval.", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
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
        title = { Text("Next service", color = scheme.onSurface, style = MaterialTheme.typography.titleLarge ) },
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
