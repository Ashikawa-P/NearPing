package de.gabriel.nearping.logic

import de.gabriel.nearping.model.PeerIndicator

data class PingState(
    val localPinged: Boolean = false,
    val remotePinged: Boolean = false,
) {
    val isMutual: Boolean
        get() = localPinged && remotePinged

    val indicator: PeerIndicator
        get() = when {
            isMutual -> PeerIndicator.GREEN
            localPinged || remotePinged -> PeerIndicator.RED
            else -> PeerIndicator.NONE
        }
}
