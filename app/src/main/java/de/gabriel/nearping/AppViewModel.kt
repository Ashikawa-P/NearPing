package de.gabriel.nearping

import android.app.Application
import android.os.Looper
import de.gabriel.nearping.logic.CoordinationSignal
import de.gabriel.nearping.logic.BackgroundAlertPolicy
import de.gabriel.nearping.logic.BackgroundPingAlert
import de.gabriel.nearping.logic.PingState
import de.gabriel.nearping.model.AppScreen
import de.gabriel.nearping.model.AppUiState
import de.gabriel.nearping.model.CoordinationEventUiModel
import de.gabriel.nearping.model.PeerUiModel
import de.gabriel.nearping.protocol.CURRENT_PROTOCOL_VERSION
import de.gabriel.nearping.protocol.MessageType
import de.gabriel.nearping.protocol.ProtocolCodec
import de.gabriel.nearping.protocol.WireMessage
import de.gabriel.nearping.transport.ConnectedPeer
import de.gabriel.nearping.transport.HybridPeerTransport
import de.gabriel.nearping.transport.PeerTransport
import de.gabriel.nearping.transport.PeerRouteRegistry
import de.gabriel.nearping.transport.TransportKind
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AppViewModel(
    private val application: Application,
    private val notifications: NearPingNotifications,
) : PeerTransport.Listener {
    private val transport: PeerTransport = HybridPeerTransport(application)
    private val controllerScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _uiState = MutableStateFlow(AppUiState())
    val uiState = _uiState.asStateFlow()

    private val peersBySession = linkedMapOf<String, PeerRecord>()
    private val routes = PeerRouteRegistry()

    private var localSessionId: String? = null
    private var localDisplayName: String = ""
    private var localSelfieJpeg: ByteArray? = null
    private var transportStatus: String = "Noch nicht aktiv"
    private var appVisible = false

    fun setEnteredName(value: String) {
        _uiState.update { it.copy(enteredName = value.take(MAX_NAME_LENGTH)) }
    }

    fun openCamera() {
        _uiState.update { it.copy(screen = AppScreen.CAMERA, errorMessage = null) }
    }

    fun cancelCamera() {
        _uiState.update { it.copy(screen = AppScreen.SETUP) }
    }

    fun acceptSelfie(jpegBytes: ByteArray) {
        _uiState.update {
            it.copy(
                screen = AppScreen.SETUP,
                selfieJpeg = jpegBytes.copyOf(),
                errorMessage = null,
            )
        }
    }

    fun startSession() {
        val current = _uiState.value
        val name = current.enteredName.trim()
        val selfie = current.selfieJpeg
        if (name.isBlank()) {
            setError("Bitte gib zuerst einen Namen ein.")
            return
        }
        if (selfie == null) {
            setError("Bitte nimm für diese Sitzung ein aktuelles Foto auf.")
            return
        }

        shutdownTransport()
        localSessionId = UUID.randomUUID().toString().replace("-", "").take(16)
        localDisplayName = name
        localSelfieJpeg = selfie.copyOf()
        transportStatus = "Starte lokale Suche …"

        _uiState.update {
            it.copy(
                screen = AppScreen.NEARBY,
                sessionActive = true,
                sessionStatus = transportStatus,
                peers = emptyList(),
                selectedPeer = null,
                errorMessage = null,
            )
        }

        notifications.clearEventNotifications()
        if (!NearPingSessionService.start(application)) {
            shutdownTransport()
            _uiState.update {
                it.copy(
                    screen = AppScreen.SETUP,
                    sessionActive = false,
                    sessionStatus = "Sitzung konnte nicht gestartet werden",
                    errorMessage = "Android konnte den NearPing-Hintergrunddienst nicht starten.",
                )
            }
            return
        }
        val sessionId = requireNotNull(localSessionId)
        transport.start(sessionId, this)
    }

    fun stopSession() {
        stopSessionInternal(stopForegroundService = true)
    }

    fun onForegroundServiceDestroyed() {
        if (!_uiState.value.sessionActive) return
        stopSessionInternal(stopForegroundService = false)
    }

    fun onAppVisibilityChanged(visible: Boolean) {
        appVisible = visible
        if (visible) {
            peersBySession.values.forEach { it.pendingPingAlert = false }
            notifications.clearEventNotifications()
        }
    }

    fun openPeerFromNotification(peerSessionId: String) {
        selectPeer(peerSessionId)
    }

    val isSessionActive: Boolean
        get() = _uiState.value.sessionActive

    private fun stopSessionInternal(stopForegroundService: Boolean) {
        shutdownTransport()
        notifications.clearEventNotifications()
        _uiState.update {
            it.copy(
                screen = AppScreen.SETUP,
                selfieJpeg = null,
                sessionActive = false,
                sessionStatus = "Sitzung beendet",
                peers = emptyList(),
                selectedPeer = null,
            )
        }
        if (stopForegroundService) NearPingSessionService.stop(application)
    }

    fun selectPeer(peerId: String) {
        val peer = peersBySession[peerId] ?: return
        if (!peer.isVisible) return
        _uiState.update {
            it.copy(
                screen = AppScreen.PROFILE,
                selectedPeer = peer.toUiModel(routes.kindsForSession(peer.sessionId)),
            )
        }
        if (peer.photoJpeg == null && peer.outgoingPhotoRequestId == null) {
            val request = WireMessage.photoRequest(
                senderSessionId = requireNotNull(localSessionId),
                targetSessionId = peer.sessionId,
            )
            peer.outgoingPhotoRequestId = request.messageId
            sendMessage(peer, request)
            publishPeers()
        }
    }

    fun closePeer() {
        _uiState.update { it.copy(screen = AppScreen.NEARBY, selectedPeer = null) }
    }

    fun pingSelectedPeer() {
        val peerId = _uiState.value.selectedPeer?.endpointId ?: return
        val peer = peersBySession[peerId] ?: return
        if (!peer.isVisible || peer.pingState.localPinged) return

        peer.pingState = peer.pingState.copy(localPinged = true)
        sendMessage(
            peer,
            WireMessage.ping(
                senderSessionId = requireNotNull(localSessionId),
                targetSessionId = peer.sessionId,
            ),
        )
        publishPeers()
    }

    fun sendCoordinationSignal(signal: CoordinationSignal) {
        val peerId = _uiState.value.selectedPeer?.endpointId ?: return
        val peer = peersBySession[peerId] ?: return
        if (!peer.isVisible || !peer.pingState.isMutual) return

        val validatedSignal = CoordinationSignal.fromWire(
            code = signal.code.wireCode,
            optionCode = signal.optionCode,
        ) ?: return
        val now = System.currentTimeMillis()
        if (now - peer.lastSignalSentAtEpochMs < SIGNAL_SEND_COOLDOWN_MS) return

        val message = WireMessage.coordinationSignal(
            senderSessionId = requireNotNull(localSessionId),
            targetSessionId = peer.sessionId,
            code = validatedSignal.code.wireCode,
            optionCode = validatedSignal.optionCode,
        )
        peer.lastSignalSentAtEpochMs = now
        peer.appendCoordinationEvent(
            CoordinationEventUiModel(
                messageId = message.messageId,
                text = validatedSignal.text,
                outgoing = true,
            ),
        )
        sendMessage(peer, message)
        publishPeers()
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun reportError(message: String) {
        setError(message)
    }

    override fun onTransportStatus(status: String) = onMainThread {
        transportStatus = status
        publishPeers()
    }

    override fun onPeerConnected(peer: ConnectedPeer) = onMainThread {
        val currentSessionId = localSessionId ?: return@onMainThread
        val remoteSessionId = peer.advertisedSessionId
        if (remoteSessionId.isBlank() || remoteSessionId == currentSessionId) return@onMainThread
        routes.connect(peer.endpointId, remoteSessionId, peer.transportKind)
        peersBySession.getOrPut(remoteSessionId) { PeerRecord(sessionId = remoteSessionId) }
        sendHelloToEndpoint(peer.endpointId)
        publishPeers()
    }

    override fun onPeerDisconnected(endpointId: String) = onMainThread {
        val disconnected = routes.disconnect(endpointId) ?: return@onMainThread
        if (!disconnected.hasRemainingRoutes) {
            peersBySession.remove(disconnected.sessionId)
            notifications.cancelPeer(disconnected.sessionId)
        }
        publishPeers()
    }

    override fun onBytesReceived(endpointId: String, bytes: ByteArray) = onMainThread {
        if (localSessionId == null) return@onMainThread
        val routedSessionId = routes.sessionForEndpoint(endpointId) ?: return@onMainThread
        val message = runCatching { ProtocolCodec.decode(bytes) }.getOrNull() ?: return@onMainThread
        if (message.protocolVersion != CURRENT_PROTOCOL_VERSION) return@onMainThread
        if (message.senderSessionId != routedSessionId) return@onMainThread

        val peer = peersBySession.getOrPut(routedSessionId) { PeerRecord(sessionId = routedSessionId) }

        when (message.type) {
            MessageType.HELLO -> receiveHello(peer, message)
            MessageType.PING -> receivePing(peer, message)
            MessageType.PHOTO_REQUEST -> receivePhotoRequest(peer, message)
            MessageType.PHOTO_DATA -> receivePhoto(peer, message)
            MessageType.COORDINATION_SIGNAL -> receiveCoordinationSignal(peer, message)
        }
        publishPeers()
    }

    override fun onTransportError(message: String) = onMainThread {
        setError(message)
    }

    private fun sendHelloToEndpoint(endpointId: String) {
        val sessionId = localSessionId ?: return
        sendMessageToEndpoint(
            endpointId,
            WireMessage.hello(
                senderSessionId = sessionId,
                displayName = localDisplayName,
            ),
        )
    }

    private fun receiveHello(peer: PeerRecord, message: WireMessage) {
        val hello = message.hello ?: return
        val cleanName = hello.displayName.trim().take(MAX_NAME_LENGTH)
        if (cleanName.isBlank()) return
        peer.displayName = cleanName
        deliverPendingPingAlert(peer)
    }

    private fun receivePing(peer: PeerRecord, message: WireMessage) {
        if (message.targetSessionId != localSessionId) return
        if (message.ping?.active != true) return
        val wasRemotePinged = peer.pingState.remotePinged
        peer.pingState = peer.pingState.copy(remotePinged = true)
        if (!wasRemotePinged) {
            peer.pendingPingAlert = !appVisible
            deliverPendingPingAlert(peer)
        }
    }

    private fun deliverPendingPingAlert(peer: PeerRecord) {
        when (
            BackgroundAlertPolicy.pendingPingAlert(
                pending = peer.pendingPingAlert,
                appVisible = appVisible,
                hasDisplayName = peer.displayName.isNotBlank(),
                isMutual = peer.pingState.isMutual,
            )
        ) {
            BackgroundPingAlert.INCOMING_PING ->
                notifications.showIncomingPing(peer.sessionId, peer.displayName)

            BackgroundPingAlert.MATCH -> notifications.showMatch(peer.sessionId, peer.displayName)
            null -> return
        }
        peer.pendingPingAlert = false
    }

    private fun receivePhotoRequest(peer: PeerRecord, message: WireMessage) {
        if (message.targetSessionId != localSessionId) return
        if (peer.displayName.isBlank() || peer.photoSharedToPeer) return
        if (!peer.receivedPhotoRequestIds.add(message.messageId)) return
        val selfie = localSelfieJpeg ?: return
        peer.photoSharedToPeer = true
        sendMessage(
            peer,
            WireMessage.photoData(
                senderSessionId = requireNotNull(localSessionId),
                targetSessionId = peer.sessionId,
                requestMessageId = message.messageId,
                jpegBytes = selfie,
            ),
        )
    }

    private fun receivePhoto(peer: PeerRecord, message: WireMessage) {
        if (message.targetSessionId != localSessionId) return
        val photoPayload = message.photo ?: return
        if (photoPayload.requestMessageId != peer.outgoingPhotoRequestId) return
        val photo = runCatching { ProtocolCodec.decodePhoto(photoPayload) }.getOrNull() ?: return
        if (photo.size !in 1..MAX_ACCEPTED_PHOTO_BYTES) return
        if (photo.size < 2 || photo[0] != JPEG_START_FIRST || photo[1] != JPEG_START_SECOND) return
        peer.outgoingPhotoRequestId = null
        peer.photoJpeg = photo
    }

    private fun receiveCoordinationSignal(peer: PeerRecord, message: WireMessage) {
        if (message.targetSessionId != localSessionId) return
        if (!peer.pingState.isMutual) return
        if (!peer.receivedCoordinationMessageIds.add(message.messageId)) return
        val payload = message.coordinationSignal ?: return
        val signal = CoordinationSignal.fromWire(payload.code, payload.optionCode) ?: return
        peer.appendCoordinationEvent(
            CoordinationEventUiModel(
                messageId = message.messageId,
                text = signal.text,
                outgoing = false,
            ),
        )
        if (!appVisible && peer.displayName.isNotBlank()) {
            notifications.showCoordinationSignal(
                peerSessionId = peer.sessionId,
                peerName = peer.displayName,
                signalText = signal.text,
            )
        }
    }

    private fun sendMessage(peer: PeerRecord, message: WireMessage) {
        val encoded = runCatching { ProtocolCodec.encode(message) }
            .onFailure { setError("Eine lokale Nachricht war zu groß und wurde nicht gesendet.") }
            .getOrNull() ?: return
        routes.endpointsForSession(peer.sessionId).forEach { endpointId ->
            transport.send(endpointId, encoded)
        }
    }

    private fun sendMessageToEndpoint(endpointId: String, message: WireMessage) {
        val encoded = runCatching { ProtocolCodec.encode(message) }
            .onFailure { setError("Eine lokale Nachricht war zu groß und wurde nicht gesendet.") }
            .getOrNull() ?: return
        transport.send(endpointId, encoded)
    }

    private fun publishPeers() {
        peersBySession.values.forEach { peer ->
            peer.isVisible = peer.displayName.isNotBlank()
        }

        val visiblePeers = peersBySession.values
            .filter(PeerRecord::isVisible)
            .sortedBy { it.displayName.lowercase() }
            .map { peer -> peer.toUiModel(routes.kindsForSession(peer.sessionId)) }

        val selectedEndpointId = _uiState.value.selectedPeer?.endpointId
        val selected = selectedEndpointId
            ?.let(peersBySession::get)
            ?.takeIf(PeerRecord::isVisible)
            ?.let { peer -> peer.toUiModel(routes.kindsForSession(peer.sessionId)) }

        val connectedCount = routes.endpointCount
        val status = when {
            visiblePeers.isNotEmpty() -> "${visiblePeers.size} Person(en) über Nearby und/oder WLAN verbunden"
            connectedCount > 0 -> "Direkte Verbindung hergestellt; warte auf Profildaten …"
            else -> transportStatus
        }

        _uiState.update { state ->
            state.copy(
                screen = if (state.screen == AppScreen.PROFILE && selected == null) {
                    AppScreen.NEARBY
                } else {
                    state.screen
                },
                sessionStatus = status,
                peers = visiblePeers,
                selectedPeer = selected,
            )
        }
        if (_uiState.value.sessionActive) {
            notifications.updateSessionNotification(visiblePeers.size)
        }
    }

    private fun shutdownTransport() {
        transport.stop()
        peersBySession.clear()
        routes.clear()
        localSessionId = null
        localDisplayName = ""
        localSelfieJpeg = null
        transportStatus = "Noch nicht aktiv"
    }

    private fun setError(message: String) {
        _uiState.update { it.copy(errorMessage = message) }
    }

    private fun onMainThread(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            controllerScope.launch { block() }
        }
    }

    private data class PeerRecord(
        val sessionId: String,
        var displayName: String = "",
        var isVisible: Boolean = false,
        var pingState: PingState = PingState(),
        var photoJpeg: ByteArray? = null,
        var outgoingPhotoRequestId: String? = null,
        var photoSharedToPeer: Boolean = false,
        var pendingPingAlert: Boolean = false,
        var lastSignalSentAtEpochMs: Long = 0L,
        val coordinationEvents: MutableList<CoordinationEventUiModel> = mutableListOf(),
        val receivedCoordinationMessageIds: MutableSet<String> = mutableSetOf(),
        val receivedPhotoRequestIds: MutableSet<String> = mutableSetOf(),
    ) {
        fun appendCoordinationEvent(event: CoordinationEventUiModel) {
            coordinationEvents += event
            while (coordinationEvents.size > MAX_COORDINATION_EVENTS) {
                coordinationEvents.removeAt(0)
            }
        }

        fun toUiModel(transportKinds: Set<TransportKind>) = PeerUiModel(
            endpointId = sessionId,
            sessionId = sessionId,
            displayName = displayName,
            indicator = pingState.indicator,
            localPinged = pingState.localPinged,
            remotePinged = pingState.remotePinged,
            photoJpeg = photoJpeg,
            coordinationEvents = coordinationEvents.toList(),
            transportKinds = transportKinds,
        )
    }

    companion object {
        private const val MAX_NAME_LENGTH = 32
        private const val MAX_ACCEPTED_PHOTO_BYTES = 22_000
        private const val MAX_COORDINATION_EVENTS = 30
        private const val SIGNAL_SEND_COOLDOWN_MS = 750L
        private val JPEG_START_FIRST = 0xFF.toByte()
        private val JPEG_START_SECOND = 0xD8.toByte()
    }
}
