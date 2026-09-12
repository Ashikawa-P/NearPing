package de.gabriel.nearping.model

import de.gabriel.nearping.transport.TransportKind

enum class AppScreen {
    SETUP,
    CAMERA,
    NEARBY,
    PROFILE,
}

enum class PeerIndicator {
    NONE,
    RED,
    GREEN,
}

data class PeerUiModel(
    val endpointId: String,
    val sessionId: String,
    val displayName: String,
    val indicator: PeerIndicator,
    val localPinged: Boolean,
    val remotePinged: Boolean,
    val photoJpeg: ByteArray? = null,
    val coordinationEvents: List<CoordinationEventUiModel> = emptyList(),
    val transportKinds: Set<TransportKind> = emptySet(),
)

data class CoordinationEventUiModel(
    val messageId: String,
    val text: String,
    val outgoing: Boolean,
)

data class AppUiState(
    val screen: AppScreen = AppScreen.SETUP,
    val enteredName: String = "",
    val selfieJpeg: ByteArray? = null,
    val sessionActive: Boolean = false,
    val sessionStatus: String = "Noch nicht aktiv",
    val peers: List<PeerUiModel> = emptyList(),
    val selectedPeer: PeerUiModel? = null,
    val errorMessage: String? = null,
)
