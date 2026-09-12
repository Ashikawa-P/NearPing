package de.gabriel.nearping.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BackgroundAlertPolicyTest {
    @Test
    fun hiddenAppReceivesIncomingPingAlert() {
        assertEquals(
            BackgroundPingAlert.INCOMING_PING,
            BackgroundAlertPolicy.pendingPingAlert(
                pending = true,
                appVisible = false,
                hasDisplayName = true,
                isMutual = false,
            ),
        )
    }

    @Test
    fun hiddenAppReceivesMatchInsteadWhenLocalPingAlreadyExists() {
        assertEquals(
            BackgroundPingAlert.MATCH,
            BackgroundAlertPolicy.pendingPingAlert(
                pending = true,
                appVisible = false,
                hasDisplayName = true,
                isMutual = true,
            ),
        )
    }

    @Test
    fun visibleAppDoesNotCreateSystemAlert() {
        assertNull(
            BackgroundAlertPolicy.pendingPingAlert(
                pending = true,
                appVisible = true,
                hasDisplayName = true,
                isMutual = true,
            ),
        )
    }

    @Test
    fun alertWaitsUntilHelloProvidesAName() {
        assertNull(
            BackgroundAlertPolicy.pendingPingAlert(
                pending = true,
                appVisible = false,
                hasDisplayName = false,
                isMutual = false,
            ),
        )
    }
}
