package app.baki

import kotlin.math.abs

/**
 * Bars stand on a zero line that sits where the gain/loss peaks meet, so one shared scale is used for
 * both directions: a −60 loss is visibly about half a +115 gain, and nothing is rescaled per side.
 */
object SignedBars {

    data class Bar(val fraction: Float, val positive: Boolean, val zero: Boolean)

    /** Share of the plot height, measured against the larger of the two peaks. */
    fun of(net: Long, peakPositive: Long, peakNegative: Long): Bar {
        if (net == 0L) return Bar(0f, true, true)
        val max = maxOf(peakPositive, peakNegative).coerceAtLeast(1L)
        return Bar((abs(net).toFloat() / max).coerceIn(0f, 1f), net > 0, false)
    }

    /** Where zero sits in the plot: gains above this line, losses below it. */
    fun zeroFraction(peakPositive: Long, peakNegative: Long): Float {
        val sum = (peakPositive + peakNegative).coerceAtLeast(1L)
        return peakPositive.toFloat() / sum
    }
}
