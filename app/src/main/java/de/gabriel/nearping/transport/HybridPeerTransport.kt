package de.gabriel.nearping.transport

import android.content.Context

/** Runs Nearby Connections and local-WLAN discovery as independent, parallel routes. */
class HybridPeerTransport(context: Context) : PeerTransport {
    private data class EndpointOwner(
        val transport: PeerTransport,
        val internalEndpointId: String,
    )

    private val transports = linkedMapOf(
        TransportKind.NEARBY to NearbyPeerTransport(context),
        TransportKind.LAN to LanPeerTransport(context),
    )
    private val endpointOwners = mutableMapOf<String, EndpointOwner>()
    private val statuses = mutableMapOf<TransportKind, String>()

    private var listener: PeerTransport.Listener? = null
    private var running = false

    override fun start(localSessionId: String, listener: PeerTransport.Listener) {
        if (running) {
            listener.onTransportError("Die lokale Suche läuft bereits.")
            return
        }
        running = true
        this.listener = listener
        statuses.clear()
        statuses[TransportKind.NEARBY] = "wird gestartet …"
        statuses[TransportKind.LAN] = "wird gestartet …"
        publishStatus()

        transports.forEach { (kind, transport) ->
            transport.start(localSessionId, forwardingListener(kind, transport))
        }
    }

    override fun send(endpointId: String, bytes: ByteArray) {
        if (!running) return
        val owner = endpointOwners[endpointId] ?: return
        owner.transport.send(owner.internalEndpointId, bytes)
    }

    override fun stop() {
        running = false
        transports.values.forEach(PeerTransport::stop)
        endpointOwners.clear()
        statuses.clear()
        listener = null
    }

    private fun forwardingListener(
        kind: TransportKind,
        transport: PeerTransport,
    ) = object : PeerTransport.Listener {
        override fun onTransportStatus(status: String) {
            if (!running) return
            statuses[kind] = status
            publishStatus()
        }

        override fun onPeerConnected(peer: ConnectedPeer) {
            if (!running) return
            val externalEndpointId = "${kind.name.lowercase()}:${peer.endpointId}"
            endpointOwners[externalEndpointId] = EndpointOwner(transport, peer.endpointId)
            listener?.onPeerConnected(
                peer.copy(
                    endpointId = externalEndpointId,
                    transportKind = kind,
                ),
            )
        }

        override fun onPeerDisconnected(endpointId: String) {
            val externalEndpointId = "${kind.name.lowercase()}:$endpointId"
            if (endpointOwners.remove(externalEndpointId) != null) {
                listener?.onPeerDisconnected(externalEndpointId)
            }
        }

        override fun onBytesReceived(endpointId: String, bytes: ByteArray) {
            if (!running) return
            val externalEndpointId = "${kind.name.lowercase()}:$endpointId"
            if (externalEndpointId in endpointOwners) {
                listener?.onBytesReceived(externalEndpointId, bytes)
            }
        }

        override fun onTransportError(message: String) {
            if (!running) return
            val source = if (kind == TransportKind.NEARBY) "Nearby" else "Lokales WLAN"
            statuses[kind] = "Fehler"
            publishStatus()
            listener?.onTransportError("$source: $message")
        }
    }

    private fun publishStatus() {
        if (!running) return
        listener?.onTransportStatus(
            "Nearby: ${statuses[TransportKind.NEARBY]} · " +
                "WLAN: ${statuses[TransportKind.LAN]}",
        )
    }
}
