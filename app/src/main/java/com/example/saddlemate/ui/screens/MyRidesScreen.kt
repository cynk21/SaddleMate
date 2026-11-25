package com.example.saddlemate.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import com.example.saddlemate.data.local.RideEntity
import com.example.saddlemate.ui.viewmodels.MyRidesViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** List of all stored rides. Tap opens the detail; the buttons rename or delete a ride. */
@Composable
fun MyRidesScreen(
    viewModel: MyRidesViewModel = hiltViewModel(),
    onOpenRide: (String) -> Unit,
    onShowOnMap: (String) -> Unit = {},
) {
    val rides by viewModel.rides.collectAsState(initial = emptyList())
    var editing by remember { mutableStateOf<RideEntity?>(null) }
    var confirmDelete by remember { mutableStateOf<RideEntity?>(null) }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Meine Ritte", style = MaterialTheme.typography.headlineMedium)
        if (rides.isEmpty()) {
            Text("Noch keine Ritte gespeichert.")
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(rides, key = { it.id }) { ride ->
                RideRow(
                    ride = ride,
                    onClick = { onOpenRide(ride.id) },
                    onShowOnMap = { onShowOnMap(ride.id) },
                    onEdit = { editing = ride },
                    onDelete = { confirmDelete = ride },
                )
            }
        }
    }

    editing?.let { ride ->
        RideRenameDialog(
            ride = ride,
            onDismiss = { editing = null },
            onSave = { name, note ->
                viewModel.rename(ride.id, name, note)
                editing = null
            },
        )
    }

    confirmDelete?.let { ride ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Ritt löschen?") },
            text = { Text("Der Ritt mit Weg und Ereignissen wird endgültig gelöscht.") },
            confirmButton = {
                Button(onClick = {
                    viewModel.delete(ride.id)
                    confirmDelete = null
                }) { Text("Löschen") }
            },
            dismissButton = { OutlinedButton(onClick = { confirmDelete = null }) { Text("Abbrechen") } },
        )
    }
}

/** Shows the ride's name, or its date when it has none. */
private fun RideEntity.displayName(): String =
    name.ifBlank { SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMANY).format(Date(startedAtMillis)) }

@Composable
private fun RideRow(
    ride: RideEntity,
    onClick: () -> Unit,
    onShowOnMap: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(ride.displayName(), style = MaterialTheme.typography.titleMedium)
            Text("Strecke: ${"%.2f".format(ride.distanceMeters / 1000)} km · Ø ${"%.1f".format(ride.averageSpeedKmh)} km/h")
            if (ride.note.isNotBlank()) Text(ride.note, style = MaterialTheme.typography.bodySmall)
            Text("Sichtbarkeit: ${if (ride.visibility == "public") "öffentlich" else "privat"}")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onShowOnMap) { Text("Auf Karte") }
                OutlinedButton(onClick = onEdit) { Text("Bearbeiten") }
                OutlinedButton(onClick = onDelete) { Text("Löschen") }
            }
        }
    }
}

@Composable
private fun RideRenameDialog(ride: RideEntity, onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var name by remember { mutableStateOf(ride.name) }
    var note by remember { mutableStateOf(ride.note) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ritt bearbeiten") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") })
                OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("Notiz") })
            }
        },
        confirmButton = { Button(onClick = { onSave(name, note) }) { Text("Speichern") } },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}
