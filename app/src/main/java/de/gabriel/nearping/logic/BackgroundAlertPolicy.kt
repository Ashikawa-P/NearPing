package de.gabriel.nearping.logic

enum class BackgroundPingAlert {
    INCOMING_PING,
    MATCH,
}

object BackgroundAlertPolicy {
    fun pendingPingAlert(
        pending: Boolean,
        appVisible: Boolean,
        hasDisplayName: Boolean,
        isMutual: Boolean,
    ): BackgroundPingAlert? {
        if (!pending || appVisible || !hasDisplayName) return null
        return if (isMutual) BackgroundPingAlert.MATCH else BackgroundPingAlert.INCOMING_PING
    }
}
