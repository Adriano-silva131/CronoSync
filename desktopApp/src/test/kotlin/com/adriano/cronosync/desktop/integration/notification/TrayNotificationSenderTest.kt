package com.adriano.cronosync.desktop.integration.notification

import com.adriano.cronosync.desktop.integration.tray.TrayController
import kotlin.test.Test
import kotlin.test.assertEquals

class TrayNotificationSenderTest {

    private val trayNotifications = mutableListOf<Pair<String, String>>()
    private var trayAvailable = true

    private val tray = object : TrayController {
        override fun install(onOpen: () -> Unit, onExit: () -> Unit) = true
        override fun notify(title: String, message: String): Boolean {
            if (trayAvailable) trayNotifications += title to message
            return trayAvailable
        }
        override fun remove() = Unit
    }

    @Test
    fun sendDelegatesToTheTrayIcon() {
        TrayNotificationSender(tray).send("Tempo esgotado!", "O timer chegou ao fim.")

        assertEquals(listOf("Tempo esgotado!" to "O timer chegou ao fim."), trayNotifications)
    }

    @Test
    fun sendWithoutTrayDoesNotFail() {
        trayAvailable = false

        TrayNotificationSender(tray).send("Tempo esgotado!", "O timer chegou ao fim.")

        assertEquals(emptyList(), trayNotifications)
    }
}
