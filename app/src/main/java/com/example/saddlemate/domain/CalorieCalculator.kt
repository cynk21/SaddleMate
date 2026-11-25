package com.example.saddlemate.domain

/** Rider profile used for calorie estimation. Stored locally in [RideSettings]. */
data class RiderProfile(
    val name: String = "",
    val weightKg: Double = 70.0,
    val heightCm: Int = 175,
    val ageYears: Int = 35,
)

/** Estimated calories burned during a ride for rider and horse. */
data class CalorieEstimate(
    val riderKcal: Int,
    val horseKcal: Int,
)

/**
 * Estimates energy spent during a ride from the gait breakdown, rider weight, and horse weight.
 * Pure logic, no Android dependencies, unit-testable.
 *
 * Uses standard MET values for horseback riding by gait (walk ~3.8, trot ~5.8, canter/gallop ~7.3,
 * standstill ~1.3) with `kcal = MET * weightKg * hours`.
 * For the horse, uses body-weight scaled kcal/min rates per gait when weight > 0 (defaults to 500 kg).
 */
object CalorieCalculator {

    private const val DEFAULT_RIDER_KG = 70.0
    private const val DEFAULT_HORSE_KG = 500.0

    fun estimate(
        gaitTimeMs: Map<Gait, Long>,
        riderWeightKg: Double,
        horseWeightKg: Double,
    ): CalorieEstimate {
        val riderKg = if (riderWeightKg > 0.0) riderWeightKg else DEFAULT_RIDER_KG
        val horseKg = if (horseWeightKg > 0.0) horseWeightKg else DEFAULT_HORSE_KG

        var riderKcal = 0.0
        var horseKcal = 0.0
        for ((gait, ms) in gaitTimeMs) {
            if (ms <= 0L) continue
            val hours = ms / 3_600_000.0
            val minutes = ms / 60_000.0
            riderKcal += riderMet(gait) * riderKg * hours
            // Per 500 kg horse: walk ~25 kcal/min, trot ~65, canter ~110, gallop ~160, stand ~7
            horseKcal += horseKcalPerMin500(gait) * (horseKg / DEFAULT_HORSE_KG) * minutes
        }
        return CalorieEstimate(
            riderKcal = kotlin.math.round(riderKcal).toInt(),
            horseKcal = kotlin.math.round(horseKcal).toInt(),
        )
    }

    private fun riderMet(gait: Gait): Double = when (gait) {
        Gait.STANDSTILL -> 1.3
        Gait.WALK -> 3.8
        Gait.TROT -> 5.8
        Gait.CANTER -> 7.3
        Gait.GALLOP -> 8.0
    }

    private fun horseKcalPerMin500(gait: Gait): Double = when (gait) {
        Gait.STANDSTILL -> 7.0
        Gait.WALK -> 25.0
        Gait.TROT -> 65.0
        Gait.CANTER -> 110.0
        Gait.GALLOP -> 160.0
    }
}
