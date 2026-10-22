package com.nova.micfx.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.nova.micfx.NovaApp
import com.nova.micfx.R
import com.nova.micfx.audio.EngineState
import com.nova.micfx.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Foreground service (type "microphone"). Android requires it, plus a visible notification, for
 * microphone capture that must continue while the app is not on screen. It is started for every
 * session so the notification always shows that the microphone is in use.
 */
class MicFxService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Microphone monitoring", NotificationManager.IMPORTANCE_LOW)
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val engine = (application as NovaApp).engine
        if (intent?.action == ACTION_STOP) {
            engine.stop()
            stopForegroundAndSelf()
            return START_NOT_STICKY
        }
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIF_ID, notification)
        }
        engine.start()
        scope.launch {
            engine.snapshot.collect {
                if (it.state == EngineState.IDLE || it.state == EngineState.ERROR) stopForegroundAndSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun stopForegroundAndSelf() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun buildNotification(): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(
            this, 1, Intent(this, MicFxService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("NOVA MIC FX - microphone is active")
            .setContentText("Live monitoring is running. Tap Stop to turn the microphone off.")
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(0, "Stop", stop)
            .build()
    }

    override fun onDestroy() {
        scope.cancel()
        (application as NovaApp).engine.stop()
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "com.nova.micfx.action.STOP"
        private const val CHANNEL_ID = "mic_fx_monitoring"
        private const val NOTIF_ID = 1001
    }
}
