package com.example.saddlemate.data.mock

import com.example.saddlemate.domain.RouteOption

/** Demo routes for the planner. Replaced by OSM-derived routes later. */
object MockRoutes {
    val routes = listOf(
        RouteOption("r10-flat", "Waldrunde Ost", 10.0, "Waldweg", false, true, true),
        RouteOption("r10-hill", "Hügelweg Nord", 10.0, "Feldweg", true, false, false),
        RouteOption("r20-loop", "Große Schleife", 20.0, "Forststraße", false, true, true),
        RouteOption("r20-steep", "Bergpfad", 20.0, "Schotter", true, false, false),
    )
}
