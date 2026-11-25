package com.example.saddlemate.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CalorieCalculatorTest {

    @Test
    fun calculatesRiderAndHorseCaloriesFromGaitsAndWeights() {
        val gaits = mapOf(
            Gait.WALK to 30 * 60_000L, // 30 min walk
            Gait.TROT to 30 * 60_000L, // 30 min trot
        )
        val est = CalorieCalculator.estimate(
            gaitTimeMs = gaits,
            riderWeightKg = 80.0,
            horseWeightKg = 600.0,
        )
        // Rider: (3.8 * 80 * 0.5) + (5.8 * 80 * 0.5) = 152 + 232 = 384
        assertEquals(384, est.riderKcal)
        // Horse (600/500 = 1.2): (25*30 + 65*30) * 1.2 = 2700 * 1.2 = 3240
        assertEquals(3240, est.horseKcal)
    }

    @Test
    fun fallsBackToDefaultWeightsWhenZero() {
        val est = CalorieCalculator.estimate(
            gaitTimeMs = mapOf(Gait.WALK to 60 * 60_000L),
            riderWeightKg = 0.0,
            horseWeightKg = 0.0,
        )
        assertTrue(est.riderKcal > 0)
        assertTrue(est.horseKcal > 0)
    }
}
