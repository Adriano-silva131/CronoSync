package com.adriano.cronosync.desktop.integration.tray.statusnotifier

import com.adriano.cronosync.desktop.integration.tray.TrayController
import com.adriano.cronosync.desktop.integration.tray.statusnotifier.protocol.StatusNotifierWatcher
import org.freedesktop.dbus.connections.impl.DBusConnection
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder
import javax.swing.SwingUtilities

class LinuxStatusNotifierTray : TrayController {

    private var connection: DBusConnection? = null
    private var busName: String? = null

    override fun install(onOpen: () -> Unit, onExit: () -> Unit): Boolean {
        if (connection != null) return true
        val open = { SwingUtilities.invokeLater(onOpen) }
        val exit = { SwingUtilities.invokeLater(onExit) }
        var conn: DBusConnection? = null
        return try {
            conn = DBusConnectionBuilder.forSessionBus().build()
            conn.exportObject(TrayMenu.PATH, TrayMenu(open, exit))
            conn.exportObject(TrayItem.PATH, TrayItem(loadTrayIconPixmaps(), open))
            val name = "org.kde.StatusNotifierItem-${ProcessHandle.current().pid()}-1"
            conn.requestBusName(name)
            conn.getRemoteObject(WATCHER_BUS, WATCHER_PATH, StatusNotifierWatcher::class.java)
                .RegisterStatusNotifierItem(name)
            connection = conn
            busName = name
            true
        } catch (e: Exception) {
            System.err.println("CronoSync: bandeja moderna (StatusNotifierItem) indisponível, usando a do AWT: $e")
            runCatching { conn?.close() }
            false
        }
    }

    override fun notify(title: String, message: String): Boolean = false

    override fun remove() {
        runCatching {
            busName?.let { connection?.releaseBusName(it) }
            connection?.close()
        }
        connection = null
        busName = null
    }

    private companion object {
        const val WATCHER_BUS = "org.kde.StatusNotifierWatcher"
        const val WATCHER_PATH = "/StatusNotifierWatcher"
    }
}
