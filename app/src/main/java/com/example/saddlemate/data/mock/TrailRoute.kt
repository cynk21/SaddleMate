package com.example.saddlemate.data.mock

import com.example.saddlemate.domain.GeoPoint
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A hiking route used as the simulated ride path.
 *
 * Source: OpenStreetMap relation 3200473 "Rundwanderweg Fischteiche Teichgruppe I",
 * member ways stitched into one ordered line. Data © OpenStreetMap contributors (ODbL).
 */
object TrailRoute {
    const val ID = "fischteiche-1"
    const val NAME = "Rundwanderweg Fischteiche Teichgruppe I"

    /** Ordered (lat, lon) pairs. Consecutive duplicates from way joins are removed. */
    private val coordinates: List<Pair<Double, Double>> = listOf(
        51.963738 to 13.889636, 51.963648 to 13.888948, 51.963609 to 13.888542,
        51.963401 to 13.887489, 51.963410 to 13.887108, 51.963454 to 13.886723,
        51.963434 to 13.886532, 51.963246 to 13.886279, 51.962836 to 13.885605,
        51.962610 to 13.885202, 51.962562 to 13.885095, 51.962525 to 13.885017,
        51.962500 to 13.884965, 51.961275 to 13.884212, 51.961250 to 13.884376,
        51.961235 to 13.884595, 51.961261 to 13.885200, 51.961221 to 13.885504,
        51.961143 to 13.885681, 51.961011 to 13.885727, 51.960688 to 13.885664,
        51.960080 to 13.885664, 51.959719 to 13.885833, 51.959078 to 13.886386,
        51.958891 to 13.886563, 51.958070 to 13.887805, 51.957892 to 13.888264,
        51.957746 to 13.888715, 51.957551 to 13.889581, 51.957441 to 13.890482,
        51.957309 to 13.891783, 51.957324 to 13.892222, 51.957314 to 13.893836,
        51.957325 to 13.894481, 51.957416 to 13.894980, 51.957586 to 13.895608,
        51.957611 to 13.895716, 51.957723 to 13.896199, 51.957821 to 13.896615,
        51.957985 to 13.896854, 51.958418 to 13.897145, 51.959033 to 13.897839,
        51.959746 to 13.898673, 51.960005 to 13.898524, 51.960223 to 13.898695,
        51.960510 to 13.898822, 51.960888 to 13.898855, 51.961020 to 13.898835,
        51.961125 to 13.898799, 51.961358 to 13.898678, 51.961552 to 13.898458,
        51.961702 to 13.898229, 51.962110 to 13.897463, 51.962204 to 13.897291,
        51.962319 to 13.897082, 51.962534 to 13.896758, 51.962732 to 13.896546,
        51.962953 to 13.896384, 51.963688 to 13.896022, 51.963912 to 13.895858,
        51.964005 to 13.895628, 51.964152 to 13.894873, 51.964268 to 13.894180,
        51.964277 to 13.893711, 51.964276 to 13.893359, 51.964257 to 13.893061,
        51.964224 to 13.892913, 51.964161 to 13.892660, 51.963778 to 13.891953,
        51.963699 to 13.891637, 51.963659 to 13.891272, 51.963718 to 13.890329,
        51.963738 to 13.889636,
    )

    /** The route as geo points. Timestamps are 0; the simulator assigns them. */
    val points: List<GeoPoint> = coordinates.map { (lat, lon) -> GeoPoint(lat, lon, 0L) }

    /** Point at [share] (0..1) of the route length, interpolated on the polyline. */
    fun pointAtShare(share: Double): GeoPoint {
        val target = lengthMeters * share.coerceIn(0.0, 1.0)
        var acc = 0.0
        for (i in 1 until points.size) {
            val seg = distanceMeters(points[i - 1], points[i])
            if (acc + seg >= target) {
                val f = if (seg > 0) (target - acc) / seg else 0.0
                val a = points[i - 1]
                val b = points[i]
                return GeoPoint(a.lat + (b.lat - a.lat) * f, a.lon + (b.lon - a.lon) * f, 0L)
            }
            acc += seg
        }
        return points.last()
    }

    /** Route length in metres (sum of segment distances). */
    val lengthMeters: Double by lazy {
        points.zipWithNext().sumOf { (a, b) -> distanceMeters(a, b) }
    }

    private fun distanceMeters(a: GeoPoint, b: GeoPoint): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLon = Math.toRadians(b.lon - a.lon)
        val h = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * r * asin(sqrt(h))
    }
}
