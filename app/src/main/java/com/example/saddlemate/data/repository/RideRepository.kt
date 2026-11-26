package com.example.saddlemate.data.repository

import android.content.Context
import com.example.saddlemate.data.local.FeedbackNoteDao
import com.example.saddlemate.data.local.FeedbackNoteEntity
import com.example.saddlemate.data.local.HorseDao
import com.example.saddlemate.data.local.HorseEntity
import com.example.saddlemate.data.local.OwnRouteDao
import com.example.saddlemate.data.local.OwnRouteEntity
import com.example.saddlemate.data.local.RideDao
import com.example.saddlemate.data.local.RideEntity
import com.example.saddlemate.data.local.RideEventEntity
import com.example.saddlemate.data.local.RideSampleEntity
import com.example.saddlemate.data.mock.MockData
import com.example.saddlemate.data.mock.RouteCatalog
import com.example.saddlemate.domain.FeedbackStoryFormatter
import com.example.saddlemate.domain.GeoPoint
import com.example.saddlemate.domain.PinProximity
import com.example.saddlemate.domain.Horse
import com.example.saddlemate.domain.HazardMarker
import com.example.saddlemate.domain.RideAnalysis
import com.example.saddlemate.domain.WelfareReport
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** A finished ride ready to be persisted. */
data class CompletedRide(
    val id: String,
    val horseId: String,
    val startedAtMillis: Long,
    val analysis: RideAnalysis,
    val welfare: WelfareReport,
    val samples: List<GeoPoint>,
    val routeId: String = "",
    /** Radar events recorded during the ride (source = AUTO). */
    val events: List<RideEventEntity> = emptyList(),
    val riderCalories: Int = 0,
    val horseCalories: Int = 0,
    val avgHorseHrBpm: Int = 0,
    val maxHorseHrBpm: Int = 0,
    val recoveryHr2MinBpm: Int = 0,
)

/** Single source of truth for horses and rides. Local Room now, Firestore sync later. */
@Singleton
class RideRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val horseDao: HorseDao,
    private val rideDao: RideDao,
    private val ownRouteDao: OwnRouteDao,
    private val feedbackNoteDao: FeedbackNoteDao,
) {
    /** General app feedback & wishes recorded by the rider as backlog tickets. */
    fun observeFeedbackNotes(): Flow<List<FeedbackNoteEntity>> = feedbackNoteDao.observeAll()

    suspend fun addFeedbackNote(text: String) {
        val trimmed = text.trim()
        if (trimmed.isNotEmpty()) {
            feedbackNoteDao.insert(
                FeedbackNoteEntity(
                    text = trimmed,
                    createdAtMillis = System.currentTimeMillis(),
                    status = "NEW",
                    priority = FeedbackStoryFormatter.suggestPriority(trimmed),
                    userStory = FeedbackStoryFormatter.buildUserStory(trimmed),
                ),
            )
        }
    }

    suspend fun updateFeedbackNote(note: FeedbackNoteEntity) = feedbackNoteDao.update(note)

    suspend fun deleteFeedbackNote(note: FeedbackNoteEntity) = feedbackNoteDao.delete(note)
    fun observeHorses(): Flow<List<Horse>> =
        horseDao.observeAll().map { list -> list.map { it.toDomain() } }

    /** Rides for the list. The internal "notes" rides that hold map marks are hidden. */
    fun observeRides(): Flow<List<RideEntity>> =
        rideDao.observeAll().map { list -> list.filterNot { it.id.startsWith("notes-") } }

    suspend fun ride(id: String): RideEntity? = rideDao.byId(id)

    suspend fun horse(id: String): Horse? = horseDao.byId(id)?.toDomain()

    /** Id of the rider's "Notizen" ride, which collects map marks taken outside a ride. */
    fun notesRideId(horseId: String) = "notes-$horseId"

    /**
     * One place for all voice pins. Pins are not tied to a horse: every horse sees the same pins.
     */
    val voicePinRideId = "voice-pins"

    /** All voice pins (and pins auto-classified by content), newest first. */
    fun observeVoicePins(): Flow<List<RideEventEntity>> =
        rideDao.observeEvents(voicePinRideId).map { list -> list.sortedByDescending { it.timeMillis } }

    fun observeNotes(horseId: String): Flow<List<RideEventEntity>> = rideDao.observeEvents(notesRideId(horseId))

    /** Voice pins as the ride sees them: id, position and text. Same for every horse. */
    suspend fun voicePins(): List<PinProximity.Pin> =
        rideDao.events(voicePinRideId)
            .map { PinProximity.Pin(it.id.toString(), GeoPoint(it.lat, it.lon, it.timeMillis), it.label) }

    /** Creates the shared pin container ride once. It holds no horse. */
    private suspend fun ensureRide(rideId: String, horseId: String) {
        if (rideDao.byId(rideId) == null) {
            rideDao.insertRide(
                RideEntity(
                    id = rideId,
                    horseId = horseId,
                    startedAtMillis = System.currentTimeMillis(),
                    durationMs = 0L,
                    distanceMeters = 0.0,
                    averageSpeedKmh = 0.0,
                    maxSpeedKmh = 0.0,
                    welfareScore = 100,
                    tendonLoad = 0.0,
                    warnings = "",
                ),
            )
        }
    }

    /** Stores a voice pin in the shared pin store. The horse is not part of the pin. */
    suspend fun addVoicePin(event: RideEventEntity) {
        // The container ride needs an existing horse (foreign key). Pins still show for every horse.
        val owner = MockData.horses.first().id
        ensureRide(voicePinRideId, horseId = owner)
        rideDao.insertEvents(listOf(event.copy(id = 0, rideId = voicePinRideId, source = "MANUAL")))
    }

    /** Stores a map mark taken outside a ride in the horse's "Notizen" ride (created on first use). */
    suspend fun addNote(horseId: String, event: RideEventEntity) {
        val rideId = notesRideId(horseId)
        if (rideDao.byId(rideId) == null) {
            rideDao.insertRide(
                RideEntity(
                    id = rideId,
                    horseId = horseId,
                    startedAtMillis = System.currentTimeMillis(),
                    durationMs = 0L,
                    distanceMeters = 0.0,
                    averageSpeedKmh = 0.0,
                    maxSpeedKmh = 0.0,
                    welfareScore = 100,
                    tendonLoad = 0.0,
                    warnings = "",
                ),
            )
        }
        val before = rideDao.events(rideId).size
        rideDao.insertEvents(listOf(event.copy(id = 0, rideId = rideId, source = "MANUAL")))
        val after = rideDao.events(rideId).size
        android.util.Log.i("PinDebug", "addNote ride=$rideId type=${event.type} label=${event.label} before=$before after=$after")
    }

    /** Hazards of a route. Only the Weinberg route has hazards so far. */
    fun hazards(routeId: String = RouteCatalog.default.id): List<HazardMarker> =
        if (routeId == RouteCatalog.default.id) weinbergHazardsCache ?: MockData.weinbergHazards(route(routeId)).also { weinbergHazardsCache = it } else emptyList()

    private var weinbergHazardsCache: List<HazardMarker>? = null

    private val routeCache = mutableMapOf<String, List<GeoPoint>>()

    /** Ordered points of a route, loaded once per id from the offline asset. Unknown id: default route. */
    fun route(routeId: String): List<GeoPoint> {
        val info = RouteCatalog.byId(routeId) ?: RouteCatalog.default
        return routeCache.getOrPut(info.id) { RouteCatalog.load(context, info) }
    }

    /** The default route, kept for callers that do not choose a route. */
    fun activeRoute(): List<GeoPoint> = route(RouteCatalog.default.id)

    suspend fun seedIfEmpty() {
        if (horseDao.count() == 0) {
            MockData.horses.forEach { horseDao.upsert(it.toEntity()) }
        }
    }

    suspend fun saveRide(ride: CompletedRide) {
        rideDao.insertRide(
            RideEntity(
                id = ride.id,
                horseId = ride.horseId,
                startedAtMillis = ride.startedAtMillis,
                durationMs = ride.analysis.durationMs,
                distanceMeters = ride.analysis.distanceMeters,
                averageSpeedKmh = ride.analysis.averageSpeedKmh,
                maxSpeedKmh = ride.analysis.maxSpeedKmh,
                welfareScore = ride.welfare.score,
                tendonLoad = ride.welfare.tendonLoad,
                warnings = ride.welfare.warnings.joinToString("\n"),
                routeId = ride.routeId,
                ascentM = ride.analysis.elevation.ascentM,
                descentM = ride.analysis.elevation.descentM,
                riderCalories = ride.riderCalories,
                horseCalories = ride.horseCalories,
                avgHorseHrBpm = ride.avgHorseHrBpm,
                maxHorseHrBpm = ride.maxHorseHrBpm,
                recoveryHr2MinBpm = ride.recoveryHr2MinBpm,
            ),
        )
        // The service writes each fix while the ride runs. Only if none reached the database
        // (e.g. the service did not start) the in-memory track is written here, so no duplicates.
        if (rideDao.samples(ride.id).isEmpty()) {
            rideDao.insertSamples(
                ride.samples.map { RideSampleEntity(rideId = ride.id, lat = it.lat, lon = it.lon, timeMillis = it.timeMillis) },
            )
        }
        if (ride.events.isNotEmpty()) {
            rideDao.insertEvents(ride.events.map { it.copy(id = 0, rideId = ride.id) })
        }
        horseDao.byId(ride.horseId)?.let { entity ->
            horseDao.upsert(
                entity.copy(kmSinceFarrier = entity.kmSinceFarrier + ride.analysis.distanceMeters / 1000.0),
            )
        }
    }

    /** Stored track of a ride, in time order. */
    suspend fun track(rideId: String): List<GeoPoint> =
        rideDao.samples(rideId).map { GeoPoint(it.lat, it.lon, it.timeMillis) }

    fun observeEvents(rideId: String): Flow<List<RideEventEntity>> = rideDao.observeEvents(rideId)

    suspend fun events(rideId: String): List<RideEventEntity> = rideDao.events(rideId)

    /** Adds a hand-made event on a stored ride. */
    suspend fun addEvent(event: RideEventEntity) = rideDao.insertEvents(listOf(event.copy(id = 0, source = "MANUAL")))

    /** Updates an event's type, label or note. Automatic events stay AUTO but become editable. */
    suspend fun updateEvent(event: RideEventEntity) = rideDao.updateEvent(event.copy(source = "MANUAL"))

    suspend fun deleteEvent(event: RideEventEntity) = rideDao.deleteEvent(event)

    /** Removes every voice pin from the shared store. Used to clear test entries. */
    /** Removes every voice pin, in the shared store and in any horse's notes, so map and list stay in sync. */
    suspend fun deleteAllVoicePins() = rideDao.deleteAllVoicePinsEverywhere()

    suspend fun setVisibility(rideId: String, visibility: String) = rideDao.setVisibility(rideId, visibility)

    suspend fun deleteRide(rideId: String) = rideDao.deleteRide(rideId)

    /** Renames a ride and sets its note. */
    suspend fun renameRide(rideId: String, name: String, note: String) = rideDao.rename(rideId, name.trim(), note.trim())

    /** The rider's own routes. Standard routes from the catalogue are never in this list. */
    fun observeOwnRoutes(): Flow<List<OwnRouteEntity>> = ownRouteDao.observeAll()

    suspend fun renameOwnRoute(id: String, name: String) = ownRouteDao.rename(id, name.trim())

    /** Deletes an own route. Standard routes cannot be reached here, so they cannot be deleted. */
    suspend fun deleteOwnRoute(id: String) = ownRouteDao.delete(id)

    /** Saves an edited horse (name, age, farrier interval). Same id, so it replaces the row. */
    suspend fun saveHorse(horse: Horse) = horseDao.upsert(horse.toEntity())

    /**
     * Deletes a horse. Its rides go with it (foreign key cascade). If it was the active horse,
     * the caller resets the selection.
     */
    suspend fun deleteHorse(horseId: String) {
        horseDao.byId(horseId)?.let { horseDao.delete(it) }
    }

    private fun HorseEntity.toDomain() = Horse(
        id = id,
        name = name,
        ageYears = ageYears,
        hasLaminitisHistory = hasLaminitisHistory,
        kmSinceFarrier = kmSinceFarrier,
        farrierIntervalKm = farrierIntervalKm,
        breed = breed,
        weightKg = weightKg,
        heightCm = heightCm,
        photoPath = photoPath,
        notes = notes,
    )

    private fun Horse.toEntity() = HorseEntity(
        id = id,
        name = name,
        ageYears = ageYears,
        hasLaminitisHistory = hasLaminitisHistory,
        kmSinceFarrier = kmSinceFarrier,
        farrierIntervalKm = farrierIntervalKm,
        breed = breed,
        weightKg = weightKg,
        heightCm = heightCm,
        photoPath = photoPath,
        notes = notes,
    )
}
