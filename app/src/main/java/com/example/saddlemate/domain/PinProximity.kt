package com.example.saddlemate.domain

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Decides which voice pins to speak while a ride runs. Pure logic, no Android dependencies.
 *
 * A pin is announced when the rider comes within [radiusMeters] of it. Each pin is announced
 * once per ride: the caller keeps the set of announced ids.
 */
object PinProximity {

    const val DEFAULT_RADIUS_M = 30.0

    /** Pins within [radiusMeters] of [position] that have not been announced yet, nearest first. */
    fun dueNow(
        position: GeoPoint,
        pins: List<Pin>,
        announced: Set<String>,
        radiusMeters: Double = DEFAULT_RADIUS_M,
    ): List<Pin> = pins
        .filter { it.id !in announced }
        .map { it to distanceMeters(position, it.location) }
        .filter { (_, d) -> d <= radiusMeters }
        .sortedBy { (_, d) -> d }
        .map { (pin, _) -> pin }

    /** A voice pin as the ride sees it: id, position and the text to speak. */
    data class Pin(val id: String, val location: GeoPoint, val text: String)

    /** Great-circle distance in metres. */
    fun distanceMeters(a: GeoPoint, b: GeoPoint): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLon = Math.toRadians(b.lon - a.lon)
        val h = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * r * asin(sqrt(h))
    }
}
