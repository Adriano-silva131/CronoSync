@file:Suppress("FunctionName") // os nomes dos métodos D-Bus são definidos pelos protocolos (PascalCase)

package com.adriano.cronosync.desktop.integration.tray.statusnotifier.protocol

import org.freedesktop.dbus.Struct
import org.freedesktop.dbus.Tuple
import org.freedesktop.dbus.annotations.DBusBoundProperty
import org.freedesktop.dbus.annotations.DBusInterfaceName
import org.freedesktop.dbus.annotations.Position
import org.freedesktop.dbus.interfaces.DBusInterface
import org.freedesktop.dbus.types.UInt32
import org.freedesktop.dbus.types.Variant

@DBusInterfaceName("com.canonical.dbusmenu")
internal interface DBusMenu : DBusInterface {
    fun GetLayout(parentId: Int, recursionDepth: Int, propertyNames: List<String>): DBusPair<UInt32, MenuLayout>
    fun GetGroupProperties(ids: List<Int>, propertyNames: List<String>): List<MenuItemProperties>
    fun GetProperty(id: Int, name: String): Variant<*>
    fun Event(id: Int, eventId: String, data: Variant<*>, timestamp: UInt32)
    fun EventGroup(events: List<MenuEvent>): List<Int>
    fun AboutToShow(id: Int): Boolean
    fun AboutToShowGroup(ids: List<Int>): DBusPair<List<Int>, List<Int>>

    @DBusBoundProperty(name = "Version") fun getVersion(): UInt32
    @DBusBoundProperty(name = "TextDirection") fun getTextDirection(): String
    @DBusBoundProperty(name = "Status") fun getStatus(): String
    @DBusBoundProperty(name = "IconThemePath") fun getIconThemePath(): List<String>
}

/** Item do menu e seus filhos (formato "(ia{sv}av)"). */
internal class MenuLayout(
    @field:Position(0) @JvmField val id: Int,
    @field:Position(1) @JvmField val properties: Map<String, Variant<*>>,
    @field:Position(2) @JvmField val children: List<Variant<*>>,
) : Struct()

/**
 * Resposta com dois valores (ex.: GetLayout → revisão + layout). Precisa ser GENÉRICA: a biblioteca
 * descobre os tipos D-Bus pelos argumentos de tipo (DBusPair<UInt32, MenuLayout>); numa classe
 * comum ela só enxergaria "List" sem saber de quê, e o export falharia.
 */
internal class DBusPair<A, B>(
    @field:Position(0) @JvmField val first: A,
    @field:Position(1) @JvmField val second: B,
) : Tuple()

internal class MenuItemProperties(
    @field:Position(0) @JvmField val id: Int,
    @field:Position(1) @JvmField val properties: Map<String, Variant<*>>,
) : Struct()

internal class MenuEvent(
    @field:Position(0) @JvmField val id: Int,
    @field:Position(1) @JvmField val eventId: String,
    @field:Position(2) @JvmField val data: Variant<*>,
    @field:Position(3) @JvmField val timestamp: UInt32,
) : Struct()
