package app.baki

import androidx.compose.foundation.shape.CircleShape

import androidx.compose.material3.MaterialTheme

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TextField
import androidx.compose.material3.toShape
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * The payout breakdown for one shift — live or already stored.
 *
 * Payouts are identified by index: `Payout` carries only (cents, timestamp) so it has no stable id
 * to address; the list order is the identity.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PayoutsSheet(
    title: String,
    payouts: List<Payout>,
    editable: Boolean,
    onEdit: (index: Int, cents: Long) -> Unit,
    onDelete: (index: Int) -> Unit,
    onAdd: (cents: Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    var editing by remember { mutableStateOf<Int?>(null) }
    var adding by remember { mutableStateOf(false) }
    val total = payouts.sumOf { it.c }

    BakiSheet(
        onDismiss = onDismiss,
        footer = if (editable) {
            {
                Button(
                    onClick = { adding = true },
                    colors = ButtonDefaults.buttonColors(containerColor = scheme.primary, contentColor = scheme.onPrimary),
                    shape = CircleShape,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                ) { Text("Add payout", fontWeight = FontWeight.Bold) }
            }
        } else {
            null
        },
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(scheme.tertiaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Text("+", color = scheme.onTertiaryContainer, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
            }
            Spacer(Modifier.width(Space.m))
            Column {
                Text("PAYOUTS", color = scheme.onSurfaceVariant, letterSpacing = 1.2.sp, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                Text(title, color = scheme.onSurface, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Text(
                if (payouts.size == 1) "1 payout" else "${payouts.size} payouts",
                color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f),
            )
            Text("RM ${rm(total)}", color = scheme.onSurface, style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"))
        }
        if (editable) Text("tap to edit · swipe to delete", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(Space.s))

        payouts.forEachIndexed { index, payout ->
            val dismiss = rememberSwipeToDismissBoxState()
            SwipeToDismissBox(
                state = dismiss,
                modifier = Modifier.clip(MaterialTheme.shapes.large),
                enableDismissFromStartToEnd = false,
                enableDismissFromEndToStart = editable,
                backgroundContent = {
                    Row(
                        Modifier.fillMaxSize()
                            .clip(MaterialTheme.shapes.large)
                            .background(scheme.error.copy(alpha = 0.22f))
                            .padding(horizontal = Space.ml),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End,
                    ) {
                        Text("Delete", color = scheme.error, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                    }
                },
                onDismiss = { if (editable) onDelete(index) },
            ) {
                Column(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth()
                        .clip(MaterialTheme.shapes.large)
                        .background(scheme.surfaceContainerLow)
                        .clickable(enabled = editable) { editing = index }
                        .heightIn(min = Control.primary)
                        .padding(horizontal = Space.m, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(scheme.tertiaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("•", color = scheme.onTertiaryContainer, style = MaterialTheme.typography.labelLarge)
                    }
                    Spacer(Modifier.width(Space.m))
                    Text(
                        clockTime(payout.at),
                        color = scheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                    )
                    Spacer(Modifier.width(Space.m))
                    Text(
                        "RM ${rm(payout.c)}",
                        color = scheme.onSurface,
                        style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum", fontWeight = FontWeight.Bold),
                        modifier = Modifier.weight(1f),
                    )
                    if (editable) {
                        Text("Edit", color = scheme.primary, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                }
            }
        }
    }

    adding.takeIf { it }?.let {
        PayoutAmountDialog(
            title = "Add payout",
            initialCents = 0,
            confirm = "Add",
            onConfirm = { cents -> onAdd(cents); adding = false },
            onDismiss = { adding = false },
        )
    }

    editing?.let { index ->
        payouts.getOrNull(index)?.let { payout ->
            PayoutAmountDialog(
                title = "Fix payout",
                initialCents = payout.c,
                confirm = "Save",
                onConfirm = { cents -> onEdit(index, cents); editing = null },
                onDismiss = { editing = null },
            )
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PayoutAmountDialog(
    title: String,
    initialCents: Long,
    confirm: String,
    onConfirm: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    var text by remember { mutableStateOf(if (initialCents > 0) "%.2f".format(initialCents / 100.0) else "") }
    val amount = text.toDoubleOrNull()?.let { cents(it) } ?: 0L
    BakiSheet(onDismiss = onDismiss) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(scheme.tertiaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Text("+", color = scheme.onTertiaryContainer, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
            }
            Spacer(Modifier.width(Space.m))
            Column {
                Text(title.uppercase(), color = scheme.onSurface, letterSpacing = 1.sp, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                Text("log the cash.", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            }
        }
        TextField(
            value = text,
            onValueChange = { raw -> text = raw.filter { it.isDigit() || it == '.' } },
            leadingIcon = {
                Text("RM", color = scheme.onSecondaryContainer.copy(alpha = 0.72f), style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
            },
            placeholder = {
                Text("0.00", color = scheme.onSecondaryContainer.copy(alpha = 0.30f), style = MaterialTheme.typography.displaySmallEmphasized.copy(fontFeatureSettings = "tnum"))
            },
            label = { Text("HOW MUCH?") },
            singleLine = true,
            textStyle = MaterialTheme.typography.displaySmallEmphasized.copy(fontFeatureSettings = "tnum"),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
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
        Button(
            enabled = amount > 0,
            onClick = { onConfirm(amount) },
            colors = ButtonDefaults.buttonColors(containerColor = scheme.primary, contentColor = scheme.onPrimary),
            shape = CircleShape,
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) { Text("$confirm RM ${rm(amount)}", fontWeight = FontWeight.Bold) }
        TextButton(onClick = onDismiss) {
            Text("Cancel", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** Local HH:mm for a payout timestamp. */
fun clockTime(at: Long): String {
    val local = Instant.fromEpochMilliseconds(at)
        .toLocalDateTime(TimeZone.currentSystemDefault())
    return "%02d:%02d".format(local.hour, local.minute)
}
