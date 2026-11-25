package com.example.saddlemate.domain

/** A single GPS fix with its timestamp (epoch millis). */
data class GeoPoint(
    val lat: Double,
    val lon: Double,
    val timeMillis: Long,
    /** GPS altitude in metres, null when the source has none (planned routes, taps). */
    val altitudeM: Double? = null,
)

enum class Gait { STANDSTILL, WALK, TROT, CANTER, GALLOP }

data class KmSplit(val index: Int, val durationMs: Long)

data class RideAnalysis(
    val distanceMeters: Double,
    val durationMs: Long,
    val averageSpeedKmh: Double,
    val maxSpeedKmh: Double,
    val splits: List<KmSplit>,
    val gaitTimeMs: Map<Gait, Long>,
    /** Ascent and descent from GPS altitude, a rough guide. NONE when the track has no altitude. */
    val elevation: Elevation = Elevation.NONE,
) {
    companion object {
        val EMPTY = RideAnalysis(0.0, 0L, 0.0, 0.0, emptyList(), emptyMap())
    }
}

data class Horse(
    val id: String,
    val name: String,
    val ageYears: Int,
    val hasLaminitisHistory: Boolean,
    val kmSinceFarrier: Double,
    val farrierIntervalKm: Double,
    val breed: String = "",
    val weightKg: Double = 0.0,
    val heightCm: Int = 0,
    val photoPath: String = "",
    val notes: String = "",
) {
    val isYoung: Boolean get() = ageYears <= 5
    val farrierDueInKm: Double get() = farrierIntervalKm - kmSinceFarrier
}

enum class HazardType { BLIND_TURN, DOG, ROAD_CROSSING, LOOSE_HORSE, GATE, WATER }

data class HazardMarker(
    val id: String,
    val location: GeoPoint,
    val type: HazardType,
    val label: String,
)

enum class AudioMode { FULL_COPILOT, RADAR_ONLY, GROUP_HAPTIC, SILENT }
