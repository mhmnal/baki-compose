package app.baki

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** What the vehicle actually returns, and what a kilometre costs. */
@Composable
fun VehicleEconomyCard(store: Store, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val economy = remember(store) { measuredEconomy(store) }
    val fuelFills = remember(store) {
        store.entries.count { isFuelCat(it.cat) && (it.odo ?: 0) > 0 }
    }
    val odometerFills = remember(store) {
        store.entries.count { isFuelCat(it.cat) }
    }
    val month = todayIso().take(7)
    val currentCost = remember(store, month) { monthCost(store, month) }
    val series = remember(store, month) {
        monthCostSeries(store, month, 4).filter { it.km > 0.01 }
    }

    Surface(
        shape = InstrumentShape,
        color = scheme.surfaceContainerLow,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(Space.m), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
            Text(
                "/ VEHICLE ECONOMY",
                color = scheme.primary,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.2.sp,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            )
            Text("What the car returns — measured, not assumed.", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)

            Spacer(Modifier.height(Space.xs))
            if (economy != null) {
                Text(
                    "%.1f km/L".format(economy.kml),
                    color = scheme.onSurface,
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                )
                Text(
                    "measured over ${"%.0f".format(economy.km)} km · ${economy.fills} fills · ${economy.from} → ${economy.to}",
                    color = scheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                )
            } else {
                Text("No measured km/L yet", color = scheme.onSurface, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                Text(
                    "$fuelFills of $odometerFills fuel fills carry an odometer reading. " +
                        "Enter the dashboard reading on two fill-ups to measure this.",
                    color = scheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            Spacer(Modifier.height(Space.s))
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text("COST PER KM · ${monthLabel(month).uppercase()}", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                    Text(
                        if (currentCost.km > 0.01) "RM ${"%.2f".format(currentCost.perKm)}/km" else "No distance this month",
                        color = scheme.onSurface,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum"),
                    )
                }
                Text(
                    if (currentCost.km > 0.01) "fuel + wear + upkeep" else "",
                    color = scheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            if (series.isNotEmpty()) {
                val peak = series.maxOf { it.perKm }.coerceAtLeast(0.01)
                Row(
                    Modifier.fillMaxWidth().height(52.dp),
                    horizontalArrangement = Arrangement.spacedBy(Space.s),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    series.forEach { c ->
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                            Box(
                                Modifier.width(22.dp)
                                    .height((8f + (c.perKm / peak).toFloat() * 24f).dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(if (c.month == month) scheme.primary else scheme.primary.copy(alpha = 0.28f)),
                            )
                            Text(monthLabel(c.month).take(3), color = scheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
            Spacer(Modifier.height(Space.xs))
            Text(
                "Cost uses recorded fuel, wear and upkeep spending; it is not a target.",
                color = scheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
