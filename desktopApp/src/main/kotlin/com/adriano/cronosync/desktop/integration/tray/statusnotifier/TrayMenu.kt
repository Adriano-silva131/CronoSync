package com.adriano.cronosync.desktop.integration.tray.statusnotifier

import com.adriano.cronosync.desktop.integration.tray.statusnotifier.protocol.DBusMenu
import com.adriano.cronosync.desktop.integration.tray.statusnotifier.protocol.DBusPair
import com.adriano.cronosync.desktop.integration.tray.statusnotifier.protocol.MenuEvent
import com.adriano.cronosync.desktop.integration.tray.statusnotifier.protocol.MenuItemProperties
import com.adriano.cronosync.desktop.integration.tray.statusnotifier.protocol.MenuLayout
import org.freedesktop.dbus.types.UInt32
import org.freedesktop.dbus.types.Variant

/** Menu fixo: "Abrir CronoSync", separador, "Sair". O item 0 é a raiz (o próprio menu). */
internal class TrayMenu(
    private val onOpen: () -> Unit,
    private val onExit: () -> Unit,
) : DBusMenu {
    override fun getObjectPath() = PATH

    private val items: Map<Int, Map<String, Variant<*>>> = mapOf(
        ID_OPEN to mapOf("label" to Variant("Abrir CronoSync")),
        ID_SEPARATOR to mapOf("type" to Variant("separator")),
        ID_EXIT to mapOf("label" to Variant("Sair")),
    )

    override fun GetLayout(parentId: Int, recursionDepth: Int, propertyNames: List<String>): DBusPair<UInt32, MenuLayout> {
        val children = items.keys.map { id -> Variant(MenuLayout(id, items.getValue(id), emptyList()), "(ia{sv}av)") }
        val root = MenuLayout(ID_ROOT, mapOf("children-display" to Variant("submenu")), children)
        val layout = if (parentId == ID_ROOT) root else MenuLayout(parentId, items[parentId].orEmpty(), emptyList())
        return DBusPair(UInt32(REVISION), layout)
    }

    override fun GetGroupProperties(ids: List<Int>, propertyNames: List<String>) =
        ids.filter { it in items }.map { MenuItemProperties(it, items.getValue(it)) }

    override fun GetProperty(id: Int, name: String): Variant<*> = items[id]?.get(name) ?: Variant("")

    override fun Event(id: Int, eventId: String, data: Variant<*>, timestamp: UInt32) {
        if (eventId != "clicked") return
        when (id) {
            ID_OPEN -> onOpen()
            ID_EXIT -> onExit()
        }
    }

    override fun EventGroup(events: List<MenuEvent>): List<Int> {
        events.forEach { Event(it.id, it.eventId, it.data, it.timestamp) }
        return emptyList()
    }

    override fun AboutToShow(id: Int) = false // o menu nunca muda: nada a atualizar
    override fun AboutToShowGroup(ids: List<Int>) = DBusPair<List<Int>, List<Int>>(emptyList(), emptyList())

    override fun getVersion() = UInt32(3)
    override fun getTextDirection() = "ltr"
    override fun getStatus() = "normal"
    override fun getIconThemePath() = emptyList<String>()

    companion object {
        const val PATH = "/MenuBar"
        private const val ID_ROOT = 0
        private const val ID_OPEN = 1
        private const val ID_SEPARATOR = 2
        private const val ID_EXIT = 3
        private const val REVISION = 1L
    }
}
