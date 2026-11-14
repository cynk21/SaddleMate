package com.example.saddlemate.domain

/**
 * Sensorless horse-welfare estimate derived from ride telemetry.
 * Score starts at 100 and is reduced by heuristic penalties.
 * This is a training aid, not a veterinary diagnosis.
 */
data class WelfareReport(val score: Int, val tendonLoad: Double, val warnings: List<String>)

class WelfareEngine {

    fun evaluate(
        analysis: RideAnalysis,
        horse: Horse,
        warmupMs: Long,
        temperatureC: Double?,
    ): WelfareReport {
        val warnings = mutableListOf<String>()
        var score = 100

        val minutesPerGait = analysis.gaitTimeMs.mapValues { it.value / 60_000.0 }
        val tendonLoad = minutesPerGait.entries.sumOf { (gait, min) -> gait.loadWeight() * min }

        val fastMinutes = (minutesPerGait[Gait.CANTER] ?: 0.0) + (minutesPerGait[Gait.GALLOP] ?: 0.0)
        val requiredWarmupMin = if (horse.isYoung) 15.0 else 10.0
        if (fastMinutes > 0.0 && warmupMs / 60_000.0 < requiredWarmupMin) {
            score -= 15
            warnings += "Warm-up unter ${requiredWarmupMin.toInt()} min vor Tempoarbeit"
        }

        val rideMinutes = analysis.durationMs / 60_000.0
        if (rideMinutes > 0.0 && tendonLoad / rideMinutes > HIGH_LOAD_PER_MINUTE) {
            score -= 10
            warnings += "Hohe Sehnenbelastung – Tempo reduzieren"
        }

        if (temperatureC != null) {
            when {
                temperatureC >= 33.0 -> { score -= 25; warnings += "Extreme Hitze – Ritt abbrechen/verkürzen" }
                temperatureC >= 28.0 -> { score -= 15; warnings += "Hitze – regelmäßig Pausen und Wasser" }
            }
        }

        if (horse.hasLaminitisHistory && (minutesPerGait[Gait.TROT] ?: 0.0) > 30.0) {
            score -= 10
            warnings += "Rehe-Historie: Trabanteil begrenzen, Hufe beobachten"
        }

        return WelfareReport(
            score = score.coerceIn(0, 100),
            tendonLoad = tendonLoad,
            warnings = warnings,
        )
    }

    private fun Gait.loadWeight(): Double = when (this) {
        Gait.STANDSTILL -> 0.0
        Gait.WALK -> 0.2
        Gait.TROT -> 1.0
        Gait.CANTER -> 2.0
        Gait.GALLOP -> 3.0
    }

    private companion object {
        const val HIGH_LOAD_PER_MINUTE = 1.2
    }
}
