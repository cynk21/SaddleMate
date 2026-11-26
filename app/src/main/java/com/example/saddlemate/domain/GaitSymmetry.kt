package com.example.saddlemate.domain

import kotlin.math.abs

/**
 * Estimates trot symmetry (0..100%) from accelerometer vertical/magnitude peak intervals
 * during trot phases. Pure logic, unit-testable.
 *
 * Compares consecutive stride-half beat amplitudes: symmetric trot produces even left/right
 * impulses (~92..100%), while persistent asymmetry drops below 85% and triggers a hoof check hint.
 */
class GaitSymmetryTracker {

    private val peaks = ArrayDeque<Double>()
    private var prevPrev = 0.0
    private var prev = 0.0
    private var lastSymmetryPct = 100.0

    fun onAccelSample(magnitudeG: Double, gait: Gait): Double {
        if (gait != Gait.TROT) {
            prevPrev = 0.0
            prev = 0.0
            return lastSymmetryPct
        }
        // Local peak above 1.25 g indicates a footfall beat
        if (prev > 1.25 && prev > prevPrev && prev > magnitudeG) {
            peaks.addLast(prev)
            while (peaks.size > WINDOW_PEAKS) peaks.removeFirst()
            if (peaks.size >= 6) {
                lastSymmetryPct = computeSymmetry(peaks.toList())
            }
        }
        prevPrev = prev
        prev = magnitudeG
        return lastSymmetryPct
    }

    fun currentSymmetryPct(): Double = lastSymmetryPct

    fun reset() {
        peaks.clear()
        prevPrev = 0.0
        prev = 0.0
        lastSymmetryPct = 100.0
    }

    companion object {
        private const val WINDOW_PEAKS = 12
        const val WARN_THRESHOLD_PCT = 85.0

        fun computeSymmetry(beats: List<Double>): Double {
            if (beats.size < 4) return 100.0
            val evens = beats.filterIndexed { i, _ -> i % 2 == 0 }
            val odds = beats.filterIndexed { i, _ -> i % 2 == 1 }
            val avgEven = evens.average()
            val avgOdd = odds.average()
            val maxAvg = maxOf(avgEven, avgOdd, 0.001)
            val diffRatio = abs(avgEven - avgOdd) / maxAvg
            return ((1.0 - diffRatio) * 100.0).coerceIn(0.0, 100.0)
        }
    }
}
