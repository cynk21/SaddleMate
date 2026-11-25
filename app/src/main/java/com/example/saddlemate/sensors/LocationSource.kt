package com.example.saddlemate.sensors

import android.annotation.SuppressLint
import android.content.Context
import com.example.saddlemate.domain.GeoPoint
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Streams GPS fixes via FusedLocationProvider. Requires location permission,
 * which the UI must request before collecting this flow.
 */
@Singleton
class LocationSource @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    @SuppressLint("MissingPermission")
    fun fixes(intervalMs: Long = 1_000L): Flow<GeoPoint> = callbackFlow {
        val client = LocationServices.getFusedLocationProviderClient(context)
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMs)
            .setMinUpdateIntervalMillis(intervalMs / 2)
            .build()
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.locations.forEach { loc ->
                    trySend(
                        GeoPoint(
                            loc.latitude,
                            loc.longitude,
                            loc.time,
                            altitudeM = if (loc.hasAltitude()) loc.altitude else null,
                        ),
                    )
                }
            }
        }
        // Explicit main looper: the flow runs on a background dispatcher, which has no looper.
        // Passing null there crashes with "invalid null looper".
        client.requestLocationUpdates(request, callback, android.os.Looper.getMainLooper())
        awaitClose { client.removeLocationUpdates(callback) }
    }
}
