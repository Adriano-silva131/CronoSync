package com.adriano.cronosync.desktop.integration.notification

import org.freedesktop.dbus.annotations.DBusInterfaceName
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder
import org.freedesktop.dbus.interfaces.DBusInterface
import org.freedesktop.dbus.types.UInt32
import org.freedesktop.dbus.types.Variant
import java.util.concurrent.Executor
import kotlin.concurrent.thread

class LinuxNotificationSender(
    private val fallback: NotificationSender,
    private val executor: Executor = Executor { task -> thread(isDaemon = true, name = "cronosync-notification") { task.run() } },
    private val deliver: (title: String, message: String) -> Unit = ::notifyOverDBus,
) : NotificationSender {

    override fun send(title: String, message: String) {
        executor.execute {
            try {
                deliver(title, message)
            } catch (e: Exception) {
                fallback.send(title, message)
            }
        }
    }
}

private fun notifyOverDBus(title: String, message: String) {
    DBusConnectionBuilder.forSessionBus().withShared(false).build().use { connection ->
        connection.getRemoteObject(NOTIFICATIONS_BUS, NOTIFICATIONS_PATH, FreedesktopNotifications::class.java)
            .Notify(
                appName = "CronoSync",
                replacesId = UInt32(0),
                appIcon = "alarm-symbolic",
                summary = title,
                body = message,
                actions = emptyList(),
                hints = mapOf("urgency" to Variant(URGENCY_NORMAL)),
                expireTimeout = -1,
            )
    }
}

private const val NOTIFICATIONS_BUS = "org.freedesktop.Notifications"
private const val NOTIFICATIONS_PATH = "/org/freedesktop/Notifications"
private const val URGENCY_NORMAL: Byte = 1

@Suppress("FunctionName")
@DBusInterfaceName("org.freedesktop.Notifications")
internal interface FreedesktopNotifications : DBusInterface {
    fun Notify(
        appName: String,
        replacesId: UInt32,
        appIcon: String,
        summary: String,
        body: String,
        actions: List<String>,
        hints: Map<String, Variant<*>>,
        expireTimeout: Int,
    ): UInt32
}
