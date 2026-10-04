@file:Suppress("FunctionName") // os nomes dos métodos D-Bus são definidos pelos protocolos (PascalCase)

package com.adriano.cronosync.desktop.integration.tray.statusnotifier.protocol

import org.freedesktop.dbus.DBusPath
import org.freedesktop.dbus.Struct
import org.freedesktop.dbus.annotations.DBusBoundProperty
import org.freedesktop.dbus.annotations.DBusInterfaceName
import org.freedesktop.dbus.annotations.Position
import org.freedesktop.dbus.interfaces.DBusInterface

@DBusInterfaceName("org.kde.StatusNotifierWatcher")
internal interface StatusNotifierWatcher : DBusInterface {
    fun RegisterStatusNotifierItem(service: String)
}

@DBusInterfaceName("org.kde.StatusNotifierItem")
internal interface StatusNotifierItem : DBusInterface {
    fun Activate(x: Int, y: Int)
    fun SecondaryActivate(x: Int, y: Int)
    fun ContextMenu(x: Int, y: Int)
    fun Scroll(delta: Int, orientation: String)

    @DBusBoundProperty(name = "Category") fun getCategory(): String
    @DBusBoundProperty(name = "Id") fun getId(): String
    @DBusBoundProperty(name = "Title") fun getTitle(): String
    @DBusBoundProperty(name = "Status") fun getStatus(): String
    @DBusBoundProperty(name = "WindowId") fun getWindowId(): Int
    @DBusBoundProperty(name = "IconName") fun getIconName(): String
    @DBusBoundProperty(name = "IconPixmap") fun getIconPixmap(): List<Pixmap>
    @DBusBoundProperty(name = "IconThemePath") fun getIconThemePath(): String
    @DBusBoundProperty(name = "OverlayIconName") fun getOverlayIconName(): String
    @DBusBoundProperty(name = "OverlayIconPixmap") fun getOverlayIconPixmap(): List<Pixmap>
    @DBusBoundProperty(name = "AttentionIconName") fun getAttentionIconName(): String
    @DBusBoundProperty(name = "AttentionIconPixmap") fun getAttentionIconPixmap(): List<Pixmap>
    @DBusBoundProperty(name = "AttentionMovieName") fun getAttentionMovieName(): String
    @DBusBoundProperty(name = "ToolTip") fun getToolTip(): ToolTip
    @DBusBoundProperty(name = "ItemIsMenu") fun getItemIsMenu(): Boolean
    @DBusBoundProperty(name = "Menu") fun getMenu(): DBusPath
}

/** Uma imagem do ícone: largura, altura e pixels ARGB (formato "(iiay)"). */
internal class Pixmap(
    @field:Position(0) @JvmField val width: Int,
    @field:Position(1) @JvmField val height: Int,
    @field:Position(2) @JvmField val data: ByteArray,
) : Struct()

/** Dica ao passar o mouse (formato "(sa(iiay)ss)"). */
internal class ToolTip(
    @field:Position(0) @JvmField val iconName: String,
    @field:Position(1) @JvmField val iconPixmap: List<Pixmap>,
    @field:Position(2) @JvmField val title: String,
    @field:Position(3) @JvmField val description: String,
) : Struct()
