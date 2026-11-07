package app.baki

import kotlin.math.abs

/**
 * Bars stand on a zero line: gains rise above it, losses hang below. Drawing every bar upward makes
 * a −60 look bigger than a +39, which is a lying chart.
 */
object SignedBars {

    /** Fraction of the half-plot a bar occupies, and which side of zero it sits on. */
    data class Bar(val fraction: Float, val positive: Boolean, val zero: Boolean)

    fun of(net: Long, peakPositive: Long, peakNegative: Long): Bar {
        if (net == 0L) return Bar(0f, true, true)
        return if (net > 0) {
            Bar((net.toFloat() / peakPositive.coerceAtLeast(1)).coerceIn(0f, 1f), true, false)
        } else {
            Bar((abs(net).toFloat() / peakNegative.coerceAtLeast(1)).coerceIn(0f, 1f), false, false)
        }
    }
}
