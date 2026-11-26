package com.example.saddlemate.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.OutlinedTextField
import com.example.saddlemate.data.local.RideEventEntity
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.Color
import com.example.saddlemate.domain.FeedbackBacklog
import com.example.saddlemate.domain.FeedbackItem
import com.example.saddlemate.domain.MarkIcon
import com.example.saddlemate.domain.Severity
import com.example.saddlemate.ui.map.markIconVector
import com.example.saddlemate.ui.viewmodels.FeedbackViewModel
import com.example.saddlemate.ui.viewmodels.RideViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Demo backlog until voice pins are stored in Firestore. */
private val demoFeedback = listOf(
    FeedbackItem("f1", "Tor am Waldweg schwer zu öffnen", Severity.MEDIUM, 3),
    FeedbackItem("f2", "Hund hinter dem Zaun, Ost-Abschnitt", Severity.HIGH, 5),
    FeedbackItem("f3", "Wegmarkierung fehlt bei der Gabelung", Severity.LOW, 1),
)

@Composable
fun FeedbackScreen(
    viewModel: FeedbackViewModel = hiltViewModel(),
    rideViewModel: RideViewModel = rideViewModelShared(),
) {
    val state by viewModel.state.collectAsState()
    val rideState by rideViewModel.state.collectAsState()
    val context = LocalContext.current
    val ranked = FeedbackBacklog.rank(demoFeedback)
    var editing by remember { mutableStateOf<RideEventEntity?>(null) }
    var confirmDelete by remember { mutableStateOf<RideEventEntity?>(null) }
    var confirmDeleteAll by remember { mutableStateOf(false) }

    // Saved pins are announced by voice as well as shown on screen.
    androidx.compose.runtime.SideEffect {
        viewModel.onSaved = { msg -> rideViewModel.announce(msg) }
    }

    // Recording needs the microphone. Ask first, then the rider holds the button to speak.
    val micLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted: Boolean ->
        if (granted) viewModel.startRecording()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Feedback", style = MaterialTheme.typography.headlineMedium)

        // General app feedback / feature wishes for the backlog. Separate from the GPS voice pin below.
        var wishDraft by remember { mutableStateOf("") }
        val hasMicForWish by rememberUpdatedState(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED,
        )
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Allgemeines Feedback & Wünsche", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Für Ideen, Wünsche oder Fehler zur App (landet im Backlog-Speicher, nicht auf der Karte).",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    value = wishDraft,
                    onValueChange = { wishDraft = it },
                    label = { Text("Wunsch oder Feedback eintippen") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            viewModel.addWish(wishDraft)
                            wishDraft = ""
                        },
                        enabled = wishDraft.isNotBlank(),
                    ) { Text("Speichern") }
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onPress = {
                                        if (!hasMicForWish) {
                                            micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                            return@detectTapGestures
                                        }
                                        viewModel.startWishRecording()
                                        tryAwaitRelease()
                                        viewModel.stopWishRecording()
                                    },
                                )
                            },
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Text(
                                if (state.wishRecording) "Loslassen …" else "Gedrückt halten & einsprechen",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        }
                    }
                }
                state.wishMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Sprach-Pin", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (state.online) "Online. Die Aufnahme wird sofort in Text umgewandelt."
                    else "Ohne Internet. Die Umwandlung läuft trotzdem offline auf dem Gerät.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    "Gedrückt halten zum Sprechen, loslassen zum Speichern. Der Pin liegt an deiner GPS-Position.",
                    style = MaterialTheme.typography.bodySmall,
                )

                // Hold to record, release to stop and store at the current GPS position.
                // A plain surface, not a Button: a Button consumes the press itself. Reading the
                // latest values through rememberUpdatedState keeps the gesture from going stale.
                val currentFix by rememberUpdatedState(rideState.trail.lastOrNull() ?: rideState.ownPosition)
                // The active horse, the same one the list reads. Not the running ride's horse.
                val currentHorse by rememberUpdatedState(state.activeHorseId)
                val hasMic by rememberUpdatedState(
                    ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                        PackageManager.PERMISSION_GRANTED,
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
                                    viewModel.startRecording()
                                    tryAwaitRelease()
                                    viewModel.stopRecording(
                                        fix = currentFix,
                                        horseId = currentHorse,
                                        nowMillis = System.currentTimeMillis(),
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
        }

        if (state.waiting.isNotEmpty()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "Wartet auf Umwandlung (${state.waiting.size})",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    val fmt = SimpleDateFormat("dd.MM. HH:mm", Locale.GERMANY)
                    state.waiting.forEach { pin ->
                        Text("Aufgenommen ${fmt.format(Date(pin.timeMillis))}, ${pin.status}")
                    }
                    Text(
                        "Wird beim nächsten Start der App erneut umgewandelt.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        if (state.pins.isNotEmpty()) {
            Text("Sprach-Pins (${state.pins.size})", style = MaterialTheme.typography.titleMedium)
            OutlinedButton(onClick = { confirmDeleteAll = true }) { Text("Alle Sprach-Pins löschen") }
            val fmt = SimpleDateFormat("dd.MM. HH:mm", Locale.GERMANY)
            state.pins.forEach { pin ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(pin.label, style = MaterialTheme.typography.bodyLarge)
                        Text("Gesprochen ${fmt.format(Date(pin.timeMillis))}")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { editing = pin }) { Text("Bearbeiten") }
                            OutlinedButton(onClick = { confirmDelete = pin }) { Text("Löschen") }
                        }
                    }
                }
            }
        }

        editing?.let { pin ->
            PinEditDialog(
                pin = pin,
                onDismiss = { editing = null },
                onSave = { text ->
                    viewModel.editPin(pin, text)
                    editing = null
                },
            )
        }

        confirmDelete?.let { pin ->
            AlertDialog(
                onDismissRequest = { confirmDelete = null },
                title = { Text("Pin löschen?") },
                text = { Text("\"${pin.label}\" wird endgültig gelöscht.") },
                confirmButton = {
                    Button(onClick = {
                        viewModel.deletePin(pin)
                        confirmDelete = null
                    }) { Text("Löschen") }
                },
                dismissButton = { OutlinedButton(onClick = { confirmDelete = null }) { Text("Abbrechen") } },
            )
        }

        if (confirmDeleteAll) {
            AlertDialog(
                onDismissRequest = { confirmDeleteAll = false },
                title = { Text("Alle Sprach-Pins löschen?") },
                text = { Text("Alle gespeicherten Sprach-Pins werden endgültig gelöscht.") },
                confirmButton = {
                    Button(onClick = {
                        viewModel.deleteAllPins()
                        confirmDeleteAll = false
                    }) { Text("Alle löschen") }
                },
                dismissButton = { OutlinedButton(onClick = { confirmDeleteAll = false }) { Text("Abbrechen") } },
            )
        }
        if (state.routeMarks.isNotEmpty()) {
            Text("Markierungen entlang der Route (${state.routeMarks.size})", style = MaterialTheme.typography.titleMedium)
            state.routeMarks.forEach { item ->
                val iconInfo = MarkIcon.forKey(item.event.type)
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            imageVector = markIconVector(iconInfo),
                            contentDescription = item.event.label,
                            tint = Color(android.graphics.Color.parseColor(iconInfo.colorHex)),
                        )
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(item.event.label, style = MaterialTheme.typography.bodyLarge)
                            val distText = item.alongMeters?.let { m ->
                                if (m >= 1000.0) "Bei ${"%.1f".format(m / 1000.0)} km auf der Route"
                                else "Bei ${m.toInt()} m auf der Route"
                            } ?: "Abseits der Route"
                            Text(distText, style = MaterialTheme.typography.bodySmall)
                        }
                        OutlinedButton(onClick = { editing = item.event }) { Text("Bearbeiten") }
                        OutlinedButton(onClick = { confirmDelete = item.event }) { Text("Löschen") }
                    }
                }
            }
        }

        if (state.wishes.isNotEmpty()) {
            val wishFmt = SimpleDateFormat("dd.MM. HH:mm", Locale.GERMANY)
            val clipboard = LocalClipboardManager.current
            var showBacklogPreview by remember { mutableStateOf(false) }
            var editingWish by remember { mutableStateOf<com.example.saddlemate.data.local.FeedbackNoteEntity?>(null) }

            Text("Backlog-Tickets & Wünsche (${state.wishes.size})", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(viewModel.buildWishBacklogMarkdown()))
                        showBacklogPreview = true
                    },
                ) { Text("Als User-Stories kopieren") }
            }
            if (showBacklogPreview) {
                AlertDialog(
                    onDismissRequest = { showBacklogPreview = false },
                    title = { Text("Backlog-Vorschau (kopiert)") },
                    text = {
                        Text(
                            viewModel.buildWishBacklogMarkdown(),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.verticalScroll(rememberScrollState()),
                        )
                    },
                    confirmButton = { Button(onClick = { showBacklogPreview = false }) { Text("OK") } },
                )
            }
            state.wishes.forEach { wish ->
                val story = wish.userStory.ifBlank {
                    com.example.saddlemate.domain.FeedbackStoryFormatter.buildUserStory(wish.text)
                }
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text("TICKET-${wish.id} · ${wish.text}", style = MaterialTheme.typography.titleSmall)
                        Text(story, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "${wishFmt.format(Date(wish.createdAtMillis))} · Status: ${wish.status} · Priorität: ${wish.priority}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("HIGH" to "Hoch", "MEDIUM" to "Mittel", "LOW" to "Niedrig").forEach { (key, label) ->
                                if (wish.priority == key) {
                                    Button(onClick = { viewModel.setWishPriority(wish, key) }) {
                                        Text(label, style = MaterialTheme.typography.labelSmall)
                                    }
                                } else {
                                    OutlinedButton(onClick = { viewModel.setWishPriority(wish, key) }) {
                                        Text(label, style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            val nextStatus = when (wish.status) {
                                "NEW" -> "BACKLOG"
                                "BACKLOG" -> "IN_PROGRESS"
                                "IN_PROGRESS" -> "DONE"
                                else -> "NEW"
                            }
                            OutlinedButton(onClick = { viewModel.setWishStatus(wish, nextStatus) }) {
                                Text("Status → $nextStatus", style = MaterialTheme.typography.labelSmall)
                            }
                            OutlinedButton(onClick = { editingWish = wish }) {
                                Text("Story bearbeiten", style = MaterialTheme.typography.labelSmall)
                            }
                            OutlinedButton(onClick = { viewModel.deleteWish(wish) }) {
                                Text("Löschen", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }

            editingWish?.let { wish ->
                var editText by remember(wish.id) { mutableStateOf(wish.text) }
                var editStory by remember(wish.id) {
                    mutableStateOf(
                        wish.userStory.ifBlank {
                            com.example.saddlemate.domain.FeedbackStoryFormatter.buildUserStory(wish.text)
                        },
                    )
                }
                AlertDialog(
                    onDismissRequest = { editingWish = null },
                    title = { Text("Ticket TICKET-${wish.id} bearbeiten") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = editText,
                                onValueChange = { editText = it },
                                label = { Text("Originalnotiz") },
                            )
                            OutlinedTextField(
                                value = editStory,
                                onValueChange = { editStory = it },
                                label = { Text("User Story") },
                            )
                        }
                    },
                    confirmButton = {
                        Button(onClick = {
                            viewModel.editWish(wish, editText, editStory)
                            editingWish = null
                        }) { Text("Speichern") }
                    },
                    dismissButton = { OutlinedButton(onClick = { editingWish = null }) { Text("Abbrechen") } },
                )
            }
        }

        // Supabase Cloud-Sync (uses supabase/migrations/001_initial_schema.sql; works offline when unconfigured)
        Card(modifier = Modifier.fillMaxWidth()) {
            var showSyncDialog by remember { mutableStateOf(false) }
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Supabase Cloud-Sync (Stufe 2)", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (state.supabaseUrl.isBlank()) "Lokal-Modus aktiv. Optional SUPABASE_URL & Anon-Key für Cloud-Sync hinterlegen."
                    else "Konfiguriert: ${state.supabaseUrl}",
                    style = MaterialTheme.typography.bodySmall,
                )
                state.syncStatus?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        enabled = !state.syncing,
                        onClick = { viewModel.syncWithSupabase() },
                    ) { Text(if (state.syncing) "Synchronisiere…" else "Jetzt synchronisieren") }
                    OutlinedButton(onClick = { showSyncDialog = true }) { Text("Konfigurieren") }
                }
            }
            if (showSyncDialog) {
                var urlDraft by remember(state.supabaseUrl) { mutableStateOf(state.supabaseUrl) }
                var keyDraft by remember(state.supabaseAnonKey) { mutableStateOf(state.supabaseAnonKey) }
                AlertDialog(
                    onDismissRequest = { showSyncDialog = false },
                    title = { Text("Supabase-Projekt verbinden") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = urlDraft,
                                onValueChange = { urlDraft = it },
                                label = { Text("SUPABASE_URL (https://...)") },
                                singleLine = true,
                            )
                            OutlinedTextField(
                                value = keyDraft,
                                onValueChange = { keyDraft = it },
                                label = { Text("SUPABASE_ANON_KEY") },
                            )
                        }
                    },
                    confirmButton = {
                        Button(onClick = {
                            viewModel.saveSupabaseConfig(urlDraft, keyDraft)
                            showSyncDialog = false
                        }) { Text("Speichern") }
                    },
                    dismissButton = { OutlinedButton(onClick = { showSyncDialog = false }) { Text("Abbrechen") } },
                )
            }
        }

        Text("Priorisierte Liste", style = MaterialTheme.typography.titleMedium)
        ranked.forEach { item ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(item.text, style = MaterialTheme.typography.bodyLarge)
                    Text("Priorität: ${item.severity.name} · ${item.reportCount} Meldungen")
                }
            }
        }
    }
}

/** Dialog to change the text of a stored voice pin. */
@Composable
private fun PinEditDialog(pin: RideEventEntity, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(pin.label) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pin bearbeiten") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Text des Pins") },
            )
        },
        confirmButton = { Button(onClick = { onSave(text) }) { Text("Speichern") } },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}
