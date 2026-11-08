package app.baki

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import kotlin.math.roundToLong

/** Fuel and wear per km, resolved against the shift's own km/L when it has one. */
data class CostRates(val fuel: Double, val wear: Double, val kmPerLitre: Double) {
    val total: Double get() = fuel + wear
}

fun costRates(cfg: Profit, shiftKmPerLitre: Double? = null): CostRates {
    val eff = if ((shiftKmPerLitre ?: 0.0) > 0) shiftKmPerLitre!! else cfg.kmPerLitre
    val fuel = if (eff > 0) cfg.fuelPrice / eff else 0.0
    return CostRates(fuel, cfg.wearPerKm, eff)
}

/** Odometer wins, then the entered km, then an estimate from hours at average speed. */
fun shiftKm(sh: Shift, cfg: Profit = Profit()): Double {
    val a = sh.odoStart; val b = sh.odoEnd
    if (a != null && b != null && b >= a) return maxOf(0.0, b - a)
    if (sh.km > 0) return sh.km
    return sh.hours * cfg.avgSpeed
}

data class ShiftMetrics(
    val km: Double, val fuel: Double, val wear: Double, val running: Double,
    val gross: Double, val extraCost: Double, val grabFee: Double, val net: Double,
    val hrs: Double, val netHr: Double?,
) {
    val band: String get() = when {
        netHr == null -> "none"
        netHr < 12 -> "quiet"
        netHr < 15 -> "slow"
        netHr < 20 -> "steady"
        netHr < 25 -> "good"
        else -> "peak"
    }
}

/**
 * Fixed costs per month: the loan instalment, plus road tax and car insurance spread evenly.
 * These are real money that the per-shift maths used to ignore.
 */
fun monthlyOverhead(profit: Profit): Double =
    profit.loanMonthly + profit.roadTaxYearly / 12.0 + profit.carInsuranceYearly / 12.0

fun monthDrivenHours(store: Store, month: String): Double =
    store.shifts.filter { it.date.startsWith(month) }.sumOf { it.hours }

/** Fixed costs apportioned over the hours actually driven that month. */
fun overheadPerHour(store: Store, month: String): Double {
    val hours = monthDrivenHours(store, month)
    if (hours <= 0.0) return 0.0
    return monthlyOverhead(store.settings.profit) / hours
}

fun shiftMetrics(sh: Shift, cfg: Profit = Profit(), overheadPerHour: Double = 0.0): ShiftMetrics {
    val c = costRates(cfg, sh.kmPerLitre)
    val km = shiftKm(sh, cfg)
    val fuel = km * c.fuel
    val wear = km * c.wear
    val running = fuel + wear
    // money is stored in cents; the maths is in ringgit
    val gross = sh.gross / 100.0 + sh.incentive / 100.0
    val extra = if (sh.reimbursedTolls) 0.0 else sh.extra / 100.0
    val grabFee = sh.grabFee?.let { it / 100.0 } ?: cfg.grabInsurance
    val net = gross - running - extra - grabFee - overheadPerHour * sh.hours
    val netHr = if (sh.hours > 0) net / sh.hours else null
    return ShiftMetrics(km, fuel, wear, running, gross, extra, grabFee, net, sh.hours, netHr)
}

/**
 * The app's one animated figure: the value eases to its new amount so the digits change in place
 * rather than snapping. Shared by every hero number so Spending, Shifts and Summary move exactly
 * like Today does.
 *
 * It enters at zero, so opening a page counts the figure up instead of showing it already settled —
 * and any later change (a category filter, a month) animates from wherever it was.
 */
@Composable
fun EasedMoney(
    cents: Long,
    color: Color,
    style: TextStyle,
    modifier: Modifier = Modifier,
    prefix: String = "RM ",
) {
    val shown = remember { Animatable(0f) }
    LaunchedEffect(cents) {
        shown.animateTo(
            targetValue = cents.toFloat(),
            animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow),
        )
    }
    Text(
        prefix + rm(shown.value.roundToLong()),
        color = color,
        style = style,
        modifier = modifier,
    )
}
