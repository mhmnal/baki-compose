package app.baki

import androidx.compose.foundation.shape.CircleShape

import androidx.compose.material3.toShape

import androidx.compose.material3.MaterialShapes

import androidx.compose.foundation.layout.Box

import androidx.compose.material3.MaterialTheme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Month heat map: green when the day kept money, coral when it cost you. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CalendarHeat(store: Store, month: String, compact: Boolean = false) {
    val scheme = MaterialTheme.colorScheme
    val cells = monthGrid(month)
    val today = todayIso()

    Column {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            Box(Modifier.size(14.dp).clip(CircleShape).background(scheme.primary))
            Text("CALENDAR", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
        }
        Spacer(Modifier.height(Space.s))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
            listOf("M", "T", "W", "T", "F", "S", "S").forEach {
                Text(it, color = scheme.onSurfaceVariant, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium )
            }
        }
        Spacer(Modifier.height(if (compact) Space.xxs else Space.xs))
        cells.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                week.forEach { iso ->
                    Box(
                        Modifier.weight(1f).then(if (compact) Modifier.height(30.dp) else Modifier.aspectRatio(1f)),
                    ) {
                        if (iso != null) DayCell(iso, store, today, scheme)
                    }
                }
                repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
            }
            Spacer(Modifier.height(if (compact) Space.xxs else Space.xs))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.m), verticalAlignment = Alignment.CenterVertically) {
            CalendarLegendDot(scheme.primary, "profit")
            CalendarLegendDot(scheme.error, "loss")
            CalendarLegendDot(scheme.surfaceContainerHigh, "not driven")
            CalendarLegendDot(scheme.tertiary, "bill due")
            CalendarLegendDot(scheme.primary, "payout")
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DayCell(iso: String, store: Store, today: String, scheme: androidx.compose.material3.ColorScheme) {
    val t = dayTotals(store, iso)
    val flow = cashFlowDay(store, iso)
    val future = iso > today
    val magnitude = when {
        t.entryCount == 0 && t.shiftCount == 0 -> 0
        kotlin.math.abs(t.net) >= 15000 -> 3
        kotlin.math.abs(t.net) >= 5000 -> 2
        else -> 1
    }
    val positive = t.net >= 0
    val base = if (positive) scheme.primary else scheme.error
    val fill = when {
        iso == today -> scheme.primaryContainer
        magnitude == 0 -> scheme.surfaceContainerHigh
        magnitude == 1 -> base.copy(alpha = 0.22f)
        magnitude == 2 -> base.copy(alpha = 0.45f)
        else -> base.copy(alpha = 0.75f)
    }
    val isToday = iso == today

    val description = buildString {
        append(prettyDate(iso))
        when {
            future -> append(", future day")
            t.entryCount == 0 && t.shiftCount == 0 -> append(", no entries")
            else -> append(", net RM ${rm(t.net)}")
        }
        if (flow.bills.isNotEmpty()) append(", bills: ${flow.bills.joinToString { it.name }}")
        if (flow.payoutCents > 0) append(", payout RM ${rm(flow.payoutCents)}")
    }
    Box(
        Modifier.fillMaxSize()
            .clip(if (isToday) MaterialShapes.SoftBurst.toShape() else CircleShape)
            .background(fill)
            .semantics(mergeDescendants = true) { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            iso.takeLast(2),
            color = when {
                future -> scheme.onSurfaceVariant.copy(alpha = 0.4f)
                isToday -> scheme.onPrimaryContainer
                positive && magnitude >= 2 -> scheme.onPrimary
                else -> scheme.onSurface
            },
            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal, style = MaterialTheme.typography.bodyMedium )
        Row(
            Modifier.align(Alignment.BottomCenter).padding(bottom = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!future && !positive && magnitude > 0) {
                Box(Modifier.width(5.dp).height(2.dp).background(scheme.onSurface))
            }
            if (flow.bills.isNotEmpty()) Box(Modifier.size(4.dp).clip(CircleShape).background(scheme.tertiary))
            if (flow.payoutCents > 0) Box(Modifier.size(4.dp).clip(CircleShape).background(scheme.primary))
        }
    }
}

@Composable
private fun CalendarLegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(color))
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
    }
}
