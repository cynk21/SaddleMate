package com.example.saddlemate.data.mock

import com.example.saddlemate.domain.GeoPoint
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Checks the shipped route asset without Android: parse the same text format
 * RouteCatalog.load() reads, then check length and closure.
 */
class RouteAssetFileTest {

    private fun parse(text: String): List<GeoPoint> =
        text.trim().split(';').map { pair ->
            val (lat, lon) = pair.split(',')
            GeoPoint(lat.toDouble(), lon.toDouble(), 0L)
        }

    private fun assetText(): String {
        // Unit tests run from the module directory.
        val f = File("src/main/assets/routes/weinberg_4780646.txt")
        assertTrue("asset missing: ${f.absolutePath}", f.exists())
        return f.readText()
    }

    @Test
    fun weinberg_hasAllPoints() {
        assertEquals(234, parse(assetText()).size)
    }

    @Test
    fun weinberg_isClosed() {
        val pts = parse(assetText())
        val d = RouteCatalog.lengthMeters(listOf(pts.first(), pts.last()))
        assertTrue("start/end gap $d m", d < 1.0)
    }

    @Test
    fun weinberg_isAboutTenKilometres() {
        val len = RouteCatalog.lengthMeters(parse(assetText()))
        assertTrue("length $len m, want 9-11 km", len in 9_000.0..11_000.0)
    }
}
