package de.gabriel.nearping

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import de.gabriel.nearping.ui.NearPingApp
import de.gabriel.nearping.ui.theme.NearPingTheme

class MainActivity : ComponentActivity() {
    private val sessionController: AppViewModel by lazy {
        (application as NearPingApplication).sessionController
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NearPingTheme {
                NearPingApp(sessionController)
            }
        }
        openPeerFromIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openPeerFromIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        sessionController.onAppVisibilityChanged(true)
    }

    override fun onStop() {
        sessionController.onAppVisibilityChanged(false)
        super.onStop()
    }

    private fun openPeerFromIntent(intent: Intent?) {
        val peerSessionId = intent?.getStringExtra(NearPingNotifications.EXTRA_PEER_SESSION_ID)
            ?: return
        intent.removeExtra(NearPingNotifications.EXTRA_PEER_SESSION_ID)
        sessionController.openPeerFromNotification(peerSessionId)
    }
}
