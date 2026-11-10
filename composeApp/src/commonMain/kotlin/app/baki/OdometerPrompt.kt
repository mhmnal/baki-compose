package app.baki

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/** Null unless the reading is positive and strictly newer than the last known odometer. */
fun validOdometer(text: String, lastOdo: Int?): Int? {
    val n = text.toIntOrNull() ?: return null
    if (n <= 0) return null
    if (lastOdo != null && n <= lastOdo) return null
    return n
}

/** A non-blocking follow-up to a saved fuel entry: add the dashboard reading or skip. */
@Composable
fun OdometerPromptDialog(
    label: String,
    cents: Long,
    lastOdo: Int?,
    onSave: (Int) -> Unit,
    onSkip: () -> Unit,
) {
    val baki = LocalBaki.current
    var text by remember { mutableStateOf("") }
    val parsed = validOdometer(text, lastOdo)
    val valid = parsed != null

    AlertDialog(
        onDismissRequest = onSkip,
        containerColor = baki.container,
        title = { Text("Add odometer?", color = baki.onSurface, style = BakiType.title ) },
        text = {
            Column {
                Text(
                    "$label · RM ${rm(cents)} is already saved.",
                    color = baki.onSurface, style = BakiType.bodyStrong,
                )
                Spacer(Modifier.height(Space.s))
                Text(
                    "Add the dashboard reading so real km/L and maintenance history stay accurate. " +
                        "Skip and we’ll ask again at the next fill.",
                    color = baki.muted, style = BakiType.caption,
                )
                if (lastOdo != null) {
                    Spacer(Modifier.height(Space.s))
                    Text("Last known reading: ${lastOdo} km", color = baki.muted, style = BakiType.captionNumeric )
                }
                Spacer(Modifier.height(Space.m))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.filter { c -> c.isDigit() } },
                    label = { Text("Dashboard odometer (total km)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    colors = bakiFieldColorsPublic(),
                    isError = text.isNotEmpty() && !valid,
                    supportingText = {
                        if (text.isNotEmpty() && !valid) {
                            Text(
                                if (lastOdo != null) "Must be higher than ${lastOdo} km"
                                else "Enter a positive reading",
                                color = baki.down, style = BakiType.caption,
                            )
                        }
                    },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { parsed?.let(onSave) }, enabled = valid) {
                Text("Save reading", color = if (valid) baki.primary else baki.muted)
            }
        },
        dismissButton = {
            TextButton(onClick = onSkip) { Text("Skip", color = baki.muted) }
        },
    )
}
