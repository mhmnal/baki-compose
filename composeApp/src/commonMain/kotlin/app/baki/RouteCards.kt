package app.baki

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
fun routeColorFor(baki: BakiPalette, index: Int): Color = when (index % 5) {
    0 -> baki.primary
    1 -> baki.up
    2 -> baki.warn
    3 -> baki.down
    else -> baki.onSurface
}

/** Every path of [day]: finished shifts first, then the live shift when it is happening today. */
private suspend fun loadDayRoutes(
    store: Store,
    day: String,
    live: LiveShift?,
    today: String,
    baki: BakiPalette,
): List<MapRoute> {
    val out = ArrayList<MapRoute>(4)
    store.shifts.filter { it.date == day }.sortedBy { it.start }.forEach { shift ->
        val route = RouteStore.route(shift.id) ?: return@forEach
        if (route.points.size < 2) return@forEach
        out += MapRoute(route, routeColorFor(baki, out.size), "Shift ${out.size + 1} · ${shift.start}")
    }
    if (live != null && day == today) {
        val route = RouteStore.route(live.id)
        if (route != null && route.points.size >= 2) {
            out += MapRoute(route, baki.up, "Live now")
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
    val baki = LocalBaki.current
    val today = todayIso()
    var routes by remember { mutableStateOf<List<MapRoute>>(emptyList()) }
    var expanded by remember { mutableStateOf(false) }

    LaunchedEffect(store, day, live?.id) {
        routes = loadDayRoutes(store, day, live, today, baki)
        if (live != null && day == today) {
            while (true) {
                delay(30_000)
                routes = loadDayRoutes(store, day, live, today, baki)
            }
        }
    }

    if (routes.isEmpty()) return

    val km = routes.sumOf { routeMetres(it.route.points) } / 1000.0
    val liveHere = live != null && day == today

    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.large))
            .background(baki.container),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Space.l, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    if (liveHere) "WHERE YOU'VE BEEN" else "WHERE YOU WENT",
                    color = baki.muted, style = BakiType.section,
                )
                Text(
                    buildString {
                        append(if (routes.size == 1) "1 path" else "${routes.size} paths")
                        append(" · %.1f km".format(km))
                        if (liveHere) append(" · live")
                    },
                    color = baki.muted, style = BakiType.captionNumeric,
                )
            }
            Text(
                "Open", color = baki.primary, style = BakiType.labelStrong,
                modifier = Modifier
                    .heightIn(min = Control.primary)
                    .clip(RoundedCornerShape(Radius.pill))
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
