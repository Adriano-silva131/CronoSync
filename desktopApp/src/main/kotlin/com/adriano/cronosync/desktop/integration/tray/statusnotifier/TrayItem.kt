package com.adriano.cronosync.desktop.integration.tray.statusnotifier

import com.adriano.cronosync.desktop.integration.tray.statusnotifier.protocol.Pixmap
import com.adriano.cronosync.desktop.integration.tray.statusnotifier.protocol.StatusNotifierItem
import com.adriano.cronosync.desktop.integration.tray.statusnotifier.protocol.ToolTip
import org.freedesktop.dbus.DBusPath

internal class TrayItem(
    private val pixmaps: List<Pixmap>,
    private val onOpen: () -> Unit,
) : StatusNotifierItem {
    override fun getObjectPath() = PATH

    // Clique no ícone: a barra mostra o menu; "Activate" é o clique principal, se ela pedir.
    override fun Activate(x: Int, y: Int) = onOpen()
    override fun SecondaryActivate(x: Int, y: Int) = onOpen()
    override fun ContextMenu(x: Int, y: Int) = Unit
    override fun Scroll(delta: Int, orientation: String) = Unit

    override fun getCategory() = "ApplicationStatus"
    override fun getId() = "cronosync"
    override fun getTitle() = "CronoSync"
    override fun getStatus() = "Active"
    override fun getWindowId() = 0
    override fun getIconName() = ""
    override fun getIconPixmap() = pixmaps
    override fun getIconThemePath() = ""
    override fun getOverlayIconName() = ""
    override fun getOverlayIconPixmap() = emptyList<Pixmap>()
    override fun getAttentionIconName() = ""
    override fun getAttentionIconPixmap() = emptyList<Pixmap>()
    override fun getAttentionMovieName() = ""
    override fun getToolTip() = ToolTip("", emptyList(), "CronoSync", "")
    override fun getItemIsMenu() = false
    override fun getMenu() = DBusPath(TrayMenu.PATH)

    companion object {
        const val PATH = "/StatusNotifierItem"
    }
}
