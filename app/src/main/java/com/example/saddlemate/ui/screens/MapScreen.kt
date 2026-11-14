package com.example.saddlemate.ui.screens

import android.content.Context
import android.view.ViewGroup
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import org.maplibre.android.MapLibre
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style

/**
 * Open-source trail map. Renders OpenStreetMap-derived vector tiles through
 * MapLibre, layered as: background -> landuse -> water -> roads/tracks ->
 * bridleways -> labels. The tile source is configurable (see [OSM_STYLE_URL]).
 */
const val OSM_STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"

@Composable
fun MapScreen() {
    val context = LocalContext.current
    MapLibreMap(context = context, modifier = Modifier.fillMaxSize())
}

@Composable
private fun MapLibreMap(context: Context, modifier: Modifier) {
    AndroidView(
        modifier = modifier,
        factory = {
            MapLibre.getInstance(context)
            MapView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                onCreate(null)
                getMapAsync { map ->
                    map.setStyle(Style.Builder().fromUri(OSM_STYLE_URL))
                    map.cameraPosition = org.maplibre.android.camera.CameraPosition.Builder()
                        .target(org.maplibre.android.geometry.LatLng(47.0, 11.0))
                        .zoom(12.0)
                        .build()
                }
            }
        },
    )
}
