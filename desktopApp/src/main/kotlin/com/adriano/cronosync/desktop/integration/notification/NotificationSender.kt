package com.adriano.cronosync.desktop.integration.notification

import com.adriano.cronosync.desktop.integration.tray.TrayController

fun interface NotificationSender {
    fun send(title: String, message: String)
}

fun createNotificationSender(tray: TrayController): NotificationSender {
    val trayNotifications = TrayNotificationSender(tray)
    val linux = "linux" in System.getProperty("os.name").orEmpty().lowercase()
    return if (linux) LinuxNotificationSender(fallback = trayNotifications) else trayNotifications
}
