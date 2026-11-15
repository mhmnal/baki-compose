package app.baki

import androidx.compose.material3.Surface

import androidx.compose.foundation.shape.CircleShape

import androidx.compose.ui.text.font.FontWeight

import androidx.compose.material3.MaterialTheme

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** Distinct, readable line colours for a few shifts on the same day. */
fun routeColorFor(scheme: androidx.compose.material3.ColorScheme, index: Int): Color = when (index % 5) {
    0 -> scheme.primary
    1 -> scheme.tertiary
    2 -> scheme.secondary
    3 -> scheme.error
    else -> scheme.onSurface
}

/** Every path of [day]: finished shifts first, then the live shift when it is happening today. */
private suspend fun loadDayRoutes(
    store: Store,
    day: String,
    live: LiveShift?,
    today: String,
    scheme: androidx.compose.material3.ColorScheme,
): List<MapRoute> {
    val out = ArrayList<MapRoute>(4)
    store.shifts.filter { it.date == day }.sortedBy { it.start }.forEach { shift ->
        val route = RouteStore.route(shift.id) ?: return@forEach
        if (route.points.size < 2) return@forEach
        out += MapRoute(route, routeColorFor(scheme, out.size), "Shift ${out.size + 1} · ${shift.start}")
    }
    if (live != null && day == today) {
        val route = RouteStore.route(live.id)
        if (route != null && route.points.size >= 2) {
            out += MapRoute(route, scheme.tertiary, "Live now")
        }
    }
    return out
}

/**
 * The day's movement, on Today. Refreshes every 30 s while a shift is running so the path grows as
 * you drive; otherwise it loads once. Read-only, like every map in the app.
 */
@Composable
fun TodayRouteCard(store: Store, day: String, live: LiveShift?, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val today = todayIso()
    var routes by remember { mutableStateOf<List<MapRoute>>(emptyList()) }
    var expanded by remember { mutableStateOf(false) }

    LaunchedEffect(store, day, live?.id) {
        routes = loadDayRoutes(store, day, live, today, scheme)
        if (live != null && day == today) {
            while (true) {
                delay(30_000)
                routes = loadDayRoutes(store, day, live, today, scheme)
            }
        }
    }

    if (routes.isEmpty()) {
        // Keep the feature discoverable when a day has shifts but no usable path (location off).
        val hasActivity = (live != null && day == today) || store.shifts.any { it.date == day }
        if (!hasActivity) return
        Surface(
            color = scheme.surfaceContainerLow,
            shape = MaterialTheme.shapes.large,
            modifier = modifier.fillMaxWidth().padding(vertical = Space.s),
        ) {
            Column(Modifier.padding(Space.ml)) {
                Text(
                    if (live != null && day == today) "WHERE YOU'VE BEEN" else "WHERE YOU WENT",
                    color = scheme.onSurfaceVariant, style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    "No movement recorded. Keep location on during a live shift to draw the map.",
                    color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        return
    }

    val km = routes.sumOf { routeMetres(it.route.points) } / 1000.0
    val liveHere = live != null && day == today

    Column(
        modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(scheme.surfaceContainerLow),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Space.l, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    if (liveHere) "WHERE YOU'VE BEEN" else "WHERE YOU WENT",
                    color = scheme.onSurfaceVariant, style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    buildString {
                        append(if (routes.size == 1) "1 path" else "${routes.size} paths")
                        append(" · %.1f km".format(km))
                        if (liveHere) append(" · live")
                    },
                    color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                )
            }
            Text(
                "Open", color = scheme.primary, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier
                    .heightIn(min = Control.primary)
                    .clip(CircleShape)
                    .clickable { expanded = true }
                    .padding(horizontal = Space.m, vertical = 6.dp),
            )
        }
        RouteMap(
            routes = routes,
            modifier = Modifier.fillMaxWidth().height(150.dp).padding(horizontal = 0.dp),
            showAttribution = true,
        )
    }

    if (expanded) {
        DayMapFullScreen(date = day, routes = routes) { expanded = false }
    }
}
