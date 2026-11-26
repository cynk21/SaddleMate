package com.example.saddlemate.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "horses")
data class HorseEntity(
    @PrimaryKey val id: String,
    val name: String,
    val ageYears: Int,
    val hasLaminitisHistory: Boolean,
    val kmSinceFarrier: Double,
    val farrierIntervalKm: Double,
    val breed: String = "",
    val weightKg: Double = 0.0,
    val heightCm: Int = 0,
    val photoPath: String = "",
    val notes: String = "",
)

@Entity(
    tableName = "rides",
    foreignKeys = [
        ForeignKey(
            entity = HorseEntity::class,
            parentColumns = ["id"],
            childColumns = ["horseId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("horseId")],
)
data class RideEntity(
    @PrimaryKey val id: String,
    val horseId: String,
    val startedAtMillis: Long,
    val durationMs: Long,
    val distanceMeters: Double,
    val averageSpeedKmh: Double,
    val maxSpeedKmh: Double,
    val welfareScore: Int,
    val tendonLoad: Double,
    val warnings: String,
    /** Route the ride followed (e.g. "weinberg"). Empty for rides without a route. */
    val routeId: String = "",
    /** Owner for the later community feature. Local rides use "local". */
    val ownerId: String = "local",
    /** Ascent in metres from GPS altitude (ca.). 0 for rides recorded before this was added. */
    val ascentM: Double = 0.0,
    /** Descent in metres from GPS altitude (ca.). */
    val descentM: Double = 0.0,
    /** Name the rider gave the ride. Empty = show the date instead. */
    val name: String = "",
    /** Free note on the ride. */
    val note: String = "",
    /** "public" lists the ride as a route in the planner; "private" hides it. New rides are public. */
    val visibility: String = "public",
    /** Estimated calories spent during the ride (kcal) for rider and horse. */
    val riderCalories: Int = 0,
    val horseCalories: Int = 0,
    /** Horse heart rate measurements from BLE strap (0x180D) or Vet-Gate recovery check. */
    val avgHorseHrBpm: Int = 0,
    val maxHorseHrBpm: Int = 0,
    val recoveryHr2MinBpm: Int = 0,
)

@Entity(
    tableName = "ride_samples",
    foreignKeys = [
        ForeignKey(
            entity = RideEntity::class,
            parentColumns = ["id"],
            childColumns = ["rideId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("rideId")],
)
data class RideSampleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val rideId: String,
    val lat: Double,
    val lon: Double,
    val timeMillis: Long,
)

/**
 * A voice pin recorded but not yet turned into text. Kept only when recognition failed.
 * [audioPath] is the WAV file on the device. Removed again once the pin is stored.
 */
@Entity(tableName = "pending_voice_pins")
data class PendingVoicePinEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val audioPath: String,
    val lat: Double,
    val lon: Double,
    val timeMillis: Long,
    val horseId: String,
    /** "PENDING" (not tried yet) or "FAILED" (tried, will be retried at next start). */
    val status: String = "PENDING",
)

/**
 * A route the rider made. Standard routes come from the app's catalogue, these are stored here.
 * [points] is the track as "lat,lon;lat,lon;...", the same format as the route assets.
 */
@Entity(tableName = "own_routes")
data class OwnRouteEntity(
    @PrimaryKey val id: String,
    val name: String,
    val points: String,
    val createdAtMillis: Long,
    /** Visibility for the later community feature: "private" or "public". */
    val visibility: String = "private",
)

/**
 * A warning or note on a ride's path. Created automatically when the radar announces a hazard
 * (source = AUTO) or by hand afterwards (source = MANUAL). Editable and deletable.
 */
@Entity(
    tableName = "ride_events",
    foreignKeys = [
        ForeignKey(
            entity = RideEntity::class,
            parentColumns = ["id"],
            childColumns = ["rideId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("rideId")],
)
data class RideEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val rideId: String,
    val lat: Double,
    val lon: Double,
    val timeMillis: Long,
    /** HazardType name, e.g. "DOG", "BLIND_TURN", "ROAD_CROSSING", "GATE", "WATER", "LOOSE_HORSE". */
    val type: String,
    val label: String,
    val note: String = "",
    /** "AUTO" for radar events, "MANUAL" for events added or changed by the rider. */
    val source: String,
)

/**
 * General feedback or feature wish recorded by the rider, stored locally as a backlog ticket
 * so admins can review, prioritize, and export it as a User Story.
 */
@Entity(tableName = "feedback_notes")
data class FeedbackNoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    val createdAtMillis: Long,
    /** "NEW", "BACKLOG", "IN_PROGRESS", "DONE". */
    val status: String = "NEW",
    /** Admin priority: "LOW", "MEDIUM", "HIGH". */
    val priority: String = "MEDIUM",
    /** Generated User Story text, editable by admin/rider. */
    val userStory: String = "",
)
