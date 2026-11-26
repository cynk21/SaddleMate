package com.example.saddlemate.domain

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

data class GeneratedLoop(
    val id: String,
    val name: String,
    val targetKm: Double,
    val actualKm: Double,
    val surfaceHint: String,
    val suitableNote: String,
    val points: List<GeoPoint>,
)

/**
 * Generates closed 10 km / 20 km equestrian loop routes around any start point
 * (e.g. current GPS position or a new barn location after Stallwechsel), shaped and labelled
 * for the active horse's profile. Pure logic, unit-testable.
 */
object RouteGenerator {

    private const val M_PER_DEG_LAT = 111_320.0

    fun generateLoops(
        center: GeoPoint,
        horse: Horse,
        distanceKm: Double,
    ): List<GeneratedLoop> {
        val variants = buildVariants(horse, distanceKm)
        return variants.mapIndexed { idx, variant ->
            val rawPts = buildLoopPoints(
                center = center,
                targetMeters = distanceKm * 1000.0,
                bearingOffsetRad = variant.bearingOffsetRad,
                wobble = variant.wobble,
            )
            // Compute raw length directly without speed-filtering (since timestamps are synthetic)
            val telemetry = TelemetryEngine()
            val rawMeters = rawPts.zipWithNext().sumOf { (a, b) -> telemetry.distanceMeters(a, b) }
            val scale = if (rawMeters > 0.0) (distanceKm * 1000.0) / rawMeters else 1.0
            val pts = scaleAroundCenter(center, rawPts, scale)
            val actualKm = pts.zipWithNext().sumOf { (a, b) -> telemetry.distanceMeters(a, b) } / 1000.0
            GeneratedLoop(
                id = "gen-${distanceKm.toInt()}-$idx-${(center.lat * 1000).toInt()}",
                name = variant.title,
                targetKm = distanceKm,
                actualKm = actualKm,
                surfaceHint = variant.surfaceHint,
                suitableNote = variant.suitableNote,
                points = pts,
            )
        }
    }

    private data class Variant(
        val title: String,
        val bearingOffsetRad: Double,
        val wobble: Double,
        val surfaceHint: String,
        val suitableNote: String,
    )

    private fun buildVariants(horse: Horse, distanceKm: Double): List<Variant> {
        val km = distanceKm.toInt()
        val note = when {
            horse.hasLaminitisHistory -> "Rehe-Profil: Fokus auf weiche Wald- & Wiesenwege"
            horse.isYoung -> "Jungpferd-Profil: ruhige, verkehrsarme Schleife"
            else -> "Ausgewogene Trainingsrunde"
        }
        val surface = if (horse.hasLaminitisHistory) "Waldboden & Sand (weich)" else "Wald- & Feldwege gemischt"
        return listOf(
            Variant("Nord-Schleife (${km} km)", 0.0, 0.14, surface, note),
            Variant("Ost-Runde (${km} km)", PI / 2.0, 0.18, surface, note),
            Variant("Südwest-Tour (${km} km)", PI * 1.2, 0.12, surface, note),
        )
    }

    private fun buildLoopPoints(
        center: GeoPoint,
        targetMeters: Double,
        bearingOffsetRad: Double,
        wobble: Double,
        steps: Int = 36,
    ): List<GeoPoint> {
        // Circumference C = 2 * pi * r -> r = targetMeters / (2 * pi)
        // Loop starts and ends at [center] by shifting the circle center north-east by radius r.
        val r = targetMeters / (2.0 * PI)
        val cosLat = cos(Math.toRadians(center.lat)).coerceAtLeast(0.2)
        val cx = r * cos(bearingOffsetRad)
        val cy = r * sin(bearingOffsetRad)

        val points = mutableListOf<GeoPoint>()
        for (i in 0..steps) {
            val t = (i.toDouble() / steps) * 2.0 * PI
            val localR = r * (1.0 + wobble * sin(3.0 * t))
            // Start at angle (bearingOffsetRad + PI) so i=0 sits right at [center].
            val angle = bearingOffsetRad + PI + t
            val eastM = cx + localR * cos(angle)
            val northM = cy + localR * sin(angle)
            val lat = center.lat + northM / M_PER_DEG_LAT
            val lon = center.lon + eastM / (M_PER_DEG_LAT * cosLat)
            points += GeoPoint(lat, lon, 0L)
        }
        // Close exactly onto the start point
        points[0] = center.copy(timeMillis = 0L)
        points[points.lastIndex] = center.copy(timeMillis = 0L)
        return points
    }

    private fun scaleAroundCenter(center: GeoPoint, pts: List<GeoPoint>, scale: Double): List<GeoPoint> {
        if (kotlin.math.abs(scale - 1.0) < 0.01) return pts
        return pts.map { p ->
            GeoPoint(
                lat = center.lat + (p.lat - center.lat) * scale,
                lon = center.lon + (p.lon - center.lon) * scale,
                timeMillis = p.timeMillis,
            )
        }
    }
}
