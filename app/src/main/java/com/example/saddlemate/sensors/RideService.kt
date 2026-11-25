package com.example.saddlemate.sensors

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.example.saddlemate.data.repository.RideStore
import com.example.saddlemate.domain.GeoPoint
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

/**
 * Keeps the GPS recording alive while the screen is off.
 *
 * Runs as a foreground service of type "location" with a permanent notification, so Android
 * does not stop it. Each fix is written to the database by [RideStore] right away, and is also
 * passed to the app through [fixes]. The service only runs while a ride is recorded.
 */
@AndroidEntryPoint
class RideService : Service() {

    @Inject lateinit var locationSource: LocationSource

    @Inject lateinit var rideStore: RideStore

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var recording: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                recording?.cancel()
                rideStore.end()
                stopForegroundCompat()
                stopSelf()
            }
            else -> startRecording()
        }
        return START_NOT_STICKY
    }

    private fun startRecording() {
        createChannel()
        val notification = buildNotification("Ritt wird aufgezeichnet")
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0,
        )
        if (recording?.isActive == true) return
        recording = scope.launch {
            locationSource.fixes().collect { point ->
                rideStore.appendSample(point)
                _fixes.tryEmit(point)
            }
        }
    }

    private fun stopForegroundCompat() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
    }

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Ritt-Aufzeichnung", NotificationManager.IMPORTANCE_LOW),
            )
        }
    }

    private fun buildNotification(text: String): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SaddleMate")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .setSilent(true)
            .build()

    override fun onDestroy() {
        recording?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "com.example.saddlemate.action.STOP_RIDE"
        private const val CHANNEL_ID = "ride_recording"
        private const val NOTIFICATION_ID = 4711

        private val _fixes = MutableSharedFlow<GeoPoint>(extraBufferCapacity = 256)

        /** GPS fixes while a ride is recorded. The ride view model collects this flow. */
        val fixes = _fixes.asSharedFlow()
    }
}
