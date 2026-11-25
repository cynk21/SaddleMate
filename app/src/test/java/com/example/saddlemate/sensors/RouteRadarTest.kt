package com.example.saddlemate.sensors

import com.example.saddlemate.data.mock.MockData
import com.example.saddlemate.data.mock.RouteCatalog
import com.example.saddlemate.domain.GeoPoint
import com.example.saddlemate.domain.HazardRadarEngine
import com.example.saddlemate.domain.TelemetryEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Riding the Weinberg route must trigger the radar for every route hazard.
 */
class RouteRadarTest {

    private val telemetry = TelemetryEngine()
    private val radar = HazardRadarEngine(telemetry = telemetry)

    @Test
    fun routeRide_triggersEveryHazard() {
        val route = RouteCatalog.points(RouteCatalog.default)
        val hazards = MockData.weinbergHazards(route)
        // Ride the route point by point in order, as a GPS track would arrive.
        val track = route
        val seen = mutableSetOf<String>()
        for (i in 1 until track.size) {
            val heading = radar.bearingDeg(track[i - 1], track[i])
            radar.evaluate(track[i], heading, hazards).forEach { seen += it.hazard.id }
        }

        assertEquals(
            "Not every hazard triggered, seen=$seen",
            hazards.map { it.id }.toSet(),
            seen,
        )
    }

    /**
     * A hazard must lie on the route line, i.e. within a few metres of the nearest SEGMENT,
     * not only of a vertex.
     */
    @Test
    fun routeHazards_lieOnTheRoute() {
        val route = RouteCatalog.points(RouteCatalog.default)
        for (hazard in MockData.weinbergHazards(route)) {
            val nearest = route.zipWithNext().minOf { (a, b) ->
                distanceToSegmentMeters(hazard.location, a, b)
            }
            assertTrue("Hazard ${hazard.id} is $nearest m off the route", nearest < 3.0)
        }
    }

    /** Planar distance to a segment, fine at route scale. */
    private fun distanceToSegmentMeters(p: GeoPoint, a: GeoPoint, b: GeoPoint): Double {
        val k = 111_320.0
        val cosLat = kotlin.math.cos(Math.toRadians(p.lat))
        val px = (p.lon - a.lon) * k * cosLat
        val py = (p.lat - a.lat) * k
        val bx = (b.lon - a.lon) * k * cosLat
        val by = (b.lat - a.lat) * k
        val len2 = bx * bx + by * by
        val t = if (len2 == 0.0) 0.0 else ((px * bx + py * by) / len2).coerceIn(0.0, 1.0)
        val dx = px - t * bx
        val dy = py - t * by
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }
}
