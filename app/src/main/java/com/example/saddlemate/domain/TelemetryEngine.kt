package com.example.saddlemate.domain

import kotlin.math.asin
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.math.sin
import kotlin.math.cos

/**
 * Pure GPS analysis: spike filtering, distance, gait inference and km splits.
 * Free of Android dependencies so it can be unit tested on the JVM.
 */
class TelemetryEngine(private val maxPlausibleSpeedMps: Double = 25.0) {

    fun distanceMeters(a: GeoPoint, b: GeoPoint): Double {
        val r = EARTH_RADIUS_M
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLon = Math.toRadians(b.lon - a.lon)
        val h = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLon / 2).pow(2)
        return 2 * r * asin(sqrt(h))
    }

    /** Drops non-monotonic timestamps and GPS jumps that imply implausible speed. */
    fun filter(points: List<GeoPoint>): List<GeoPoint> {
        val out = mutableListOf<GeoPoint>()
        for (p in points) {
            val last = out.lastOrNull()
            if (last == null) {
                out += p
                continue
            }
            val dtSec = (p.timeMillis - last.timeMillis) / 1000.0
            if (dtSec <= 0.0) continue
            val speedMps = distanceMeters(last, p) / dtSec
            if (speedMps > maxPlausibleSpeedMps) continue
            out += p
        }
        return out
    }

    fun inferGait(speedKmh: Double): Gait = when {
        speedKmh < 0.5 -> Gait.STANDSTILL
        speedKmh < 7.0 -> Gait.WALK
        speedKmh < 15.0 -> Gait.TROT
        speedKmh < 22.0 -> Gait.CANTER
        else -> Gait.GALLOP
    }

    fun analyze(rawPoints: List<GeoPoint>): RideAnalysis {
        val pts = filter(rawPoints)
        if (pts.size < 2) return RideAnalysis.EMPTY

        var total = 0.0
        var maxSpeed = 0.0
        val splits = mutableListOf<KmSplit>()
        var nextKmMark = 1000.0
        var splitStart = pts.first().timeMillis
        val gaitTime = mutableMapOf<Gait, Long>()

        for (i in 1 until pts.size) {
            val a = pts[i - 1]
            val b = pts[i]
            val segment = distanceMeters(a, b)
            val dt = b.timeMillis - a.timeMillis
            val speedKmh = if (dt > 0) segment / (dt / 1000.0) * 3.6 else 0.0
            maxSpeed = max(maxSpeed, speedKmh)
            val gait = inferGait(speedKmh)
            gaitTime[gait] = (gaitTime[gait] ?: 0L) + dt
            total += segment

            while (total >= nextKmMark) {
                splits += KmSplit(index = splits.size + 1, durationMs = b.timeMillis - splitStart)
                splitStart = b.timeMillis
                nextKmMark += 1000.0
            }
        }

        val duration = pts.last().timeMillis - pts.first().timeMillis
        val avg = if (duration > 0) total / (duration / 1000.0) * 3.6 else 0.0
        return RideAnalysis(
            distanceMeters = total,
            durationMs = duration,
            averageSpeedKmh = avg,
            maxSpeedKmh = maxSpeed,
            splits = splits,
            gaitTimeMs = gaitTime,
        )
    }

    private companion object {
        const val EARTH_RADIUS_M = 6_371_000.0
    }
}
