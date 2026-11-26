package com.example.saddlemate.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.saddlemate.ui.viewmodels.RideSummaryUiState
import com.example.saddlemate.ui.viewmodels.RideSummaryViewModel

@Composable
fun RideSummaryScreen(
    viewModel: RideSummaryViewModel = hiltViewModel(),
    onBackToMap: () -> Unit = {},
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(Unit) { viewModel.load() }
    RideSummaryContent(state, onBackToMap)
}

@Composable
fun RideSummaryContent(state: RideSummaryUiState, onBackToMap: () -> Unit = {}) {
    val ride = state.ride
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Ritt-Zusammenfassung", style = MaterialTheme.typography.headlineMedium)
        // Always a way back to the map, so the rider is never stuck on this screen.
        OutlinedButton(onClick = onBackToMap) { Text("Zurück zur Karte") }
        if (ride == null) {
            Text("Ritt nicht gefunden.")
            return@Column
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Distanz: ${"%.2f".format(ride.distanceMeters / 1000)} km")
                Text("Dauer: ${ride.durationMs / 60_000} min")
                Text("Ø Tempo: ${"%.1f".format(ride.averageSpeedKmh)} km/h")
                Text("Max. Tempo: ${"%.1f".format(ride.maxSpeedKmh)} km/h")
                if (ride.riderCalories > 0 || ride.horseCalories > 0) {
                    Text("Kalorien: Reiter ~${ride.riderCalories} kcal · Pferd ~${ride.horseCalories} kcal")
                }
                if (ride.avgHorseHrBpm > 0 || ride.recoveryHr2MinBpm > 0) {
                    Text(
                        "Pferde-Puls: Ø ${ride.avgHorseHrBpm} / Max ${ride.maxHorseHrBpm} bpm" +
                            if (ride.recoveryHr2MinBpm > 0) " · Erholung ${ride.recoveryHr2MinBpm} bpm" else "",
                    )
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Welfare: ${ride.welfareScore} / 100", style = MaterialTheme.typography.titleMedium)
                LinearProgressIndicator(
                    progress = { ride.welfareScore / 100f },
                    modifier = Modifier.fillMaxWidth(),
                )
                state.warnings.forEach { Text("• $it") }
            }
        }
    }
}
