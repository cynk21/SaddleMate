package com.example.saddlemate.ui.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.saddlemate.data.local.FeedbackNoteEntity
import com.example.saddlemate.data.local.PendingVoicePinDao
import com.example.saddlemate.data.local.PendingVoicePinEntity
import com.example.saddlemate.data.local.RideEventEntity
import com.example.saddlemate.data.mock.MockData
import com.example.saddlemate.data.mock.RouteCatalog
import com.example.saddlemate.data.repository.RideRepository
import com.example.saddlemate.domain.FeedbackStoryFormatter
import com.example.saddlemate.domain.GeoPoint
import com.example.saddlemate.domain.NavigationGuidance
import com.example.saddlemate.domain.PinClassifier
import com.example.saddlemate.sensors.NetworkState
import com.example.saddlemate.sensors.VoiceRecorder
import com.example.saddlemate.sensors.VoskTranscriber
import com.example.saddlemate.store.RideSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class VoicePinState(
    val recording: Boolean = false,
    /** Recognised text, editable before saving. */
    val draft: String = "",
    val message: String? = null,
    /** Pins recorded but not yet turned into text (shown as a waiting list). */
    val waiting: List<PendingVoicePinEntity> = emptyList(),
    /** Stored voice pins, newest first. */
    val pins: List<RideEventEntity> = emptyList(),
    /** Marks (non-voice) of the active horse, sorted along the selected route. */
    val routeMarks: List<RouteMarkItem> = emptyList(),
    /** General feedback & wish notes recorded by the rider for the backlog. */
    val wishes: List<FeedbackNoteEntity> = emptyList(),
    val wishRecording: Boolean = false,
    val wishMessage: String? = null,
    /** Whether the phone is online right now. */
    val online: Boolean = true,
    /** The active horse. Pins are stored for this horse and the list shows its pins. */
    val activeHorseId: String = "bella",
)

data class RouteMarkItem(
    val event: RideEventEntity,
    val alongMeters: Double?,
)

/**
 * Records a spoken pin offline, turns it into text with Vosk, and stores it as a map mark.
 * A pin whose text cannot be made is kept in a waiting list and retried at the next start.
 */
@HiltViewModel
class FeedbackViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val repository: RideRepository,
    private val pendingDao: PendingVoicePinDao,
    private val transcriber: VoskTranscriber,
    private val network: NetworkState,
    private val settings: RideSettings,
) : ViewModel() {

    /** Called with the saved-pin message so the screen can speak it. Set by the screen. */
    var onSaved: ((String) -> Unit)? = null

    private val recorder = VoiceRecorder(appContext.filesDir)
    private val _state = MutableStateFlow(VoicePinState())
    val state: StateFlow<VoicePinState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            network.watch()
            network.online.collect { online -> _state.value = _state.value.copy(online = online) }
        }
        viewModelScope.launch { retryWaiting() }
        // All voice pins, for every horse. They show up in the list as soon as they are saved.
        viewModelScope.launch {
            repository.observeVoicePins().collect { pins ->
                _state.value = _state.value.copy(pins = pins)
            }
        }
        // Marks along the active/selected route (sorted by distance along it) + voice pins.
        viewModelScope.launch {
            combine(
                settings.activeHorseId.flatMapLatest { id ->
                    val horse = id ?: MockData.horses.first().id
                    _state.value = _state.value.copy(activeHorseId = horse)
                    repository.observeNotes(horse)
                },
                repository.observeVoicePins(),
                settings.selectedRouteId,
            ) { notes, pins, routeId -> Triple(notes, pins, routeId) }.collect { (notes, pins, routeId) ->
                val route = repository.route(routeId ?: RouteCatalog.default.id)
                val items = (notes + pins)
                    .map { e ->
                        val along = NavigationGuidance.alongMeters(route, GeoPoint(e.lat, e.lon, e.timeMillis))
                        RouteMarkItem(e, along)
                    }
                    .sortedBy { it.alongMeters ?: Double.MAX_VALUE }
                _state.value = _state.value.copy(routeMarks = items)
            }
        }
        viewModelScope.launch {
            repository.observeFeedbackNotes().collect { notes ->
                _state.value = _state.value.copy(wishes = notes)
            }
        }
    }

    /** Adds a general feedback / wish note (typed or spoken). */
    fun addWish(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            repository.addFeedbackNote(trimmed)
            _state.value = _state.value.copy(wishMessage = "Wunsch gespeichert: \"$trimmed\"")
        }
    }

    fun deleteWish(note: FeedbackNoteEntity) {
        viewModelScope.launch { repository.deleteFeedbackNote(note) }
    }

    fun setWishPriority(note: FeedbackNoteEntity, priority: String) {
        viewModelScope.launch { repository.updateFeedbackNote(note.copy(priority = priority)) }
    }

    fun setWishStatus(note: FeedbackNoteEntity, status: String) {
        viewModelScope.launch { repository.updateFeedbackNote(note.copy(status = status)) }
    }

    fun editWish(note: FeedbackNoteEntity, text: String, userStory: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        val story = userStory.trim().ifBlank { FeedbackStoryFormatter.buildUserStory(trimmed) }
        viewModelScope.launch {
            repository.updateFeedbackNote(note.copy(text = trimmed, userStory = story))
        }
    }

    /** Formats all saved wishes as structured Markdown User Stories for the backlog/ticket system. */
    fun buildWishBacklogMarkdown(): String = FeedbackStoryFormatter.toMarkdownBacklog(
        _state.value.wishes.map {
            FeedbackStoryFormatter.FeedbackNoteInput(
                id = it.id,
                text = it.text,
                createdAtMillis = it.createdAtMillis,
                status = it.status,
                priority = it.priority,
                userStory = it.userStory,
            )
        },
    )

    fun startWishRecording() {
        if (_state.value.wishRecording || _state.value.recording) return
        runCatching { recorder.start() }
            .onSuccess { _state.value = _state.value.copy(wishRecording = true, wishMessage = "Ich nehme deinen Wunsch auf …") }
            .onFailure { _state.value = _state.value.copy(wishMessage = "Aufnahme nicht möglich: ${it.message}") }
    }

    fun stopWishRecording() {
        if (!_state.value.wishRecording) return
        val path = recorder.stop()
        _state.value = _state.value.copy(wishRecording = false)
        if (path == null) {
            _state.value = _state.value.copy(wishMessage = "Aufnahme konnte nicht gespeichert werden.")
            return
        }
        viewModelScope.launch {
            val text = runCatching { withContext(Dispatchers.Default) { transcriber.transcribe(path) } }.getOrNull()
            File(path).delete()
            if (text.isNullOrBlank()) {
                _state.value = _state.value.copy(wishMessage = "Nichts verstanden. Bitte nochmal oder tippen.")
            } else {
                repository.addFeedbackNote(text)
                _state.value = _state.value.copy(wishMessage = "Wunsch gespeichert: \"$text\"")
            }
        }
    }

    /** Starts recording. The screen checks the microphone permission first. */
    fun startRecording() {
        if (_state.value.recording) return
        runCatching { recorder.start() }
            .onSuccess { _state.value = _state.value.copy(recording = true, message = "Ich nehme auf …") }
            .onFailure { _state.value = _state.value.copy(message = "Aufnahme nicht möglich: ${it.message}") }
    }

    /**
     * Stops recording and stores the pin at [fix]. The audio is turned into text at once, fully
     * offline. If that fails, the pin waits in the list and is retried at the next start.
     */
    fun stopRecording(fix: GeoPoint?, horseId: String, nowMillis: Long) {
        if (!_state.value.recording) return
        val path = recorder.stop()
        _state.value = _state.value.copy(recording = false)
        if (path == null) {
            _state.value = _state.value.copy(message = "Aufnahme konnte nicht gespeichert werden.")
            return
        }
        if (fix == null || nowMillis - fix.timeMillis > MAX_FIX_AGE_MS) {
            File(path).delete()
            _state.value = _state.value.copy(
                message = "Noch kein aktuelles GPS-Signal. Bitte kurz warten und erneut aufnehmen.",
            )
            return
        }
        viewModelScope.launch {
            val pin = PendingVoicePinEntity(
                audioPath = path,
                lat = fix.lat,
                lon = fix.lon,
                timeMillis = fix.timeMillis,
                horseId = horseId,
            )
            val id = pendingDao.insert(pin)
            transcribeAndStore(pin.copy(id = id))
        }
    }

    fun editDraft(text: String) {
        _state.value = _state.value.copy(draft = text)
    }

    /** Changes the text of a stored voice pin. Empty text is refused. */
    fun editPin(pin: RideEventEntity, newText: String) {
        val text = newText.trim()
        if (text.isEmpty()) {
            _state.value = _state.value.copy(message = "Der Text ist leer.")
            return
        }
        viewModelScope.launch {
            repository.updateEvent(pin.copy(label = text))
            _state.value = _state.value.copy(message = "Pin geändert: \"$text\"")
        }
    }

    /** Deletes one stored voice pin. */
    fun deletePin(pin: RideEventEntity) {
        viewModelScope.launch {
            repository.deleteEvent(pin)
            _state.value = _state.value.copy(message = "Pin gelöscht")
        }
    }

    /** Deletes every stored voice pin. Used to clear test entries. */
    fun deleteAllPins() {
        viewModelScope.launch {
            repository.deleteAllVoicePins()
            _state.value = _state.value.copy(message = "Alle Sprach-Pins gelöscht")
        }
    }

    /** Stores the edited draft as a pin when the rider has changed the recognised text. */
    fun saveDraft() {
        val text = _state.value.draft.trim()
        if (text.isEmpty()) {
            _state.value = _state.value.copy(message = "Der Text ist leer.")
            return
        }
        _state.value = _state.value.copy(draft = "", message = "Gespeichert.")
    }

    /** Turns the audio of a pin into text and stores it as a voice pin. Failure keeps it waiting. */
    private suspend fun transcribeAndStore(pin: PendingVoicePinEntity) {
        val text = runCatching { withContext(Dispatchers.Default) { transcriber.transcribe(pin.audioPath) } }
            .getOrNull()
        if (text.isNullOrBlank()) {
            pendingDao.setStatus(pin.id, "FAILED")
            refreshWaiting()
            _state.value = _state.value.copy(message = "Pin wartet auf Umwandlung.")
            return
        }
        val iconType = PinClassifier.classify(text)
        repository.addVoicePin(
            RideEventEntity(
                rideId = "",
                lat = pin.lat,
                lon = pin.lon,
                timeMillis = pin.timeMillis,
                type = iconType.name,
                label = text,
                note = "",
                source = "MANUAL",
            ),
        )
        File(pin.audioPath).delete()
        pendingDao.delete(pin)
        refreshWaiting()
        // Clear feedback: the pin text and the word "gespeichert", shown and spoken.
        val msg = "Pin: \"$text\" gespeichert"
        _state.value = _state.value.copy(message = msg)
        onSaved?.invoke(msg)
    }

    /** At app start: turn every waiting pin into text, now that the model is available. */
    private suspend fun retryWaiting() {
        pendingDao.all().forEach { transcribeAndStore(it) }
        refreshWaiting()
    }

    private suspend fun refreshWaiting() {
        val all = pendingDao.all()
        _state.value = _state.value.copy(waiting = all)
    }

    private companion object {
        /** A GPS fix older than this is not used for a pin. */
        const val MAX_FIX_AGE_MS = 60_000L
    }
}
