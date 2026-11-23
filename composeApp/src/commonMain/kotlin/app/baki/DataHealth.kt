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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class PayoutGap(val shift: Shift, val gapCents: Long)

data class DataHealthReport(
    val payoutGaps: List<PayoutGap>,
    val missingDueBills: List<Bill>,
    val inactiveBills: List<Bill>,
    val odometerFills: Int,
    val fuelFills: Int,
    val backupAt: Long?,
    val backupAgeDays: Long?,
    val backupBehindEntries: Boolean,
    val openWeek: WeekOption?,
    val weekVariances: List<WeekCloseView>,
) {
    val payoutGapTotal: Long get() = payoutGaps.sumOf { it.gapCents }
    val needsOdometer: Boolean get() = fuelFills > 0 && odometerFills < 2
    val backupStale: Boolean get() = backupAt == null || (backupAgeDays ?: 0) >= 7 || backupBehindEntries
    val issueCount: Int get() = payoutGaps.size + missingDueBills.size + inactiveBills.size +
        (if (needsOdometer) 1 else 0) + (if (backupStale) 1 else 0) +
        (if (openWeek != null) 1 else 0) + weekVariances.size

    val hasIssues: Boolean get() = issueCount > 0
}

/** Facts that need review; this is deliberately not a score or a judgement. */
fun dataHealth(
    store: Store,
    lastBackupAt: Long?,
    lastBackupEntries: Int,
    now: Long = nowMillis(),
    today: String = todayIso(),
): DataHealthReport {
    val gaps = store.shifts
        .mapNotNull { shift ->
            val received = shift.payouts.sumOf { it.c }
            val gap = (shift.gross + shift.incentive - received).coerceAtLeast(0)
            if (gap > 0) PayoutGap(shift, gap) else null
        }
        .sortedByDescending { it.shift.date }

    val missingDue = store.bills
        .filter { it.active && it.cadence != "annual" && it.dueDay == null }
        .sortedBy { it.name }

    val inactive = store.bills
        .filter { bill -> bill.active && store.billPayments.any { it.billId == bill.id && !it.active } }
        .sortedBy { it.name }

    val fuel = store.entries.filter { isFuelCat(it.cat) }
    val odometerFuel = fuel.count { (it.odo ?: 0) > 0 }

    val backupAge = lastBackupAt?.let { ((now - it) / 86_400_000).coerceAtLeast(0) }
    val backupBehind = lastBackupAt != null && lastBackupEntries < store.entries.size

    val currentWeek = weekStartOf(today)
    val open = weekOptions(store, today).firstOrNull { it.weekStart == currentWeek && !it.isClosed && it.shiftCount > 0 }
    val variances = store.weekCloses.keys
        .filter { store.weekCloses[it]?.closedAt?.isNotBlank() == true }
        .sortedDescending()
        .mapNotNull { week ->
            val view = weekCloseView(store, week)
            if ((view.cashVariance ?: 0L) != 0L) view else null
        }

    return DataHealthReport(
        payoutGaps = gaps,
        missingDueBills = missingDue,
        inactiveBills = inactive,
        odometerFills = odometerFuel,
        fuelFills = fuel.size,
        backupAt = lastBackupAt,
        backupAgeDays = backupAge,
        backupBehindEntries = backupBehind,
        openWeek = open,
        weekVariances = variances,
    )
}

@Composable
fun DataHealthCard(report: DataHealthReport, onOpen: () -> Unit) {
    if (!report.hasIssues) return
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = MaterialTheme.shapes.large,
        color = scheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(Space.m), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "DATA HEALTH",
                    color = scheme.onSecondaryContainer,
                    letterSpacing = 1.2.sp,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "${report.issueCount} open",
                    color = scheme.onSecondaryContainer.copy(alpha = 0.74f),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            if (report.payoutGaps.isNotEmpty()) {
                Text(
                    "Payout gap · RM ${rm(report.payoutGapTotal)}",
                    color = scheme.onSecondaryContainer,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                )
            }
            val detail = buildList {
                if (report.missingDueBills.isNotEmpty()) add("${report.missingDueBills.size} bill${if (report.missingDueBills.size == 1) "" else "s"} missing a due day")
                if (report.needsOdometer) add("${report.odometerFills} of ${report.fuelFills} fuel fills have an odometer")
                if (report.backupStale) add("backup is ${report.backupAgeDays?.let { "${it}d old" } ?: "missing"}")
                if (report.openWeek != null) add("week ${report.openWeek.weekStart} is open")
                if (report.weekVariances.isNotEmpty()) add("week close differs from statement")
            }
            if (detail.isNotEmpty()) {
                Text(detail.joinToString(" · "), color = scheme.onSecondaryContainer.copy(alpha = 0.78f), style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = onOpen, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                Text("Review", color = scheme.onSecondaryContainer, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
            }
        }
    }
}

@Composable
fun DataHealthSheet(
    report: DataHealthReport,
    onPayout: (Shift) -> Unit,
    onEditBill: (Bill) -> Unit,
    onOdometer: () -> Unit,
    onBackup: () -> Unit,
    onOpenWeek: () -> Unit,
    onDismiss: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    BakiSheet(onDismiss = onDismiss) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(40.dp).background(scheme.tertiaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) { Text("!", color = scheme.onTertiaryContainer, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)) }
            Spacer(Modifier.width(Space.m))
            Column {
                Text("DATA HEALTH", color = scheme.onSurfaceVariant, letterSpacing = 1.2.sp, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                Text("What still needs a look", color = scheme.onSurface, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
            }
        }
        Text("These are incomplete records, not errors. Each one has a direct next step.", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(Space.s))

        if (!report.hasIssues) {
            Text("Nothing needs a look right now.", color = scheme.tertiary, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
            return@BakiSheet
        }

        if (report.payoutGaps.isNotEmpty()) {
            HealthSection("PAYOUT GAPS", "RM ${rm(report.payoutGapTotal)} across ${report.payoutGaps.size} shift${if (report.payoutGaps.size == 1) "" else "s"}")
            report.payoutGaps.take(5).forEach { gap ->
                HealthRow(
                    title = "${prettyDate(gap.shift.date)} · ${gap.shift.start}",
                    sub = "RM ${rm(gap.gapCents)} not recorded as received",
                    action = "Payouts",
                    onAction = { onPayout(gap.shift) },
                )
            }
        }

        if (report.missingDueBills.isNotEmpty()) {
            HealthSection("BILLS MISSING A DUE DAY", "${report.missingDueBills.size} bill${if (report.missingDueBills.size == 1) "" else "s"}")
            report.missingDueBills.forEach { bill ->
                HealthRow(bill.name, "Set a due day so the month can show it", "Edit", { onEditBill(bill) })
            }
        }

        if (report.inactiveBills.isNotEmpty()) {
            HealthSection("INACTIVE BILL LINKS", "${report.inactiveBills.size} bill${if (report.inactiveBills.size == 1) "" else "s"}")
            report.inactiveBills.forEach { bill ->
                HealthRow(bill.name, "Payment history is inactive", "Edit") { onEditBill(bill) }
            }
        }

        if (report.needsOdometer) {
            HealthSection("ODOMETER COVERAGE", "${report.odometerFills} of ${report.fuelFills} fuel fills")
            HealthRow(
                "Measured km/L is waiting",
                "Two fuel fills with a dashboard reading unlock it",
                "Add reading",
                onOdometer,
            )
        }

        if (report.backupStale) {
            HealthSection("BACKUP", report.backupAgeDays?.let { "${it}d old" } ?: "none yet")
            HealthRow(
                "Portable backup",
                if (report.backupBehindEntries) "Newer entries are not in the last backup" else "Create a fresh encrypted copy",
                "Backup now",
                onBackup,
            )
        }

        if (report.openWeek != null) {
            HealthSection("OPEN WEEK", report.openWeek.weekStart)
            HealthRow("This week is still open", "Close it with the payout actually received", "Open week", onOpenWeek)
        }

        report.weekVariances.take(3).forEach { view ->
            HealthSection("WEEK CLOSE VARIANCE", "week of ${view.weekStart}")
            HealthRow(
                "Received RM ${rm(view.received)}",
                "Statement weekly RM ${rm(view.statement?.weekly ?: 0.0)} · difference RM ${rm(kotlin.math.abs(view.cashVariance ?: 0L))}",
                "Open week",
                onOpenWeek,
            )
        }
    }
}

@Composable
private fun HealthSection(title: String, detail: String) {
    Spacer(Modifier.height(Space.s))
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        Text(title, color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 1.sp, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), modifier = Modifier.weight(1f))
        Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"))
    }
}

@Composable
private fun HealthRow(title: String, sub: String, action: String?, onAction: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().padding(vertical = Space.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = scheme.onSurface, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
            Text(sub, color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
        if (action != null) {
            TextButton(onClick = onAction, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = Space.s)) {
                Text(action, color = scheme.primary, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
            }
        }
    }
}
