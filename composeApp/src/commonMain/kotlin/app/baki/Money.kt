package app.baki

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

fun shiftMetrics(sh: Shift, cfg: Profit = Profit()): ShiftMetrics {
    val c = costRates(cfg, sh.kmPerLitre)
    val km = shiftKm(sh, cfg)
    val fuel = km * c.fuel
    val wear = km * c.wear
    val running = fuel + wear
    // money is stored in cents; the maths is in ringgit
    val gross = sh.gross / 100.0 + sh.incentive / 100.0
    val extra = sh.extra / 100.0
    val grabFee = sh.grabFee?.let { it / 100.0 } ?: cfg.grabInsurance
    val net = gross - running - extra - grabFee
    val netHr = if (sh.hours > 0) net / sh.hours else null
    return ShiftMetrics(km, fuel, wear, running, gross, extra, grabFee, net, sh.hours, netHr)
}
