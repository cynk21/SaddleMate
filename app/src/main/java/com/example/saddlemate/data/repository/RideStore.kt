package com.example.saddlemate.data.repository

import com.example.saddlemate.data.local.RideDao
import com.example.saddlemate.data.local.RideEntity
import com.example.saddlemate.data.local.RideSampleEntity
import com.example.saddlemate.domain.GeoPoint
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Writes the GPS track of the running ride to the database as it arrives.
 *
 * The ride row is created at the start, before any fix is written. The track references it
 * through a foreign key, so the row must exist first. The row is completed when the ride ends.
 */
@Singleton
class RideStore @Inject constructor(
    private val rideDao: RideDao,
) {
    /** Id of the ride being recorded, or null when no ride runs. */
    @Volatile
    var activeRideId: String? = null
        private set

    /** Starts a ride: creates its row so the track can reference it. */
    suspend fun begin(rideId: String, horseId: String, startedAtMillis: Long) {
        rideDao.insertRide(
            RideEntity(
                id = rideId,
                horseId = horseId,
                startedAtMillis = startedAtMillis,
                durationMs = 0L,
                distanceMeters = 0.0,
                averageSpeedKmh = 0.0,
                maxSpeedKmh = 0.0,
                welfareScore = 100,
                tendonLoad = 0.0,
                warnings = "",
            ),
        )
        activeRideId = rideId
    }

    fun end() {
        activeRideId = null
    }

    /** Stores one fix of the running ride. Ignored when no ride runs. */
    suspend fun appendSample(point: GeoPoint) {
        val id = activeRideId ?: return
        rideDao.insertSamples(
            listOf(RideSampleEntity(rideId = id, lat = point.lat, lon = point.lon, timeMillis = point.timeMillis)),
        )
    }
}
