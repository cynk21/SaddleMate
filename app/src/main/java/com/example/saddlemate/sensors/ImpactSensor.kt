package com.example.saddlemate.sensors

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.example.saddlemate.domain.FallDetector
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlin.math.sqrt

/**
 * Accelerometer magnitude in g, fed into the FallDetector state machine.
 * Emits the detector result for every sample that changes state.
 */
@Singleton
class ImpactSensor @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    @SuppressLint("MissingPermission")
    fun results(detector: FallDetector): Flow<FallDetector.Result> = callbackFlow {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val accel = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val (x, y, z) = Triple(event.values[0], event.values[1], event.values[2])
                val gForce = sqrt(x * x + y * y + z * z) / SENSOR_GRAVITY
                val result = detector.onSample(gForce.toDouble(), event.timestamp / 1_000_000L)
                if (result != FallDetector.Result.NONE) trySend(result)
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        if (accel == null) {
            close(IllegalStateException("No accelerometer on this device"))
        } else {
            sm.registerListener(listener, accel, SensorManager.SENSOR_DELAY_GAME)
        }
        awaitClose { sm.unregisterListener(listener) }
    }

    private companion object {
        const val SENSOR_GRAVITY = 9.80665f
    }
}
