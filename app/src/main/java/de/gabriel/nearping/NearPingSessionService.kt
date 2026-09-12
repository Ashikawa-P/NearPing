package de.gabriel.nearping

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.content.ContextCompat

class NearPingSessionService : Service() {
    private val nearPingApplication: NearPingApplication
        get() = application as NearPingApplication

    override fun onCreate() {
        super.onCreate()
        startForeground(
            NearPingNotifications.SESSION_NOTIFICATION_ID,
            nearPingApplication.notifications.buildSessionNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_SESSION) {
            nearPingApplication.sessionController.stopSession()
            stopSelf()
            return START_NOT_STICKY
        }
        if (!nearPingApplication.sessionController.isSessionActive) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        nearPingApplication.sessionController.onForegroundServiceDestroyed()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_STOP_SESSION = "de.gabriel.nearping.action.STOP_SESSION"
        private const val ACTION_START_SESSION = "de.gabriel.nearping.action.START_SESSION"

        fun start(context: Context): Boolean = runCatching {
            ContextCompat.startForegroundService(
                context,
                Intent(context, NearPingSessionService::class.java).apply {
                    action = ACTION_START_SESSION
                },
            )
        }.isSuccess

        fun stop(context: Context) {
            context.stopService(Intent(context, NearPingSessionService::class.java))
        }
    }
}
