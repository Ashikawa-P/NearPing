package de.gabriel.nearping.logic

import de.gabriel.nearping.model.PeerIndicator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PingStateTest {
    @Test
    fun noPingHasNoIndicator() {
        assertEquals(PeerIndicator.NONE, PingState().indicator)
        assertFalse(PingState().isMutual)
    }

    @Test
    fun eitherOneSidedPingIsRed() {
        assertEquals(PeerIndicator.RED, PingState(localPinged = true).indicator)
        assertEquals(PeerIndicator.RED, PingState(remotePinged = true).indicator)
    }

    @Test
    fun mutualPingIsGreen() {
        val state = PingState(localPinged = true, remotePinged = true)
        assertEquals(PeerIndicator.GREEN, state.indicator)
        assertTrue(state.isMutual)
    }
}
