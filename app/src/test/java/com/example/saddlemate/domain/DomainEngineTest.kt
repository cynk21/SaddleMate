package com.example.saddlemate.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TelemetryEngineTest {
    private val engine = TelemetryEngine()

    // ~0.0009 deg lat ≈ 100 m
    private fun pt(latSteps: Int, t: Long) = GeoPoint(47.0 + latSteps * 0.0009, 11.0, t)

    @Test
    fun distance_isAboutOneHundredMetersPer0009Lat() {
        val d = engine.distanceMeters(pt(0, 0), pt(1, 0))
        assertEquals(100.0, d, 1.0)
    }

    @Test
    fun filter_dropsNonMonotonicTimestamps() {
        val out = engine.filter(listOf(pt(0, 0), pt(1, 0), pt(2, 0)))
        assertEquals(1, out.size)
    }

    @Test
    fun filter_dropsTeleportSpikes() {
        val spike = GeoPoint(48.0, 11.0, 1_000L) // ~111 km in 1 s
        val out = engine.filter(listOf(pt(0, 0), spike))
        assertEquals(1, out.size)
    }

    @Test
    fun inferGait_thresholds() {
        assertEquals(Gait.STANDSTILL, engine.inferGait(0.2))
        assertEquals(Gait.WALK, engine.inferGait(5.0))
        assertEquals(Gait.TROT, engine.inferGait(12.0))
        assertEquals(Gait.CANTER, engine.inferGait(18.0))
        assertEquals(Gait.GALLOP, engine.inferGait(30.0))
    }

    @Test
    fun analyze_producesOneKmSplitPer1000m() {
        // 100 m every 30 s ≈ 12 km/h trot, 12 steps ≈ 1.1 km
        val pts = (0..11).map { i -> pt(i, i * 30_000L) }
        val result = engine.analyze(pts)
        assertTrue(result.distanceMeters > 1000.0)
        assertEquals(1, result.splits.size)
        assertEquals(Gait.TROT, engine.inferGait(result.averageSpeedKmh))
    }

    @Test
    fun analyze_emptyForSinglePoint() {
        assertEquals(RideAnalysis.EMPTY, engine.analyze(listOf(pt(0, 0))))
    }
}

class WelfareEngineTest {
    private val engine = WelfareEngine()
    private val youngHorse = Horse("1", "Bella", 4, false, 100.0, 500.0)
    private val oldHorse = Horse("2", "Ben", 12, true, 100.0, 500.0)

    private fun analysis(canterMin: Double, trotMin: Double = 0.0) = RideAnalysis(
        distanceMeters = 5000.0,
        durationMs = ((canterMin + trotMin) * 60_000).toLong(),
        averageSpeedKmh = 12.0,
        maxSpeedKmh = 20.0,
        splits = emptyList(),
        gaitTimeMs = buildMap {
            if (canterMin > 0) put(Gait.CANTER, (canterMin * 60_000).toLong())
            if (trotMin > 0) put(Gait.TROT, (trotMin * 60_000).toLong())
        },
    )

    @Test
    fun perfectRide_scores100() {
        val r = engine.evaluate(analysis(canterMin = 0.0, trotMin = 10.0), youngHorse, 15 * 60_000L, 15.0)
        assertEquals(100, r.score)
        assertTrue(r.warnings.isEmpty())
    }

    @Test
    fun canterWithoutWarmup_isPenalised() {
        val r = engine.evaluate(analysis(canterMin = 3.0), youngHorse, 2 * 60_000L, 15.0)
        assertTrue(r.score < 100)
        assertTrue(r.warnings.any { it.contains("Warm-up") })
    }

    @Test
    fun extremeHeat_isPenalisedHeavily() {
        val r = engine.evaluate(analysis(0.0, 10.0), youngHorse, 15 * 60_000L, 35.0)
        assertEquals(75, r.score)
    }

    @Test
    fun laminitisHistory_limitsTrot() {
        val r = engine.evaluate(analysis(0.0, 40.0), oldHorse, 15 * 60_000L, null)
        assertTrue(r.warnings.any { it.contains("Rehe") })
    }
}

class HazardRadarEngineTest {
    private val radar = HazardRadarEngine()
    private val me = GeoPoint(47.0, 11.0, 0L)

    private fun hazardNorth(meters: Double): HazardMarker {
        val deg = meters / 111_320.0
        return HazardMarker("h", GeoPoint(47.0 + deg, 11.0, 0L), HazardType.DOG, "Hund")
    }

    @Test
    fun hazardAheadWithinRadius_isReported() {
        val alerts = radar.evaluate(me, headingDeg = 0.0, hazards = listOf(hazardNorth(50.0)))
        assertEquals(1, alerts.size)
        assertEquals(RelativeSide.RIGHT, alerts.first().side)
    }

    @Test
    fun hazardBehind_isIgnored() {
        val alerts = radar.evaluate(me, headingDeg = 180.0, hazards = listOf(hazardNorth(50.0)))
        assertTrue(alerts.isEmpty())
    }

    @Test
    fun hazardBeyondRadius_isIgnored() {
        val alerts = radar.evaluate(me, headingDeg = 0.0, hazards = listOf(hazardNorth(500.0)))
        assertFalse(alerts.isNotEmpty())
    }

    @Test
    fun normalizeDeg_wrapsCorrectly() {
        assertEquals(-90.0, radar.normalizeDeg(270.0), 1e-9)
        assertEquals(10.0, radar.normalizeDeg(-350.0), 1e-9)
    }
}

class FallDetectorTest {
    @Test
    fun impactFollowedByStillness_confirmsFall() {
        val d = FallDetector()
        assertEquals(FallDetector.Result.IMPACT_DETECTED, d.onSample(4.0, 0L))
        d.onSample(1.0, 500L)
        assertEquals(FallDetector.Result.FALL_CONFIRMED, d.onSample(1.0, 4_000L))
    }

    @Test
    fun impactFollowedByMovement_doesNotConfirm() {
        val d = FallDetector()
        d.onSample(4.0, 0L)
        assertEquals(FallDetector.Result.NONE, d.onSample(2.5, 1_000L))
        assertEquals(FallDetector.Result.NONE, d.onSample(1.0, 2_000L))
        assertEquals(FallDetector.Result.NONE, d.onSample(1.0, 4_000L))
    }

    @Test
    fun noImpact_neverTriggers() {
        val d = FallDetector()
        assertEquals(FallDetector.Result.NONE, d.onSample(1.0, 0L))
        assertEquals(FallDetector.Result.NONE, d.onSample(1.0, 10_000L))
    }
}

class AudioModeTest {
    @Test
    fun silent_disablesAllChannels() {
        val out = AudioMode.SILENT.outputForAlert()
        assertFalse(out.speak || out.haptic || out.tone)
    }

    @Test
    fun fullCopilot_speaks() {
        assertTrue(AudioMode.FULL_COPILOT.outputForAlert().speak)
    }
}
