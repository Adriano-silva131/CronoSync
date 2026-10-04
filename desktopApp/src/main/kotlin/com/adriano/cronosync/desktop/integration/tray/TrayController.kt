package com.adriano.cronosync.desktop.integration.tray

import com.adriano.cronosync.desktop.integration.tray.statusnotifier.LinuxStatusNotifierTray

interface TrayController {
    // onOpen/onExit precisam ser chamados na thread do Swing.
    fun install(onOpen: () -> Unit, onExit: () -> Unit): Boolean

    fun notify(title: String, message: String): Boolean

    fun remove()
}

fun createTrayController(): TrayController {
    val linux = System.getProperty("os.name").orEmpty().lowercase().contains("linux")
    return if (linux) FirstAvailableTray(listOf(LinuxStatusNotifierTray(), AwtTray())) else AwtTray()
}

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
