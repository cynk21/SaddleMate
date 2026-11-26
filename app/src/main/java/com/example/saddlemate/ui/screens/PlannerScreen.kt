package com.example.saddlemate.ui.screens

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.saddlemate.data.mock.MockData
import com.example.saddlemate.data.mock.RouteCatalog
import com.example.saddlemate.data.mock.RouteInfo
import com.example.saddlemate.domain.GeneratedLoop
import com.example.saddlemate.domain.GeoPoint
import com.example.saddlemate.domain.RouteGenerator
import com.example.saddlemate.domain.RoutePlanner
import com.example.saddlemate.ui.viewmodels.HorseViewModel
import com.example.saddlemate.ui.viewmodels.MyRidesViewModel
import com.example.saddlemate.ui.viewmodels.RideViewModel

@Composable
fun PlannerScreen(
    viewModel: HorseViewModel = hiltViewModel(),
    sharedRidesViewModel: MyRidesViewModel = hiltViewModel(),
    rideViewModel: RideViewModel = rideViewModelShared(),
    onChooseRide: (String) -> Unit = {},
    onChooseCatalogRoute: (String) -> Unit = {},
    onChooseGeneratedRoute: (GeneratedLoop) -> Unit = {},
) {
    val state by viewModel.uiState.collectAsState()
    val rideState by rideViewModel.state.collectAsState()
    val horse = state.horses.firstOrNull { it.id == state.activeHorseId }
    var distance by remember { mutableStateOf(RoutePlanner.distancesKm.first()) }
    var useCurrentLocation by remember { mutableStateOf(true) }
    val sharedRides by sharedRidesViewModel.rides.collectAsState(initial = emptyList())

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Routenplaner & Stallwechsel", style = MaterialTheme.typography.headlineMedium)

        if (horse == null) {
            Text("Bitte zuerst ein aktives Pferd im Tab „Pferd“ wählen.")
            return@Column
        }

        Text(
            "Profil: ${horse.name}" +
                (if (horse.isYoung) " · Jungpferd" else "") +
                (if (horse.hasLaminitisHistory) " · Rehe-Historie" else ""),
            style = MaterialTheme.typography.titleMedium,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RoutePlanner.distancesKm.forEach { km ->
                if (km == distance) {
                    Button(onClick = { distance = km }) { Text("${km.toInt()} km") }
                } else {
                    OutlinedButton(onClick = { distance = km }) { Text("${km.toInt()} km") }
                }
            }
        }

        // Stallwechsel / Rundrouten-Generator um aktuellen Standort oder Stall-Zentrum
        val startCenter = if (useCurrentLocation && rideState.ownPosition != null) {
            rideState.ownPosition!!
        } else {
            GeoPoint(MockData.CENTER_LAT, MockData.CENTER_LON, 0L)
        }
        val loops = remember(startCenter.lat, startCenter.lon, horse.id, horse.hasLaminitisHistory, horse.isYoung, distance) {
            RouteGenerator.generateLoops(startCenter, horse, distance)
        }

        Text("Stallwechsel-Rundkurse (${distance.toInt()} km)", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (useCurrentLocation) {
                Button(onClick = { useCurrentLocation = true }) { Text("Aktueller Standort") }
                OutlinedButton(onClick = { useCurrentLocation = false }) { Text("Heimat-Stall") }
            } else {
                OutlinedButton(onClick = { useCurrentLocation = true }) { Text("Aktueller Standort") }
                Button(onClick = { useCurrentLocation = false }) { Text("Heimat-Stall") }
            }
        }
        loops.forEach { loop ->
            Card(
                modifier = Modifier.fillMaxWidth().clickable { onChooseGeneratedRoute(loop) },
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(loop.name, style = MaterialTheme.typography.titleMedium)
                    Text("${"%.1f".format(loop.actualKm)} km · ${loop.surfaceHint}")
                    Text(loop.suitableNote, style = MaterialTheme.typography.bodySmall)
                    Text("Antippen: auf Karte zeigen & Navi starten", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        val matchingCatalog = remember(distance) {
            RouteCatalog.routes
                .sortedBy { kotlin.math.abs(it.lengthKm - distance) }
                .take(5)
        }
        Text("Katalog-Routen", style = MaterialTheme.typography.titleMedium)
        matchingCatalog.forEach { info ->
            CatalogRouteCard(info, onClick = { onChooseCatalogRoute(info.id) })
        }

        // Saved rides that are not private appear as routes too. Only the route is taken over,
        // not the speeds. Private rides are never listed here.
        val visibleRides = sharedRides.filter { it.visibility != "private" }
        if (visibleRides.isNotEmpty()) {
            Text("Meine Ritte als Route", style = MaterialTheme.typography.titleMedium)
            visibleRides.forEach { ride ->
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { onChooseRide(ride.id) },
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            ride.name.ifBlank { "Ritt ${ride.id.take(6)}" },
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text("${"%.1f".format(ride.distanceMeters / 1000.0)} km · aus Ritt")
                        Text("Antippen: auf der Karte zeigen und als Navi-Ziel wählen", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun CatalogRouteCard(info: RouteInfo, onClick: () -> Unit) {
    val status = if (info.gapMeters == 0) "geschlossen" else "Lücke ${info.gapMeters} m"
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(info.name, style = MaterialTheme.typography.titleMedium)
            Text("${"%.2f".format(info.lengthKm)} km · $status")
            Text("Antippen: ganz auf der Karte zeigen & Navi starten", style = MaterialTheme.typography.bodySmall)
        }
    }
}
