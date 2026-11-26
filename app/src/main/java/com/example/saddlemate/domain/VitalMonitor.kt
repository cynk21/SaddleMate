package com.example.saddlemate.domain

/**
 * Pure domain logic for horse heart rate monitoring:
 * - 15-second manual pulse counter (taps * 4 = bpm)
 * - Vet-Gate recovery evaluation (< 64 bpm within 2 minutes of cool-down = passed)
 * - Heart rate statistics across a ride (avg / max)
 */
object VitalMonitor {

    const val VET_GATE_PASS_BPM = 64
    const val PULSE_WINDOW_SEC = 15

    fun bpmFrom15SecTaps(taps: Int): Int = (taps.coerceAtLeast(0)) * 4

    fun isVetGatePassed(recoveryBpm: Int): Boolean = recoveryBpm in 1 until VET_GATE_PASS_BPM

    fun zoneLabel(bpm: Int): String = when {
        bpm <= 0 -> "Keine Messung"
        bpm < 45 -> "Ruhepuls ($bpm bpm)"
        bpm < VET_GATE_PASS_BPM -> "Erholung / Vet-Gate OK ($bpm bpm)"
        bpm < 110 -> "Lockeres Training ($bpm bpm)"
        bpm < 150 -> "Ausdauer / Trab-Galopp ($bpm bpm)"
        else -> "Hohe Belastung ($bpm bpm)"
    }

    fun stats(samples: List<Int>): Pair<Int, Int> {
        val valid = samples.filter { it > 0 }
        if (valid.isEmpty()) return 0 to 0
        return valid.average().toInt() to valid.max()
    }
}
