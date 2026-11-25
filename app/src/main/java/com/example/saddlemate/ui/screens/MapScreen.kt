package com.example.saddlemate.ui.screens

import android.Manifest
import android.graphics.RectF
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.saddlemate.data.local.RideEventEntity
import com.example.saddlemate.data.mock.MockData
import com.example.saddlemate.data.mock.RouteCatalog
import com.example.saddlemate.data.mock.RouteInfo
import com.example.saddlemate.data.mock.SurfaceData
import com.example.saddlemate.data.mock.SurfaceSegment
import com.example.saddlemate.domain.SurfaceLine
import com.example.saddlemate.domain.SurfaceStyle
import com.example.saddlemate.domain.MarkIcon
import com.example.saddlemate.ui.map.MarkIconBitmaps
import com.example.saddlemate.ui.map.markIconVector
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.graphics.Color
import com.example.saddlemate.domain.AudioMode
import com.example.saddlemate.domain.GeoPoint
import com.example.saddlemate.domain.HazardMarker
import com.example.saddlemate.domain.HazardType
import com.example.saddlemate.domain.SpeedBand
import com.example.saddlemate.domain.SpeedBands
import com.example.saddlemate.ui.viewmodels.RadarEvent
import com.example.saddlemate.ui.viewmodels.RideViewModel
import com.example.saddlemate.ui.viewmodels.FeedbackViewModel
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

const val OSM_STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"

private const val ROUTE_SOURCE_ID = "route-source"
private const val ROUTE_LAYER_ID = "route-layer"
private const val TRAIL_SOURCE_ID = "ride-trail-source"
private const val TRAIL_LAYER_PREFIX = "ride-trail-"
private const val HAZARD_SOURCE_ID = "ride-hazard-source"
private const val HAZARD_LAYER_ID = "ride-hazard-layer"
private const val EVENT_SOURCE_ID = "ride-event-source"
private const val EVENT_LAYER_ID = "ride-event-layer"
private const val ME_SOURCE_ID = "own-position-source"
private const val ME_LAYER_ID = "own-position-layer"
private const val MARK_SOURCE_ID = "mark-source"
private const val MARK_LAYER_ID = "mark-layer"

/** A map mark the rider can pick. [key] matches a HazardType name. */
private data class MarkKind(val key: String, val name: String)

private val markKinds = listOf(
    MarkKind("DOG", "Hund"),
    MarkKind("BLIND_TURN", "Unübersichtliche Kurve"),
    MarkKind("ROAD_CROSSING", "Straßenquerung"),
    MarkKind("GATE", "Tor"),
    MarkKind("WATER", "Wasserstelle"),
    MarkKind("LOOSE_HORSE", "Freilaufendes Pferd"),
    MarkKind("NOTE", "Hinweis"),
    MarkKind("VOICE_PIN", "Sprach-Pin"),
)

@Composable
fun MapScreen(viewModel: RideViewModel = rideViewModelShared()) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    var style by remember { mutableStateOf<Style?>(null) }
    var mapRef by remember { mutableStateOf<MapLibreMap?>(null) }
    var showHazards by remember { mutableStateOf(true) }
    var showMarks by remember { mutableStateOf(true) }
    var showGround by remember { mutableStateOf(true) }
    var followRider by remember { mutableStateOf(true) }
    // The mark the rider long-pressed on the map, opens its edit menu. Set from the map listener.
    val selectedMarkId = remember { androidx.compose.runtime.mutableStateOf<Long?>(null) }
    val followRiderRef = remember { androidx.compose.runtime.mutableStateOf(true) }
    LaunchedEffect(followRiderRef.value) { followRider = followRiderRef.value }
    var pendingTap by remember { mutableStateOf<GeoPoint?>(null) }
    // Set when the rider picks "Sprach-Pin" from the mark menu: the pin goes to this tapped point.
    var voicePinAt by remember { mutableStateOf<GeoPoint?>(null) }

    // Every catalogued route, with its points and surface stretches.
    val loadedRoutes = remember {
        RouteCatalog.routes.map { info ->
            LoadedRoute(info, RouteCatalog.load(context, info), SurfaceData.load(context, info))
        }
    }
    val defaultRoute = loadedRoutes.first { it.info.id == RouteCatalog.default.id }
    val route = defaultRoute.points
    val hazards = remember(route) { MockData.weinbergHazards(route) }

    // Location: ask once, then follow the own position on the map.
    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { granted: Map<String, Boolean> ->
        if (granted.values.any { it }) viewModel.trackOwnPosition()
    }
    LaunchedEffect(Unit) {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (fine) {
            viewModel.trackOwnPosition()
        } else {
            locationLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
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
                        map.setStyle(Style.Builder().fromUri(OSM_STYLE_URL)) { loaded ->
                            MarkIconBitmaps.register(ctx, loaded)
                            style = loaded
                        }
                        val mid = route[route.size / 2]
                        map.cameraPosition = CameraPosition.Builder()
                            .target(LatLng(mid.lat, mid.lon))
                            .zoom(14.0)
                            .build()
                        // A movement by the rider (not by the app) stops the camera from following.
                        map.addOnCameraMoveStartedListener { reason ->
                            if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) {
                                followRiderRef.value = false
                            }
                        }
                        // A long press on a mark opens its edit menu.
                        map.addOnMapLongClickListener { point ->
                            val p = map.projection.toScreenLocation(point)
                            val box = RectF(p.x - 40f, p.y - 40f, p.x + 40f, p.y + 40f)
                            val hit = map.queryRenderedFeatures(box, MARK_LAYER_ID, HAZARD_MARK_LAYER_ID).firstOrNull()
                            val id = hit?.getNumberProperty("id")?.toLong()
                            if (id != null) selectedMarkId.value = id
                            id != null
                        }
                        // A tap on a mark speaks its text. Also check hazard-type voice pins on HAZARD_MARK_LAYER_ID.
                        map.addOnMapClickListener { point ->
                            val p = map.projection.toScreenLocation(point)
                            val box = RectF(p.x - 40f, p.y - 40f, p.x + 40f, p.y + 40f)
                            val hit = map.queryRenderedFeatures(box, MARK_LAYER_ID, HAZARD_MARK_LAYER_ID).firstOrNull()
                            val text = hit?.getStringProperty("label")
                            if (!text.isNullOrBlank()) {
                                viewModel.speakMark(text)
                            } else {
                                pendingTap = GeoPoint(point.latitude, point.longitude, System.currentTimeMillis())
                            }
                            true
                        }
                    }
                }
            },
        )

        // Layer switches, top left.
        Row(
            modifier = Modifier.padding(8.dp).align(Alignment.TopStart),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = showHazards,
                onClick = { showHazards = !showHazards },
                label = { Text("Hindernisse") },
            )
            FilterChip(
                selected = showMarks,
                onClick = { showMarks = !showMarks },
                label = { Text("Markierungen") },
            )
            FilterChip(
                selected = showGround,
                onClick = { showGround = !showGround },
                label = { Text("Bodenbelag") },
            )
        }

        // Audio mode overlay (top right, below the compass): speaker button that opens the 4 modes.
        AudioModeOverlay(
            current = state.audioMode,
            onSelect = viewModel::setAudioMode,
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 64.dp, end = 8.dp),
        )

        // "Zentrieren": appears once the map was moved by hand; puts the own position back in the middle.
        if (!followRider) {
            OutlinedButton(
                onClick = {
                    followRiderRef.value = true
                    followRider = true
                },
                modifier = Modifier.padding(16.dp).align(Alignment.BottomEnd),
            ) { Text("Zentrieren") }
        }

        // Navigation hint: remaining distance, next turn and off-route, shown while navigating.
        state.navHint?.let { hint ->
            Card(
                modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 130.dp).align(Alignment.BottomCenter),
            ) {
                Text(hint, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
            }
        }

        // Navigation: appears when a route is shown on the map. Starts and stops the guidance.
        if (state.shownRideId != null) {
            Row(
                modifier = Modifier.padding(bottom = 80.dp).align(Alignment.BottomCenter),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = {
                        if (state.navRouteId == null) viewModel.startNavigation() else viewModel.stopNavigation()
                    },
                ) {
                    Text(if (state.navRouteId == null) "Navi starten" else "Navi beenden")
                }
                if (state.navRouteId == null && !state.running) {
                    OutlinedButton(onClick = { viewModel.clearStoredRide() }) {
                        Text("Ausblenden")
                    }
                }
            }
        }

        // Compact surface legend, steady next to "Ritt starten / beenden" (bottom left).
        if (showGround) {
            SurfaceLegend(modifier = Modifier.padding(16.dp).align(Alignment.BottomStart))
        }

        // Start / stop, bottom centre.
        Button(
            onClick = { viewModel.toggleRide() },
            modifier = Modifier.padding(16.dp).align(Alignment.BottomCenter),
        ) {
            Text(if (state.running) "Ritt beenden" else "Ritt starten")
        }
    }

    LaunchedEffect(style, state.selectedRouteId) {
        style?.let { drawRoutes(it, loadedRoutes, state.selectedRouteId) }
    }

    // Surface is always on for every route in view. It is drawn from zoom 13 on, so the map
    // stays calm when zoomed out.
    // Ground layer from the map tiles: paved and unpaved ways across the whole view.
    LaunchedEffect(style, showGround) {
        style?.let { drawGroundLayer(it, visible = showGround) }
    }

    LaunchedEffect(style, loadedRoutes) {
        style?.let { drawSurface(it, loadedRoutes.flatMap { r -> r.surfaces }, visible = true) }
    }

    LaunchedEffect(state.trail, style) {
        style?.let { drawTrail(it, state.trail) }
    }

    // The chosen stored ride: a dashed route, like the walking trails, without speed colours.
    // The whole route is fitted into view, so the rider sees it at once.
    LaunchedEffect(state.shownTrack, style, mapRef) {
        val s = style ?: return@LaunchedEffect
        drawShownRoute(s, state.shownTrack)
        val map = mapRef ?: return@LaunchedEffect
        if (state.shownTrack.size >= 2) {
            val bounds = LatLngBounds.Builder().apply {
                state.shownTrack.forEach { include(LatLng(it.lat, it.lon)) }
            }.build()
            map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 120))
            followRiderRef.value = false
        }
    }

    // Hazards: the route's hazards and the hazard-type marks. The switch shows and hides both.
    val hazardMarks by viewModel.marks.collectAsState()
    LaunchedEffect(showHazards, style, hazardMarks) {
        style?.let {
            drawHazards(it, hazards, visible = showHazards)
            drawHazardMarks(it, hazardMarks, visible = showHazards)
        }
    }

    LaunchedEffect(state.radarEvents, showMarks, style) {
        style?.let { drawEvents(it, state.radarEvents, visible = showMarks) }
    }

    LaunchedEffect(state.ownPosition, style) {
        style?.let { drawOwnPosition(it, state.ownPosition) }
    }

    // Map marks of the horse (its notes) follow the Markierungen switch.
    val marks by viewModel.marks.collectAsState()
    LaunchedEffect(marks, showMarks, style) {
        style?.let { drawMarks(it, marks, visible = showMarks) }
    }

    // Summary of the last ride, shown over the map so the map stays in view.
    val summary = state.lastSummary
    val summaryRideId = state.lastRideId
    if (summary != null && !state.running && summaryRideId != null) {
        var rideName by remember(summaryRideId) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { viewModel.dismissSummary() },
            title = { Text("Ritt beendet") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Strecke: ${"%.2f".format(summary.distanceMeters / 1000)} km")
                    Text("Dauer: ${summary.durationMs / 60_000} min")
                    Text("Ø ${"%.1f".format(summary.averageSpeedKmh)} km/h · max ${"%.1f".format(summary.maxSpeedKmh)} km/h")
                    if (state.calories.riderKcal > 0 || state.calories.horseKcal > 0) {
                        Text("Kalorien: Reiter ~${state.calories.riderKcal} kcal · Pferd ~${state.calories.horseKcal} kcal")
                    }
                    OutlinedTextField(
                        value = rideName,
                        onValueChange = { rideName = it },
                        label = { Text("Ritt benennen (optional)") },
                        singleLine = true,
                    )
                }
            },
            confirmButton = {
                Button(onClick = { viewModel.renameLastRideAndDismiss(rideName) }) {
                    Text("Speichern & Schließen")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { viewModel.dismissSummary() }) { Text("Schließen") }
            },
        )
    }

    // The camera follows the rider only while "followRider" is on. Moving the map turns it off,
    // so the rider can look around and edit. The "Zentrieren" button turns it back on.
    LaunchedEffect(state.trail.size, state.ownPosition, mapRef, followRider) {
        if (!followRider) return@LaunchedEffect
        val target = state.trail.lastOrNull() ?: state.ownPosition ?: return@LaunchedEffect
        mapRef?.animateCamera(CameraUpdateFactory.newLatLng(LatLng(target.lat, target.lon)))
    }

    // Edit menu for a long-pressed mark: edit its text or delete it.
    val markIdChosen = selectedMarkId.value
    val chosenMark = markIdChosen?.let { id -> marks.firstOrNull { it.id == id } }
    if (chosenMark != null) {
        MarkEditDialog(
            mark = chosenMark,
            onDismiss = { selectedMarkId.value = null },
            onSave = { text ->
                viewModel.updateMark(chosenMark, text)
                selectedMarkId.value = null
            },
            onDelete = {
                viewModel.deleteMark(chosenMark)
                selectedMarkId.value = null
            },
        )
    }

    // Sprach-Pin at the tapped point: hold to speak, release to store it there.
    voicePinAt?.let { point ->
        VoicePinAtPointDialog(
            point = point,
            onDone = { voicePinAt = null },
        )
    }

    // Mark menu, opened by a tap on the map.
    pendingTap?.let { point ->
        AlertDialog(
            onDismissRequest = { pendingTap = null },
            title = { Text("Markierung setzen") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    markKinds.chunked(2).forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            row.forEach { kind ->
                                val iconInfo = MarkIcon.forKey(kind.key)
                                OutlinedButton(
                                    modifier = Modifier.weight(1f),
                                    onClick = {
                                        if (kind.key == "VOICE_PIN") {
                                            voicePinAt = point
                                        } else {
                                            viewModel.addMapMark(point, kind.key, kind.name, "")
                                        }
                                        pendingTap = null
                                    },
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        Icon(
                                            imageVector = markIconVector(iconInfo),
                                            contentDescription = kind.name,
                                            tint = Color(android.graphics.Color.parseColor(iconInfo.colorHex)),
                                        )
                                        Text(kind.name, style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }
                    Text(
                        if (state.running) "Wird im laufenden Ritt gespeichert." else "Wird in den Notizen des Pferdes gespeichert.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            },
            confirmButton = {},
            dismissButton = { OutlinedButton(onClick = { pendingTap = null }) { Text("Abbrechen") } },
        )
    }
}

/**
 * Records a Sprach-Pin at [point] (the spot the rider tapped). Hold to speak, release to store.
 * Uses the same recording and transcription as the Feedback tab, with this point as position.
 */
@Composable
private fun VoicePinAtPointDialog(point: GeoPoint, onDone: () -> Unit) {
    val feedback: FeedbackViewModel = hiltViewModel()
    val state by feedback.state.collectAsState()
    val context = LocalContext.current
    val currentPoint by rememberUpdatedState(point)
    val hasMic by rememberUpdatedState(
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED,
    )
    val micLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted: Boolean -> if (granted) feedback.startRecording() }

    AlertDialog(
        onDismissRequest = onDone,
        title = { Text("Sprach-Pin an dieser Stelle") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (state.recording) "Loslassen zum Speichern." else "Gedrückt halten und sprechen.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onPress = {
                                    if (!hasMic) {
                                        micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                        return@detectTapGestures
                                    }
                                    feedback.startRecording()
                                    tryAwaitRelease()
                                    feedback.stopRecording(
                                        fix = currentPoint,
                                        horseId = state.activeHorseId,
                                        nowMillis = currentPoint.timeMillis,
                                    )
                                },
                            )
                        },
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.primary,
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Text(
                            if (state.recording) "Loslassen zum Speichern" else "Gedrückt halten zum Sprechen",
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                }
                state.message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {},
        dismissButton = { OutlinedButton(onClick = onDone) { Text("Schließen") } },
    )
}

/** One RideViewModel for the whole Activity, so the map and the cockpit show the same ride. */
@Composable
fun rideViewModelShared(): RideViewModel {
    // ComponentActivity is the ViewModelStoreOwner for the whole app. Using the nav
    // destination's owner (the default) gives every tab its own RideViewModel.
    val activity = checkNotNull(LocalContext.current.findActivity()) { "No ComponentActivity in context" }
    return hiltViewModel(activity)
}

private tailrec fun Context.findActivity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * All catalogued routes as dashed lines, each with its name. The selected route is drawn
 * stronger. Names appear from zoom 12 on, so the map stays readable when zoomed out.
 */
/**
 * The chosen stored ride, drawn as one dashed line like the walking trails. No speed colours:
 * the speeds belong to the ride, only its route is taken over.
 */
private fun drawShownRoute(style: Style, track: List<GeoPoint>) {
    clearLayer(style, SHOWN_ROUTE_SOURCE_ID, listOf(SHOWN_ROUTE_LAYER_ID))
    if (track.size < 2) return
    val line = LineString.fromLngLats(track.map { Point.fromLngLat(it.lon, it.lat) })
    style.addSource(GeoJsonSource(SHOWN_ROUTE_SOURCE_ID, Feature.fromGeometry(line)))
    style.addLayer(
        LineLayer(SHOWN_ROUTE_LAYER_ID, SHOWN_ROUTE_SOURCE_ID).withProperties(
            PropertyFactory.lineColor("#607D8B"),
            PropertyFactory.lineWidth(4f),
            PropertyFactory.lineDasharray(arrayOf(2f, 2f)),
            PropertyFactory.lineCap("round"),
        ),
    )
}

private const val SHOWN_ROUTE_SOURCE_ID = "shown-route-source"
private const val SHOWN_ROUTE_LAYER_ID = "shown-route-layer"

private fun drawRoutes(style: Style, routes: List<LoadedRoute>, selectedId: String) {
    clearLayer(style, ROUTE_SOURCE_ID, listOf(ROUTE_LAYER_ID, ROUTE_LABEL_LAYER_ID))
    if (routes.isEmpty()) return
    val features = routes.map { r ->
        Feature.fromGeometry(LineString.fromLngLats(r.points.map { Point.fromLngLat(it.lon, it.lat) })).apply {
            addStringProperty("name", r.info.name)
            addBooleanProperty("selected", r.info.id == selectedId)
        }
    }
    style.addSource(GeoJsonSource(ROUTE_SOURCE_ID, FeatureCollection.fromFeatures(features)))
    style.addLayer(
        LineLayer(ROUTE_LAYER_ID, ROUTE_SOURCE_ID).withProperties(
            PropertyFactory.lineColor("#607D8B"),
            PropertyFactory.lineWidth(
                Expression.switchCase(Expression.get("selected"), Expression.literal(5f), Expression.literal(2.5f)),
            ),
            PropertyFactory.lineDasharray(arrayOf(2f, 2f)),
        ),
    )
    // Route names at the start point, from zoom 12. Symbols need a glyph font; the map style
    // provides it, so the label is drawn as text on the line's first point.
    val labels = routes.map { r ->
        val p = r.points.first()
        Feature.fromGeometry(Point.fromLngLat(p.lon, p.lat)).apply {
            addStringProperty("name", r.info.name)
        }
    }
    style.addSource(GeoJsonSource(ROUTE_LABEL_SOURCE_ID, FeatureCollection.fromFeatures(labels)))
    style.addLayer(
        SymbolLayer(ROUTE_LABEL_LAYER_ID, ROUTE_LABEL_SOURCE_ID).withProperties(
            PropertyFactory.textField(Expression.get("name")),
            PropertyFactory.textSize(12f),
            PropertyFactory.textColor("#263238"),
            PropertyFactory.textHaloColor("#FFFFFF"),
            PropertyFactory.textHaloWidth(1.5f),
            // Invisible below zoom 12, so the map stays readable when zoomed out.
            PropertyFactory.textOpacity(
                Expression.step(Expression.zoom(), Expression.literal(0f), Expression.stop(12f, Expression.literal(1f))),
            ),
        ),
    )
}

private const val ROUTE_LABEL_SOURCE_ID = "route-label-source"
private const val ROUTE_LABEL_LAYER_ID = "route-label-layer"

/** A catalogued route with its points and surface stretches, loaded once from the assets. */
data class LoadedRoute(
    val info: RouteInfo,
    val points: List<GeoPoint>,
    val surfaces: List<SurfaceSegment>,
)

/**
 * Map marks taken by the rider, drawn as the icon of their type (see [MarkIcon]).
 * The icon image is registered in the style by [MarkIconBitmaps].
 */
/** Edit or delete one map mark, chosen by a long press on the map. */
@Composable
private fun MarkEditDialog(
    mark: RideEventEntity,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    onDelete: () -> Unit,
) {
    var text by remember(mark.id) { mutableStateOf(mark.label) }
    var confirmDelete by remember(mark.id) { mutableStateOf(false) }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Markierung löschen?") },
            text = { Text("\"${mark.label}\" wird endgültig gelöscht.") },
            confirmButton = { Button(onClick = onDelete) { Text("Löschen") } },
            dismissButton = { OutlinedButton(onClick = { confirmDelete = false }) { Text("Abbrechen") } },
        )
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Markierung") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Text") },
            )
        },
        confirmButton = { Button(onClick = { onSave(text) }) { Text("Speichern") } },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { confirmDelete = true }) { Text("Löschen") }
                OutlinedButton(onClick = onDismiss) { Text("Abbrechen") }
            }
        },
    )
}

/** Compact surface legend next to the Start/Stop button on the map. */
@Composable
private fun SurfaceLegend(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
        tonalElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            LegendLineRow(color = Color(0xFF1A237E), dashed = false, label = "Befestigt")
            LegendLineRow(color = Color(0xFF6D4C41), dashed = true, label = "Unbefestigt")
        }
    }
}

@Composable
private fun LegendLineRow(color: Color, dashed: Boolean, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Canvas(modifier = Modifier.width(20.dp).height(6.dp)) {
            drawLine(
                color = color,
                start = Offset(0f, size.height / 2),
                end = Offset(size.width, size.height / 2),
                strokeWidth = 5f,
                pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f) else null,
            )
        }
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

/** Google-Maps-style speaker button over the map; opens a dropdown with the 4 audio modes. */
@Composable
private fun AudioModeOverlay(
    current: AudioMode,
    onSelect: (AudioMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
            tonalElevation = 4.dp,
            shadowElevation = 4.dp,
        ) {
            IconButton(onClick = { expanded = !expanded }) {
                Icon(
                    imageVector = audioModeIcon(current),
                    contentDescription = "Audio-Modus: ${audioModeLabel(current)}",
                )
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            AudioMode.entries.forEach { mode ->
                DropdownMenuItem(
                    text = { Text(if (mode == current) "✓ ${audioModeLabel(mode)}" else audioModeLabel(mode)) },
                    leadingIcon = { Icon(audioModeIcon(mode), contentDescription = null) },
                    onClick = {
                        onSelect(mode)
                        expanded = false
                    },
                )
            }
        }
    }
}

private fun audioModeIcon(mode: AudioMode) = when (mode) {
    AudioMode.FULL_COPILOT -> Icons.AutoMirrored.Filled.VolumeUp
    AudioMode.RADAR_ONLY -> Icons.Filled.Notifications
    AudioMode.GROUP_HAPTIC -> Icons.Filled.Vibration
    AudioMode.SILENT -> Icons.AutoMirrored.Filled.VolumeOff
}

private fun audioModeLabel(mode: AudioMode) = when (mode) {
    AudioMode.FULL_COPILOT -> "Voll"
    AudioMode.RADAR_ONLY -> "Radar"
    AudioMode.GROUP_HAPTIC -> "Haptik"
    AudioMode.SILENT -> "Stumm"
}

private fun drawMarks(style: Style, marks: List<RideEventEntity>, visible: Boolean) {
    clearLayer(style, MARK_SOURCE_ID, listOf(MARK_LAYER_ID))
    if (!visible) return
    // Hazard-type marks (Tor, Wasser, Pferd, Hund, Kurve, Querung) belong to the
    // "Hindernisse" switch, so they are left out here.
    val shown = marks.filter { m -> HazardType.entries.none { it.name == m.type } }
    if (shown.isEmpty()) return
    val features = shown.map { m ->
        Feature.fromGeometry(Point.fromLngLat(m.lon, m.lat)).apply {
            addStringProperty("icon", MarkIconBitmaps.imageId(MarkIcon.forKey(m.type)))
            addStringProperty("label", m.label)
            addNumberProperty("id", m.id)
        }
    }
    style.addSource(GeoJsonSource(MARK_SOURCE_ID, FeatureCollection.fromFeatures(features)))
    style.addLayer(
        SymbolLayer(MARK_LAYER_ID, MARK_SOURCE_ID).withProperties(
            PropertyFactory.iconImage(Expression.get("icon")),
            PropertyFactory.iconSize(1.0f),
            PropertyFactory.iconAllowOverlap(true),
        ),
    )
}

/**
 * Surface stretches as lines over the route, in the line style of their surface
 * (solid, short dash, dotted, long dash). Untagged or unknown surfaces are not drawn.
 */
/**
 * Ground layer from the map tiles, for every way in view. Paved ways are solid, unpaved ways are
 * dashed. Ways without a surface tag are not drawn. Shown or hidden with the Bodenbelag chip.
 */
private fun drawGroundLayer(style: Style, visible: Boolean) {
    val ids = listOf("ground-paved", "ground-unpaved")
    val visibility = if (visible) "visible" else "none"
    if (style.getLayer(ids[0]) == null) {
        val paved = LineLayer(ids[0], "openmaptiles").withSourceLayer("transportation").withProperties(
            PropertyFactory.lineColor("#1A237E"),
            PropertyFactory.lineWidth(2.5f),
            PropertyFactory.lineOpacity(0.85f),
        ).withFilter(
            Expression.all(
                Expression.has("surface"),
                Expression.`in`(Expression.get("surface"), Expression.literal(arrayOf("paved"))),
            ),
        )
        val unpaved = LineLayer(ids[1], "openmaptiles").withSourceLayer("transportation").withProperties(
            PropertyFactory.lineColor("#6D4C41"),
            PropertyFactory.lineWidth(2.5f),
            PropertyFactory.lineOpacity(0.85f),
            PropertyFactory.lineDasharray(arrayOf(2f, 2f)),
        ).withFilter(
            Expression.all(
                Expression.has("surface"),
                Expression.`in`(Expression.get("surface"), Expression.literal(arrayOf("unpaved"))),
            ),
        )
        // Put the ground layers above the roads, so the colour is visible.
        style.addLayer(paved)
        style.addLayer(unpaved)
    }
    ids.forEach { id -> style.getLayer(id)?.setProperties(PropertyFactory.visibility(visibility)) }
}

private fun drawSurface(style: Style, segments: List<SurfaceSegment>, visible: Boolean) {
    for (i in 0 until SURFACE_LAYER_COUNT) {
        style.getLayer("surface-layer-$i")?.let { style.removeLayer(it) }
    }
    style.getSource(SURFACE_SOURCE_ID)?.let { style.removeSource(it) }
    if (!visible) return

    val drawable = segments.mapNotNull { seg ->
        val kind = SurfaceStyle.lineFor(seg.surface) ?: return@mapNotNull null
        if (seg.points.size < 2) return@mapNotNull null
        Feature.fromGeometry(
            LineString.fromLngLats(seg.points.map { Point.fromLngLat(it.lon, it.lat) }),
        ).apply { addStringProperty("kind", kind.name) }
    }
    if (drawable.isEmpty()) return
    style.addSource(GeoJsonSource(SURFACE_SOURCE_ID, FeatureCollection.fromFeatures(drawable)))
    SurfaceLine.entries.forEachIndexed { i, kind ->
        style.addLayer(
            LineLayer("surface-layer-$i", SURFACE_SOURCE_ID).withProperties(
                PropertyFactory.lineColor("#1A237E"),
                PropertyFactory.lineWidth(4f),
                PropertyFactory.lineDasharray(dashFor(kind)),
                // Only from zoom 13 on; below that the surface lines stay hidden.
                PropertyFactory.lineOpacity(
                    Expression.step(Expression.zoom(), Expression.literal(0f), Expression.stop(13f, Expression.literal(1f))),
                ),
            ).withFilter(Expression.eq(Expression.get("kind"), Expression.literal(kind.name))),
        )
    }
}

private const val SURFACE_SOURCE_ID = "surface-source"
private const val SURFACE_LAYER_COUNT = 4

/** Dash pattern per surface kind, in map units. */
private fun dashFor(kind: SurfaceLine): Array<Float> = when (kind) {
    SurfaceLine.SOLID -> arrayOf(1f, 0f)
    SurfaceLine.SHORT_DASH -> arrayOf(2f, 2f)
    SurfaceLine.DOTTED -> arrayOf(0.5f, 2f)
    SurfaceLine.LONG_DASH -> arrayOf(5f, 2f)
}

/** The rider's own position as a blue dot with a white ring. */
private fun drawOwnPosition(style: Style, p: GeoPoint?) {
    clearLayer(style, ME_SOURCE_ID, listOf(ME_LAYER_ID))
    if (p == null) return
    style.addSource(GeoJsonSource(ME_SOURCE_ID, Feature.fromGeometry(Point.fromLngLat(p.lon, p.lat))))
    style.addLayer(
        CircleLayer(ME_LAYER_ID, ME_SOURCE_ID).withProperties(
            PropertyFactory.circleRadius(9f),
            PropertyFactory.circleColor("#1E88E5"),
            PropertyFactory.circleStrokeColor("#FFFFFF"),
            PropertyFactory.circleStrokeWidth(3f),
        ),
    )
}

/** Rebuilds the ridden track: one coloured segment per consecutive pair of fixes. */
private fun drawTrail(style: Style, trail: List<GeoPoint>) {
    clearLayer(style, TRAIL_SOURCE_ID, SpeedBand.entries.map { "$TRAIL_LAYER_PREFIX${it.name}" })
    if (trail.size < 2) return

    val features = trail.zipWithNext().mapIndexed { index, (a, b) ->
        val band = SpeedBands.of(speedKmh(a, b))
        Feature.fromGeometry(
            LineString.fromLngLats(listOf(Point.fromLngLat(a.lon, a.lat), Point.fromLngLat(b.lon, b.lat))),
        ).apply {
            addStringProperty("band", band.name)
            addNumberProperty("index", index)
        }
    }

    style.addSource(GeoJsonSource(TRAIL_SOURCE_ID, FeatureCollection.fromFeatures(features)))
    SpeedBand.entries.forEach { band ->
        val layer = LineLayer("$TRAIL_LAYER_PREFIX${band.name}", TRAIL_SOURCE_ID)
            .withProperties(
                PropertyFactory.lineColor(band.hex),
                PropertyFactory.lineWidth(5f),
                PropertyFactory.lineCap("round"),
                PropertyFactory.lineJoin("round"),
            )
            .withFilter(Expression.eq(Expression.get("band"), Expression.literal(band.name)))
        style.addLayer(layer)
    }
}

/**
 * Hazard-type marks the rider set on the map (Tor, Wasser, Pferd, Hund, Kurve, Querung). They use
 * the same icon as the mark menu and are shown and hidden with the "Hindernisse" switch.
 */
private fun drawHazardMarks(style: Style, marks: List<RideEventEntity>, visible: Boolean) {
    clearLayer(style, HAZARD_MARK_SOURCE_ID, listOf(HAZARD_MARK_LAYER_ID))
    if (!visible) return
    val hazardMarks = marks.filter { m -> HazardType.entries.any { it.name == m.type } }
    if (hazardMarks.isEmpty()) return
    val features = hazardMarks.map { m ->
        Feature.fromGeometry(Point.fromLngLat(m.lon, m.lat)).apply {
            addStringProperty("icon", MarkIconBitmaps.imageId(MarkIcon.forKey(m.type)))
            addStringProperty("label", m.label)
            addNumberProperty("id", m.id)
        }
    }
    style.addSource(GeoJsonSource(HAZARD_MARK_SOURCE_ID, FeatureCollection.fromFeatures(features)))
    style.addLayer(
        SymbolLayer(HAZARD_MARK_LAYER_ID, HAZARD_MARK_SOURCE_ID).withProperties(
            PropertyFactory.iconImage(Expression.get("icon")),
            PropertyFactory.iconSize(1.0f),
            PropertyFactory.iconAllowOverlap(true),
        ),
    )
}

private const val HAZARD_MARK_SOURCE_ID = "hazard-mark-source"
private const val HAZARD_MARK_LAYER_ID = "hazard-mark-layer"

/** Route hazards, shown when the "Hindernisse" switch is on. */
private fun drawHazards(style: Style, hazards: List<HazardMarker>, visible: Boolean) {
    clearLayer(style, HAZARD_SOURCE_ID, listOf(HAZARD_LAYER_ID))
    if (!visible) return
    // Hazards are marks like the others: same icon per type as the mark menu.
    val features = hazards.map { hazard ->
        Feature.fromGeometry(Point.fromLngLat(hazard.location.lon, hazard.location.lat)).apply {
            addStringProperty("icon", MarkIconBitmaps.imageId(MarkIcon.forKey(hazard.type.name)))
            addStringProperty("label", hazard.label)
        }
    }
    style.addSource(GeoJsonSource(HAZARD_SOURCE_ID, FeatureCollection.fromFeatures(features)))
    style.addLayer(
        SymbolLayer(HAZARD_LAYER_ID, HAZARD_SOURCE_ID).withProperties(
            PropertyFactory.iconImage(Expression.get("icon")),
            PropertyFactory.iconSize(1.0f),
            PropertyFactory.iconAllowOverlap(true),
        ),
    )
}

/** Hazards the radar announced during the ride, red. Shown with the "Markierungen" switch. */
private fun drawEvents(style: Style, events: List<RadarEvent>, visible: Boolean) {
    clearLayer(style, EVENT_SOURCE_ID, listOf(EVENT_LAYER_ID))
    if (!visible || events.isEmpty()) return
    val features = events.map { event ->
        Feature.fromGeometry(Point.fromLngLat(event.position.lon, event.position.lat)).apply {
            addStringProperty("label", event.hazard.label)
            addNumberProperty("distance", event.distanceMeters)
        }
    }
    style.addSource(GeoJsonSource(EVENT_SOURCE_ID, FeatureCollection.fromFeatures(features)))
    style.addLayer(
        CircleLayer(EVENT_LAYER_ID, EVENT_SOURCE_ID).withProperties(
            PropertyFactory.circleRadius(10f),
            PropertyFactory.circleColor("#D32F2F"),
            PropertyFactory.circleStrokeColor("#FFFFFF"),
            PropertyFactory.circleStrokeWidth(3f),
        ),
    )
}

private fun clearLayer(style: Style, sourceId: String, layerIds: List<String>) {
    layerIds.forEach { id -> style.getLayer(id)?.let { style.removeLayer(it) } }
    style.getSource(sourceId)?.let { style.removeSource(it) }
}

private fun speedKmh(a: GeoPoint, b: GeoPoint): Double {
    val dt = (b.timeMillis - a.timeMillis) / 1000.0
    if (dt <= 0) return 0.0
    return haversine(a, b) / dt * 3.6
}

private fun haversine(a: GeoPoint, b: GeoPoint): Double {
    val r = 6_371_000.0
    val dLat = Math.toRadians(b.lat - a.lat)
    val dLon = Math.toRadians(b.lon - a.lon)
    val h = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
        Math.cos(Math.toRadians(a.lat)) * Math.cos(Math.toRadians(b.lat)) *
        Math.sin(dLon / 2) * Math.sin(dLon / 2)
    return 2 * r * Math.asin(Math.sqrt(h))
}
