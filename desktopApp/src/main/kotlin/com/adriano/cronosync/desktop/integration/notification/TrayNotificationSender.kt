package com.adriano.cronosync.desktop.integration.notification

import com.adriano.cronosync.desktop.integration.tray.TrayController

class TrayNotificationSender(private val tray: TrayController) : NotificationSender {

    override fun send(title: String, message: String) {
        tray.notify(title, message)
    }
}
