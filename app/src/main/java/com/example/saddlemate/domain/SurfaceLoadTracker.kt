package com.example.saddlemate.domain

/**
 * Tracks fast riding (trot/canter/gallop) over hard surfaces (asphalt/paved/gravel) or deep mud,
 * supporting the day-1 sensorless tendon & laminitis load warnings.
 */
object SurfaceLoadTracker {

    fun isHardSurface(surface: String?): Boolean = surface in setOf(
        "asphalt", "paved", "concrete", "concrete:plates", "paving_stones", "gravel", "compacted",
    )

    fun isDeepMud(surface: String?): Boolean = surface == "mud"

    fun checkLoad(gait: Gait, surface: String?, hasLaminitisHistory: Boolean): String? {
        val fast = gait == Gait.TROT || gait == Gait.CANTER || gait == Gait.GALLOP
        if (!fast) return null
        if (isHardSurface(surface)) {
            return if (hasLaminitisHistory) {
                "Harter Boden ($surface) mit Rehe-Pferd – bitte durchparieren in den Schritt!"
            } else {
                "Schnelle Gangart auf hartem Boden ($surface) – Sehnen schonen!"
            }
        }
        if (isDeepMud(surface)) {
            return "Tiefer Boden im ${if (gait == Gait.TROT) "Trab" else "Galopp"} – hohe Sehnenlast!"
        }
        return null
    }
}
