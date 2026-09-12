package de.gabriel.nearping.transport

enum class TransportKind {
    NEARBY,
    LAN,
}

data class ConnectedPeer(
    val endpointId: String,
    val advertisedSessionId: String,
    val transportKind: TransportKind,
)

interface PeerTransport {
    interface Listener {
        fun onTransportStatus(status: String)
        fun onPeerConnected(peer: ConnectedPeer)
        fun onPeerDisconnected(endpointId: String)
        fun onBytesReceived(endpointId: String, bytes: ByteArray)
        fun onTransportError(message: String)
    }

    fun start(localSessionId: String, listener: Listener)
    fun send(endpointId: String, bytes: ByteArray)
    fun stop()
}
