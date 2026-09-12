package de.gabriel.nearping

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class NearPingNotifications(private val context: Context) {
    private val manager = NotificationManagerCompat.from(context)
    private val eventIdsBySession = ConcurrentHashMap<String, Int>()
    private val nextEventId = AtomicInteger(FIRST_EVENT_NOTIFICATION_ID)

    init {
        createChannels()
    }

    fun buildSessionNotification(peerCount: Int = 0): Notification {
        val contentIntent = appPendingIntent(peerSessionId = null, requestCode = SESSION_CONTENT_REQUEST)
        val stopIntent = PendingIntent.getService(
            context,
            SESSION_STOP_REQUEST,
            Intent(context, NearPingSessionService::class.java).apply {
                action = NearPingSessionService.ACTION_STOP_SESSION
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val status = if (peerCount == 0) {
            "Suche über Nearby und lokales WLAN"
        } else {
            "$peerCount Person(en) momentan erreichbar"
        }
        return NotificationCompat.Builder(context, SESSION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("NearPing-Sitzung aktiv")
            .setContentText(status)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .addAction(R.drawable.ic_notification, "Beenden", stopIntent)
            .build()
    }

    fun updateSessionNotification(peerCount: Int) {
        postNotification(SESSION_NOTIFICATION_ID, buildSessionNotification(peerCount))
    }

    fun showIncomingPing(peerSessionId: String, peerName: String) {
        showEvent(
            peerSessionId = peerSessionId,
            title = "$peerName hat dich angepingt",
            text = "Öffne NearPing, um das Profil anzusehen und zu antworten.",
        )
    }

    fun showMatch(peerSessionId: String, peerName: String) {
        showEvent(
            peerSessionId = peerSessionId,
            title = "Match mit $peerName",
            text = "Ihr habt euch gegenseitig angepingt. Zeit, euch persönlich zu treffen.",
        )
    }

    fun showCoordinationSignal(
        peerSessionId: String,
        peerName: String,
        signalText: String,
    ) {
        showEvent(
            peerSessionId = peerSessionId,
            title = "Neue Absprache von $peerName",
            text = signalText,
        )
    }

    fun cancelPeer(peerSessionId: String) {
        eventIdsBySession.remove(peerSessionId)?.let(manager::cancel)
    }

    fun clearEventNotifications() {
        eventIdsBySession.values.forEach(manager::cancel)
        eventIdsBySession.clear()
    }

    private fun showEvent(peerSessionId: String, title: String, text: String) {
        if (!canPostNotifications()) return
        val notificationId = eventIdsBySession.getOrPut(peerSessionId) {
            nextEventId.getAndIncrement()
        }
        val notification = NotificationCompat.Builder(context, EVENT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(appPendingIntent(peerSessionId, notificationId))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setCategory(NotificationCompat.CATEGORY_SOCIAL)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .build()
        postNotification(notificationId, notification)
    }

    private fun appPendingIntent(peerSessionId: String?, requestCode: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            peerSessionId?.let { putExtra(EXTRA_PEER_SESSION_ID, it) }
        }
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun createChannels() {
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        val sessionChannel = NotificationChannel(
            SESSION_CHANNEL_ID,
            "Aktive NearPing-Sitzung",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Zeigt an, dass NearPing im Hintergrund nach Personen sucht."
            setSound(null, null)
            enableVibration(false)
            lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        }
        val eventChannel = NotificationChannel(
            EVENT_CHANNEL_ID,
            "Pings, Matches und Absprachen",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Benachrichtigt über neue Pings, Matches und feste Signale."
            enableVibration(true)
            lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        }
        notificationManager.createNotificationChannels(listOf(sessionChannel, eventChannel))
    }

    private fun canPostNotifications(): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    private fun postNotification(notificationId: Int, notification: Notification) {
        if (!canPostNotifications()) return
        runCatching { manager.notify(notificationId, notification) }
    }

    companion object {
        const val SESSION_NOTIFICATION_ID = 1001
        const val EXTRA_PEER_SESSION_ID = "de.gabriel.nearping.extra.PEER_SESSION_ID"
        private const val SESSION_CHANNEL_ID = "nearping_session_v1"
        private const val EVENT_CHANNEL_ID = "nearping_events_v1"
        private const val SESSION_CONTENT_REQUEST = 101
        private const val SESSION_STOP_REQUEST = 102
        private const val FIRST_EVENT_NOTIFICATION_ID = 2000
    }
}
