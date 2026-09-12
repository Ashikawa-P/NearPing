package de.gabriel.nearping.transport

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PeerRouteRegistryTest {
    @Test
    fun nearbyAndLanRoutesBecomeOneLogicalSession() {
        val routes = PeerRouteRegistry()

        routes.connect("nearby:one", "session-b", TransportKind.NEARBY)
        routes.connect("lan:one", "session-b", TransportKind.LAN)

        assertEquals("session-b", routes.sessionForEndpoint("nearby:one"))
        assertEquals(setOf("nearby:one", "lan:one"), routes.endpointsForSession("session-b"))
        assertEquals(
            setOf(TransportKind.NEARBY, TransportKind.LAN),
            routes.kindsForSession("session-b"),
        )
    }

    @Test
    fun peerRemainsUntilItsLastRouteDisconnects() {
        val routes = PeerRouteRegistry()
        routes.connect("nearby:one", "session-b", TransportKind.NEARBY)
        routes.connect("lan:one", "session-b", TransportKind.LAN)

        val first = routes.disconnect("nearby:one")
        assertEquals("session-b", first?.sessionId)
        assertTrue(first?.hasRemainingRoutes == true)
        assertEquals(setOf(TransportKind.LAN), routes.kindsForSession("session-b"))

        val second = routes.disconnect("lan:one")
        assertEquals("session-b", second?.sessionId)
        assertFalse(second?.hasRemainingRoutes ?: true)
        assertTrue(routes.endpointsForSession("session-b").isEmpty())
    }

    @Test
    fun reconnectingEndpointCannotLeakItsPreviousSession() {
        val routes = PeerRouteRegistry()
        routes.connect("route", "old-session", TransportKind.LAN)
        routes.connect("route", "new-session", TransportKind.LAN)

        assertNull(routes.sessionForEndpoint("missing"))
        assertTrue(routes.endpointsForSession("old-session").isEmpty())
        assertEquals(setOf("route"), routes.endpointsForSession("new-session"))
        assertEquals(1, routes.endpointCount)
    }
}
