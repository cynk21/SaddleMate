package com.example.saddlemate.domain

/** Colour band for a track segment: green = slow, yellow = medium, red = fast. */
enum class SpeedBand(val hex: String, val label: String) {
    SLOW("#2E7D32", "langsam"),
    MEDIUM("#F9A825", "mittel"),
    FAST("#C62828", "schnell"),
}

object SpeedBands {
    /** Below this speed the segment is green (walk). */
    const val SLOW_BELOW_KMH = 7.0

    /** From this speed on the segment is red (canter and faster). */
    const val FAST_FROM_KMH = 15.0

    fun of(speedKmh: Double): SpeedBand = when {
        speedKmh < SLOW_BELOW_KMH -> SpeedBand.SLOW
        speedKmh < FAST_FROM_KMH -> SpeedBand.MEDIUM
        else -> SpeedBand.FAST
    }
}
