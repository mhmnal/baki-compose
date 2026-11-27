package app.baki

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.abs

enum class StatementImportOutcome { IMPORTED, ALREADY, CONFLICT }

data class StatementFieldChange(val label: String, val before: String, val after: String)

data class StatementConflict(
    val existing: Statement,
    val incoming: Statement,
    val changes: List<StatementFieldChange>,
)

private fun money(v: Double): String = "RM ${"%.2f".format(v)}"
private fun sameMoney(a: Double, b: Double): Boolean = abs(a - b) < 0.0001

/** Material fields only; timestamps and parser metadata do not count as a change. */
fun statementChanges(existing: Statement, incoming: Statement): List<StatementFieldChange> {
    val fields = listOf(
        Triple("Weekly payout", existing.weekly, incoming.weekly),
        Triple("Income", existing.income, incoming.income),
        Triple("Deductions", existing.deductions, incoming.deductions),
        Triple("Incentives", existing.incentives, incoming.incentives),
        Triple("Tips", existing.tips, incoming.tips),
        Triple("Net GSF", existing.netGsf, incoming.netGsf),
        Triple("Delivery incentive", existing.deliveryIncentive, incoming.deliveryIncentive),
    )
    return fields.mapNotNull { (label, before, after) ->
        if (sameMoney(before, after)) null
        else StatementFieldChange(label, money(before), money(after))
    }
}

fun sameStatement(existing: Statement, incoming: Statement): Boolean = statementChanges(existing, incoming).isEmpty()

/** A same-week replacement waits here until the user confirms it. */
object StatementImportGate {
    var queue by mutableStateOf<List<StatementConflict>>(emptyList())
        private set

    fun enqueue(conflict: StatementConflict) {
        if (queue.any { it.incoming.weekStart == conflict.incoming.weekStart && it.changes == conflict.changes }) return
        queue = queue + conflict
    }

    fun pop() {
        queue = queue.drop(1)
    }
}

suspend fun stageStatementImport(repo: BakiRepo, incoming: Statement): StatementImportOutcome {
    val existing = repo.store.statements[incoming.weekStart]
    return when {
        existing == null -> {
            repo.saveStatement(incoming)
            StatementImportOutcome.IMPORTED
        }
        sameStatement(existing, incoming) -> StatementImportOutcome.ALREADY
        else -> {
            StatementImportGate.enqueue(StatementConflict(existing, incoming, statementChanges(existing, incoming)))
            StatementImportOutcome.CONFLICT
        }
    }
}
