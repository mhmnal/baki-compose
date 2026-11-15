package app.baki

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Null unless the reading is positive and strictly newer than the last known odometer. */
fun validOdometer(text: String, lastOdo: Int?): Int? {
    val n = text.toIntOrNull() ?: return null
    if (n <= 0) return null
    if (lastOdo != null && n <= lastOdo) return null
    return n
}

/** A non-blocking follow-up to a saved fuel entry: add the dashboard reading or skip. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun OdometerPromptDialog(
    label: String,
    cents: Long,
    lastOdo: Int?,
    onSave: (Int) -> Unit,
    onSkip: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    var text by remember { mutableStateOf("") }
    val parsed = validOdometer(text, lastOdo)
    val valid = parsed != null

    BakiSheet(onDismiss = onSkip) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(46.dp)
                    .clip(MaterialShapes.SoftBurst.toShape())
                    .background(scheme.tertiaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Text("↗", color = scheme.onTertiaryContainer, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
            }
            Spacer(Modifier.width(Space.m))
            Column {
                Text(
                    "HOW FAR?",
                    color = scheme.onSurface,
                    letterSpacing = 1.sp,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                )
                Text(
                    "$label · RM ${rm(cents)} is safe.",
                    color = scheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        Text(
            "Add the dashboard reading so real km/L and maintenance history stay honest. Skip and we'll ask again next fill.",
            color = scheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
        SurfaceTintNote(lastOdo)
        TextField(
            value = text,
            onValueChange = { text = it.filter { c -> c.isDigit() } },
            leadingIcon = {
                Text(
                    "ODO",
                    color = scheme.onSecondaryContainer.copy(alpha = 0.72f),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                )
            },
            placeholder = {
                Text(
                    lastOdo?.let { "$it" } ?: "000000",
                    color = scheme.onSecondaryContainer.copy(alpha = 0.30f),
                    style = MaterialTheme.typography.displaySmallEmphasized.copy(fontFeatureSettings = "tnum"),
                )
            },
            label = { Text("DASHBOARD KM") },
            singleLine = true,
            textStyle = MaterialTheme.typography.displaySmallEmphasized.copy(fontFeatureSettings = "tnum"),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            isError = text.isNotEmpty() && !valid,
            supportingText = {
                if (text.isNotEmpty() && !valid) {
                    Text(
                        if (lastOdo != null) "Must be higher than ${lastOdo} km" else "Enter a positive reading",
                        color = scheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            },
            shape = MaterialTheme.shapes.extraLarge,
            modifier = Modifier.fillMaxWidth(),
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
        Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            Button(
                enabled = valid,
                onClick = { parsed?.let(onSave) },
                colors = ButtonDefaults.buttonColors(containerColor = scheme.primary, contentColor = scheme.onPrimary),
                shape = MaterialTheme.shapes.extraLarge,
                modifier = Modifier.weight(1f).height(50.dp),
            ) { Text("Save reading", fontWeight = FontWeight.Bold) }
            TextButton(onClick = onSkip) {
                Text("Skip", color = scheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SurfaceTintNote(lastOdo: Int?) {
    if (lastOdo == null) return
    Spacer(Modifier.height(Space.xs))
    Text(
        "Last known reading: ${lastOdo} km",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
    )
}
