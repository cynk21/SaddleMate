package com.example.saddlemate.domain

/**
 * Detects continuous canter/gallop stretches >= [MIN_CANTER_METERS] from a ride track,
 * so the post-ride wizard can offer 1-tap saving as a community "Galoppstrecke" pin.
 */
data class CanterStretch(
    val start: GeoPoint,
    val end: GeoPoint,
    val midpoint: GeoPoint,
    val lengthMeters: Double,
)

object CanterStretchDetector {
    const val MIN_CANTER_METERS = 250.0

    fun detect(track: List<GeoPoint>, telemetry: TelemetryEngine = TelemetryEngine()): List<CanterStretch> {
        if (track.size < 2) return emptyList()
        val stretches = mutableListOf<CanterStretch>()
        var runPoints = mutableListOf<GeoPoint>()
        var runMeters = 0.0

        fun flush() {
            if (runMeters >= MIN_CANTER_METERS && runPoints.size >= 2) {
                val mid = runPoints[runPoints.size / 2]
                stretches += CanterStretch(runPoints.first(), runPoints.last(), mid, runMeters)
            }
            runPoints = mutableListOf()
            runMeters = 0.0
        }

        for (i in 1 until track.size) {
            val a = track[i - 1]
            val b = track[i]
            val seg = telemetry.distanceMeters(a, b)
            val dtSec = (b.timeMillis - a.timeMillis) / 1000.0
            val speedKmh = if (dtSec > 0) seg / dtSec * 3.6 else 0.0
            val gait = telemetry.inferGait(speedKmh)
            if (gait == Gait.CANTER || gait == Gait.GALLOP) {
                if (runPoints.isEmpty()) runPoints.add(a)
                runPoints.add(b)
                runMeters += seg
            } else {
                flush()
            }
        }
        flush()
        return stretches
    }
}
