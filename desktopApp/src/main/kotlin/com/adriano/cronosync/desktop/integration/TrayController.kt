package com.adriano.cronosync.desktop.integration

/**
 * Ícone do app na bandeja do sistema. É por ele que o app continua acessível com a janela oculta.
 *
 * Interface porque há uma diferença CONCRETA entre os sistemas:
 * - Linux: protocolo moderno de bandeja (StatusNotifierItem via D-Bus) — o mesmo do Spotify, com
 *   transparência, tamanho certo e menu nativo da barra ([LinuxStatusNotifierTray]);
 * - Windows (e reserva no Linux): bandeja do AWT ([AwtTray]).
 */
interface TrayController {
    /**
     * Mostra o ícone. [onOpen]/[onExit] são chamados na thread da interface (Swing).
     * @return false se o sistema não tem bandeja (aí o app não pode se esconder ao fechar).
     */
    fun install(onOpen: () -> Unit, onExit: () -> Unit): Boolean

    /** Balão de notificação pelo próprio ícone, quando o sistema oferece. @return false se não deu. */
    fun notify(title: String, message: String): Boolean

    fun remove()
}

fun createTrayController(): TrayController {
    val linux = System.getProperty("os.name").orEmpty().lowercase().contains("linux")
    return if (linux) FirstAvailableTray(listOf(LinuxStatusNotifierTray(), AwtTray())) else AwtTray()
}

/** Usa a primeira bandeja que conseguir se instalar (ex.: protocolo moderno; senão, o AWT). */
private class FirstAvailableTray(private val candidates: List<TrayController>) : TrayController {
    private var active: TrayController? = null

    override fun install(onOpen: () -> Unit, onExit: () -> Unit): Boolean {
        active = candidates.firstOrNull { it.install(onOpen, onExit) }
        return active != null
    }

    override fun notify(title: String, message: String): Boolean = active?.notify(title, message) ?: false

    override fun remove() {
        active?.remove()
        active = null
    }
}
