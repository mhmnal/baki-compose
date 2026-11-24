package app.baki

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * The short path from a widget, shortcut or live notification. It deliberately keeps only the
 * facts needed for a normal entry; date, odometer and receipt remain in the full form.
 */
@Composable
fun QuickCaptureSheet(
    request: QuickAddRequest,
    settings: Settings,
    onDismiss: () -> Unit,
    onMoreDetails: (cents: Long, note: String) -> Unit,
    onSave: (type: String, cents: Long, cat: String, note: String) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    var type by remember(request) { mutableStateOf(request.type) }
    val allCats = remember(type, settings) {
        (if (type == "income") settings.cats.catsIn else settings.cats.catsOut)
            .filterNot { isDerivedCategory(it.id) }
    }
    var cat by remember(type, request.category) {
        mutableStateOf(
            request.category?.takeIf { id -> allCats.any { it.id == id } }
                ?: allCats.firstOrNull()?.id
                ?: "other",
        )
    }
    val visibleCats = remember(allCats, cat) {
        val preferred = (listOf(cat) + allCats.map { it.id }).distinct().take(6)
        preferred.mapNotNull { id -> allCats.firstOrNull { it.id == id } }
    }
    var digits by remember(request) { mutableStateOf("") }
    var note by remember(request) { mutableStateOf("") }
    val cents = centsFromDigits(digits)
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(220)
        runCatching { focus.requestFocus() }
    }

    BakiSheet(
        onDismiss = onDismiss,
        footer = {
            Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                Button(
                    onClick = { if (cents > 0) onSave(type, cents, cat, note) },
                    enabled = cents > 0,
                    colors = ButtonDefaults.buttonColors(containerColor = scheme.primary, contentColor = scheme.onPrimary),
                    shape = CircleShape,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) { Text("Save", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)) }
                TextButton(onClick = { onMoreDetails(cents, note) }, modifier = Modifier.fillMaxWidth()) {
                    Text("More details · date, odometer, receipt", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(38.dp).background(scheme.tertiaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(if (type == "income") "+" else "−", color = scheme.onTertiaryContainer, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
            }
            Spacer(Modifier.width(Space.m))
            Column {
                Text("QUICK LOG", color = scheme.onSurface, letterSpacing = 1.sp, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                Text("Two taps and it is recorded.", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            }
        }

        ConnectedChoiceGroup(
            options = listOf("Spending", "Income"),
            selected = if (type == "income") "Income" else "Spending",
            onSelect = { choice ->
                type = if (choice == "Income") "income" else "expense"
                cat = if (type == "income") {
                    settings.cats.catsIn.firstOrNull()?.id ?: "otherin"
                } else {
                    settings.cats.catsOut.firstOrNull { !isDerivedCategory(it.id) }?.id ?: "other"
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )

        CentsField(
            digits = digits,
            onDigitsChange = { digits = it },
            label = "HOW MUCH?",
            textStyle = MaterialTheme.typography.displaySmallEmphasized.copy(fontFeatureSettings = "tnum"),
            shape = MaterialTheme.shapes.extraLarge,
            filled = true,
            focusRequester = focus,
            colors = androidx.compose.material3.TextFieldDefaults.colors(
                focusedContainerColor = scheme.secondaryContainer,
                unfocusedContainerColor = scheme.secondaryContainer,
                focusedTextColor = scheme.onSecondaryContainer,
                unfocusedTextColor = scheme.onSecondaryContainer,
                focusedLabelColor = scheme.onSecondaryContainer.copy(alpha = 0.72f),
                unfocusedLabelColor = scheme.onSecondaryContainer.copy(alpha = 0.72f),
                cursorColor = scheme.onSecondaryContainer,
                focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
            ),
        )

        Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
            Text("WHAT FOR?", color = scheme.onSurfaceVariant, letterSpacing = 1.sp, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s), verticalArrangement = Arrangement.spacedBy(Space.s)) {
                visibleCats.forEach { c ->
                    val selected = c.id == cat
                    Row(
                        Modifier
                            .heightIn(min = 44.dp)
                            .background(if (selected) scheme.primaryContainer else scheme.surfaceContainerHigh, RoundedCornerShape(50))
                            .clickable { cat = c.id }
                            .padding(horizontal = Space.m, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Space.xs),
                    ) {
                        Icon(categoryIcon(c.id), contentDescription = null, tint = if (selected) scheme.onPrimaryContainer else scheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                        Text(c.name, color = if (selected) scheme.onPrimaryContainer else scheme.onSurface, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }

        OutlinedTextField(
            value = note,
            onValueChange = { note = it },
            label = { Text("Note (optional)") },
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
