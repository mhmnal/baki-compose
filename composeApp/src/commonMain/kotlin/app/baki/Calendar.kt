package app.baki

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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Month heat map: green when the day kept money, coral when it cost you. */
@Composable
fun CalendarHeat(store: Store, month: String) {
    val baki = LocalBaki.current
    val cells = monthGrid(month)
    val today = todayIso()

    Column {
        Text("CALENDAR", color = baki.muted, style = if (baki.paper) BakiType.section else BakiType.captionStrong)
        Spacer(Modifier.height(Space.s))
        if (baki.paper) { LedgerRule(alpha = 0.14f); Spacer(Modifier.height(Space.s)) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
            listOf("M", "T", "W", "T", "F", "S", "S").forEach {
                Text(it, color = baki.muted, modifier = Modifier.weight(1f), style = BakiType.caption )
            }
        }
        Spacer(Modifier.height(Space.xs))
        cells.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                week.forEach { iso ->
                    Box(Modifier.weight(1f).aspectRatio(1f)) {
                        if (iso != null) DayCell(iso, store, today, baki)
                    }
                }
                repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
            }
            Spacer(Modifier.height(Space.xs))
        }
    }
}

@Composable
private fun DayCell(iso: String, store: Store, today: String, baki: BakiPalette) {
    val t = dayTotals(store, iso)
    val future = iso > today
    val magnitude = when {
        t.entryCount == 0 && t.shiftCount == 0 -> 0
        kotlin.math.abs(t.net) >= 15000 -> 3
        kotlin.math.abs(t.net) >= 5000 -> 2
        else -> 1
    }
    val positive = t.net >= 0
    val base = if (positive) baki.primary else baki.down
    val fill = when (magnitude) {
        0 -> baki.surfaceHigh
        1 -> base.copy(alpha = 0.22f)
        2 -> base.copy(alpha = 0.45f)
        else -> base.copy(alpha = 0.75f)
    }
    val isToday = iso == today

    Box(
        Modifier.fillMaxSize()
            .then(if (baki.paper) Modifier else Modifier.clip(RoundedCornerShape(Radius.small)))
            .background(fill)
            .then(if (isToday) Modifier.border(1.5.dp, baki.onSurface, RoundedCornerShape(Radius.tiny)) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            iso.takeLast(2),
            color = if (future) baki.muted.copy(alpha = 0.4f) else if (positive && magnitude >= 2) baki.onPrimary else baki.onSurface,
            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal, style = BakiType.caption )
    }
}
