package com.example.saddlemate.data.mock

import com.example.saddlemate.domain.GeoPoint
import com.example.saddlemate.domain.Horse
import com.example.saddlemate.domain.HazardMarker
import com.example.saddlemate.domain.HazardType
import kotlin.math.cos
import kotlin.math.sin

/**
 * Realistic offline test data so the app can be exercised indoors without a horse.
 * Later replaced/extended by Firestore data.
 */
object MockData {
    /** Test area: Glietz (Märkische Heide / Spreewald, Brandenburg). OSM node 195220566. */
    const val CENTER_LAT = 52.0091361
    const val CENTER_LON = 14.0812010

    /** Simulated ride: circle of this radius at trot pace (≈ 12 km/h). */
    const val SIM_RADIUS_M = 300.0
    const val SIM_SPEED_MPS = 3.3

    private const val M_PER_DEG_LAT = 111_320.0

    val horses = listOf(
        Horse(
            id = "bella",
            name = "Bella",
            ageYears = 4,
            hasLaminitisHistory = false,
            kmSinceFarrier = 120.0,
            farrierIntervalKm = 500.0,
        ),
        Horse(
            id = "ben",
            name = "Ben",
            ageYears = 12,
            hasLaminitisHistory = true,
            kmSinceFarrier = 430.0,
            farrierIntervalKm = 500.0,
        ),
    )

    /**
     * Hazards along the test route, placed at fixed shares of its length, so the radar
     * triggers while riding the route. Positions are interpolated on the route polyline.
     */
    val hazards: List<HazardMarker> = listOf(
        HazardMarker("hz-dog", TrailRoute.pointAtShare(0.20), HazardType.DOG, "Hund am Zaun"),
        HazardMarker("hz-turn", TrailRoute.pointAtShare(0.45), HazardType.BLIND_TURN, "Unübersichtliche Kurve"),
        HazardMarker("hz-road", TrailRoute.pointAtShare(0.70), HazardType.ROAD_CROSSING, "Straßenquerung"),
    )

    /**
     * Hazards on the Weinberg route, placed on fixed shares of the given route points.
     * The points come from the app's assets (see RideRepository.route), so this works on device.
     */
    fun weinbergHazards(routePoints: List<GeoPoint>): List<HazardMarker> {
        fun at(share: Double): GeoPoint = RouteCatalog.pointAtShare(routePoints, share)
        return listOf(
            HazardMarker("hz-dog", at(0.20), HazardType.DOG, "Hund am Zaun"),
            HazardMarker("hz-turn", at(0.45), HazardType.BLIND_TURN, "Unübersichtliche Kurve"),
            HazardMarker("hz-road", at(0.70), HazardType.ROAD_CROSSING, "Straßenquerung"),
        )
    }

    /** Circle hazards for the old circle test (kept so the existing radar tests stay valid). */
    val circleHazards: List<HazardMarker> = listOf(
        HazardMarker("hz-dog", circlePoint(1.0), HazardType.DOG, "Hund am Zaun"),
        HazardMarker("hz-turn", circlePoint(3.0), HazardType.BLIND_TURN, "Unübersichtliche Kurve"),
        HazardMarker("hz-road", circlePoint(4.5), HazardType.ROAD_CROSSING, "Straßenquerung"),
    )

    /**
     * Point on the simulated circle for angle [theta] (radians).
     * Starts at the origin (CENTER) and loops clockwise.
     */
    fun circlePoint(theta: Double, radiusM: Double = SIM_RADIUS_M, timeMillis: Long = 0L): GeoPoint {
        val northM = radiusM * sin(theta)
        val eastM = radiusM * (1 - cos(theta))
        val lat = CENTER_LAT + northM / M_PER_DEG_LAT
        val lon = CENTER_LON + eastM / (M_PER_DEG_LAT * cos(Math.toRadians(CENTER_LAT)))
        return GeoPoint(lat, lon, timeMillis)
    }
}
