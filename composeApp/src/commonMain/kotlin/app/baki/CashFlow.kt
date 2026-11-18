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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** One day in the cash-flow calendar: recorded net, bills due and payouts landing. */
data class CashFlowDay(
    val iso: String,
    val net: Long,
    val bills: List<Bill>,
    val payoutCents: Long,
    val shiftCount: Int,
    val entryCount: Int,
) {
    val active: Boolean get() = shiftCount > 0 || entryCount > 0 || payoutCents > 0 || bills.isNotEmpty()
}

fun cashFlowDay(store: Store, iso: String): CashFlowDay {
    val entries = store.entries.filter { it.date == iso }
    val shifts = store.shifts.filter { it.date == iso }
    val bills = store.bills.filter { bill ->
        bill.active && bill.dueDay == iso.takeLast(2).trimStart('0').toIntOrNull() &&
            (bill.cadence == "monthly" || bill.anchorMonth == iso.take(4).toInt())
    }
    val payoutCents = store.shifts.sumOf { shift ->
        shift.payouts.filter { p ->
            runCatching {
                Instant.fromEpochMilliseconds(p.at).toLocalDateTime(TimeZone.currentSystemDefault()).date.toString() == iso
            }.getOrDefault(false)
        }.sumOf { it.c }
    }
    return CashFlowDay(iso, dayTotals(store, iso).net, bills, payoutCents, shifts.size, entries.size)
}

fun cashFlowMonth(store: Store, month: String): Map<String, CashFlowDay> =
    monthGrid(month).filterNotNull().associateWith { cashFlowDay(store, it) }

@Composable
fun CashFlowGrid(store: Store, month: String, compact: Boolean = false) {
    val scheme = MaterialTheme.colorScheme
    val days = remember(store, month) { cashFlowMonth(store, month) }
    val cells = monthGrid(month)
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
            listOf("M", "T", "W", "T", "F", "S", "S").forEach {
                Text(it, color = scheme.onSurfaceVariant, modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall)
            }
        }
        Spacer(Modifier.height(Space.xs))
        cells.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                week.forEach { iso ->
                    Box(Modifier.weight(1f).height(if (compact) 34.dp else 54.dp)) {
                        if (iso != null) {
                            val day = days[iso]
                            val positive = (day?.net ?: 0L) >= 0
                            val tint = when {
                                day == null || day.net == 0L -> scheme.surfaceContainerHigh
                                positive -> scheme.primary.copy(alpha = 0.16f)
                                else -> scheme.error.copy(alpha = 0.16f)
                            }
                            Surface(
                                color = tint,
                                shape = MaterialTheme.shapes.small,
                                modifier = Modifier.fillMaxWidth().height(if (compact) 34.dp else 54.dp)
                                    .semantics {
                                        contentDescription = buildString {
                                            append(prettyDate(iso))
                                            day?.let {
                                                append(", net RM ${rm(it.net)}")
                                                if (it.bills.isNotEmpty()) append(", bills: ${it.bills.joinToString { b -> b.name }}")
                                                if (it.payoutCents > 0) append(", payout RM ${rm(it.payoutCents)}")
                                            }
                                        }
                                    },
                            ) {
                                Column(Modifier.padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(iso.takeLast(2), color = if (positive) scheme.onSurface else scheme.error, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                                    if (!compact && day != null) {
                                        Text(if (day.net == 0L) "—" else "RM ${rm(day.net)}", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                        if (day != null && day.bills.isNotEmpty()) Dot(scheme.tertiary)
                                        if (day != null && day.payoutCents > 0) Dot(scheme.primary)
                                    }
                                }
                            }
                        }
                    }
                }
                repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
            }
            Spacer(Modifier.height(Space.xs))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Space.m), verticalAlignment = Alignment.CenterVertically) {
            Legend(scheme.tertiary, "bill due")
            Legend(scheme.primary, "payout")
        }
    }
}

@Composable
private fun Dot(color: androidx.compose.ui.graphics.Color) {
    Box(Modifier.size(4.dp).clip(CircleShape).background(color))
}

@Composable
private fun Legend(color: androidx.compose.ui.graphics.Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Dot(color)
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
    }
}
