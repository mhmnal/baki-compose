package app.baki

import kotlin.math.max

/**
 * How much distance one accepted fix actually represents.
 *
 * Position alone is a poor judge of travel at low speed: consumer GPS jitters by metres every
 * second, so a fixed "must move 3 m" gate throws away real movement in stop-go traffic while a
 * stationary car can still appear to wander. The fix carries a Doppler speed that is far steadier
 * than its position, so we believe the speed when it says the car is moving, and otherwise trust the
 * position once it clears a noise floor proportional to the reported accuracy.
 *
 * @param stepMeters straight-line distance since the previous accepted fix
 * @param seconds time since the previous accepted fix
 * @param accuracyMeters horizontal accuracy of this fix, metres (68th percentile)
 * @param speedMps speed reported by the fix, m/s
 * @param hasSpeed whether the fix carried a speed at all
 */
fun travelMetres(
    stepMeters: Double,
    seconds: Double,
    accuracyMeters: Double,
    speedMps: Double,
    hasSpeed: Boolean,
): Double {
    if (seconds <= 0.0) return 0.0
    // jitter scales with accuracy: a 5 m fix can be trusted to ~1.5 m, a 30 m fix only to ~8 m
    val noiseFloor = (accuracyMeters * 0.25).coerceIn(1.5, 8.0)
    val byPosition = if (stepMeters >= noiseFloor) stepMeters else 0.0
    // speed is only believed above walking pace, and capped so a bad sample cannot invent travel
    val bySpeed = if (hasSpeed && speedMps > 0.7) {
        (speedMps * seconds).coerceAtMost(stepMeters + 15.0)
    } else {
        0.0
    }
    return max(byPosition, bySpeed)
}
