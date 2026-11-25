package com.example.saddlemate.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class ElevationCalculatorTest {

    @Test
    fun steadyClimb_countsAllAscent() {
        val e = ElevationCalculator.ascentDescent(listOf(100.0, 110.0, 120.0, 130.0))
        assertEquals(30.0, e.ascentM, 0.001)
        assertEquals(0.0, e.descentM, 0.001)
    }

    @Test
    fun jitterBelowThreshold_isIgnored() {
        // Wiggles of 1 m around 100 m must not add up to climbing.
        val e = ElevationCalculator.ascentDescent(listOf(100.0, 101.0, 100.0, 101.0, 100.0, 101.0))
        assertEquals(0.0, e.ascentM, 0.001)
        assertEquals(0.0, e.descentM, 0.001)
    }

    @Test
    fun climbThenDescent_countsBoth() {
        val e = ElevationCalculator.ascentDescent(listOf(100.0, 120.0, 100.0))
        assertEquals(20.0, e.ascentM, 0.001)
        assertEquals(20.0, e.descentM, 0.001)
    }

    @Test
    fun fewerThanTwoPoints_isNone() {
        assertEquals(Elevation.NONE, ElevationCalculator.ascentDescent(emptyList()))
        assertEquals(Elevation.NONE, ElevationCalculator.ascentDescent(listOf(100.0)))
    }
}
