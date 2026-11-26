package com.example.saddlemate.domain

import com.example.saddlemate.data.local.FeedbackNoteEntity
import com.example.saddlemate.data.local.RideEntity
import com.example.saddlemate.data.remote.SupabaseSyncClient
import com.example.saddlemate.sensors.SensorBridgeBLE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Stage2FeaturesTest {

    @Test
    fun routeGenerator_buildsThreeClosedLoopsNearRequestedDistance() {
        val horse = Horse("bella", "Bella", 4, hasLaminitisHistory = true, 120.0, 500.0)
        val center = GeoPoint(52.009, 14.081, 0L)
        val loops = RouteGenerator.generateLoops(center, horse, 10.0)
        assertEquals(3, loops.size)
        loops.forEach { loop ->
            assertEquals(center.lat, loop.points.first().lat, 1e-6)
            assertEquals(center.lat, loop.points.last().lat, 1e-6)
            assertTrue("Expected ~10 km loop, got ${loop.actualKm}", loop.actualKm in 8.0..13.5)
            assertTrue(loop.suitableNote.contains("Rehe"))
        }
    }

    @Test
    fun vitalMonitor_counts15SecTapsAndChecksVetGate() {
        assertEquals(56, VitalMonitor.bpmFrom15SecTaps(14))
        assertTrue(VitalMonitor.isVetGatePassed(60))
        assertFalse(VitalMonitor.isVetGatePassed(68))
        val (avg, max) = VitalMonitor.stats(listOf(60, 120, 90))
        assertEquals(90, avg)
        assertEquals(120, max)
    }

    @Test
    fun bleHeartRateParser_handlesUint8AndUint16() {
        // Flags 0x00 -> uint8 BPM = 72
        assertEquals(72, SensorBridgeBLE.parseHeartRateBpm(byteArrayOf(0x00, 72)))
        // Flags 0x01 -> uint16 little-endian = 0x0104 = 260
        assertEquals(260, SensorBridgeBLE.parseHeartRateBpm(byteArrayOf(0x01, 0x04, 0x01)))
    }

    @Test
    fun supabaseClient_buildsTicketAndRideJsonAndRejectsMissingConfig() {
        val client = SupabaseSyncClient()
        assertFalse(client.isConfigured("", ""))
        val ticketsJson = SupabaseSyncClient.buildTicketsJson(
            listOf(FeedbackNoteEntity(1L, "Route merken", 1000L, status = "BACKLOG", priority = "HIGH")),
        )
        assertTrue(ticketsJson.contains("backlog_ranked"))
        assertTrue(ticketsJson.contains("Route merken"))

        val ridesJson = SupabaseSyncClient.buildRidesJson(
            listOf(
                RideEntity(
                    id = "r1",
                    horseId = "bella",
                    startedAtMillis = 1_700_000_000_000L,
                    durationMs = 600_000L,
                    distanceMeters = 3200.0,
                    averageSpeedKmh = 12.0,
                    maxSpeedKmh = 21.0,
                    welfareScore = 95,
                    tendonLoad = 4.0,
                    warnings = "",
                    riderCalories = 220,
                    horseCalories = 900,
                ),
            ),
        )
        assertTrue(ridesJson.contains("3200"))
        assertTrue(ridesJson.contains("220"))
    }
}
