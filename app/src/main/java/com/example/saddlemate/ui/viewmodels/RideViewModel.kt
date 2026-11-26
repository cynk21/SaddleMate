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
import com.example.saddlemate.domain.CanterStretch
import com.example.saddlemate.domain.CanterStretchDetector
import com.example.saddlemate.domain.FallAlarm
import com.example.saddlemate.domain.FallDetector
import com.example.saddlemate.domain.Gait
import com.example.saddlemate.domain.GaitSymmetryTracker
import com.example.saddlemate.domain.GeoPoint
import com.example.saddlemate.domain.HazardMarker
import com.example.saddlemate.domain.HazardRadarEngine
import com.example.saddlemate.domain.Horse
import com.example.saddlemate.domain.RadarAlert
import com.example.saddlemate.domain.RideAnalysis
import com.example.saddlemate.domain.RelativeSide
import com.example.saddlemate.domain.SurfaceLoadTracker
import com.example.saddlemate.domain.TelemetryEngine
import com.example.saddlemate.domain.VoiceQaRouter
import com.example.saddlemate.domain.WelfareEngine
import com.example.saddlemate.domain.WelfareReport
import com.example.saddlemate.domain.NavigationGuidance
import com.example.saddlemate.domain.PinProximity
import com.example.saddlemate.domain.outputForAlert
import com.example.saddlemate.sensors.AlertFeedback
import com.example.saddlemate.sensors.ImpactSensor
import com.example.saddlemate.sensors.RideService
import com.example.saddlemate.sensors.LocationSource
import com.example.saddlemate.store.RideSettings
import kotlinx.coroutines.delay
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
    /** Warm-up walk progress (0..1) and cool-down minutes needed after fast work. */
    val warmupMinutes: Double = 0.0,
    val requiredWarmupMinutes: Double = 10.0,
    val cooldownNeededMinutes: Double = 0.0,
    /** Live trot symmetry percentage from IMU (100% = even). */
    val trotSymmetryPct: Double = 100.0,
    /** Surface load warning (e.g. fast trot on asphalt). */
    val surfaceWarning: String? = null,
    /** Detected >= 250 m canter stretches ready for 1-tap saving after the ride. */
    val detectedCanterStretches: List<CanterStretch> = emptyList(),
    /** Whether the current ride is running via the indoor Demo Ride Simulator. */
    val isSimulating: Boolean = false,
    /** Fall detection countdown state & seconds left (Dummy SOS). */
    val fallState: FallAlarm.State = FallAlarm.State.IDLE,
    val fallRemainingSec: Int = 0,
    val fallMessage: String? = null,
    /** Last spoken Q&A answer for the rider. */
    val lastQaAnswer: String? = null,
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
    private val impactSensor: ImpactSensor,
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
    private var sensorJob: Job? = null
    private var fallTimerJob: Job? = null
    private val navCoach = NavigationGuidance.Coach()
    private val fallDetector = FallDetector()
    private val fallAlarm = FallAlarm(countdownSec = 30)
    private val symmetryTracker = GaitSymmetryTracker()
    private val track = mutableListOf<GeoPoint>()
    private var rideStart = 0L
    private var warmupMs = 0L
    private var hasStartedFastWork = false
    private var cooldownDebtMs = 0L
    private var lastSurfaceWarnAtMs = 0L
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
            symmetryTracker.reset()
            fallDetector.reset()
            fallAlarm.reset()
            voicePins = repository.voicePins()
            announce("Ritt gestartet")
            rideStart = System.currentTimeMillis()
            warmupMs = 0L
            hasStartedFastWork = false
            cooldownDebtMs = 0L
            val routeId = _state.value.selectedRouteId
            routeLength = RouteCatalog.lengthMeters(repository.route(routeId))
            val reqWarmup = if (horse.isYoung) 15.0 else 10.0
            _state.value = CockpitState(
                running = true,
                horse = horse,
                audioMode = settings.audioMode.value,
                selectedRouteId = routeId,
                routeLengthMeters = routeLength,
                requiredWarmupMinutes = reqWarmup,
                shownRideId = _state.value.shownRideId,
                shownTrack = _state.value.shownTrack,
                navRouteId = _state.value.navRouteId,
                navHint = _state.value.navHint,
                ownPosition = _state.value.ownPosition,
                bannerMessage = "Ritt gestartet",
            )
            val newId = UUID.randomUUID().toString()
            currentRideId = newId
            rideStore.begin(newId, horse.id, System.currentTimeMillis())
            ContextCompat.startForegroundService(appContext, Intent(appContext, RideService::class.java))
            startMotionMonitoring()
            job = viewModelScope.launch { runRide(horse) }
        }
    }

    /**
     * Starts an accelerated indoor Demo Ride Simulator along the selected route so every display,
     * gait transition (walk -> trot -> canter -> walk), hazard radar, and welfare check can be
     * verified indoors without a horse.
     */
    fun startDemoSimulation() {
        if (job?.isActive == true) return
        viewModelScope.launch {
            val horseId = settings.activeHorseId.value ?: MockData.horses.first().id
            val horse = repository.horse(horseId) ?: return@launch
            feedback.init()
            track.clear()
            spokenAlerts.clear()
            spokenPins.clear()
            symmetryTracker.reset()
            fallDetector.reset()
            fallAlarm.reset()
            voicePins = repository.voicePins()
            announce("Demo-Ritt gestartet")
            rideStart = System.currentTimeMillis()
            warmupMs = 0L
            hasStartedFastWork = false
            cooldownDebtMs = 0L
            val routeId = _state.value.selectedRouteId
            val routePts = repository.route(routeId)
            routeLength = RouteCatalog.lengthMeters(routePts)
            val reqWarmup = if (horse.isYoung) 15.0 else 10.0
            _state.value = CockpitState(
                running = true,
                isSimulating = true,
                horse = horse,
                audioMode = settings.audioMode.value,
                selectedRouteId = routeId,
                routeLengthMeters = routeLength,
                requiredWarmupMinutes = reqWarmup,
                shownRideId = _state.value.shownRideId,
                shownTrack = _state.value.shownTrack,
                navRouteId = _state.value.navRouteId,
                navHint = _state.value.navHint,
                ownPosition = routePts.firstOrNull(),
                bannerMessage = "Demo-Ritt läuft (Schritt → Trab → Galopp)",
            )
            val newId = UUID.randomUUID().toString()
            currentRideId = newId
            rideStore.begin(newId, horse.id, rideStart)
            job = viewModelScope.launch { runDemoSimulation(horse, routePts) }
        }
    }

    private suspend fun runDemoSimulation(horse: Horse, routePts: List<GeoPoint>) {
        if (routePts.size < 2) return
        var simTime = rideStart
        var elapsedSec = 0
        // Simulate 60 steps along the route: walk (0..18), trot (19..38), canter (39..50), cooldown walk (51..59)
        val totalSteps = 60
        for (step in 0 until totalSteps) {
            if (!(_state.value.running)) break
            val share = step.toDouble() / (totalSteps - 1)
            val base = RouteCatalog.pointAtShare(routePts, share)
            // Adjust timestamp delta to yield realistic walk (~5.5 km/h), trot (~11 km/h), canter (~20 km/h)
            val targetSpeedMps = when {
                step < 18 -> 1.5
                step < 38 -> 3.1
                step < 50 -> 5.6
                else -> 1.6
            }
            if (step > 0) {
                val prev = track.last()
                val dist = telemetry.distanceMeters(prev, base).coerceAtLeast(1.5)
                val dtMs = ((dist / targetSpeedMps) * 1000.0).toLong().coerceIn(500L, 60_000L)
                simTime += dtMs
                elapsedSec += (dtMs / 1000L).toInt().coerceAtLeast(1)
            }
            val pt = base.copy(timeMillis = simTime)
            rideStore.appendSample(pt)
            _state.value = _state.value.copy(ownPosition = pt)
            navigate(pt)
            processPoint(pt, horse, elapsedSec)
            delay(450L)
        }
    }

    /** Triggers the 30-second Dummy Fall Alarm countdown (for testing or real accelerometer spike). */
    fun triggerFallAlert(fromTestButton: Boolean = false) {
        if (fallAlarm.currentState == FallAlarm.State.COUNTING) return
        val now = System.currentTimeMillis()
        fallAlarm.start(now)
        val msg = if (fromTestButton) {
            "Sturz-Test! 30 Sekunden Countdown – sage Alles gut oder tippe Abbrechen."
        } else {
            "Sturz erkannt! Sage Alles gut oder tippe Abbrechen!"
        }
        announce(msg)
        _state.value = _state.value.copy(
            fallState = fallAlarm.currentState,
            fallRemainingSec = fallAlarm.remainingSec(now),
            fallMessage = null,
        )
        fallTimerJob?.cancel()
        fallTimerJob = viewModelScope.launch {
            while (fallAlarm.currentState == FallAlarm.State.COUNTING) {
                delay(500L)
                val t = System.currentTimeMillis()
                val expired = fallAlarm.tick(t)
                if (expired) {
                    val contact = settings.emergencyContact.value
                    val target = if (contact.first.isNotBlank()) "${contact.first} (${contact.second})" else "Notfallkontakt"
                    val dummyNote = "DUMMY-SOS ausgelöst: Standort-SMS an $target simuliert (kein echter Notruf)."
                    announce("Dummy-Notruf ausgelöst.")
                    _state.value = _state.value.copy(
                        fallState = FallAlarm.State.SOS_TRIGGERED,
                        fallRemainingSec = 0,
                        fallMessage = dummyNote,
                        bannerMessage = dummyNote,
                    )
                    break
                } else {
                    _state.value = _state.value.copy(
                        fallState = fallAlarm.currentState,
                        fallRemainingSec = fallAlarm.remainingSec(t),
                    )
                }
            }
        }
    }

    fun cancelFallAlert() {
        fallTimerJob?.cancel()
        if (fallAlarm.cancel()) {
            announce("Sturzalarm abgebrochen. Alles gut.")
        } else {
            fallAlarm.reset()
        }
        _state.value = _state.value.copy(
            fallState = FallAlarm.State.IDLE,
            fallRemainingSec = 0,
            fallMessage = null,
        )
    }

    fun dismissFallResult() {
        fallAlarm.reset()
        _state.value = _state.value.copy(
            fallState = FallAlarm.State.IDLE,
            fallRemainingSec = 0,
            fallMessage = null,
        )
    }

    /** Answers a rider question hands-free via TTS + banner. */
    fun askRiderQuestion(question: String) {
        val st = _state.value
        val route = if (st.shownTrack.size >= 2) st.shownTrack else repository.route(st.selectedRouteId)
        val pos = st.ownPosition ?: st.trail.lastOrNull()
        val rem = pos?.let { NavigationGuidance.status(route, it)?.remainingM }
        val nextHazard = st.radar.firstOrNull()
        val ctx = VoiceQaRouter.RideQaContext(
            warmupMinutes = st.warmupMinutes,
            requiredWarmupMinutes = st.requiredWarmupMinutes,
            cooldownNeededMinutes = st.cooldownNeededMinutes,
            distanceMeters = st.distanceMeters,
            remainingMeters = rem,
            welfareScore = st.welfare?.score ?: 100,
            symmetryPct = st.trotSymmetryPct,
            nextHazardLabel = nextHazard?.hazard?.label,
            nextHazardDistM = nextHazard?.distanceMeters,
        )
        val answer = VoiceQaRouter.answer(question, ctx)
            ?: "Dazu habe ich noch keine Daten. Frag nach Strecke, Warm-up, Takt oder Gefahr."
        _state.value = _state.value.copy(lastQaAnswer = answer)
        announce(answer)
    }

    /** Saves a detected >= 250 m canter stretch as a community map mark ("Galoppstrecke"). */
    fun saveCanterStretchMark(stretch: CanterStretch) {
        viewModelScope.launch {
            val horseId = settings.activeHorseId.value ?: MockData.horses.first().id
            repository.addNote(
                horseId,
                RideEventEntity(
                    rideId = "",
                    lat = stretch.midpoint.lat,
                    lon = stretch.midpoint.lon,
                    timeMillis = System.currentTimeMillis(),
                    type = "CANTER_STRETCH",
                    label = "Galoppstrecke (${stretch.lengthMeters.toInt()} m)",
                    note = "Automatisch erkannt",
                    source = "AUTO",
                ),
            )
            _state.value = _state.value.copy(
                detectedCanterStretches = _state.value.detectedCanterStretches.filterNot { it == stretch },
                bannerMessage = "Galoppstrecke gespeichert (${stretch.lengthMeters.toInt()} m)",
            )
        }
    }

    private fun startMotionMonitoring() {
        sensorJob?.cancel()
        sensorJob = viewModelScope.launch {
            runCatching {
                impactSensor.motionStream(fallDetector).collect { sample ->
                    val sym = symmetryTracker.onAccelSample(sample.magnitudeG, _state.value.gait)
                    if (sym != _state.value.trotSymmetryPct) {
                        _state.value = _state.value.copy(trotSymmetryPct = sym)
                    }
                    if (sample.fallResult == FallDetector.Result.FALL_CONFIRMED && _state.value.running) {
                        triggerFallAlert(fromTestButton = false)
                    }
                }
            }
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
        sensorJob?.cancel()
        sensorJob = null
        if (!_state.value.isSimulating) {
            appContext.startService(Intent(appContext, RideService::class.java).setAction(RideService.ACTION_STOP))
        }
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
        if (previous != null) headingDeg = radar.bearingDeg(previous, point)
        val dtMs = if (previous != null) (point.timeMillis - previous.timeMillis).coerceAtLeast(0L) else 0L
        val analysis = telemetry.analyze(track.toList())
        val speedKmh = currentSpeedKmh(track)
        val gait = telemetry.inferGait(speedKmh)

        // Warm-Up & Cool-Down Guardian: accumulate initial walk before first trot/canter/gallop;
        // fast work adds cool-down walk time, walk afterwards pays it down.
        when (gait) {
            Gait.WALK -> {
                if (!hasStartedFastWork) warmupMs += dtMs
                else cooldownDebtMs = (cooldownDebtMs - dtMs).coerceAtLeast(0L)
            }
            Gait.TROT -> {
                hasStartedFastWork = true
                cooldownDebtMs += (dtMs / 3L)
            }
            Gait.CANTER, Gait.GALLOP -> {
                hasStartedFastWork = true
                cooldownDebtMs += dtMs
            }
            Gait.STANDSTILL -> Unit
        }

        // Surface-load check against nearest route segment surface
        val surface = nearestRouteSurface(point, _state.value.selectedRouteId)
        val surfaceWarn = SurfaceLoadTracker.checkLoad(gait, surface, horse.hasLaminitisHistory)
        val nowMs = System.currentTimeMillis()
        if (surfaceWarn != null && nowMs - lastSurfaceWarnAtMs > 20_000L) {
            lastSurfaceWarnAtMs = nowMs
            feedback.deliver(settings.audioMode.value.outputForAlert(), surfaceWarn)
        }

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
            gait = gait,
            radar = alerts,
            welfare = welfareEngine.evaluate(analysis, horse, warmupMs, temperatureC = null),
            trail = track.toList(),
            radarEvents = _state.value.radarEvents + newEvents,
            routeProgress = progress,
            calories = calories,
            warmupMinutes = warmupMs / 60_000.0,
            cooldownNeededMinutes = cooldownDebtMs / 60_000.0,
            surfaceWarning = surfaceWarn,
        )
    }

    private fun nearestRouteSurface(point: GeoPoint, routeId: String): String? {
        val info = RouteCatalog.byId(routeId) ?: return null
        val segments = runCatching { com.example.saddlemate.data.mock.SurfaceData.load(appContext, info) }.getOrDefault(emptyList())
        var bestDist = Double.MAX_VALUE
        var bestSurface: String? = null
        for (seg in segments) {
            for (p in seg.points) {
                val d = telemetry.distanceMeters(point, p)
                if (d < bestDist) {
                    bestDist = d
                    bestSurface = seg.surface
                }
            }
        }
        return if (bestDist <= 60.0) bestSurface else null
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
        val canterStretches = CanterStretchDetector.detect(track.toList(), telemetry)
        feedback.shutdown()
        _state.value = _state.value.copy(
            running = false,
            isSimulating = false,
            lastSummary = analysis,
            lastRideId = id,
            welfare = welfare,
            calories = calories,
            detectedCanterStretches = canterStretches,
            bannerMessage = "Ritt beendet",
        )
    }

    private fun RelativeSide.toGerman() = when (this) {
        RelativeSide.AHEAD -> "geradeaus"
        RelativeSide.LEFT -> "links"
        RelativeSide.RIGHT -> "rechts"
    }

}
