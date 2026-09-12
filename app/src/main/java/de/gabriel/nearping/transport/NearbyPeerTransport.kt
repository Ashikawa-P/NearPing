package de.gabriel.nearping.transport

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsClient
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy

class NearbyPeerTransport(context: Context) : PeerTransport {
    private val client: ConnectionsClient = Nearby.getConnectionsClient(context.applicationContext)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val discoveredSessions = mutableMapOf<String, String>()
    private val connectedEndpoints = mutableSetOf<String>()
    private val pendingEndpoints = mutableSetOf<String>()
    private val scheduledEndpoints = mutableSetOf<String>()

    private var listener: PeerTransport.Listener? = null
    private var localSessionId: String = ""
    private var running = false
    private var advertisingStarted = false
    private var discoveryStarted = false
    private var discoveryAttempt = 0

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            if (payload.type != Payload.Type.BYTES) return
            payload.asBytes()?.let { listener?.onBytesReceived(endpointId, it) }
        }

        override fun onPayloadTransferUpdate(
            endpointId: String,
            update: PayloadTransferUpdate,
        ) = Unit
    }

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            if (!running) {
                client.rejectConnection(endpointId)
                return
            }
            discoveredSessions[endpointId] = info.endpointName
            scheduledEndpoints.remove(endpointId)
            pendingEndpoints += endpointId
            client.acceptConnection(endpointId, payloadCallback)
                .addOnFailureListener {
                    pendingEndpoints.remove(endpointId)
                    listener?.onTransportError("Verbindung konnte nicht angenommen werden.")
                }
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            scheduledEndpoints.remove(endpointId)
            pendingEndpoints.remove(endpointId)
            if (!running) return

            if (result.status.statusCode == ConnectionsStatusCodes.STATUS_OK) {
                if (connectedEndpoints.add(endpointId)) {
                    listener?.onPeerConnected(
                        ConnectedPeer(
                            endpointId = endpointId,
                            advertisedSessionId = discoveredSessions[endpointId].orEmpty(),
                            transportKind = TransportKind.NEARBY,
                        ),
                    )
                }
            } else {
                connectedEndpoints.remove(endpointId)
            }
        }

        override fun onDisconnected(endpointId: String) {
            scheduledEndpoints.remove(endpointId)
            pendingEndpoints.remove(endpointId)
            discoveredSessions.remove(endpointId)
            if (connectedEndpoints.remove(endpointId)) {
                listener?.onPeerDisconnected(endpointId)
            }
        }
    }

    private val discoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            if (!running || endpointId in connectedEndpoints || endpointId in pendingEndpoints ||
                endpointId in scheduledEndpoints
            ) return

            val remoteSessionId = info.endpointName
            if (remoteSessionId.isBlank() || remoteSessionId == localSessionId) return
            discoveredSessions[endpointId] = remoteSessionId

            // Prefer one deterministic initiator. The other side still requests after a short
            // fallback delay if discovery happened only in that direction.
            scheduledEndpoints += endpointId
            val delayMs = if (localSessionId < remoteSessionId) 0L else FALLBACK_INITIATOR_DELAY_MS
            mainHandler.postDelayed({
                scheduledEndpoints.remove(endpointId)
                if (running && endpointId in discoveredSessions &&
                    endpointId !in connectedEndpoints && endpointId !in pendingEndpoints
                ) {
                    requestConnection(endpointId)
                }
            }, delayMs)
        }

        override fun onEndpointLost(endpointId: String) {
            if (endpointId !in connectedEndpoints) {
                scheduledEndpoints.remove(endpointId)
                pendingEndpoints.remove(endpointId)
                discoveredSessions.remove(endpointId)
            }
        }
    }

    override fun start(localSessionId: String, listener: PeerTransport.Listener) {
        if (running) {
            listener.onTransportError("Nearby läuft bereits. Beende die Sitzung und starte sie erneut.")
            return
        }
        this.localSessionId = localSessionId
        this.listener = listener
        running = true
        discoveryAttempt = 0
        listener.onTransportStatus("Starte Nearby-Ankündigung …")

        val advertisingOptions = AdvertisingOptions.Builder()
            .setStrategy(Strategy.P2P_CLUSTER)
            .build()

        client.startAdvertising(
            localSessionId,
            SERVICE_ID,
            connectionLifecycleCallback,
            advertisingOptions,
        ).addOnSuccessListener {
            if (!running) {
                client.stopAdvertising()
                return@addOnSuccessListener
            }
            advertisingStarted = true
            listener.onTransportStatus("Nearby-Ankündigung aktiv; starte Suche …")
            startDiscovery()
        }.addOnFailureListener { error ->
            if (running) listener.onTransportError(error.readableNearbyMessage("Ankündigung"))
        }
    }

    override fun send(endpointId: String, bytes: ByteArray) {
        if (!running || endpointId !in connectedEndpoints) return
        client.sendPayload(endpointId, Payload.fromBytes(bytes))
            .addOnFailureListener {
                if (running) listener?.onTransportError("Daten konnten nicht übertragen werden.")
            }
    }

    override fun stop() {
        val hadActiveTransport = running || advertisingStarted || discoveryStarted ||
            connectedEndpoints.isNotEmpty() || pendingEndpoints.isNotEmpty()
        running = false
        mainHandler.removeCallbacksAndMessages(null)
        if (advertisingStarted) client.stopAdvertising()
        if (discoveryStarted) client.stopDiscovery()
        if (hadActiveTransport) client.stopAllEndpoints()
        discoveredSessions.clear()
        connectedEndpoints.clear()
        pendingEndpoints.clear()
        scheduledEndpoints.clear()
        advertisingStarted = false
        discoveryStarted = false
        discoveryAttempt = 0
        localSessionId = ""
        listener = null
    }

    private fun startDiscovery() {
        if (!running || discoveryStarted) return
        discoveryAttempt += 1
        val discoveryOptions = DiscoveryOptions.Builder()
            .setStrategy(Strategy.P2P_CLUSTER)
            .build()

        client.startDiscovery(
            SERVICE_ID,
            discoveryCallback,
            discoveryOptions,
        ).addOnSuccessListener {
            if (!running) {
                client.stopDiscovery()
                return@addOnSuccessListener
            }
            discoveryStarted = true
            listener?.onTransportStatus("Nearby-Suche aktiv …")
        }.addOnFailureListener { error ->
            if (!running) return@addOnFailureListener
            if (discoveryAttempt < MAX_DISCOVERY_ATTEMPTS && error.isRetryableDiscoveryFailure()) {
                listener?.onTransportStatus(
                    "Nearby-Suche noch nicht bereit; neuer Versuch " +
                        "${discoveryAttempt + 1}/$MAX_DISCOVERY_ATTEMPTS …",
                )
                mainHandler.postDelayed(::startDiscovery, DISCOVERY_RETRY_DELAY_MS)
            } else {
                listener?.onTransportError(error.readableNearbyMessage("Suche"))
            }
        }
    }

    private fun requestConnection(endpointId: String) {
        scheduledEndpoints.remove(endpointId)
        if (!pendingEndpoints.add(endpointId)) return
        client.requestConnection(localSessionId, endpointId, connectionLifecycleCallback)
            .addOnFailureListener {
                pendingEndpoints.remove(endpointId)
                if (!running || endpointId !in discoveredSessions) return@addOnFailureListener
                mainHandler.postDelayed({
                    if (running && endpointId in discoveredSessions && endpointId !in connectedEndpoints) {
                        requestConnection(endpointId)
                    }
                }, RETRY_DELAY_MS)
            }
    }

    private fun Exception.isRetryableDiscoveryFailure(): Boolean {
        val statusCode = (this as? ApiException)?.statusCode ?: return false
        return statusCode == ConnectionsStatusCodes.STATUS_ERROR ||
            statusCode == ConnectionsStatusCodes.STATUS_RADIO_ERROR ||
            statusCode == ConnectionsStatusCodes.STATUS_OUT_OF_ORDER_API_CALL
    }

    private fun Exception.readableNearbyMessage(action: String): String {
        val apiException = this as? ApiException
        val statusCode = apiException?.statusCode
        val statusName = statusCode?.let(ConnectionsStatusCodes::getStatusCodeString)
            ?: javaClass.simpleName
        val codeSuffix = statusCode?.let { " ($it)" }.orEmpty()
        return "$action konnte nicht gestartet werden. Nearby-Fehler: $statusName$codeSuffix."
    }

    companion object {
        private const val SERVICE_ID = "de.gabriel.nearping.protocol.v3"
        private const val RETRY_DELAY_MS = 2_500L
        private const val FALLBACK_INITIATOR_DELAY_MS = 1_500L
        private const val DISCOVERY_RETRY_DELAY_MS = 1_200L
        private const val MAX_DISCOVERY_ATTEMPTS = 3
    }
}
