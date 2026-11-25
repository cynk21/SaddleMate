package com.example.saddlemate.domain

/** Ascent and descent in metres, from GPS altitude. A rough guide only (GPS altitude is noisy). */
data class Elevation(val ascentM: Double, val descentM: Double) {
    companion object {
        val NONE = Elevation(0.0, 0.0)
    }
}

/**
 * Turns a noisy GPS altitude series into ascent and descent.
 *
 * Small up and down wiggles are ignored: a change counts only after the altitude has moved
 * by at least [thresholdM] from the last counted level. That keeps GPS jitter out of the total.
 */
object ElevationCalculator {

    const val DEFAULT_THRESHOLD_M = 3.0

    fun ascentDescent(altitudes: List<Double>, thresholdM: Double = DEFAULT_THRESHOLD_M): Elevation {
        if (altitudes.size < 2) return Elevation.NONE
        var ascent = 0.0
        var descent = 0.0
        var reference = altitudes.first()
        for (alt in altitudes.drop(1)) {
            val diff = alt - reference
            when {
                diff >= thresholdM -> {
                    ascent += diff
                    reference = alt
                }
                diff <= -thresholdM -> {
                    descent += -diff
                    reference = alt
                }
            }
        }
        return Elevation(ascent, descent)
    }
}
