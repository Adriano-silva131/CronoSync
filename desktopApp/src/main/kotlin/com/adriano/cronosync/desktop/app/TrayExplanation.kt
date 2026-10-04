package com.adriano.cronosync.desktop.app

import com.adriano.cronosync.desktop.integration.notification.NotificationSender
import com.russhwolf.settings.Settings

class TrayExplanation(
    private val settings: Settings,
    private val notifications: NotificationSender,
) {
    fun showOnce() {
        if (settings.getBoolean(KEY_TRAY_EXPLAINED, false)) return
        settings.putBoolean(KEY_TRAY_EXPLAINED, true)
        notifications.send(
            "O CronoSync continua aberto",
            "Ele está na bandeja do sistema. Use o ícone para abrir de novo ou para sair.",
        )
    }

    private companion object {
        const val KEY_TRAY_EXPLAINED = "desktop.trayExplained"
    }
}
