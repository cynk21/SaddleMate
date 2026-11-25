package com.example.saddlemate.data.mock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrailRouteTest {

    @Test
    fun route_hasNoConsecutiveDuplicates() {
        // The 81 raw OSM fixes contained 8 consecutive duplicates from way joins; those are removed.
        assertEquals(73, TrailRoute.points.size)
        TrailRoute.points.zipWithNext().forEach { (a, b) ->
            assertTrue("duplicate fix at ${a.lat},${a.lon}", a.lat != b.lat || a.lon != b.lon)
        }
    }

    @Test
    fun route_isClosed() {
        val first = TrailRoute.points.first()
        val last = TrailRoute.points.last()
        assertEquals(first.lat, last.lat, 1e-9)
        assertEquals(first.lon, last.lon, 1e-9)
    }

    @Test
    fun route_lengthMatchesOsmMeasurement() {
        // Measured from the OSM geometry before import: 2.89 km.
        assertTrue(
            "Length ${TrailRoute.lengthMeters} m, expected about 2890 m",
            kotlin.math.abs(TrailRoute.lengthMeters - 2890.0) < 60.0,
        )
    }

    @Test
    fun route_liesInBrandenburg_nearGlietz30kmRadius() {
        // Centroid should be within 30 km of the Glietz test centre (52.00914, 14.08120).
        val lat = TrailRoute.points.map { it.lat }.average()
        val lon = TrailRoute.points.map { it.lon }.average()
        val d = TrailRoute.lengthMeters // unused guard to keep route initialised
        assertTrue(d > 0)
        val dLat = (lat - MockData.CENTER_LAT) * 111_320.0
        val dLon = (lon - MockData.CENTER_LON) * 111_320.0 * kotlin.math.cos(Math.toRadians(MockData.CENTER_LAT))
        val km = kotlin.math.sqrt(dLat * dLat + dLon * dLon) / 1000.0
        assertTrue("Route centroid is $km km from Glietz", km < 30.0)
    }
}
