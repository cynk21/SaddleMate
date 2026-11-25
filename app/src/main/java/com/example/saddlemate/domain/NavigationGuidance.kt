package com.example.saddlemate.domain

import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round
import kotlin.math.sin
import kotlin.math.sqrt

enum class TurnSide { LEFT, RIGHT }

/** A direction change ahead on the route. [inM] is the distance from the rider, [atM] from the route start. */
data class Turn(val side: TurnSide, val inM: Double, val atM: Double)

/** Where the rider is relative to the route: off by [offRouteM], [remainingM] still to ride. */
data class NavStatus(val offRouteM: Double, val remainingM: Double, val nextTurn: Turn?)

/**
 * Navigation along a stored route. Pure logic, no Android dependencies, unit-testable.
 * The route is the recorded track of a ride, in order.
 */
object NavigationGuidance {
    /** Direction change that counts as a turn, in degrees. */
    const val TURN_MIN_DEG = 35.0

    /** A turn is announced once the rider is this close to it. */
    const val TURN_LOOKAHEAD_M = 150.0

    /** Rider is off the route beyond this distance. */
    const val OFF_ROUTE_M = 40.0

    /** Back on the route once within this distance (hysteresis, so the message does not flicker). */
    const val BACK_ON_ROUTE_M = 25.0

    /** The route counts as reached within this distance of its end. */
    const val ARRIVE_M = 30.0

    /** How far the rider must move along the route before messages start. */
    const val MOVED_M = 30.0

    /** Distance on each side of a vertex used to judge its direction change. */
    private const val LOOK_AROUND_M = 25.0

    /** Turns further ahead than this are not looked at yet. */
    private const val SEARCH_AHEAD_M = 400.0

    /** Status for [position] on [route], or null if the route has fewer than two points. */
    fun status(route: List<GeoPoint>, position: GeoPoint): NavStatus? {
        if (route.size < 2) return null
        val cum = cumulative(route)
        val total = cum.last()
        var bestDist = Double.MAX_VALUE
        var bestAlong = 0.0
        for (i in 0 until route.size - 1) {
            val (d, t) = project(position, route[i], route[i + 1])
            if (d < bestDist) {
                bestDist = d
                bestAlong = cum[i] + t * (cum[i + 1] - cum[i])
            }
        }
        val remaining = (total - bestAlong).coerceAtLeast(0.0)
        return NavStatus(bestDist, remaining, nextTurn(route, cum, bestAlong))
    }

    /** Position along [route] in metres from start for [position], or null if route has < 2 points. */
    fun alongMeters(route: List<GeoPoint>, position: GeoPoint): Double? {
        if (route.size < 2) return null
        val cum = cumulative(route)
        var bestDist = Double.MAX_VALUE
        var bestAlong = 0.0
        for (i in 0 until route.size - 1) {
            val (d, t) = project(position, route[i], route[i + 1])
            if (d < bestDist) {
                bestDist = d
                bestAlong = cum[i] + t * (cum[i + 1] - cum[i])
            }
        }
        return bestAlong
    }

    /** Short line for the map: remaining distance, next turn, and how far off the route. */
    fun hint(status: NavStatus): String {
        val parts = mutableListOf("Noch ${distanceText(status.remainingM)}")
        status.nextTurn?.let { t ->
            parts += "Abbiegen ${sideWord(t.side)} in ${t.inM.toInt()} m"
        }
        if (status.offRouteM > OFF_ROUTE_M) parts += "${status.offRouteM.toInt()} m neben der Route"
        return parts.joinToString(" · ")
    }

    private fun nextTurn(route: List<GeoPoint>, cum: DoubleArray, alongM: Double): Turn? {
        val total = cum.last()
        for (i in 1 until route.size - 1) {
            val atM = cum[i]
            if (atM <= alongM + 5.0) continue
            if (atM - alongM > SEARCH_AHEAD_M) break
            val before = pointAt(route, cum, max(0.0, atM - LOOK_AROUND_M))
            val after = pointAt(route, cum, min(total, atM + LOOK_AROUND_M))
            val vertex = route[i]
            val delta = normalizeDeg(bearing(vertex, after) - bearing(before, vertex))
            if (abs(delta) >= TURN_MIN_DEG) {
                return Turn(if (delta > 0) TurnSide.RIGHT else TurnSide.LEFT, atM - alongM, atM)
            }
        }
        return null
    }

    /** Cumulative distance in metres at each route point. */
    private fun cumulative(route: List<GeoPoint>): DoubleArray {
        val cum = DoubleArray(route.size)
        for (i in 1 until route.size) cum[i] = cum[i - 1] + meters(route[i - 1], route[i])
        return cum
    }

    /** Point at [along] metres from the start, interpolated on the segment that contains it. */
    private fun pointAt(route: List<GeoPoint>, cum: DoubleArray, along: Double): GeoPoint {
        val a = along.coerceIn(0.0, cum.last())
        var lo = 0
        var hi = route.size - 2
        while (lo < hi) {
            val mid = (lo + hi + 1) / 2
            if (cum[mid] <= a) lo = mid else hi = mid - 1
        }
        val seg = cum[lo + 1] - cum[lo]
        val t = if (seg <= 0.0) 0.0 else (a - cum[lo]) / seg
        val p = route[lo]
        val q = route[lo + 1]
        return GeoPoint(p.lat + (q.lat - p.lat) * t, p.lon + (q.lon - p.lon) * t, p.timeMillis)
    }

    /** Distance from [p] to segment [a]-[b] in metres, and the position along it (0..1). Planar, fine at route scale. */
    private fun project(p: GeoPoint, a: GeoPoint, b: GeoPoint): Pair<Double, Double> {
        val k = 111_320.0
        val cosLat = cos(Math.toRadians(a.lat))
        val px = (p.lon - a.lon) * k * cosLat
        val py = (p.lat - a.lat) * k
        val bx = (b.lon - a.lon) * k * cosLat
        val by = (b.lat - a.lat) * k
        val len2 = bx * bx + by * by
        val t = if (len2 == 0.0) 0.0 else ((px * bx + py * by) / len2).coerceIn(0.0, 1.0)
        val dx = px - t * bx
        val dy = py - t * by
        return Pair(sqrt(dx * dx + dy * dy), t)
    }

    /** Compass bearing from [a] to [b] in degrees, clockwise from north. */
    private fun bearing(a: GeoPoint, b: GeoPoint): Double {
        val dy = b.lat - a.lat
        val dx = (b.lon - a.lon) * cos(Math.toRadians(a.lat))
        return Math.toDegrees(atan2(dx, dy))
    }

    /** Maps an angle difference into -180..180. Positive means a turn to the right. */
    private fun normalizeDeg(d: Double): Double {
        var x = d % 360.0
        if (x > 180.0) x -= 360.0
        if (x < -180.0) x += 360.0
        return x
    }

    private fun meters(a: GeoPoint, b: GeoPoint): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLon = Math.toRadians(b.lon - a.lon)
        val h = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * r * asin(sqrt(h))
    }

    private fun sideWord(side: TurnSide): String = if (side == TurnSide.LEFT) "links" else "rechts"

    /** Distance in words for speech, rounded: to 50 m from 100 m on, to 10 m below. */
    private fun distanceText(m: Double): String = when {
        m >= 1000.0 -> "%.1f Kilometer".format(m / 1000.0).replace('.', ',')
        m >= 100.0 -> "${(round(m / 50.0) * 50).toInt()} Meter"
        else -> "${(round(m / 10.0) * 10).toInt()} Meter"
    }

    /**
     * Spoken messages while navigating. Each message is given once, except the off-route
     * warning, which comes again after a new departure.
     */
    class Coach {
        private val spokenTurns = mutableSetOf<Long>()
        private val spokenMilestones = mutableSetOf<Int>()
        private var offRoute = false
        private var arrived = false

        fun reset() {
            moved = false
            firstRemainingM = null
            spokenTurns.clear()
            spokenMilestones.clear()
            offRoute = false
            arrived = false
        }

        /** Set once the rider has moved along the route. Until then nothing is announced. */
        private var moved = false
        private var firstRemainingM: Double? = null

        fun messages(status: NavStatus): List<String> {
            val out = mutableListOf<String>()
            // Nothing is spoken before the rider has actually moved away from the start.
            if (!moved) {
                val first = firstRemainingM ?: status.remainingM.also { firstRemainingM = it }
                if (first - status.remainingM >= MOVED_M) moved = true else return out
            }
            if (!offRoute && status.offRouteM > OFF_ROUTE_M) {
                offRoute = true
                out += "Du bist von der Route abgekommen."
                return out
            }
            if (offRoute) {
                if (status.offRouteM < BACK_ON_ROUTE_M) {
                    offRoute = false
                    out += "Wieder auf der Route."
                }
                return out
            }
            status.nextTurn?.let { turn ->
                if (turn.inM <= TURN_LOOKAHEAD_M && spokenTurns.add(round(turn.atM).toLong())) {
                    val side = sideWord(turn.side)
                    out += if (turn.inM < 15.0) {
                        "Jetzt $side abbiegen."
                    } else {
                        "In ${distanceText(turn.inM)} $side abbiegen."
                    }
                }
            }
            for (mark in listOf(1000, 500)) {
                if (status.remainingM <= mark && spokenMilestones.add(mark)) {
                    out += if (mark >= 1000) "Noch einen Kilometer." else "Noch 500 Meter."
                }
            }
            if (!arrived && status.remainingM <= ARRIVE_M) {
                arrived = true
                out += "Ziel erreicht."
            }
            return out
        }
    }
}
