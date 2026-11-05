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
fun StatementCard(store: Store) {
    val baki = LocalBaki.current
    val st = store.statements.values.maxByOrNull { it.weekStart } ?: return
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(baki.container).padding(14.dp),
    ) {
        Text("LAST STATEMENT · ${st.weekStart}", color = baki.muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text("RM ${"%.2f".format(st.income)} gross", color = baki.onSurface, fontSize = 20.sp, fontWeight = FontWeight.Black)
        Text(
            "${st.insuranceCount} insurance charge(s) · RM ${"%.2f".format(st.deductions)} deducted",
            color = baki.muted, fontSize = 11.sp,
        )
        if (st.rangeText.isNotEmpty()) {
            Text(st.rangeText, color = baki.muted, fontSize = 10.sp)
        }
        Text("imported ${st.importedAt.take(16).replace('T', ' ')}", color = baki.muted, fontSize = 10.sp)
    }
}

/** Add or remove spending categories; a category still in use can't be deleted. */
@Composable
fun CategoriesCard(repo: BakiRepo, editable: Boolean = true) {
    val baki = LocalBaki.current
    val scope = rememberCoroutineScope()
    var adding by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }
    val cats = repo.store.settings.cats.catsOut
    val usage = remember(repo.store) {
        repo.store.entries.filter { it.type != "income" }.groupingBy { it.cat }.eachCount()
    }

    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(baki.container).padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Spending categories", color = baki.onSurface, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text("${cats.size} categories · tap × to remove", color = baki.muted, fontSize = 11.sp)
            }
            TextButton(enabled = editable, onClick = { adding = true }) {
                Text("Add", color = if (editable) baki.primary else baki.muted, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(6.dp))
        cats.forEach { c ->
            val used = usage[c.id] ?: 0
            Row(
                Modifier.fillMaxWidth().padding(vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(c.icon, fontSize = 14.sp)
                Spacer(Modifier.width(8.dp))
                Text(c.name, color = baki.onSurface, fontSize = 13.sp, modifier = Modifier.weight(1f))
                Text(if (used > 0) "$used" else "", color = baki.muted, fontSize = 11.sp)
                Spacer(Modifier.width(8.dp))
                Text(
                    "×",
                    color = if (used > 0 || !editable) baki.muted.copy(alpha = 0.35f) else baki.down,
                    fontSize = 18.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable(enabled = used == 0 && editable) {
                            scope.launch { repo.removeCategory(c.id) }
                        }
                        .padding(horizontal = 10.dp),
                )
            }
        }
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
