package com.example.saddlemate.ui.screens

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.saddlemate.domain.Horse
import com.example.saddlemate.domain.RiderProfile
import com.example.saddlemate.domain.WeatherConditions
import com.example.saddlemate.domain.WeatherShield
import com.example.saddlemate.ui.viewmodels.HorseUiState
import com.example.saddlemate.ui.viewmodels.HorseViewModel
import java.io.File

@Composable
fun WelfareScreen(viewModel: HorseViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    WelfareContent(
        state = state,
        onSelect = viewModel::select,
        onSave = viewModel::save,
        onDelete = viewModel::delete,
        onSaveRider = viewModel::saveRiderProfile,
        onSaveEmergency = viewModel::saveEmergencyContact,
    )
}

@Composable
fun WelfareContent(
    state: HorseUiState,
    onSelect: (String) -> Unit,
    onSave: (Horse) -> Unit = {},
    onDelete: (String) -> Unit = {},
    onSaveRider: (RiderProfile) -> Unit = {},
    onSaveEmergency: (String, String) -> Unit = { _, _ -> },
) {
    var editing by remember { mutableStateOf<Horse?>(null) }
    var addingHorse by remember { mutableStateOf(false) }
    var editingRider by remember { mutableStateOf(false) }
    var editingEmergency by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<Horse?>(null) }
    var tempC by remember { mutableStateOf(18.0) }
    var humidityPct by remember { mutableStateOf(60) }
    var nightMinC by remember { mutableStateOf(4.0) }
    var sunny by remember { mutableStateOf(true) }

    val activeHorse = state.horses.firstOrNull { it.id == state.activeHorseId } ?: state.horses.firstOrNull()
    val weatherReport = remember(tempC, humidityPct, nightMinC, sunny, activeHorse?.hasLaminitisHistory) {
        WeatherShield.evaluate(
            WeatherConditions(tempC, humidityPct, nightMinC, sunny),
            hasLaminitisHistory = activeHorse?.hasLaminitisHistory == true,
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Reiter & Pferde", style = MaterialTheme.typography.headlineMedium)

        RiderProfileCard(
            profile = state.riderProfile,
            onEdit = { editingRider = true },
        )

        EmergencyContactCard(
            name = state.emergencyContact.first,
            phone = state.emergencyContact.second,
            onEdit = { editingEmergency = true },
        )

        WeatherShieldCard(
            tempC = tempC,
            nightMinC = nightMinC,
            sunny = sunny,
            report = weatherReport,
            onTempStep = { delta -> tempC = (tempC + delta).coerceIn(-5.0, 40.0) },
            onNightStep = { delta -> nightMinC = (nightMinC + delta).coerceIn(-10.0, 25.0) },
            onToggleSunny = { sunny = !sunny },
        )

        ManualPulseCounterCard()

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Pferde-Steckbriefe", style = MaterialTheme.typography.titleMedium)
            OutlinedButton(onClick = { addingHorse = true }) { Text("+ Pferd anlegen") }
        }
        state.horses.forEach { horse ->
            HorseCard(
                horse = horse,
                selected = horse.id == state.activeHorseId,
                onSelect = { onSelect(horse.id) },
                onEdit = { editing = horse },
                onDelete = { confirmDelete = horse },
            )
        }
    }

    if (addingHorse) {
        HorseEditDialog(
            horse = Horse(
                id = "horse-${System.currentTimeMillis()}",
                name = "",
                ageYears = 6,
                hasLaminitisHistory = false,
                kmSinceFarrier = 0.0,
                farrierIntervalKm = 500.0,
            ),
            onDismiss = { addingHorse = false },
            onSave = { newHorse ->
                onSave(newHorse)
                onSelect(newHorse.id)
                addingHorse = false
            },
        )
    }

    editing?.let { horse ->
        HorseEditDialog(
            horse = horse,
            onDismiss = { editing = null },
            onSave = { updated ->
                onSave(updated)
                editing = null
            },
        )
    }

    confirmDelete?.let { horse ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("${horse.name} löschen?") },
            text = { Text("Das Pferd und alle seine Ritte werden endgültig gelöscht.") },
            confirmButton = {
                Button(onClick = {
                    onDelete(horse.id)
                    confirmDelete = null
                }) { Text("Löschen") }
            },
            dismissButton = { OutlinedButton(onClick = { confirmDelete = null }) { Text("Abbrechen") } },
        )
    }

    if (editingRider) {
        RiderEditDialog(
            profile = state.riderProfile,
            onDismiss = { editingRider = false },
            onSave = { updated ->
                onSaveRider(updated)
                editingRider = false
            },
        )
    }

    if (editingEmergency) {
        EmergencyEditDialog(
            initialName = state.emergencyContact.first,
            initialPhone = state.emergencyContact.second,
            onDismiss = { editingEmergency = false },
            onSave = { n, p ->
                onSaveEmergency(n, p)
                editingEmergency = false
            },
        )
    }
}

@Composable
private fun EmergencyContactCard(name: String, phone: String, onEdit: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Sturz-Notfallkontakt (Dummy-SOS)", style = MaterialTheme.typography.titleMedium)
            Text(
                if (name.isBlank() && phone.isBlank()) "Noch kein Kontakt hinterlegt (Dummy-Test aktiv)"
                else "$name · $phone",
            )
            OutlinedButton(onClick = onEdit) { Text("Notfallkontakt bearbeiten") }
        }
    }
}

@Composable
private fun WeatherShieldCard(
    tempC: Double,
    nightMinC: Double,
    sunny: Boolean,
    report: com.example.saddlemate.domain.WeatherShieldReport,
    onTempStep: (Double) -> Unit,
    onNightStep: (Double) -> Unit,
    onToggleSunny: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Fruktan- & Hitzestress-Ampel", style = MaterialTheme.typography.titleMedium)
            Text(
                "Hitze: ${report.heatRisk.name} (TSI ${"%.1f".format(report.thermalStressIndex)}) · " +
                    "Fruktan: ${report.fructanRisk.name}",
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = { onTempStep(-3.0) }) { Text("-3°C") }
                OutlinedButton(onClick = { onTempStep(3.0) }) { Text("${tempC.toInt()}°C +3") }
                OutlinedButton(onClick = { onNightStep(-3.0) }) { Text("Nacht ${nightMinC.toInt()}°C -3") }
                OutlinedButton(onClick = onToggleSunny) { Text(if (sunny) "Sonne" else "Bewölkt") }
            }
            report.warnings.forEach { w -> Text("• $w", style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
private fun ManualPulseCounterCard() {
    var running by remember { mutableStateOf(false) }
    var secLeft by remember { mutableStateOf(com.example.saddlemate.domain.VitalMonitor.PULSE_WINDOW_SEC) }
    var taps by remember { mutableStateOf(0) }
    var lastBpm by remember { mutableStateOf<Int?>(null) }

    androidx.compose.runtime.LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        while (secLeft > 0) {
            kotlinx.coroutines.delay(1_000L)
            secLeft -= 1
        }
        running = false
        lastBpm = com.example.saddlemate.domain.VitalMonitor.bpmFrom15SecTaps(taps)
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("15-Sekunden-Stall-Pulszähler", style = MaterialTheme.typography.titleMedium)
            Text(
                "Für sofortige Nutzung ohne Bluetooth-Gurt: Start drücken und bei jedem Herzschlag / Atemzug tippen.",
                style = MaterialTheme.typography.bodySmall,
            )
            if (running) {
                Text("Noch $secLeft s · $taps Schläge", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { taps += 1 },
                        modifier = Modifier.weight(1f),
                    ) { Text("+ Schlag tippen ($taps)") }
                    OutlinedButton(onClick = {
                        running = false
                        lastBpm = com.example.saddlemate.domain.VitalMonitor.bpmFrom15SecTaps(taps)
                    }) { Text("Fertig") }
                }
            } else {
                lastBpm?.let { bpm ->
                    val ok = com.example.saddlemate.domain.VitalMonitor.isVetGatePassed(bpm)
                    Text(
                        "${com.example.saddlemate.domain.VitalMonitor.zoneLabel(bpm)} · " +
                            if (ok) "Vet-Gate OK (< 64)" else "Über 64 bpm",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                Button(
                    onClick = {
                        taps = 0
                        secLeft = com.example.saddlemate.domain.VitalMonitor.PULSE_WINDOW_SEC
                        running = true
                    },
                ) { Text("15-s-Messung starten") }
            }
        }
    }
}

@Composable
private fun EmergencyEditDialog(
    initialName: String,
    initialPhone: String,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var phone by remember { mutableStateOf(initialPhone) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sturz-Notfallkontakt") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true)
                OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Telefonnummer") }, singleLine = true)
            }
        },
        confirmButton = { Button(onClick = { onSave(name, phone) }) { Text("Speichern") } },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

@Composable
private fun RiderProfileCard(profile: RiderProfile, onEdit: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                profile.name.ifBlank { "Reiter-Profil" },
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                "${profile.weightKg.toInt()} kg · ${profile.heightCm} cm · ${profile.ageYears} Jahre",
            )
            Text(
                "Grundlage für Kalorienberechnung beim Ritt (Reiter & Pferd).",
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedButton(onClick = onEdit) { Text("Reiter-Profil bearbeiten") }
        }
    }
}

@Composable
private fun RiderEditDialog(
    profile: RiderProfile,
    onDismiss: () -> Unit,
    onSave: (RiderProfile) -> Unit,
) {
    var name by remember { mutableStateOf(profile.name) }
    var weight by remember { mutableStateOf(profile.weightKg.toInt().toString()) }
    var height by remember { mutableStateOf(profile.heightCm.toString()) }
    var age by remember { mutableStateOf(profile.ageYears.toString()) }

    val w = weight.toDoubleOrNull()
    val h = height.toIntOrNull()
    val a = age.toIntOrNull()
    val valid = w != null && w in 25.0..250.0 && h != null && h in 100..230 && a != null && a in 5..110

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reiter-Profil") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true)
                OutlinedTextField(value = weight, onValueChange = { weight = it }, label = { Text("Gewicht (kg)") }, singleLine = true)
                OutlinedTextField(value = height, onValueChange = { height = it }, label = { Text("Größe (cm)") }, singleLine = true)
                OutlinedTextField(value = age, onValueChange = { age = it }, label = { Text("Alter (Jahre)") }, singleLine = true)
            }
        },
        confirmButton = {
            Button(
                enabled = valid,
                onClick = { onSave(RiderProfile(name.trim(), w!!, h!!, a!!)) },
            ) { Text("Speichern") }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

@Composable
private fun HorseCard(
    horse: Horse,
    selected: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val bitmap = remember(horse.photoPath) {
        if (horse.photoPath.isNotBlank() && File(horse.photoPath).exists()) {
            BitmapFactory.decodeFile(horse.photoPath)?.asImageBitmap()
        } else null
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = horse.name,
                        modifier = Modifier.size(64.dp).clip(CircleShape),
                        contentScale = ContentScale.Crop,
                    )
                }
                Column {
                    Text(horse.name, style = MaterialTheme.typography.titleLarge)
                    if (horse.breed.isNotBlank()) {
                        Text(horse.breed, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            Text("Alter: ${horse.ageYears} Jahre" + if (horse.isYoung) " (Jungpferd)" else "")
            if (horse.weightKg > 0 || horse.heightCm > 0) {
                val parts = buildList {
                    if (horse.weightKg > 0) add("${horse.weightKg.toInt()} kg")
                    if (horse.heightCm > 0) add("${horse.heightCm} cm Stockmaß")
                }
                Text(parts.joinToString(" · "))
            }
            if (horse.hasLaminitisHistory) Text("Rehe-Historie beachten")
            Text("Hufschmied in ${"%.0f".format(horse.farrierDueInKm)} km fällig")
            if (horse.notes.isNotBlank()) {
                Text(horse.notes, style = MaterialTheme.typography.bodySmall)
            }
            if (selected) {
                Button(onClick = onSelect) { Text("Aktives Pferd") }
            } else {
                OutlinedButton(onClick = onSelect) { Text("Als aktives Pferd wählen") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onEdit) { Text("Steckbrief bearbeiten") }
                OutlinedButton(onClick = onDelete) { Text("Löschen") }
            }
        }
    }
}

@Composable
private fun HorseEditDialog(horse: Horse, onDismiss: () -> Unit, onSave: (Horse) -> Unit) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(horse.name) }
    var breed by remember { mutableStateOf(horse.breed) }
    var age by remember { mutableStateOf(horse.ageYears.toString()) }
    var weight by remember { mutableStateOf(if (horse.weightKg > 0) horse.weightKg.toInt().toString() else "") }
    var height by remember { mutableStateOf(if (horse.heightCm > 0) horse.heightCm.toString() else "") }
    var interval by remember { mutableStateOf(horse.farrierIntervalKm.toInt().toString()) }
    var laminitis by remember { mutableStateOf(horse.hasLaminitisHistory) }
    var notes by remember { mutableStateOf(horse.notes) }
    var photoPath by remember { mutableStateOf(horse.photoPath) }

    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? ->
        if (uri != null) {
            copyPhotoToAppStorage(context, uri, horse.id)?.let { photoPath = it }
        }
    }

    val ageValue = age.toIntOrNull()
    val intervalValue = interval.toDoubleOrNull()
    val weightValue = if (weight.isBlank()) 0.0 else weight.toDoubleOrNull()
    val heightValue = if (height.isBlank()) 0 else height.toIntOrNull()
    val valid = name.isNotBlank() && ageValue != null && ageValue in 0..60 &&
        intervalValue != null && intervalValue > 0 &&
        weightValue != null && weightValue >= 0 &&
        heightValue != null && heightValue >= 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Steckbrief: ${horse.name}") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val preview = remember(photoPath) {
                    if (photoPath.isNotBlank() && File(photoPath).exists()) {
                        BitmapFactory.decodeFile(photoPath)?.asImageBitmap()
                    } else null
                }
                if (preview != null) {
                    Image(
                        bitmap = preview,
                        contentDescription = name,
                        modifier = Modifier.fillMaxWidth().height(140.dp),
                        contentScale = ContentScale.Crop,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            photoPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                            )
                        },
                    ) { Text(if (photoPath.isBlank()) "Foto wählen" else "Foto ändern") }
                    if (photoPath.isNotBlank()) {
                        OutlinedButton(onClick = { photoPath = "" }) { Text("Foto entfernen") }
                    }
                }
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true)
                OutlinedTextField(value = breed, onValueChange = { breed = it }, label = { Text("Rasse") }, singleLine = true)
                OutlinedTextField(value = age, onValueChange = { age = it }, label = { Text("Alter (Jahre)") }, singleLine = true)
                OutlinedTextField(value = weight, onValueChange = { weight = it }, label = { Text("Gewicht (kg)") }, singleLine = true)
                OutlinedTextField(value = height, onValueChange = { height = it }, label = { Text("Stockmaß (cm)") }, singleLine = true)
                OutlinedTextField(
                    value = interval,
                    onValueChange = { interval = it },
                    label = { Text("Hufschmied-Intervall (km)") },
                    singleLine = true,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = laminitis, onCheckedChange = { laminitis = it })
                    Text("Rehe-Historie")
                }
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Besonderheiten / Notizen") })
            }
        },
        confirmButton = {
            Button(
                enabled = valid,
                onClick = {
                    onSave(
                        horse.copy(
                            name = name.trim(),
                            breed = breed.trim(),
                            ageYears = ageValue!!,
                            weightKg = weightValue!!,
                            heightCm = heightValue!!,
                            farrierIntervalKm = intervalValue!!,
                            hasLaminitisHistory = laminitis,
                            notes = notes.trim(),
                            photoPath = photoPath,
                        ),
                    )
                },
            ) { Text("Speichern") }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

/** Copies a picked gallery photo into internal app files so it stays readable after reboot. */
private fun copyPhotoToAppStorage(context: Context, uri: Uri, horseId: String): String? =
    runCatching {
        val dir = File(context.filesDir, "horse_photos").apply { mkdirs() }
        val target = File(dir, "$horseId-${System.currentTimeMillis()}.jpg")
        context.contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        } ?: return null
        target.absolutePath
    }.getOrNull()
