package com.example.saddlemate.ui.screens

import android.view.ViewGroup
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.saddlemate.data.local.RideEventEntity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import com.example.saddlemate.domain.GeoPoint
import com.example.saddlemate.domain.SpeedBand
import com.example.saddlemate.domain.SpeedBands
import com.example.saddlemate.domain.TrackPicker
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

private const val PREVIEW_OSM_STYLE = "https://tiles.openfreemap.org/styles/liberty"

/**
 * Map of a stored ride: the ridden track as one line, and its events as markers
 * (automatic ones red, manual ones blue). Fits the camera to the track.
 */
@Composable
fun RideMapPreview(
    track: List<GeoPoint>,
    events: List<RideEventEntity>,
    selectedIndex: Int? = null,
    onTrackPick: (Int) -> Unit = {},
) {
    var style by remember { mutableStateOf<Style?>(null) }
    var mapRef by remember { mutableStateOf<MapLibreMap?>(null) }

    AndroidView(
        modifier = Modifier.fillMaxWidth().height(320.dp),
        factory = { ctx ->
            MapLibre.getInstance(ctx)
            MapView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                onCreate(null)
                getMapAsync { map: MapLibreMap ->
                    mapRef = map
                    map.setStyle(Style.Builder().fromUri(PREVIEW_OSM_STYLE)) { loaded -> style = loaded }
                    map.addOnMapClickListener { point ->
                        val index = TrackPicker.nearestIndex(track, point.latitude, point.longitude)
                        if (index != null) onTrackPick(index)
                        true
                    }
                }
            }
        },
    )

    LaunchedEffect(style, track) {
        val s = style ?: return@LaunchedEffect
        drawTrack(s, track)
        val map = mapRef ?: return@LaunchedEffect
        if (track.isNotEmpty()) {
            val mid = track[track.size / 2]
            map.cameraPosition = CameraPosition.Builder()
                .target(LatLng(mid.lat, mid.lon))
                .zoom(13.5)
                .build()
        }
    }

    LaunchedEffect(style, events) {
        style?.let { drawEvents(it, events) }
    }

    LaunchedEffect(style, selectedIndex, track) {
        val s = style ?: return@LaunchedEffect
        drawSelection(s, track.getOrNull(selectedIndex ?: -1))
    }
}

private fun drawSelection(style: Style, point: GeoPoint?) {
    style.getLayer("preview-selection-layer")?.let { style.removeLayer(it) }
    style.getSource("preview-selection-source")?.let { style.removeSource(it) }
    if (point == null) return
    style.addSource(
        GeoJsonSource("preview-selection-source", Feature.fromGeometry(Point.fromLngLat(point.lon, point.lat))),
    )
    style.addLayer(
        CircleLayer("preview-selection-layer", "preview-selection-source").withProperties(
            PropertyFactory.circleRadius(12f),
            PropertyFactory.circleColor("#FFB300"),
            PropertyFactory.circleStrokeColor("#000000"),
            PropertyFactory.circleStrokeWidth(2f),
        ),
    )
}

/**
 * Draws the ridden track coloured by speed: one segment per pair of fixes, green (slow),
 * yellow (medium), red (fast). Same bands as the live map.
 */
private fun drawTrack(style: Style, track: List<GeoPoint>) {
    for (band in SpeedBand.entries) {
        style.getLayer("preview-track-${band.name}")?.let { style.removeLayer(it) }
    }
    style.getSource(TRACK_SOURCE)?.let { style.removeSource(it) }
    if (track.size < 2) return

    val features = track.zipWithNext().map { (a, b) ->
        val band = SpeedBands.of(speedKmh(a, b))
        Feature.fromGeometry(
            LineString.fromLngLats(listOf(Point.fromLngLat(a.lon, a.lat), Point.fromLngLat(b.lon, b.lat))),
        ).apply { addStringProperty("band", band.name) }
    }
    style.addSource(GeoJsonSource(TRACK_SOURCE, FeatureCollection.fromFeatures(features)))
    for (band in SpeedBand.entries) {
        style.addLayer(
            LineLayer("preview-track-${band.name}", TRACK_SOURCE).withProperties(
                PropertyFactory.lineColor(band.hex),
                PropertyFactory.lineWidth(5f),
                PropertyFactory.lineCap("round"),
                PropertyFactory.lineJoin("round"),
            ).withFilter(Expression.eq(Expression.get("band"), Expression.literal(band.name))),
        )
    }
}

private const val TRACK_SOURCE = "preview-track-source"

private fun speedKmh(a: GeoPoint, b: GeoPoint): Double {
    val dt = (b.timeMillis - a.timeMillis) / 1000.0
    if (dt <= 0) return 0.0
    return haversineMeters(a, b) / dt * 3.6
}

private fun haversineMeters(a: GeoPoint, b: GeoPoint): Double {
    val r = 6_371_000.0
    val dLat = Math.toRadians(b.lat - a.lat)
    val dLon = Math.toRadians(b.lon - a.lon)
    val h = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
        Math.cos(Math.toRadians(a.lat)) * Math.cos(Math.toRadians(b.lat)) *
        Math.sin(dLon / 2) * Math.sin(dLon / 2)
    return 2 * r * Math.asin(Math.sqrt(h))
}

/** Legend for the speed colours: one row per band with its German label. */
@Composable
fun SpeedLegend() {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        SpeedBand.entries.forEach { band ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(12.dp)
                        .background(Color(android.graphics.Color.parseColor(band.hex))),
                )
                Text(" ${band.label}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private fun drawEvents(style: Style, events: List<RideEventEntity>) {
    style.getLayer("preview-event-layer")?.let { style.removeLayer(it) }
    style.getSource("preview-event-source")?.let { style.removeSource(it) }
    if (events.isEmpty()) return
    val features = events.map { e ->
        Feature.fromGeometry(Point.fromLngLat(e.lon, e.lat)).apply {
            addStringProperty("source", e.source)
        }
    }
    style.addSource(GeoJsonSource("preview-event-source", FeatureCollection.fromFeatures(features)))
    style.addLayer(
        CircleLayer("preview-event-layer", "preview-event-source").withProperties(
            PropertyFactory.circleRadius(8f),
            PropertyFactory.circleColor("#D32F2F"),
            PropertyFactory.circleStrokeColor("#FFFFFF"),
            PropertyFactory.circleStrokeWidth(2f),
        ),
    )
}
