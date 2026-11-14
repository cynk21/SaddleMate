package com.example.saddlemate.domain

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.abs

enum class RelativeSide { AHEAD, LEFT, RIGHT }

data class RadarAlert(
    val hazard: HazardMarker,
    val distanceMeters: Double,
    val relativeBearingDeg: Double,
    val side: RelativeSide,
)

/**
 * Directional proximity radar ("Blitzer-style"): only hazards within the radius
 * and inside the rider's direction of travel are reported, nearest first.
 */
class HazardRadarEngine(
    private val telemetry: TelemetryEngine = TelemetryEngine(),
    private val radiusMeters: Double = 150.0,
    private val aheadConeDeg: Double = 60.0,
) {

    fun bearingDeg(from: GeoPoint, to: GeoPoint): Double {
        val phi1 = Math.toRadians(from.lat)
        val phi2 = Math.toRadians(to.lat)
        val dLambda = Math.toRadians(to.lon - from.lon)
        val y = sin(dLambda) * cos(phi2)
        val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(dLambda)
        return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
    }

    fun evaluate(position: GeoPoint, headingDeg: Double, hazards: List<HazardMarker>): List<RadarAlert> =
        hazards.mapNotNull { hazard ->
            val distance = telemetry.distanceMeters(position, hazard.location)
            if (distance > radiusMeters) return@mapNotNull null
            val relative = normalizeDeg(bearingDeg(position, hazard.location) - headingDeg)
            if (abs(relative) > aheadConeDeg) return@mapNotNull null
            RadarAlert(
                hazard = hazard,
                distanceMeters = distance,
                relativeBearingDeg = relative,
                side = if (relative < 0) RelativeSide.LEFT else RelativeSide.RIGHT,
            )
        }.sortedBy { it.distanceMeters }

    /** Maps to (-180, 180]. */
    internal fun normalizeDeg(deg: Double): Double {
        var d = (deg % 360.0 + 360.0) % 360.0
        if (d > 180.0) d -= 360.0
        return d
    }
}
