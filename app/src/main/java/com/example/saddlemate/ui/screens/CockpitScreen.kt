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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import com.example.saddlemate.data.mock.RouteCatalog
import com.example.saddlemate.domain.AudioMode
import com.example.saddlemate.domain.CanterStretch
import com.example.saddlemate.domain.Elevation
import com.example.saddlemate.domain.FallAlarm
import com.example.saddlemate.domain.Gait
import com.example.saddlemate.domain.GaitSymmetryTracker
import com.example.saddlemate.domain.RadarAlert
import com.example.saddlemate.domain.RelativeSide
import com.example.saddlemate.domain.VitalMonitor
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
        onStartDemo = viewModel::startDemoSimulation,
        onTestFall = { viewModel.triggerFallAlert(fromTestButton = true) },
        onCancelFall = viewModel::cancelFallAlert,
        onDismissFall = viewModel::dismissFallResult,
        onAskQuestion = viewModel::askRiderQuestion,
        onSaveCanterStretch = viewModel::saveCanterStretchMark,
        onConnectBle = viewModel::connectBleHeartRate,
        onDummyBle = viewModel::startDummyBleHeartRate,
        onDisconnectBle = viewModel::disconnectBleHeartRate,
        onSaveRecoveryPulse = viewModel::recordVetGateRecoveryBpm,
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
    onStartDemo: () -> Unit = {},
    onTestFall: () -> Unit = {},
    onCancelFall: () -> Unit = {},
    onDismissFall: () -> Unit = {},
    onAskQuestion: (String) -> Unit = {},
    onSaveCanterStretch: (CanterStretch) -> Unit = {},
    onConnectBle: () -> Unit = {},
    onDummyBle: () -> Unit = {},
    onDisconnectBle: () -> Unit = {},
    onSaveRecoveryPulse: (Int) -> Unit = {},
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
        HeartRateCard(
            state = state,
            onConnectBle = onConnectBle,
            onDummyBle = onDummyBle,
            onDisconnectBle = onDisconnectBle,
            onSaveRecoveryPulse = onSaveRecoveryPulse,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (state.running) {
                Button(onClick = onStop, modifier = Modifier.weight(1f)) {
                    Text(if (state.isSimulating) "Demo beenden" else "Ritt beenden")
                }
            } else {
                Button(onClick = onStart, modifier = Modifier.weight(1f)) { Text("Ritt starten") }
                OutlinedButton(onClick = onStartDemo) { Text("Demo-Ritt") }
            }
        }

        // Hands-free Rider Q&A + Dummy Fall Protection controls
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Sprach-Assistent & Schutzengel", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        onClick = { onAskQuestion("Ist das Pferd warmgeritten?") },
                    ) { Text("Warm-up?", style = MaterialTheme.typography.labelSmall) }
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        onClick = { onAskQuestion("Wie weit noch bis zum Ziel?") },
                    ) { Text("Wie weit?", style = MaterialTheme.typography.labelSmall) }
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        onClick = { onAskQuestion("Wie ist der Takt?") },
                    ) { Text("Takt?", style = MaterialTheme.typography.labelSmall) }
                }
                state.lastQaAnswer?.let { ans ->
                    Text(ans, style = MaterialTheme.typography.bodySmall)
                }
                OutlinedButton(onClick = onTestFall) { Text("Sturz-Schutzengel testen (Dummy)") }
            }
        }

        if (state.detectedCanterStretches.isNotEmpty() && !state.running) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Neue Galoppstrecke erkannt!", style = MaterialTheme.typography.titleMedium)
                    state.detectedCanterStretches.forEach { stretch ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("${stretch.lengthMeters.toInt()} m Galopp")
                            Button(onClick = { onSaveCanterStretch(stretch) }) {
                                Text("Als Galoppstrecke speichern")
                            }
                        }
                    }
                }
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

    FallAlertOverlay(
        state = state,
        onCancel = onCancelFall,
        onDismissResult = onDismissFall,
    )
}

@Composable
private fun SpeedCard(state: CockpitState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondary,
            contentColor = MaterialTheme.colorScheme.onSecondary,
        ),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "${"%.1f".format(state.currentSpeedKmh)} km/h",
                style = MaterialTheme.typography.displaySmall,
            )
            Text("Gangart: ${state.gait.toGerman()}", style = MaterialTheme.typography.titleMedium)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Strecke: ${"%.2f".format(state.distanceMeters / 1000.0)} km")
                Text("Zeit: ${state.elapsedSec / 60}:${"%02d".format(state.elapsedSec % 60)}")
            }
            if (state.calories.riderKcal > 0 || state.calories.horseKcal > 0) {
                Text(
                    "Kalorien: Reiter ~${state.calories.riderKcal} kcal · Pferd ~${state.calories.horseKcal} kcal",
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
    val warmupProgress = (state.warmupMinutes / state.requiredWarmupMinutes).toFloat().coerceIn(0f, 1f)
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Tierwohl-Wächter: $score / 100", style = MaterialTheme.typography.titleMedium)
            LinearProgressIndicator(
                progress = { score / 100f },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "Warm-up: ${state.warmupMinutes.toInt()} / ${state.requiredWarmupMinutes.toInt()} Min. Schritt",
                style = MaterialTheme.typography.bodySmall,
            )
            LinearProgressIndicator(
                progress = { warmupProgress },
                modifier = Modifier.fillMaxWidth(),
            )
            if (state.cooldownNeededMinutes > 0.5) {
                Text(
                    "Cool-down empfohlen: noch ${state.cooldownNeededMinutes.toInt()} Min. Schritt",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            val symText = if (state.trotSymmetryPct < GaitSymmetryTracker.WARN_THRESHOLD_PCT) {
                "Trab-Takt: ${state.trotSymmetryPct.toInt()}% (Hufe / Steine prüfen!)"
            } else {
                "Trab-Takt-Symmetrie: ${state.trotSymmetryPct.toInt()}%"
            }
            Text(symText, style = MaterialTheme.typography.bodySmall)
            state.surfaceWarning?.let { Text("⚠ $it", color = MaterialTheme.colorScheme.tertiary) }
            state.welfare?.warnings?.forEach { Text("• $it") }
        }
    }
}

@Composable
private fun HeartRateCard(
    state: CockpitState,
    onConnectBle: () -> Unit,
    onDummyBle: () -> Unit,
    onDisconnectBle: () -> Unit,
    onSaveRecoveryPulse: (Int) -> Unit,
) {
    val ble = state.bleHr
    val context = LocalContext.current
    val blePermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { granted ->
        if (granted.values.any { it }) onConnectBle()
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Pferde-Herzfrequenz & Vet-Gate", style = MaterialTheme.typography.titleMedium)
            Text(
                if (ble.bpm != null) VitalMonitor.zoneLabel(ble.bpm) else ble.status,
                style = MaterialTheme.typography.bodyLarge,
            )
            if (state.avgHorseHrBpm > 0) {
                Text(
                    "Ritt Ø ${state.avgHorseHrBpm} bpm · Max ${state.maxHorseHrBpm} bpm",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (state.recoveryHr2MinBpm > 0) {
                val ok = VitalMonitor.isVetGatePassed(state.recoveryHr2MinBpm)
                Text(
                    "Vet-Gate Erholung (2 Min.): ${state.recoveryHr2MinBpm} bpm → " +
                        if (ok) "BESTANDEN (< ${VitalMonitor.VET_GATE_PASS_BPM} bpm)" else "NOCH ERHÖHT",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!ble.connected) {
                    OutlinedButton(
                        onClick = {
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                                val hasScan = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.BLUETOOTH_SCAN,
                                ) == PackageManager.PERMISSION_GRANTED
                                val hasConn = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.BLUETOOTH_CONNECT,
                                ) == PackageManager.PERMISSION_GRANTED
                                if (hasScan && hasConn) {
                                    onConnectBle()
                                } else {
                                    blePermLauncher.launch(
                                        arrayOf(
                                            Manifest.permission.BLUETOOTH_SCAN,
                                            Manifest.permission.BLUETOOTH_CONNECT,
                                        ),
                                    )
                                }
                            } else {
                                onConnectBle()
                            }
                        },
                    ) { Text("BLE-Gurt (0x180D)") }
                    OutlinedButton(onClick = onDummyBle) { Text("Test-Gurt") }
                } else {
                    OutlinedButton(onClick = onDisconnectBle) { Text("Gurt trennen") }
                    ble.bpm?.let { currentBpm ->
                        OutlinedButton(onClick = { onSaveRecoveryPulse(currentBpm) }) {
                            Text("Als 2-Min-Vet-Gate")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FallAlertOverlay(
    state: CockpitState,
    onCancel: () -> Unit,
    onDismissResult: () -> Unit,
) {
    when (state.fallState) {
        FallAlarm.State.COUNTING -> {
            AlertDialog(
                onDismissRequest = {},
                title = { Text("Sturz erkannt! (${state.fallRemainingSec} s)") },
                text = {
                    Text(
                        "Wenn du in ${state.fallRemainingSec} Sekunden nicht abbrichst, " +
                            "wird der Dummy-Notruf (simulierte Standort-SMS) ausgelöst.",
                    )
                },
                confirmButton = {
                    Button(
                        onClick = onCancel,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    ) { Text("Ich bin okay (Abbrechen)") }
                },
            )
        }
        FallAlarm.State.SOS_TRIGGERED -> {
            AlertDialog(
                onDismissRequest = onDismissResult,
                title = { Text("Dummy-Notruf ausgelöst") },
                text = { Text(state.fallMessage ?: "Simulierte Standort-SMS gesendet.") },
                confirmButton = { Button(onClick = onDismissResult) { Text("OK") } },
            )
        }
        else -> Unit
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
