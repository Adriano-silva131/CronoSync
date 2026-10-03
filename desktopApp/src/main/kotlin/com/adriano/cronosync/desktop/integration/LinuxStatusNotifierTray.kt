@file:Suppress("FunctionName") // os nomes dos métodos D-Bus são definidos pelos protocolos (PascalCase)

package com.adriano.cronosync.desktop.integration

import org.freedesktop.dbus.DBusPath
import org.freedesktop.dbus.Struct
import org.freedesktop.dbus.Tuple
import org.freedesktop.dbus.annotations.DBusBoundProperty
import org.freedesktop.dbus.annotations.DBusInterfaceName
import org.freedesktop.dbus.annotations.Position
import org.freedesktop.dbus.connections.impl.DBusConnection
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder
import org.freedesktop.dbus.interfaces.DBusInterface
import org.freedesktop.dbus.types.UInt32
import org.freedesktop.dbus.types.Variant
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import javax.swing.SwingUtilities

/**
 * Bandeja no Linux pelo protocolo moderno, StatusNotifierItem ("AppIndicator"), o mesmo do
 * Spotify e de outros apps: o app publica um objeto no D-Bus da sessão e se registra no
 * "observador" da barra (no GNOME, a extensão AppIndicator). Ganhos em relação ao AWT:
 * - transparência de verdade (o destaque da barra aparece através do ícone, sem "bordas");
 * - o ícone vai em vários tamanhos e a barra escolhe o certo (sem recortes);
 * - o menu é desenhado pela própria barra (protocolo com.canonical.dbusmenu), com o visual do sistema.
 *
 * Se não houver observador (barra sem suporte), [install] devolve false e o app usa o AWT.
 */
class LinuxStatusNotifierTray : TrayController {

    private var connection: DBusConnection? = null
    private var busName: String? = null

    override fun install(onOpen: () -> Unit, onExit: () -> Unit): Boolean {
        if (connection != null) return true
        // Ações chegam em threads do D-Bus: repassamos para a thread da interface (Swing).
        val open = { SwingUtilities.invokeLater(onOpen) }
        val exit = { SwingUtilities.invokeLater(onExit) }
        var conn: DBusConnection? = null
        return try {
            conn = DBusConnectionBuilder.forSessionBus().build()
            conn.exportObject(MENU_PATH, TrayMenu(open, exit))
            conn.exportObject(ITEM_PATH, TrayItem(iconPixmaps(), open))
            // Nome no formato que o protocolo recomenda: org.kde.StatusNotifierItem-<pid>-<n>.
            val name = "org.kde.StatusNotifierItem-${ProcessHandle.current().pid()}-1"
            conn.requestBusName(name)
            conn.getRemoteObject(WATCHER_BUS, WATCHER_PATH, StatusNotifierWatcher::class.java)
                .RegisterStatusNotifierItem(name)
            connection = conn
            busName = name
            true
        } catch (e: Exception) {
            // Sem D-Bus, sem observador (barra sem suporte)…: fica para a bandeja do AWT.
            System.err.println("CronoSync: bandeja moderna (StatusNotifierItem) indisponível, usando a do AWT: $e")
            runCatching { conn?.close() }
            false
        }
    }

    /** No Linux as notificações vão pelo notify-send, não pela bandeja. */
    override fun notify(title: String, message: String): Boolean = false

    override fun remove() {
        runCatching {
            busName?.let { connection?.releaseBusName(it) }
            connection?.close()
        }
        connection = null
        busName = null
    }

    /**
     * O ícone em vários tamanhos, como o protocolo pede: ARGB 32 bits, ordem de rede (big-endian),
     * com o canal alfa preservado — é ele que faz o fundo ser transparente.
     */
    private fun iconPixmaps(): List<Pixmap> {
        val source = javaClass.getResourceAsStream(TRAY_ICON_RESOURCE)?.use(ImageIO::read)
            ?: error("ícone da bandeja não encontrado")
        return listOf(16, 22, 24, 32, 48, 64).map { size -> scaled(source, size).toPixmap() }
    }

    private fun scaled(source: BufferedImage, size: Int): BufferedImage {
        // Reduz em etapas (ex.: 64 → 32 → 16): uma redução grande de uma vez serrilha o desenho.
        var current = source
        while (current.width / 2 >= size) current = resize(current, current.width / 2)
        return if (current.width == size) current else resize(current, size)
    }

    private fun resize(image: BufferedImage, size: Int) =
        BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB).apply {
            createGraphics().run {
                setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
                setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
                drawImage(image, 0, 0, size, size, null)
                dispose()
            }
        }

    private fun BufferedImage.toPixmap(): Pixmap {
        val bytes = ByteArray(width * height * 4)
        var i = 0
        for (y in 0 until height) {
            for (x in 0 until width) {
                val argb = getRGB(x, y)
                bytes[i++] = (argb ushr 24).toByte()
                bytes[i++] = (argb ushr 16).toByte()
                bytes[i++] = (argb ushr 8).toByte()
                bytes[i++] = argb.toByte()
            }
        }
        return Pixmap(width, height, bytes)
    }

    private companion object {
        const val ITEM_PATH = "/StatusNotifierItem"
        const val MENU_PATH = "/MenuBar"
        const val WATCHER_BUS = "org.kde.StatusNotifierWatcher"
        const val WATCHER_PATH = "/StatusNotifierWatcher"
    }
}

// ---------------------------------------------------------------------------------------------
// StatusNotifierItem: o ícone em si. Especificação:
// https://www.freedesktop.org/wiki/Specifications/StatusNotifierItem/
// ---------------------------------------------------------------------------------------------

@DBusInterfaceName("org.kde.StatusNotifierWatcher")
interface StatusNotifierWatcher : DBusInterface {
    fun RegisterStatusNotifierItem(service: String)
}

@DBusInterfaceName("org.kde.StatusNotifierItem")
interface StatusNotifierItem : DBusInterface {
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
class Pixmap(
    @field:Position(0) @JvmField val width: Int,
    @field:Position(1) @JvmField val height: Int,
    @field:Position(2) @JvmField val data: ByteArray,
) : Struct()

/** Dica ao passar o mouse (formato "(sa(iiay)ss)"). */
class ToolTip(
    @field:Position(0) @JvmField val iconName: String,
    @field:Position(1) @JvmField val iconPixmap: List<Pixmap>,
    @field:Position(2) @JvmField val title: String,
    @field:Position(3) @JvmField val description: String,
) : Struct()

private class TrayItem(
    private val pixmaps: List<Pixmap>,
    private val onOpen: () -> Unit,
) : StatusNotifierItem {
    override fun getObjectPath() = "/StatusNotifierItem"

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
    override fun getMenu() = DBusPath("/MenuBar")
}

// ---------------------------------------------------------------------------------------------
// com.canonical.dbusmenu: o menu do ícone, desenhado pela própria barra.
// ---------------------------------------------------------------------------------------------

@DBusInterfaceName("com.canonical.dbusmenu")
interface DBusMenu : DBusInterface {
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
class MenuLayout(
    @field:Position(0) @JvmField val id: Int,
    @field:Position(1) @JvmField val properties: Map<String, Variant<*>>,
    @field:Position(2) @JvmField val children: List<Variant<*>>,
) : Struct()

/**
 * Resposta com dois valores (ex.: GetLayout → revisão + layout). Precisa ser GENÉRICA: a biblioteca
 * descobre os tipos D-Bus pelos argumentos de tipo (DBusPair<UInt32, MenuLayout>); numa classe
 * comum ela só enxergaria "List" sem saber de quê, e o export falharia.
 */
class DBusPair<A, B>(
    @field:Position(0) @JvmField val first: A,
    @field:Position(1) @JvmField val second: B,
) : Tuple()

class MenuItemProperties(
    @field:Position(0) @JvmField val id: Int,
    @field:Position(1) @JvmField val properties: Map<String, Variant<*>>,
) : Struct()

class MenuEvent(
    @field:Position(0) @JvmField val id: Int,
    @field:Position(1) @JvmField val eventId: String,
    @field:Position(2) @JvmField val data: Variant<*>,
    @field:Position(3) @JvmField val timestamp: UInt32,
) : Struct()


/** Menu fixo: "Abrir CronoSync", separador, "Sair". O item 0 é a raiz (o próprio menu). */
private class TrayMenu(
    private val onOpen: () -> Unit,
    private val onExit: () -> Unit,
) : DBusMenu {
    override fun getObjectPath() = "/MenuBar"

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

    private companion object {
        const val ID_ROOT = 0
        const val ID_OPEN = 1
        const val ID_SEPARATOR = 2
        const val ID_EXIT = 3
        const val REVISION = 1L
    }
}
