package com.example.saddlemate.data.mock

import android.content.Context
import com.example.saddlemate.domain.GeoPoint

/** One stretch of a route with its OSM surface (null when untagged). */
data class SurfaceSegment(val surface: String?, val points: List<GeoPoint>)

/**
 * Surface stretches of a route, from `assets/routes/<name>_surface.json`.
 * The file is written from OpenStreetMap `surface` tags; a missing tag stays null.
 *
 * The format is fixed and simple, so it is parsed here without a JSON library. That keeps
 * the parser usable in plain unit tests too.
 */
object SurfaceData {

    fun load(context: Context, route: RouteInfo): List<SurfaceSegment> {
        val base = route.assetFile.substringBeforeLast(".txt")
        val text = runCatching {
            context.assets.open("${base}_surface.json").bufferedReader().use { it.readText() }
        }.getOrNull() ?: return emptyList()
        return parse(text)
    }

    /**
     * Parses `[{"surface":"asphalt","pts":"lat,lon;lat,lon"}, {"surface":null,"pts":"..."}]`.
     * Malformed entries are skipped rather than failing the whole route.
     */
    fun parse(text: String): List<SurfaceSegment> {
        val objects = Regex("""\{[^{}]*\}""").findAll(text).map { it.value }
        return objects.mapNotNull { obj ->
            val pts = Regex(""""pts"\s*:\s*"([^"]*)"""").find(obj)?.groupValues?.get(1) ?: return@mapNotNull null
            val surface = Regex(""""surface"\s*:\s*(null|"([^"]*)")""").find(obj)?.groupValues?.get(2)
                ?.takeIf { it.isNotEmpty() }
            val points = pts.split(';').filter { it.isNotBlank() }.mapNotNull { pair ->
                val parts = pair.split(',')
                if (parts.size != 2) return@mapNotNull null
                val lat = parts[0].toDoubleOrNull() ?: return@mapNotNull null
                val lon = parts[1].toDoubleOrNull() ?: return@mapNotNull null
                GeoPoint(lat, lon, 0L)
            }
            SurfaceSegment(surface, points)
        }.toList()
    }
}
