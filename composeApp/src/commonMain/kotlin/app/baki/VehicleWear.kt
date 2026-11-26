package app.baki

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.max
import kotlin.math.min

/**
 * Odometer-anchored wear.
 *
 * Work wear already runs per shift from shift km. This layer only asks a different question:
 * between two odometer readings, how much of the total distance was work, and how much was
 * personal? It never invents a personal distance when the readings do not bracket an interval.
 */
data class OdometerWear(
    val anchors: Int = 0,
    val intervals: Int = 0,
    val coveredKm: Double = 0.0,
    val workKm: Double = 0.0,
    val personalKm: Double = 0.0,
    val totalWear: Double = 0.0,
    val workWear: Double = 0.0,
    val personalWear: Double = 0.0,
    val unknownIntervals: Int = 0,
    val firstDate: String? = null,
    val lastDate: String? = null,
) {
    val hasCoverage: Boolean get() = intervals > 0
}

private data class Anchor(val at: Long, val date: String, val km: Double)

/**
 * Builds a single odometer timeline from the readings that already exist. Fuel entries use noon
 * on their date because a fuel row has no clock time; shifts use their real start/end wall clock.
 */
fun odometerWear(store: Store): OdometerWear {
    val rate = store.settings.profit.wearPerKm.coerceAtLeast(0.0)
    val anchors = mutableListOf<Anchor>()
    for (sh in store.shifts) {
        val a = sh.odoStart ?: continue
        val b = sh.odoEnd ?: continue
        if (!a.isFinite() || !b.isFinite() || a <= 0 || b < a) continue
        val start = shiftStartMs(sh)
        val end = shiftEndMs(sh)
        if (start <= 0L) continue
        anchors += Anchor(start, sh.date, a)
        anchors += Anchor(if (end > start) end else start, sh.date, b)
    }
    val noon = Shift(id = "fuel-anchor", date = "", start = "12:00")
    for (r in store.odometerReadings) {
        if (r.km <= 0) continue
        val at = shiftStartMs(noon.copy(date = r.date))
        if (at > 0L) anchors += Anchor(at, r.date, r.km.toDouble())
    }
    val base = store.settings.serviceOdoBase
    val baseOn = store.settings.serviceOdoBaseOn
    if (base != null && base > 0 && baseOn != null) {
        val at = shiftStartMs(noon.copy(date = baseOn))
        if (at > 0L) anchors += Anchor(at, baseOn, base.toDouble())
    }
    for (e in store.entries) {
        val km = e.odo ?: continue
        if (!isFuelCat(e.cat) || km <= 0) continue
        val at = shiftStartMs(noon.copy(date = e.date))
        if (at > 0L) anchors += Anchor(at, e.date, km.toDouble())
    }
    val ordered = anchors
        .distinctBy { it.at to it.km }
        .sortedWith(compareBy<Anchor> { it.at }.thenBy { it.km })
    if (ordered.size < 2) {
        return OdometerWear(anchors = ordered.size, firstDate = ordered.firstOrNull()?.date, lastDate = ordered.lastOrNull()?.date)
    }

    var intervals = 0
    var covered = 0.0
    var work = 0.0
    var personal = 0.0
    var unknown = 0
    var totalWear = 0.0
    var workWear = 0.0
    var personalWear = 0.0

    for (i in 0 until ordered.lastIndex) {
        val from = ordered[i]
        val to = ordered[i + 1]
        if (to.km < from.km) {
            unknown++
            continue
        }
        val total = to.km - from.km
        if (total <= 0.0) continue
        val intervalWork = store.shifts.sumOf { sh ->
            val start = shiftStartMs(sh)
            val end = shiftEndMs(sh)
            val shiftKm = shiftKm(sh, store.settings.profit).coerceAtLeast(0.0)
            if (shiftKm <= 0.0) return@sumOf 0.0
            val duration = end - start
            if (duration <= 0L) {
                if (start in from.at until to.at) shiftKm else 0.0
            } else {
                val overlap = (minOf(end, to.at) - maxOf(start, from.at)).coerceAtLeast(0L)
                if (overlap <= 0L) 0.0 else shiftKm * (overlap.toDouble() / duration.toDouble())
            }
        }
        // A shift can only use at most the distance the odometer says existed. If the work
        // figure is larger, the anchors are incomplete or inconsistent; report it rather than
        // subtracting a negative personal distance.
        val boundedWork = min(intervalWork, total)
        val intervalPersonal = (total - boundedWork).coerceAtLeast(0.0)
        intervals++
        covered += total
        work += boundedWork
        personal += intervalPersonal
        totalWear += total * rate
        workWear += boundedWork * rate
        personalWear += intervalPersonal * rate
        if (intervalWork > total + 0.5) unknown++
    }

    return OdometerWear(
        anchors = ordered.size,
        intervals = intervals,
        coveredKm = covered,
        workKm = work,
        personalKm = personal,
        totalWear = totalWear,
        workWear = workWear,
        personalWear = personalWear,
        unknownIntervals = unknown,
        firstDate = ordered.first().date,
        lastDate = ordered.last().date,
    )
}

@Composable
fun VehicleWearCard(store: Store, modifier: Modifier = Modifier, onAddReading: (() -> Unit)? = null) {
    val scheme = MaterialTheme.colorScheme
    val wear = remember(store) { odometerWear(store) }
    Surface(
        modifier = modifier.fillMaxWidth().semantics {
            contentDescription = "Odometer wear, work and personal distance"
        },
        shape = InstrumentShape,
        color = scheme.surfaceContainerLow,
    ) {
        Column(Modifier.padding(Space.m), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("/ ODOMETER WEAR", color = scheme.primary, fontFamily = FontFamily.Monospace, letterSpacing = 1.2.sp, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), modifier = Modifier.weight(1f))
                Text("${wear.anchors} anchors", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
            }
            if (!wear.hasCoverage) {
                Spacer(Modifier.height(Space.xs))
                Text(
                    "Work wear is already calculated per shift. Add the odometer at a fuel fill to start splitting work and personal kilometres.",
                    color = scheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "No daily entry is needed; the next two odometer readings unlock this.",
                    color = scheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
                if (onAddReading != null) {
                    androidx.compose.material3.TextButton(onClick = onAddReading, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                        Text("Add odometer reading", color = scheme.primary, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                    }
                }
                return@Column
            }
            Text(
                "RM ${rm(wear.totalWear)} total wear",
                color = scheme.onSurface,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            )
            Text(
                "${"%.1f".format(wear.coveredKm)} km covered · ${wear.intervals} intervals",
                color = scheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(Space.xs))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                WearStat("Work", "${"%.1f".format(wear.workKm)} km", "RM ${rm(wear.workWear)}", scheme, Modifier.weight(1f))
                WearStat("Personal", "${"%.1f".format(wear.personalKm)} km", "RM ${rm(wear.personalWear)}", scheme, Modifier.weight(1f))
            }
            Text(
                "Work wear is already included in shift net. Personal wear is a vehicle cost, not a shift deduction.",
                color = scheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            val perKm = if (wear.coveredKm > 0.01) wear.totalWear / wear.coveredKm else 0.0
            val workPerKm = if (wear.workKm > 0.01) wear.workWear / wear.workKm else 0.0
            val personalPerKm = if (wear.personalKm > 0.01) wear.personalWear / wear.personalKm else 0.0
            Text(
                "RM ${"%.2f".format(perKm)}/km total · work ${"%.2f".format(workPerKm)} · personal ${"%.2f".format(personalPerKm)}",
                color = scheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
            )
            Text(
                "Coverage ${wear.firstDate ?: "?"} → ${wear.lastDate ?: "?"}" +
                    if (wear.unknownIntervals > 0) " · ${wear.unknownIntervals} reading gap(s)" else "",
                color = if (wear.unknownIntervals > 0) scheme.secondary else scheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            if (onAddReading != null) {
                androidx.compose.material3.TextButton(onClick = onAddReading, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                    Text("Add odometer reading", color = scheme.primary, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                }
            }
        }
    }
}

@Composable
private fun WearStat(
    label: String,
    km: String,
    wear: String,
    scheme: androidx.compose.material3.ColorScheme,
    modifier: Modifier = Modifier,
) {
    Column(modifier.background(scheme.surfaceContainerHigh, MaterialTheme.shapes.medium).padding(Space.s)) {
        Text(label, color = scheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
        Text(km, color = scheme.onSurface, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
        Text(wear, color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"))
    }
}


@Composable
fun ManualOdometerDialog(
    onSave: (String, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    var date by remember { mutableStateOf(todayIso()) }
    var km by remember { mutableStateOf("") }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = scheme.surfaceContainerHigh,
        title = { BakiDialogHeader("Add odometer reading", "one anchor, no daily habit", "◎") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
                Text(
                    "This is optional. It improves total and personal wear; fuel and shift odometers already count automatically.",
                    color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium,
                )
                androidx.compose.material3.OutlinedTextField(
                    value = date,
                    onValueChange = { date = it.filter { c -> c.isDigit() || c == '-' }.take(10) },
                    label = { Text("Date") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = bakiFieldColorsPublic(),
                )
                androidx.compose.material3.OutlinedTextField(
                    value = km,
                    onValueChange = { km = it.filter { c -> c.isDigit() }.take(9) },
                    label = { Text("Dashboard km") }, singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    colors = bakiFieldColorsPublic(),
                )
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(
                enabled = (km.toIntOrNull() ?: 0) > 0 && date.length == 10,
                onClick = { onSave(date, km.toIntOrNull() ?: 0) },
            ) { Text("Save reading", color = scheme.primary) }
        },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
