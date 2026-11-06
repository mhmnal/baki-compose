package app.baki

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Distinct, readable line colours for up to a few shifts on the same day. */
private fun routeColor(baki: BakiPalette, index: Int): Color = when (index % 5) {
    0 -> baki.primary
    1 -> baki.up
    2 -> baki.warn
    3 -> baki.down
    else -> baki.onSurface
}

/**
 * Day-by-day maps for the month. Routes are read from the local encrypted store, so a shift with no
 * recorded path simply never appears here.
 */
@Composable
fun RouteDaysSection(store: Store, month: String, modifier: Modifier = Modifier) {
    val baki = LocalBaki.current
    var days by remember(store, month) { mutableStateOf<List<Pair<String, List<MapRoute>>>>(emptyList()) }
    var expanded by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(store, month) {
        val byDate = store.shifts
            .filter { it.date.startsWith(month) }
            .groupBy { it.date }
        val loaded = byDate.toList()
            .sortedByDescending { it.first }
            .map { (date, shifts) ->
                val routes = shifts.sortedBy { it.start }.mapIndexedNotNull { i, shift ->
                    val route = RouteStore.route(shift.id) ?: return@mapIndexedNotNull null
                    if (route.points.size < 2) return@mapIndexedNotNull null
                    MapRoute(route = route, color = routeColor(baki, i), label = "Shift ${i + 1}")
                }
                date to routes
            }
            .filter { it.second.isNotEmpty() }
        days = loaded
    }

    if (days.isEmpty()) return

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("ROUTES", color = baki.muted, style = BakiType.section)
        days.forEach { (date, routes) ->
            DayMapCard(
                date = date,
                routes = routes,
                onOpen = { expanded = date },
            )
        }
    }

    expanded?.let { date ->
        val routes = days.firstOrNull { it.first == date }?.second ?: return@let
        DayMapFullScreen(date = date, routes = routes) { expanded = null }
    }
}
