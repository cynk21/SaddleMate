package com.example.saddlemate.domain

/** A planned route option the rider can choose from. */
data class RouteOption(
    val id: String,
    val name: String,
    val distanceKm: Double,
    val surface: String,
    val hasSteepClimb: Boolean,
    val suitableForLaminitis: Boolean,
    val suitableForYoungHorse: Boolean,
)

/** Filters route options by the active horse's profile. Pure logic, unit-testable. */
object RoutePlanner {

    /** Distance presets offered in the planner. */
    val distancesKm = listOf(10.0, 20.0)

    fun filter(routes: List<RouteOption>, horse: Horse, distanceKm: Double): List<RouteOption> =
        routes
            .filter { it.distanceKm == distanceKm }
            .filter { !horse.hasLaminitisHistory || it.suitableForLaminitis }
            .filter { !horse.isYoung || it.suitableForYoungHorse }
            .sortedBy { if (it.hasSteepClimb) 1 else 0 }
}
