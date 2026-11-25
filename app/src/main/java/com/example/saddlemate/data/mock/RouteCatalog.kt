package com.example.saddlemate.data.mock

import android.content.Context
import com.example.saddlemate.domain.GeoPoint
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Hiking routes shipped as offline text assets under assets/routes/.
 *
 * Each file holds one ordered "lat,lon;lat,lon;..." line, prepared from OpenStreetMap
 * (ODbL, © OpenStreetMap contributors) by stitching the member ways of the route relation.
 */
data class RouteInfo(
    val id: String,
    val name: String,
    val assetFile: String,
    val osmRelationId: Long,
    /** Measured length in km, from the OSM geometry. */
    val lengthKm: Double,
    /** Distance between start and end in metres. 0 means closed. */
    val gapMeters: Int,
)

object RouteCatalog {
    val routes: List<RouteInfo> = listOf(
        RouteInfo("weinberg", "Rundwanderweg Weinberg", "routes/weinberg_4780646.txt", 4780646L, 9.40, 0),
        RouteInfo("ruhe_luebben", "Rund um Lübben", "routes/ruhe_luebben_18827886.txt", 18827886L, 7.60, 0),
        RouteInfo("eichberge_dahmeblick", "Rundwanderweg Eichberge Dahmeblick", "routes/eichberge_dahmeblick_14638548.txt", 14638548L, 5.12, 0),
        RouteInfo("steinkirchen", "Rundwanderweg Lübben - Steinkirchen", "routes/steinkirchen_3870164.txt", 3870164L, 4.52, 28),
        RouteInfo("neu_zauche", "Rundwanderweg Neu Zauche", "routes/neu_zauche_3808902.txt", 3808902L, 4.44, 55),
        RouteInfo("stietensee", "Rundweg Stietensee", "routes/stietensee_4780649.txt", 4780649L, 4.38, 0),
        RouteInfo("herzberger_see", "Rundwanderweg Herzberger See", "routes/herzberger_see_11367496.txt", 11367496L, 4.00, 146),
        RouteInfo("pretschener_weinberg", "Rundwanderweg Pretschener Weinberg", "routes/pretschener_weinberg_4079834.txt", 4079834L, 3.34, 0),
        RouteInfo("leibsch", "Rundwanderweg Leibsch", "routes/leibsch_4077171.txt", 4077171L, 2.56, 0),
        RouteInfo("schlepzig", "Rundwanderweg Schlepzig", "routes/schlepzig_3824199.txt", 3824199L, 2.15, 91),
        RouteInfo("duerrenhofer_see", "Rundwanderweg Dürrenhofer See", "routes/duerrenhofer_see_3933209.txt", 3933209L, 1.86, 0),
        RouteInfo("buchenhain", "Rundwanderweg Buchenhain", "routes/buchenhain_3824198.txt", 3824198L, 1.83, 5),
        RouteInfo("ahrensdoorfer_see", "Rundwanderweg Ahrensdoorfer See", "routes/ahrensdoorfer_see_15804207.txt", 15804207L, 4.74, 326),
        RouteInfo("maerkisch_buchholz", "Stadtrundwanderweg Märkisch Buchholz", "routes/maerkisch_buchholz_14680753.txt", 14680753L, 9.75, 466),
        RouteInfo("gross_leuthener_see", "Rundwanderweg Groß Leuthener See", "routes/gross_leuthener_see_3933210.txt", 3933210L, 6.60, 280),
    )

    val default: RouteInfo get() = routes.first()

    /** Looks up a route by its id, or null if unknown. */
    fun byId(id: String): RouteInfo? = routes.firstOrNull { it.id == id }

    /** Reads an asset route into ordered geo points. Timestamps are 0; the simulator assigns them. */
    fun load(context: Context, route: RouteInfo): List<GeoPoint> {
        val text = context.assets.open(route.assetFile).bufferedReader().use { it.readText() }
        return text.trim().split(';').map { pair ->
            val (lat, lon) = pair.split(',')
            GeoPoint(lat.toDouble(), lon.toDouble(), 0L)
        }
    }

    /** Reads the route points for unit tests and non-Android callers, from the module's assets folder. */
    fun points(route: RouteInfo): List<GeoPoint> {
        val f = java.io.File("src/main/assets/${route.assetFile}")
        val text = if (f.exists()) f.readText() else java.io.File("app/src/main/assets/${route.assetFile}").readText()
        return parseText(text)
    }

    /** Point at [share] (0..1) of the route length, interpolated on the polyline. */
    fun pointAtShare(points: List<GeoPoint>, share: Double): GeoPoint {
        val target = lengthMeters(points) * share.coerceIn(0.0, 1.0)
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

    private fun parseText(text: String): List<GeoPoint> =
        text.trim().split(';').map { pair ->
            val (lat, lon) = pair.split(',')
            GeoPoint(lat.toDouble(), lon.toDouble(), 0L)
        }

    /** Length of a point sequence in metres. */
    fun lengthMeters(points: List<GeoPoint>): Double =
        points.zipWithNext().sumOf { (a, b) -> distanceMeters(a, b) }

    private fun distanceMeters(a: GeoPoint, b: GeoPoint): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLon = Math.toRadians(b.lon - a.lon)
        val h = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * r * asin(sqrt(h))
    }
}
