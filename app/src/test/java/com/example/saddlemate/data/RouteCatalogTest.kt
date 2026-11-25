package com.example.saddlemate.data

import com.example.saddlemate.data.mock.RouteCatalog
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every catalogued route must have its asset file, and its stored length must match the
 * geometry. A missing file or a stale length shows up here, not on the phone.
 */
class RouteCatalogTest {

    @Test
    fun everyRoute_hasAnAssetFile() {
        for (route in RouteCatalog.routes) {
            val f = listOf(File("src/main/assets/${route.assetFile}"), File("app/src/main/assets/${route.assetFile}"))
            assertTrue("Missing asset for ${route.id}: ${route.assetFile}", f.any { it.exists() })
        }
    }

    @Test
    fun everyRoute_lengthMatchesGeometry() {
        for (route in RouteCatalog.routes) {
            val points = RouteCatalog.points(route)
            val km = RouteCatalog.lengthMeters(points) / 1000
            assertEquals("Length of ${route.id}", route.lengthKm, km, 0.05)
        }
    }

    @Test
    fun everyRoute_gapMatchesStartAndEnd() {
        for (route in RouteCatalog.routes) {
            val points = RouteCatalog.points(route)
            val gap = RouteCatalog.lengthMeters(listOf(points.first(), points.last()))
            assertEquals("Gap of ${route.id}", route.gapMeters.toDouble(), gap, 15.0)
        }
    }
}
