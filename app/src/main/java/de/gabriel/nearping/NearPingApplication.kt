package de.gabriel.nearping

import android.app.Application

class NearPingApplication : Application() {
    lateinit var notifications: NearPingNotifications
        private set

    lateinit var sessionController: AppViewModel
        private set

    override fun onCreate() {
        super.onCreate()
        notifications = NearPingNotifications(this)
        sessionController = AppViewModel(this, notifications)
    }
}
