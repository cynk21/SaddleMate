package com.example.saddlemate.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface PendingVoicePinDao {
    @Query("SELECT * FROM pending_voice_pins ORDER BY timeMillis")
    fun observeAll(): Flow<List<PendingVoicePinEntity>>

    @Query("SELECT * FROM pending_voice_pins ORDER BY timeMillis")
    suspend fun all(): List<PendingVoicePinEntity>

    @Insert
    suspend fun insert(pin: PendingVoicePinEntity): Long

    @Delete
    suspend fun delete(pin: PendingVoicePinEntity)

    @Query("UPDATE pending_voice_pins SET status = :status WHERE id = :id")
    suspend fun setStatus(id: Long, status: String)
}

@Dao
interface OwnRouteDao {
    @Query("SELECT * FROM own_routes ORDER BY createdAtMillis DESC")
    fun observeAll(): Flow<List<OwnRouteEntity>>

    @Upsert
    suspend fun upsert(route: OwnRouteEntity)

    @Query("UPDATE own_routes SET name = :name WHERE id = :id")
    suspend fun rename(id: String, name: String)

    @Query("DELETE FROM own_routes WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface HorseDao {
    @Query("SELECT * FROM horses ORDER BY name")
    fun observeAll(): Flow<List<HorseEntity>>

    @Query("SELECT * FROM horses WHERE id = :id")
    suspend fun byId(id: String): HorseEntity?

    @Query("SELECT COUNT(*) FROM horses")
    suspend fun count(): Int

    @Upsert
    suspend fun upsert(horse: HorseEntity)

    @Delete
    suspend fun delete(horse: HorseEntity)
}

@Dao
interface RideDao {
    @Query("SELECT * FROM rides ORDER BY startedAtMillis DESC")
    fun observeAll(): Flow<List<RideEntity>>

    @Query("SELECT * FROM rides WHERE id = :id")
    suspend fun byId(id: String): RideEntity?

    /** Upsert: the ride row is created at start and completed at the end, under the same id. */
    @Upsert
    suspend fun insertRide(ride: RideEntity)

    @Insert
    suspend fun insertSamples(samples: List<RideSampleEntity>)

    @Query("SELECT * FROM ride_samples WHERE rideId = :rideId ORDER BY timeMillis")
    suspend fun samples(rideId: String): List<RideSampleEntity>

    @Insert
    suspend fun insertEvents(events: List<RideEventEntity>)

    @Query("SELECT * FROM ride_events WHERE rideId = :rideId ORDER BY timeMillis")
    fun observeEvents(rideId: String): Flow<List<RideEventEntity>>

    @Query("SELECT * FROM ride_events WHERE rideId = :rideId ORDER BY timeMillis")
    suspend fun events(rideId: String): List<RideEventEntity>

    @Update
    suspend fun updateEvent(event: RideEventEntity)

    @Delete
    suspend fun deleteEvent(event: RideEventEntity)

    @Query("UPDATE rides SET visibility = :visibility WHERE id = :rideId")
    suspend fun setVisibility(rideId: String, visibility: String)

    @Query("UPDATE rides SET name = :name, note = :note WHERE id = :rideId")
    suspend fun rename(rideId: String, name: String, note: String)

    @Query("DELETE FROM rides WHERE id = :rideId")
    suspend fun deleteRide(rideId: String)

    @Query("DELETE FROM ride_events WHERE rideId = :rideId AND type = 'VOICE_PIN'")
    suspend fun deleteVoicePins(rideId: String)

    /** Every voice pin in the database, whatever ride container it sits in. */
    @Query("DELETE FROM ride_events WHERE rideId = 'voice-pins' OR type = 'VOICE_PIN'")
    suspend fun deleteAllVoicePinsEverywhere()
}

@Dao
interface FeedbackNoteDao {
    @Query(
        """
        SELECT * FROM feedback_notes
        ORDER BY CASE priority WHEN 'HIGH' THEN 3 WHEN 'MEDIUM' THEN 2 ELSE 1 END DESC,
                 createdAtMillis DESC
        """,
    )
    fun observeAll(): Flow<List<FeedbackNoteEntity>>

    @Insert
    suspend fun insert(note: FeedbackNoteEntity): Long

    @Update
    suspend fun update(note: FeedbackNoteEntity)

    @Delete
    suspend fun delete(note: FeedbackNoteEntity)
}
