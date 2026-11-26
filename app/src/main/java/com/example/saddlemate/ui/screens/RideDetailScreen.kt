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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.saddlemate.data.local.RideEventEntity
import com.example.saddlemate.domain.MarkIcon
import com.example.saddlemate.ui.map.markIconVector
import com.example.saddlemate.ui.viewmodels.RideDetailViewModel

/** Event types a rider can set by hand, matching HazardType names. */
private val eventTypes = listOf(
    "DOG" to "Hund",
    "BLIND_TURN" to "Unübersichtliche Kurve",
    "ROAD_CROSSING" to "Straßenquerung",
    "LOOSE_HORSE" to "Freilaufendes Pferd",
    "GATE" to "Tor",
    "WATER" to "Wasserstelle",
)

private fun typeLabel(type: String): String = eventTypes.firstOrNull { it.first == type }?.second ?: type

/**
 * Stored ride: map with track and events, the event list, and editing.
 * Events are added at a point on the track, edited, or deleted.
 */
@Composable
fun RideDetailScreen(viewModel: RideDetailViewModel = hiltViewModel(), onDeleted: () -> Unit) {
    val state by viewModel.state.collectAsState()
    val ride = state.ride
    var adding by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<RideEventEntity?>(null) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(ride?.name?.ifBlank { "Ritt" } ?: "Ritt", style = MaterialTheme.typography.headlineMedium)
        if (ride == null) {
            Text("Ritt nicht gefunden.")
            return@Column
        }

        Text(
            "Tippe auf die Strecke, um die Stelle für ein neues Ereignis zu wählen.",
            style = MaterialTheme.typography.bodySmall,
        )
        SpeedLegend()
        RideMapPreview(
            track = state.track,
            events = state.events,
            selectedIndex = state.pickedIndex,
            onTrackPick = { viewModel.pickTrackIndex(it) },
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Strecke: ${"%.2f".format(ride.distanceMeters / 1000)} km")
                Text("Dauer: ${ride.durationMs / 60_000} min")
                Text("Ø ${"%.1f".format(ride.averageSpeedKmh)} km/h · max ${"%.1f".format(ride.maxSpeedKmh)} km/h")
                Text("Route: ${ride.routeId.ifEmpty { "keine" }}")
                if (ride.riderCalories > 0 || ride.horseCalories > 0) {
                    Text("Kalorien: Reiter ~${ride.riderCalories} kcal · Pferd ~${ride.horseCalories} kcal")
                }
                if (ride.avgHorseHrBpm > 0 || ride.recoveryHr2MinBpm > 0) {
                    Text(
                        "Pferde-Puls: Ø ${ride.avgHorseHrBpm} / Max ${ride.maxHorseHrBpm} bpm" +
                            if (ride.recoveryHr2MinBpm > 0) " · Erholung ${ride.recoveryHr2MinBpm} bpm" else "",
                    )
                }
                if (ride.ascentM > 0 || ride.descentM > 0) {
                    Text("ca. ${"%.0f".format(ride.ascentM)} m auf · ${"%.0f".format(ride.descentM)} m ab")
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { renaming = true }) { Text("Benennen") }
            OutlinedButton(onClick = { viewModel.setVisibility(if (ride.visibility == "public") "private" else "public") }) {
                Text(if (ride.visibility == "public") "Privat machen" else "Öffentlich machen")
            }
            OutlinedButton(onClick = { viewModel.deleteRide(onDone = onDeleted) }) { Text("Ritt löschen") }
        }

        Text("Ereignisse", style = MaterialTheme.typography.titleMedium)
        Button(onClick = { adding = true }) { Text("Ereignis hinzufügen") }

        if (state.events.isEmpty()) {
            Text("Keine Ereignisse.")
        }
        state.events.forEach { event ->
            Card(
                modifier = Modifier.fillMaxWidth().clickable { editing = event },
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text("${typeLabel(event.type)}: ${event.label}", style = MaterialTheme.typography.titleSmall)
                    if (event.note.isNotBlank()) Text(event.note)
                    Text(if (event.source == "AUTO") "automatisch erkannt" else "von Hand")
                }
            }
        }
    }

    if (adding) {
        // New events go to the tapped point; without a tap, the track's middle point.
        EventDialog(
            title = "Ereignis hinzufügen",
            initialType = eventTypes.first().first,
            initialLabel = eventTypes.first().second,
            initialNote = "",
            onDismiss = { adding = false },
            onSave = { type, label, note ->
                val index = state.pickedIndex ?: (state.track.size / 2)
                viewModel.addEventAt(index, type, label, note)
                viewModel.pickTrackIndex(null)
                adding = false
            },
            onDelete = null,
        )
    }

    editing?.let { event ->
        EventDialog(
            title = "Ereignis bearbeiten",
            initialType = event.type,
            initialLabel = event.label,
            initialNote = event.note,
            onDismiss = { editing = null },
            onSave = { type, label, note ->
                viewModel.updateEvent(event, type, label, note)
                editing = null
            },
            onDelete = {
                viewModel.deleteEvent(event)
                editing = null
            },
        )
    }

    if (renaming && ride != null) {
        var name by remember(ride.id) { mutableStateOf(ride.name) }
        var note by remember(ride.id) { mutableStateOf(ride.note) }
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text("Ritt benennen") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true)
                    OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("Notiz") })
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.rename(name, note)
                    renaming = false
                }) { Text("Speichern") }
            },
            dismissButton = { OutlinedButton(onClick = { renaming = false }) { Text("Abbrechen") } },
        )
    }
}

@Composable
private fun EventDialog(
    title: String,
    initialType: String,
    initialLabel: String,
    initialNote: String,
    onDismiss: () -> Unit,
    onSave: (type: String, label: String, note: String) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var type by remember { mutableStateOf(initialType) }
    var label by remember { mutableStateOf(initialLabel) }
    var note by remember { mutableStateOf(initialNote) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                eventTypes.chunked(2).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        row.forEach { (key, name) ->
                            val iconInfo = MarkIcon.forKey(key)
                            val selected = type == key
                            val buttonContent: @Composable () -> Unit = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Icon(
                                        imageVector = markIconVector(iconInfo),
                                        contentDescription = name,
                                        tint = if (selected) MaterialTheme.colorScheme.onPrimary
                                        else Color(android.graphics.Color.parseColor(iconInfo.colorHex)),
                                    )
                                    Text(name, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                            if (selected) {
                                Button(
                                    modifier = Modifier.weight(1f),
                                    onClick = { type = key; label = name },
                                ) { buttonContent() }
                            } else {
                                OutlinedButton(
                                    modifier = Modifier.weight(1f),
                                    onClick = { type = key; label = name },
                                ) { buttonContent() }
                            }
                        }
                    }
                }
                OutlinedTextField(value = label, onValueChange = { label = it }, label = { Text("Bezeichnung") })
                OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("Notiz") })
            }
        },
        confirmButton = { Button(onClick = { onSave(type, label, note) }) { Text("Speichern") } },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (onDelete != null) OutlinedButton(onClick = onDelete) { Text("Löschen") }
                OutlinedButton(onClick = onDismiss) { Text("Abbrechen") }
            }
        },
    )
}
