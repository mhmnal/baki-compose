package app.baki

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The mic is a platform feature: a real recogniser on Android, absent on desktop. */
@Composable
expect fun VoiceMicButton(onText: (String) -> Unit, modifier: Modifier = Modifier)

/** One line of free text that becomes entries: "toll 4.20 parking 2". */
@Composable
fun QuickEntryBar(onSubmit: (String) -> Unit, modifier: Modifier = Modifier) {
    val baki = LocalBaki.current
    var text by remember { mutableStateOf("") }
    val submit = {
        val clean = text.trim()
        if (clean.isNotEmpty()) {
            onSubmit(clean)
            text = ""
        }
    }
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.pill))
            .background(baki.surfaceHigh)
            .padding(start = 16.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextField(
            value = text,
            onValueChange = { text = it },
            placeholder = { Text("toll 4.20 parking 2", color = baki.muted, fontSize = 13.sp) },
            singleLine = true,
            textStyle = BakiType.body.copy(fontSize = 14.sp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                cursorColor = baki.primary,
                focusedTextColor = baki.onSurface,
                unfocusedTextColor = baki.onSurface,
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            modifier = Modifier.weight(1f),
        )
        VoiceMicButton(onText = { spoken -> onSubmit(spoken) }, modifier = Modifier.size(40.dp))
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(Radius.pill))
                .background(if (text.isBlank()) Color.Transparent else baki.primary)
                .clickable(enabled = text.isNotBlank()) { submit() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                BakiIcons.ArrowRight, contentDescription = "Add",
                tint = if (text.isBlank()) baki.muted else baki.onPrimary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/**
 * Shows what the sentence meant before anything is saved. Every line can be removed; loose amounts
 * have to be assigned, because guessing a category is worse than asking.
 */
@Composable
fun QuickEntrySheet(
    initial: String,
    cats: List<Cat>,
    today: String,
    onConfirm: (entries: List<QuickEntry>, payouts: List<Long>, date: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val baki = LocalBaki.current
    var text by remember { mutableStateOf(initial) }
    var removed by remember { mutableStateOf(setOf<Int>()) }
    var assigned by remember { mutableStateOf<Map<Int, String>>(emptyMap()) }
    val parse = remember(text, cats) { parseQuickEntry(text, today, cats) }

    val kept = parse.entries.filterIndexed { i, _ -> i !in removed }
    val looseLeft = parse.loose.filterIndexed { i, _ -> assigned[i] == null }
    val total = kept.sumOf { it.cents } + parse.loose.filterIndexed { i, _ -> assigned[i] != null }.sum()

    BakiSheet(onDismiss = onDismiss) {
        Text("What did you mean?", color = baki.onSurface, style = BakiType.subtitle)
        TextField(
            value = text,
            onValueChange = { text = it; removed = emptySet(); assigned = emptyMap() },
            singleLine = true,
            textStyle = BakiType.body.copy(fontSize = 14.sp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = baki.surfaceHigh,
                unfocusedContainerColor = baki.surfaceHigh,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                cursorColor = baki.primary,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        if (parse.date != today) {
            Text(dateLabel(parse.date), color = baki.muted, style = BakiType.caption)
        }
        Spacer(Modifier.height(4.dp))

        kept.forEach { entry ->
            val index = parse.entries.indexOf(entry)
            Row(
                Modifier.fillMaxWidth().heightIn(min = 44.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    cats.firstOrNull { it.id == entry.cat }?.name ?: entry.cat,
                    color = baki.onSurface, style = BakiType.label, modifier = Modifier.weight(1f),
                )
                Text("RM ${rm(entry.cents)}", color = baki.onSurface, style = BakiType.labelNumeric)
                Spacer(Modifier.size(10.dp))
                Text(
                    "remove", color = baki.muted, style = BakiType.caption,
                    modifier = Modifier.clickable { removed = removed + index },
                )
            }
        }

        parse.loose.forEachIndexed { i, cents ->
            if (assigned[i] == null) {
                Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Text("RM ${rm(cents)} — which category?", color = baki.warn, style = BakiType.caption)
                    Row(
                        Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        cats.take(4).forEach { c ->
                            Text(
                                c.name, color = baki.onSurface, style = BakiType.caption,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(Radius.pill))
                                    .background(baki.container)
                                    .clickable { assigned = assigned + (i to c.name) }
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                            )
                        }
                    }
                }
            }
        }

        if (parse.payouts.isNotEmpty()) {
            Text(
                "${parse.payouts.size} payout(s) · RM ${rm(parse.payouts.sum())}",
                color = baki.up, style = BakiType.caption,
            )
        }

        val ready = kept.isNotEmpty() && looseLeft.isEmpty()
        Button(
            enabled = ready,
            onClick = { onConfirm(kept, parse.payouts, parse.date) },
            colors = ButtonDefaults.buttonColors(
                containerColor = baki.primary, contentColor = baki.onPrimary,
            ),
            shape = RoundedCornerShape(Radius.pill),
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) {
            Text(
                if (looseLeft.isEmpty()) "Add ${kept.size + parse.payouts.size} item(s) · RM ${rm(total)}"
                else "Assign the unlabelled amount(s) first",
            )
        }
    }
}

private fun dateLabel(date: String): String = "dated ${prettyDate(date)}"
