package de.gabriel.nearping.transport

/** Maps transport-specific endpoints to one logical peer session. */
class PeerRouteRegistry {
    data class DisconnectedRoute(
        val sessionId: String,
        val hasRemainingRoutes: Boolean,
    )

    private data class Route(
        val sessionId: String,
        val kind: TransportKind,
    )

    private val routesByEndpoint = linkedMapOf<String, Route>()
    private val endpointsBySession = linkedMapOf<String, LinkedHashSet<String>>()

    val endpointCount: Int
        get() = routesByEndpoint.size

    fun connect(endpointId: String, sessionId: String, kind: TransportKind) {
        require(endpointId.isNotBlank())
        require(sessionId.isNotBlank())

        val previous = routesByEndpoint[endpointId]
        if (previous != null && previous.sessionId != sessionId) {
            endpointsBySession[previous.sessionId]?.let { endpoints ->
                endpoints -= endpointId
                if (endpoints.isEmpty()) endpointsBySession.remove(previous.sessionId)
            }
        }
        routesByEndpoint[endpointId] = Route(sessionId, kind)
        endpointsBySession.getOrPut(sessionId, ::linkedSetOf) += endpointId
    }

    fun disconnect(endpointId: String): DisconnectedRoute? {
        val route = routesByEndpoint.remove(endpointId) ?: return null
        val endpoints = endpointsBySession[route.sessionId]
        endpoints?.remove(endpointId)
        val hasRemainingRoutes = !endpoints.isNullOrEmpty()
        if (!hasRemainingRoutes) endpointsBySession.remove(route.sessionId)
        return DisconnectedRoute(route.sessionId, hasRemainingRoutes)
    }

    fun sessionForEndpoint(endpointId: String): String? =
        routesByEndpoint[endpointId]?.sessionId

    fun endpointsForSession(sessionId: String): Set<String> =
        endpointsBySession[sessionId]?.toSet().orEmpty()

    fun kindsForSession(sessionId: String): Set<TransportKind> =
        endpointsBySession[sessionId]
            .orEmpty()
            .mapNotNullTo(linkedSetOf()) { routesByEndpoint[it]?.kind }

    fun clear() {
        routesByEndpoint.clear()
        endpointsBySession.clear()
    }
}
