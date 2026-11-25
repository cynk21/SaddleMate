package com.example.saddlemate.ui.viewmodels

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.saddlemate.data.local.RideEventEntity
import com.example.saddlemate.data.mock.MockData
import com.example.saddlemate.data.mock.RouteCatalog
import com.example.saddlemate.data.repository.CompletedRide
import com.example.saddlemate.data.repository.RideRepository
import com.example.saddlemate.data.repository.RideStore
import com.example.saddlemate.domain.AudioMode
import com.example.saddlemate.domain.CalorieEstimate
import com.example.saddlemate.domain.CalorieCalculator
import com.example.saddlemate.domain.Gait
import com.example.saddlemate.domain.GeoPoint
import com.example.saddlemate.domain.HazardMarker
import com.example.saddlemate.domain.HazardRadarEngine
import com.example.saddlemate.domain.Horse
import com.example.saddlemate.domain.RadarAlert
import com.example.saddlemate.domain.RideAnalysis
import com.example.saddlemate.domain.RelativeSide
import com.example.saddlemate.domain.TelemetryEngine
import com.example.saddlemate.domain.WelfareEngine
import com.example.saddlemate.domain.WelfareReport
import com.example.saddlemate.domain.NavigationGuidance
import com.example.saddlemate.domain.PinProximity
import com.example.saddlemate.domain.outputForAlert
import com.example.saddlemate.sensors.AlertFeedback
import com.example.saddlemate.sensors.RideService
import com.example.saddlemate.sensors.LocationSource
import com.example.saddlemate.store.RideSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class CockpitState(
    val running: Boolean = false,
    val elapsedSec: Int = 0,
    val distanceMeters: Double = 0.0,
    val currentSpeedKmh: Double = 0.0,
    val gait: Gait = Gait.STANDSTILL,
    val radar: List<RadarAlert> = emptyList(),
    val welfare: WelfareReport? = null,
    val horse: Horse? = null,
    val audioMode: AudioMode = AudioMode.FULL_COPILOT,
    val lastSummary: RideAnalysis? = null,
    val lastRideId: String? = null,
    /** A stored ride shown on the map, or null when none is shown. */
    val shownRideId: String? = null,
    /** The track of the chosen ride, drawn as a dashed route (no speed colours). */
    val shownTrack: List<GeoPoint> = emptyList(),
    /** The route the rider started as navigation target (stage 1: visual only). Null when none. */
    val navRouteId: String? = null,
    /** Short navigation line shown on the map: remaining distance, next turn, off-route. */
    val navHint: String? = null,
    /** Points of the current ride, shown as the coloured trail on the map. */
    val trail: List<GeoPoint> = emptyList(),
    /** Each hazard announced during this ride, once. Shown as markers on the map. */
    val radarEvents: List<RadarEvent> = emptyList(),
    /** Progress along the route, 0..1. */
    val routeProgress: Double = 0.0,
    /** Route chosen for the next (or current) ride. */
    val selectedRouteId: String = RouteCatalog.default.id,
    /** Own position from GPS, shown as a blue dot on the map. Null until the first fix. */
    val ownPosition: GeoPoint? = null,
    /** Route length in metres, for the progress text. */
    val routeLengthMeters: Double = 0.0,
    /** Estimated calories spent in the current/last ride. */
    val calories: CalorieEstimate = CalorieEstimate(0, 0),
    /** Short status banner on the cockpit (e.g. "Ritt gestartet"). */
    val bannerMessage: String? = null,
)

/** A hazard that triggered the radar during the ride, with where and when it happened. */
data class RadarEvent(
    val hazard: HazardMarker,
    val position: GeoPoint,
    val side: RelativeSide,
    val distanceMeters: Double,
)

@HiltViewModel
class RideViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val repository: RideRepository,
    private val rideStore: RideStore,
    private val settings: RideSettings,
    private val telemetry: TelemetryEngine,
    private val radar: HazardRadarEngine,
    private val welfareEngine: WelfareEngine,
    private val feedback: AlertFeedback,
    private val locationSource: LocationSource,
) : ViewModel() {

    private val _state = MutableStateFlow(CockpitState())
    val state: StateFlow<CockpitState> = _state.asStateFlow()

    /** Map marks shown on the map: the horse's notes (taken by tapping) and all voice pins. */
    val marks: StateFlow<List<RideEventEntity>> = combine(
        settings.activeHorseId.flatMapLatest { id -> repository.observeNotes(id ?: MockData.horses.first().id) },
        repository.observeVoicePins(),
    ) { notes, pins -> notes + pins }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private var job: Job? = null
    private val navCoach = NavigationGuidance.Coach()
    private val track = mutableListOf<GeoPoint>()
    private var rideStart = 0L
    private var warmupMs = 0L
    private var headingDeg = 0.0
    private val spokenAlerts = mutableSetOf<String>()
    /** Voice pins of the horse, loaded at ride start. Spoken once each when the rider is near. */
    private var voicePins: List<PinProximity.Pin> = emptyList()
    private val spokenPins = mutableSetOf<String>()
    private var routeLength = 0.0
    private var currentRideId: String? = null

    init {
        // Ensure horses are seeded, keep the active horse in CockpitState in sync,
        // and restore the remembered route + audio mode across app restarts.
        viewModelScope.launch {
            repository.seedIfEmpty()
            if (settings.activeHorseId.value == null) {
                settings.selectHorse(MockData.horses.first().id)
            }
        }
        viewModelScope.launch {
            settings.activeHorseId.collect { id ->
                val targetId = id ?: MockData.horses.first().id
                val horse = repository.horse(targetId) ?: MockData.horses.firstOrNull { it.id == targetId }
                if (horse != null && !_state.value.running) {
                    _state.value = _state.value.copy(horse = horse)
                }
            }
        }
        viewModelScope.launch {
            settings.selectedRouteId.collect { savedId ->
                val info = savedId?.let { RouteCatalog.byId(it) } ?: RouteCatalog.default
                val len = RouteCatalog.lengthMeters(repository.route(info.id))
                if (!_state.value.running) {
                    _state.value = _state.value.copy(selectedRouteId = info.id, routeLengthMeters = len)
                }
            }
        }
        viewModelScope.launch {
            settings.audioMode.collect { mode ->
                _state.value = _state.value.copy(audioMode = mode)
            }
        }
    }

    fun selectHorse(id: String?) = settings.selectHorse(id)

    /** Own position for the map, independent of a ride. Needs the location permission. */
    fun trackOwnPosition() {
        viewModelScope.launch {
            locationSource.fixes().collect { p ->
                _state.value = _state.value.copy(ownPosition = p)
                navigate(p)
            }
        }
    }

    /** Map mark taken by tapping the map: in the running ride, or in the horse's notes otherwise. */
    fun addMapMark(point: GeoPoint, type: String, label: String, note: String) {
        viewModelScope.launch {
            val event = RideEventEntity(
                rideId = "",
                lat = point.lat,
                lon = point.lon,
                timeMillis = point.timeMillis,
                type = type,
                label = label,
                note = note,
                source = "MANUAL",
            )
            // Without a chosen horse the first one is used, so a mark is never lost.
            val horseId = settings.activeHorseId.value ?: MockData.horses.first().id
            repository.addNote(horseId, event)
        }
    }

    /** Starts or stops the ride from the map. Uses the horse and route chosen in the cockpit settings. */
    fun toggleRide() {
        if (_state.value.running) {
            stop()
        } else {
            start(settings.activeHorseId.value ?: MockData.horses.first().id)
        }
    }

    fun setAudioMode(mode: AudioMode) {
        settings.setAudioMode(mode)
        _state.value = _state.value.copy(audioMode = mode)
    }

    /** Chooses the route for the next ride. Ignored while a ride is running. */
    fun selectRoute(routeId: String) {
        if (_state.value.running) return
        val info = RouteCatalog.byId(routeId) ?: return
        val length = RouteCatalog.lengthMeters(repository.route(info.id))
        settings.selectRoute(info.id)
        _state.value = _state.value.copy(selectedRouteId = info.id, routeLengthMeters = length)
    }

    fun start(horseId: String) {
        if (job?.isActive == true) return
        viewModelScope.launch {
            val horse = repository.horse(horseId) ?: return@launch
            feedback.init()
            track.clear()
            spokenAlerts.clear()
            spokenPins.clear()
            voicePins = repository.voicePins()
            announce("Ritt gestartet")
            rideStart = System.currentTimeMillis()
            warmupMs = 0L
            val routeId = _state.value.selectedRouteId
            routeLength = RouteCatalog.lengthMeters(repository.route(routeId))
            _state.value = CockpitState(
                running = true,
                horse = horse,
                audioMode = settings.audioMode.value,
                selectedRouteId = routeId,
                routeLengthMeters = routeLength,
                shownRideId = _state.value.shownRideId,
                shownTrack = _state.value.shownTrack,
                navRouteId = _state.value.navRouteId,
                navHint = _state.value.navHint,
                ownPosition = _state.value.ownPosition,
                bannerMessage = "Ritt gestartet",
            )
            // The ride id is fixed now, so the service can write each fix to this ride.
            // The ride row is created first; the service then writes fixes that reference it.
            val newId = UUID.randomUUID().toString()
            currentRideId = newId
            rideStore.begin(newId, horse.id, System.currentTimeMillis())
            ContextCompat.startForegroundService(appContext, Intent(appContext, RideService::class.java))
            job = viewModelScope.launch { runRide(horse) }
        }
    }

    /** Short feedback for the rider: shown on the cockpit and spoken. */
    fun announce(text: String) {
        _state.value = _state.value.copy(bannerMessage = text)
        feedback.init()
        feedback.deliver(settings.audioMode.value.outputForAlert(), text)
    }

    /**
     * Speaks the text of a voice pin the rider tapped on the map. Works outside a ride too:
     * the speech engine is started here if it is not ready yet.
     */
    fun speakMark(text: String) {
        feedback.init()
        feedback.deliver(settings.audioMode.value.outputForAlert(), text)
    }

    /**
     * Shows a stored ride on the map: its track, coloured by speed. Only when no ride runs,
     * so a running ride is never replaced.
     */
    fun showStoredRide(rideId: String) {
        if (_state.value.running) return
        viewModelScope.launch {
            val points = repository.track(rideId)
            // A chosen ride is a plain dashed route, not a speed-coloured track. Kept apart from state.trail.
            _state.value = _state.value.copy(
                shownTrack = points,
                shownRideId = rideId,
                navRouteId = null,
                navHint = null,
            )
        }
    }

    /**
     * Shows a catalogued route on the map so the rider can preview it and start navigation
     * along it without needing a previously recorded ride.
     */
    fun showCatalogRoute(routeId: String) {
        if (_state.value.running) return
        val info = RouteCatalog.byId(routeId) ?: return
        val points = repository.route(info.id)
        settings.selectRoute(info.id)
        _state.value = _state.value.copy(
            selectedRouteId = info.id,
            routeLengthMeters = RouteCatalog.lengthMeters(points),
            shownTrack = points,
            shownRideId = info.id,
            navRouteId = null,
            navHint = null,
        )
    }

    /** Changes the text of a map mark. Empty text is refused. */
    fun updateMark(mark: RideEventEntity, text: String) {
        val label = text.trim()
        if (label.isEmpty()) return
        viewModelScope.launch { repository.updateEvent(mark.copy(label = label)) }
    }

    /** Deletes a map mark. */
    fun deleteMark(mark: RideEventEntity) {
        viewModelScope.launch { repository.deleteEvent(mark) }
    }

    /**
     * Navigation along the chosen route: updates the hint on the map and speaks the messages
     * (turn ahead, remaining distance, arrival, off route). Runs for each own position fix.
     */
    private fun navigate(p: GeoPoint) {
        if (_state.value.navRouteId == null) return
        val route = _state.value.shownTrack
        val status = NavigationGuidance.status(route, p) ?: return
        _state.value = _state.value.copy(navHint = NavigationGuidance.hint(status))
        navCoach.messages(status).forEach { text -> announce(text) }
    }

    /** Starts navigation on the shown route. Stage 1: only the route is marked as the target. */
    fun startNavigation() {
        val id = _state.value.shownRideId ?: return
        navCoach.reset()
        _state.value = _state.value.copy(navRouteId = id)
        val p = _state.value.ownPosition
        if (p != null) navigate(p)
    }

    /** Stops the navigation target. */
    fun stopNavigation() {
        navCoach.reset()
        _state.value = _state.value.copy(navRouteId = null, navHint = null)
    }

    /** Removes the stored ride from the map again. */
    fun clearStoredRide() {
        if (_state.value.running) return
        _state.value = _state.value.copy(shownTrack = emptyList(), shownRideId = null)
    }

    /** Closes the summary of the last ride. */
    fun dismissSummary() {
        _state.value = _state.value.copy(lastSummary = null, lastRideId = null)
    }

    /** Renames the last finished ride and closes the summary popup. */
    fun renameLastRideAndDismiss(name: String) {
        val id = _state.value.lastRideId
        val trimmed = name.trim()
        if (id != null && trimmed.isNotEmpty()) {
            viewModelScope.launch { repository.renameRide(id, trimmed, "") }
        }
        dismissSummary()
    }

    fun stop() {
        job?.cancel()
        job = null
        appContext.startService(Intent(appContext, RideService::class.java).setAction(RideService.ACTION_STOP))
        viewModelScope.launch { finish() }
    }

    /**
     * Records the ride. The foreground service [RideService] keeps the GPS running with the
     * screen off; its fixes arrive here and go through [processPoint].
     */
    private suspend fun runRide(horse: Horse) {
        var elapsedSec = 0
        RideService.fixes.collect { point: GeoPoint ->
            processPoint(point, horse, elapsedSec++)
        }
    }

    private fun processPoint(point: GeoPoint, horse: Horse, elapsedSec: Int) {
        val previous = track.lastOrNull()
        track += point
        // Heading follows the track; on a curve the radar cone rotates with the rider.
        if (previous != null) headingDeg = radar.bearingDeg(previous, point)
        val analysis = telemetry.analyze(track.toList())
        val speedKmh = currentSpeedKmh(track)
        val alerts = radar.evaluate(point, headingDeg, repository.hazards(_state.value.selectedRouteId))
        val newEvents = announce(alerts, point)
        announceNearPins(point)
        val progress = if (routeLength > 0) (analysis.distanceMeters / routeLength).coerceIn(0.0, 1.0) else 0.0
        val calories = CalorieCalculator.estimate(
            gaitTimeMs = analysis.gaitTimeMs,
            riderWeightKg = settings.riderProfile.value.weightKg,
            horseWeightKg = horse.weightKg,
        )
        _state.value = _state.value.copy(
            elapsedSec = elapsedSec,
            distanceMeters = analysis.distanceMeters,
            currentSpeedKmh = speedKmh,
            gait = telemetry.inferGait(speedKmh),
            radar = alerts,
            welfare = welfareEngine.evaluate(analysis, horse, warmupMs, temperatureC = null),
            trail = track.toList(),
            radarEvents = _state.value.radarEvents + newEvents,
            routeProgress = progress,
            calories = calories,
        )
    }

    private fun currentSpeedKmh(points: List<GeoPoint>): Double {
        if (points.size < 2) return 0.0
        val a = points[points.size - 2]
        val b = points.last()
        val dt = (b.timeMillis - a.timeMillis) / 1000.0
        return if (dt > 0) telemetry.distanceMeters(a, b) / dt * 3.6 else 0.0
    }

    /** Speaks each voice pin within reach once per ride. */
    private fun announceNearPins(point: GeoPoint) {
        val due = PinProximity.dueNow(point, voicePins, spokenPins)
        for (pin in due) {
            spokenPins += pin.id
            feedback.deliver(settings.audioMode.value.outputForAlert(), pin.text)
        }
    }

    /** Announces each hazard once per ride and returns the events it created. */
    private fun announce(alerts: List<RadarAlert>, position: GeoPoint): List<RadarEvent> {
        val mode = settings.audioMode.value
        val events = mutableListOf<RadarEvent>()
        for (alert in alerts) {
            if (!spokenAlerts.add(alert.hazard.id)) continue
            val text = "${alert.hazard.label}, ${alert.side.toGerman()}, ${alert.distanceMeters.toInt()} Meter"
            feedback.deliver(mode.outputForAlert(), text)
            events += RadarEvent(
                hazard = alert.hazard,
                position = position,
                side = alert.side,
                distanceMeters = alert.distanceMeters,
            )
        }
        return events
    }

    private suspend fun finish() {
        if (!_state.value.running) return
        val analysis = telemetry.analyze(track.toList())
        val horse = _state.value.horse ?: return
        val welfare: WelfareReport = welfareEngine.evaluate(analysis, horse, warmupMs, temperatureC = null)
        // Same id as the one the service wrote the track under.
        val id = currentRideId ?: UUID.randomUUID().toString()
        rideStore.end()
        currentRideId = null
        val autoEvents = _state.value.radarEvents.map { event ->
            RideEventEntity(
                rideId = id,
                lat = event.position.lat,
                lon = event.position.lon,
                timeMillis = event.position.timeMillis,
                type = event.hazard.type.name,
                label = event.hazard.label,
                source = "AUTO",
            )
        }
        val calories = CalorieCalculator.estimate(
            gaitTimeMs = analysis.gaitTimeMs,
            riderWeightKg = settings.riderProfile.value.weightKg,
            horseWeightKg = horse.weightKg,
        )
        repository.saveRide(
            CompletedRide(
                id = id,
                horseId = horse.id,
                startedAtMillis = rideStart,
                analysis = analysis,
                welfare = welfare,
                samples = track.toList(),
                routeId = _state.value.selectedRouteId,
                events = autoEvents,
                riderCalories = calories.riderKcal,
                horseCalories = calories.horseKcal,
            ),
        )
        feedback.shutdown()
        _state.value = _state.value.copy(
            running = false,
            lastSummary = analysis,
            lastRideId = id,
            welfare = welfare,
            calories = calories,
            bannerMessage = "Ritt beendet",
        )
    }

    private fun RelativeSide.toGerman() = when (this) {
        RelativeSide.AHEAD -> "geradeaus"
        RelativeSide.LEFT -> "links"
        RelativeSide.RIGHT -> "rechts"
    }

}
