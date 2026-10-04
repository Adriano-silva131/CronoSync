package com.adriano.cronosync.desktop.integration.tray

import java.awt.MenuItem
import java.awt.PopupMenu
import java.awt.SystemTray
import java.awt.TrayIcon
import javax.imageio.ImageIO

class AwtTray : TrayController {

    private var trayIcon: TrayIcon? = null

    override fun install(onOpen: () -> Unit, onExit: () -> Unit): Boolean {
        if (trayIcon != null) return true
        if (!runCatching { SystemTray.isSupported() }.getOrDefault(false)) return false
        val image = javaClass.getResourceAsStream(TRAY_ICON_RESOURCE)?.use(ImageIO::read) ?: return false
        val menu = PopupMenu().apply {
            add(MenuItem("Abrir CronoSync").apply { addActionListener { onOpen() } })
            addSeparator()
            add(MenuItem("Sair").apply { addActionListener { onExit() } })
        }
        val icon = TrayIcon(image, "CronoSync", menu).apply {
            isImageAutoSize = true
            addActionListener { onOpen() }
        }
        return runCatching { SystemTray.getSystemTray().add(icon) }
            .onSuccess { trayIcon = icon }
            .isSuccess
    }

    override fun notify(title: String, message: String): Boolean {
        val icon = trayIcon ?: return false
        icon.displayMessage(title, message, TrayIcon.MessageType.INFO)
        return true
    }

    override fun remove() {
        trayIcon?.let { SystemTray.getSystemTray().remove(it) }
        trayIcon = null
    }
}

internal const val TRAY_ICON_RESOURCE = "/tray-icon.png"
