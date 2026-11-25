package com.example.saddlemate.domain

/** Line style for a path surface. Unknown or missing surfaces are not drawn at all. */
enum class SurfaceLine { SOLID, SHORT_DASH, DOTTED, LONG_DASH }

/**
 * Maps OSM `surface` values to a line style. Only listed values are drawn, so a surface
 * that is not known or not tagged stays unmarked instead of guessed.
 */
object SurfaceStyle {
    private val map: Map<String, SurfaceLine> = mapOf(
        "asphalt" to SurfaceLine.SOLID,
        "paved" to SurfaceLine.SOLID,
        "concrete" to SurfaceLine.SOLID,
        "concrete:plates" to SurfaceLine.SOLID,
        "paving_stones" to SurfaceLine.SOLID,
        "gravel" to SurfaceLine.SHORT_DASH,
        "fine_gravel" to SurfaceLine.SHORT_DASH,
        "compacted" to SurfaceLine.SHORT_DASH,
        "dirt" to SurfaceLine.DOTTED,
        "earth" to SurfaceLine.DOTTED,
        "ground" to SurfaceLine.DOTTED,
        "grass" to SurfaceLine.DOTTED,
        "mud" to SurfaceLine.DOTTED,
        "sand" to SurfaceLine.LONG_DASH,
    )

    /** Line style for [surface], or null when it is missing or not in the list. */
    fun lineFor(surface: String?): SurfaceLine? = surface?.let { map[it] }

    /** The distinct surfaces from [surfaces] that have a line style, for the legend. */
    fun drawable(surfaces: Collection<String?>): List<Pair<String, SurfaceLine>> =
        surfaces.filterNotNull().distinct().mapNotNull { s -> map[s]?.let { s to it } }
}
