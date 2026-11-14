package app.baki

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
@Composable
fun CalendarHeat(store: Store, month: String, compact: Boolean = false) {
    val scheme = MaterialTheme.colorScheme
    val cells = monthGrid(month)
    val today = todayIso()
    val bestDay = monthDays(store, month).maxByOrNull { it.second.net }?.first

    Column(Modifier.semantics { contentDescription = "Calendar heatmap of daily net" }) {
        Text("CALENDAR", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
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
                        if (iso != null) DayCell(iso, store, today, scheme, bestDay)
                    }
                }
                repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
            }
            Spacer(Modifier.height(if (compact) Space.xxs else Space.xs))
        }
    }
}

@Composable
private fun DayCell(iso: String, store: Store, today: String, scheme: androidx.compose.material3.ColorScheme, bestDay: String?) {
    val t = dayTotals(store, iso)
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
        iso == bestDay -> scheme.secondary.copy(alpha = 0.22f)
        magnitude == 0 -> scheme.surfaceContainerHigh
        magnitude == 1 -> base.copy(alpha = 0.22f)
        magnitude == 2 -> base.copy(alpha = 0.45f)
        else -> base.copy(alpha = 0.75f)
    }
    val isToday = iso == today

    Box(
        Modifier.fillMaxSize()
            .clip(MaterialTheme.shapes.small)
            .background(fill),
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
    }
}
