package com.example.saddlemate.domain

import com.example.saddlemate.data.mock.RouteCatalog
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SurfaceStyleTest {

    @Test
    fun knownSurfaces_getTheirLineStyle() {
        assertEquals(SurfaceLine.SOLID, SurfaceStyle.lineFor("asphalt"))
        assertEquals(SurfaceLine.SHORT_DASH, SurfaceStyle.lineFor("gravel"))
        assertEquals(SurfaceLine.SHORT_DASH, SurfaceStyle.lineFor("compacted"))
        assertEquals(SurfaceLine.DOTTED, SurfaceStyle.lineFor("dirt"))
        assertEquals(SurfaceLine.LONG_DASH, SurfaceStyle.lineFor("sand"))
    }

    @Test
    fun missingOrUnknownSurface_isNotDrawn() {
        assertNull(SurfaceStyle.lineFor(null))
        assertNull(SurfaceStyle.lineFor("something_unlisted"))
    }

    @Test
    fun everyRouteWithSurfaceFile_hasTaggedOrUntaggedStretches() {
        for (route in RouteCatalog.routes) {
            val base = route.assetFile.substringBeforeLast(".txt")
            val f = listOf(File("src/main/assets/${base}_surface.json"), File("app/src/main/assets/${base}_surface.json"))
                .firstOrNull { it.exists() } ?: continue
            val segments = com.example.saddlemate.data.mock.SurfaceData.parse(f.readText())
            assertTrue("No stretches in ${route.id}", segments.isNotEmpty())
            assertTrue("Stretch without points in ${route.id}", segments.all { it.points.size >= 2 })
        }
    }
}
