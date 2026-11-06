package app.baki

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.Text
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
    val baki = LocalBaki.current
    var editing by remember { mutableStateOf<Int?>(null) }
    var adding by remember { mutableStateOf(false) }
    val total = payouts.sumOf { it.c }

    BakiSheet(
        onDismiss = onDismiss,
        footer = if (editable) {
            {
                Button(
                    onClick = { adding = true },
                    colors = ButtonDefaults.buttonColors(containerColor = baki.primary, contentColor = baki.onPrimary),
                    shape = RoundedCornerShape(Radius.pill),
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                ) { Text("Add payout", fontWeight = FontWeight.Bold) }
            }
        } else {
            null
        },
    ) {
        Text(title, color = baki.onSurface, style = BakiType.subtitle)
        Text(
            buildString {
                append(if (payouts.size == 1) "1 payout" else "${payouts.size} payouts")
                append(" · RM ${rm(total)}")
                if (editable) append("  ·  tap to edit, swipe to delete")
            },
            color = baki.muted, style = BakiType.captionNumeric,
        )
        Spacer(Modifier.height(6.dp))

        payouts.forEachIndexed { index, payout ->
            val dismiss = rememberSwipeToDismissBoxState()
            SwipeToDismissBox(
                state = dismiss,
                enableDismissFromStartToEnd = false,
                enableDismissFromEndToStart = editable,
                backgroundContent = {
                    Row(
                        Modifier.fillMaxSize()
                            .clip(RoundedCornerShape(Radius.card))
                            .background(baki.down.copy(alpha = 0.22f))
                            .padding(horizontal = 20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End,
                    ) {
                        Text("Delete", color = baki.down, style = BakiType.labelStrong)
                    }
                },
                onDismiss = { if (editable) onDelete(index) },
            ) {
                Row(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(Radius.card))
                        .background(baki.surfaceHigh)
                        .clickable(enabled = editable) { editing = index }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        clockTime(payout.at),
                        color = baki.muted,
                        style = BakiType.captionNumeric,
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "RM ${rm(payout.c)}",
                        color = baki.onSurface,
                        style = BakiType.labelNumeric.copy(fontSize = 15.sp),
                        modifier = Modifier.weight(1f),
                    )
                    if (editable) {
                        Text("Edit", color = baki.primary, style = BakiType.caption)
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

@Composable
private fun PayoutAmountDialog(
    title: String,
    initialCents: Long,
    confirm: String,
    onConfirm: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val baki = LocalBaki.current
    var text by remember { mutableStateOf(if (initialCents > 0) "%.2f".format(initialCents / 100.0) else "") }
    val amount = text.toDoubleOrNull()?.let { cents(it) } ?: 0L
    BakiSheet(onDismiss = onDismiss) {
        Text(title, color = baki.onSurface, style = BakiType.subtitle)
        OutlinedTextField(
            value = text,
            onValueChange = { raw -> text = raw.filter { it.isDigit() || it == '.' } },
            label = { Text("Amount (RM)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
            colors = bakiFieldColorsPublic(),
        )
        Button(
            enabled = amount > 0,
            onClick = { onConfirm(amount) },
            colors = ButtonDefaults.buttonColors(containerColor = baki.primary, contentColor = baki.onPrimary),
            shape = RoundedCornerShape(Radius.pill),
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) { Text("$confirm RM ${rm(amount)}", fontWeight = FontWeight.Bold) }
        TextButton(onClick = onDismiss) {
            Text("Cancel", color = baki.muted, style = BakiType.label)
        }
    }
}

/** Local HH:mm for a payout timestamp. */
fun clockTime(at: Long): String {
    val local = Instant.fromEpochMilliseconds(at)
        .toLocalDateTime(TimeZone.currentSystemDefault())
    return "%02d:%02d".format(local.hour, local.minute)
}
