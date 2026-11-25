package com.example.saddlemate.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.saddlemate.data.mock.RouteCatalog
import com.example.saddlemate.domain.AudioMode
import com.example.saddlemate.domain.Elevation
import com.example.saddlemate.domain.Gait
import com.example.saddlemate.domain.RadarAlert
import com.example.saddlemate.domain.RelativeSide
import com.example.saddlemate.ui.viewmodels.CockpitState
import com.example.saddlemate.ui.viewmodels.RideViewModel

@Composable
fun CockpitScreen(
    viewModel: RideViewModel = rideViewModelShared(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    // The ride needs precise location and, with the screen off, background location.
    // Android asks for background location only after the foreground one is granted.
    var needsPermission by remember { mutableStateOf(false) }
    var askBackground by remember { mutableStateOf(false) }
    val backgroundLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { _: Boolean ->
        // Granted or not, the ride starts: without it the ride still records with the screen on.
        state.horse?.id?.let(viewModel::start)
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { granted: Map<String, Boolean> ->
        if (granted[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            askBackground = true
        } else {
            needsPermission = true
        }
    }
    LaunchedEffect(askBackground) {
        if (askBackground) {
            askBackground = false
            backgroundLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }
    }

    CockpitContent(
        state = state,
        needsPermission = needsPermission,
        onStart = {
            val hasFine = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED
            if (hasFine) {
                viewModel.toggleRide()
            } else {
                // Notifications (Android 13+) keep the recording visible; asked together with location.
                permissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.POST_NOTIFICATIONS,
                    ),
                )
            }
        },
        onStop = viewModel::stop,
        onAudioMode = viewModel::setAudioMode,
        onSelectRoute = viewModel::selectRoute,
    )
}

@Composable
fun CockpitContent(
    state: CockpitState,
    needsPermission: Boolean = false,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onAudioMode: (AudioMode) -> Unit,
    onSelectRoute: (String) -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Ritt-Cockpit", style = MaterialTheme.typography.headlineMedium)
        state.bannerMessage?.let { msg ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    msg,
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
        state.horse?.let { Text("Pferd: ${it.name}", style = MaterialTheme.typography.titleMedium) }

        SpeedCard(state)
        RouteProgressCard(state)
        RadarCard(state.radar)
        WelfareCard(state)

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (state.running) {
                Button(onClick = onStop, modifier = Modifier.weight(1f)) { Text("Ritt beenden") }
            } else {
                Button(onClick = onStart, modifier = Modifier.weight(1f)) { Text("Ritt starten") }
            }
        }

        if (!state.running) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Route", style = MaterialTheme.typography.titleMedium)
                RouteCatalog.routes.forEach { route ->
                    val selected = state.selectedRouteId == route.id
                    val status = if (route.gapMeters == 0) "geschlossen" else "Lücke ${route.gapMeters} m"
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { onSelectRoute(route.id) },
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp).fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = selected, onClick = { onSelectRoute(route.id) })
                            Column(Modifier.padding(start = 8.dp)) {
                                Text(route.name, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    "${"%.2f".format(route.lengthKm)} km · $status",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
            }
        }

        if (needsPermission) {
            Text(
                "Ohne Standort-Erlaubnis kann der Ritt nicht aufgezeichnet werden. Bitte in den Einstellungen erlauben.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        AudioModeRow(current = state.audioMode, onSelect = onAudioMode)

        state.lastSummary?.let { summary ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Letzter Ritt", style = MaterialTheme.typography.titleMedium)
                    Text("Distanz: ${"%.2f".format(summary.distanceMeters / 1000)} km")
                    Text("Ø Tempo: ${"%.1f".format(summary.averageSpeedKmh)} km/h")
                    Text("Km-Splits: ${summary.splits.size}")
                    if (summary.elevation != Elevation.NONE) {
                        Text(
                            "ca. ${"%.0f".format(summary.elevation.ascentM)} m auf · ${"%.0f".format(summary.elevation.descentM)} m ab",
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SpeedCard(state: CockpitState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "${"%.1f".format(state.currentSpeedKmh)} km/h",
                style = MaterialTheme.typography.displaySmall,
            )
            Text("Gangart: ${state.gait.toGerman()}", style = MaterialTheme.typography.titleMedium)
            Text("Strecke: ${"%.0f".format(state.distanceMeters)} m · Zeit: ${state.elapsedSec} s")
            if (state.calories.riderKcal > 0 || state.calories.horseKcal > 0) {
                Text(
                    "Kalorien: Reiter ca. ${state.calories.riderKcal} kcal · Pferd ca. ${state.calories.horseKcal} kcal",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun RouteProgressCard(state: CockpitState) {
    if (state.routeLengthMeters <= 0.0) return
    val doneKm = state.distanceMeters / 1000
    val totalKm = state.routeLengthMeters / 1000
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Route", style = MaterialTheme.typography.titleMedium)
            LinearProgressIndicator(
                progress = { state.routeProgress.toFloat() },
                modifier = Modifier.fillMaxWidth(),
            )
            Text("${"%.2f".format(doneKm)} km von ${"%.2f".format(totalKm)} km")
        }
    }
}

@Composable
private fun RadarCard(alerts: List<RadarAlert>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Gefahrenradar", style = MaterialTheme.typography.titleMedium)
            if (alerts.isEmpty()) {
                Text("Keine Gefahr in Reichweite")
            } else {
                alerts.forEach { alert ->
                    Text(
                        "${alert.hazard.label}: ${alert.distanceMeters.toInt()} m ${alert.side.toGerman()}",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }
    }
}

@Composable
private fun WelfareCard(state: CockpitState) {
    val score = state.welfare?.score ?: 100
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Welfare: $score / 100", style = MaterialTheme.typography.titleMedium)
            LinearProgressIndicator(
                progress = { score / 100f },
                modifier = Modifier.fillMaxWidth(),
            )
            state.welfare?.warnings?.forEach { Text("• $it") }
        }
    }
}

@Composable
private fun AudioModeRow(current: AudioMode, onSelect: (AudioMode) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Audio-Modus", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            AudioMode.entries.forEach { mode ->
                if (mode == current) {
                    Button(onClick = { onSelect(mode) }) { Text(mode.toGerman()) }
                } else {
                    OutlinedButton(onClick = { onSelect(mode) }) { Text(mode.toGerman()) }
                }
            }
        }
    }
}

private fun Gait.toGerman() = when (this) {
    Gait.STANDSTILL -> "Stand"
    Gait.WALK -> "Schritt"
    Gait.TROT -> "Trab"
    Gait.CANTER -> "Galopp (langsam)"
    Gait.GALLOP -> "Galopp"
}

private fun RelativeSide.toGerman() = when (this) {
    RelativeSide.AHEAD -> "geradeaus"
    RelativeSide.LEFT -> "links"
    RelativeSide.RIGHT -> "rechts"
}

private fun AudioMode.toGerman() = when (this) {
    AudioMode.FULL_COPILOT -> "Voll"
    AudioMode.RADAR_ONLY -> "Radar"
    AudioMode.GROUP_HAPTIC -> "Haptik"
    AudioMode.SILENT -> "Stumm"
}
