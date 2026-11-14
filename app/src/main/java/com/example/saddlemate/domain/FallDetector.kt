package com.example.saddlemate.domain

import kotlin.math.abs

/**
 * State machine for fall detection: a high-g impact followed by a sustained
 * period of stillness (~1 g) confirms a fall. Feed it accelerometer magnitudes in g.
 */
class FallDetector(
    private val impactG: Double = 3.5,
    private val stillToleranceG: Double = 0.15,
    private val stillWindowMs: Long = 3_000L,
    private val observeWindowMs: Long = 15_000L,
) {
    enum class Result { NONE, IMPACT_DETECTED, FALL_CONFIRMED }

    private var impactAt: Long? = null
    private var stillSince: Long? = null

    fun onSample(magnitudeG: Double, timestampMs: Long): Result {
        val impact = impactAt
        if (impact == null) {
            if (magnitudeG >= impactG) {
                impactAt = timestampMs
                stillSince = null
                return Result.IMPACT_DETECTED
            }
            return Result.NONE
        }

        if (timestampMs - impact > observeWindowMs) {
            reset()
            return Result.NONE
        }

        if (abs(magnitudeG - 1.0) <= stillToleranceG) {
            val start = stillSince ?: timestampMs.also { stillSince = it }
            if (timestampMs - start >= stillWindowMs) {
                reset()
                return Result.FALL_CONFIRMED
            }
        } else {
            stillSince = null
        }
        return Result.NONE
    }

    fun reset() {
        impactAt = null
        stillSince = null
    }
}
