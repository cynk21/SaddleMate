package com.example.saddlemate.domain

/**
 * Finds the track point closest to a tapped coordinate.
 * Returns the index into [track], or null for an empty track.
 * Distance is planar with longitude scaled by cos(lat): accurate at track scale (a few km).
 */
object TrackPicker {
    fun nearestIndex(track: List<GeoPoint>, lat: Double, lon: Double): Int? {
        if (track.isEmpty()) return null
        val cosLat = kotlin.math.cos(Math.toRadians(lat))
        var best = 0
        var bestD2 = Double.MAX_VALUE
        track.forEachIndexed { i, p ->
            val dy = p.lat - lat
            val dx = (p.lon - lon) * cosLat
            val d2 = dx * dx + dy * dy
            if (d2 < bestD2) {
                bestD2 = d2
                best = i
            }
        }
        return best
    }
}
