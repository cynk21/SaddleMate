package com.example.saddlemate.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class SpeedBandTest {

    @Test
    fun slowSpeedsAreGreen() {
        assertEquals(SpeedBand.SLOW, SpeedBands.of(0.0))
        assertEquals(SpeedBand.SLOW, SpeedBands.of(6.9))
    }

    @Test
    fun mediumSpeedsAreYellow() {
        assertEquals(SpeedBand.MEDIUM, SpeedBands.of(7.0))
        assertEquals(SpeedBand.MEDIUM, SpeedBands.of(14.9))
    }

    @Test
    fun fastSpeedsAreRed() {
        assertEquals(SpeedBand.FAST, SpeedBands.of(15.0))
        assertEquals(SpeedBand.FAST, SpeedBands.of(30.0))
    }
}
