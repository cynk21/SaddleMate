package com.example.saddlemate.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Stage1CompletionTest {

    @Test
    fun gaitSymmetryTracker_detectsAsymmetryBelowThreshold() {
        val tracker = GaitSymmetryTracker()
        // Feed alternating strong / weak impulses in trot (6 peaks -> 3 pairs)
        val samples = listOf(1.0, 2.2, 1.0, 1.4, 1.0, 2.2, 1.0, 1.4, 1.0, 2.2, 1.0, 1.4, 1.0)
        var last = 100.0
        for (g in samples) last = tracker.onAccelSample(g, Gait.TROT)
        assertTrue("Expected asymmetry < 85%, got $last", last < GaitSymmetryTracker.WARN_THRESHOLD_PCT)
    }

    @Test
    fun weatherShield_flagsHighFructanForLaminitisHorseOnColdSunnyDay() {
        val report = WeatherShield.evaluate(
            WeatherConditions(temperatureC = 16.0, humidityPct = 55, nightMinTempC = 2.0, sunny = true),
            hasLaminitisHistory = true,
        )
        assertEquals(RiskLevel.HIGH, report.fructanRisk)
        assertTrue(report.warnings.any { it.contains("Fruktan") })
    }

    @Test
    fun canterStretchDetector_findsStretchOver250Meters() {
        // 4 points moving north at ~20 km/h (~5.55 m/s) over 60 s -> ~333 m canter
        val pts = (0..6).map { i ->
            val meters = i * 55.5
            GeoPoint(52.0 + meters / 111_320.0, 14.0, i * 10_000L)
        }
        val stretches = CanterStretchDetector.detect(pts)
        assertEquals(1, stretches.size)
        assertTrue(stretches.first().lengthMeters >= 250.0)
    }

    @Test
    fun voiceQaRouter_answersWarmupAndDistanceQuestions() {
        val ctx = VoiceQaRouter.RideQaContext(
            warmupMinutes = 6.0,
            requiredWarmupMinutes = 10.0,
            cooldownNeededMinutes = 3.0,
            distanceMeters = 2400.0,
            remainingMeters = 1200.0,
            welfareScore = 92,
            symmetryPct = 96.0,
            nextHazardLabel = "Hund am Zaun",
            nextHazardDistM = 110.0,
        )
        assertTrue(VoiceQaRouter.answer("Ist mein Pferd schon warmgeritten?", ctx)!!.contains("4 Minuten"))
        assertTrue(VoiceQaRouter.answer("Wie weit noch bis zum Stall?", ctx)!!.contains("1,2 Kilometer"))
        assertNotNull(VoiceQaRouter.answer("Gibt es eine Gefahr voraus?", ctx))
    }

    @Test
    fun surfaceLoadTracker_warnsOnTrotOverAsphalt() {
        assertNotNull(SurfaceLoadTracker.checkLoad(Gait.TROT, "asphalt", hasLaminitisHistory = true))
        assertNull(SurfaceLoadTracker.checkLoad(Gait.WALK, "asphalt", hasLaminitisHistory = true))
    }
}
