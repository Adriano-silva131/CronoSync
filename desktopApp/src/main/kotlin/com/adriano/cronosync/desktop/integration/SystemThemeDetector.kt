package com.adriano.cronosync.desktop.integration

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Preferência de tema claro/escuro do sistema.
 *
 * [isDark] = null significa "não sei detectar aqui": a janela usa então o isSystemInDarkTheme()
 * do Compose, que funciona no Windows e no macOS mas no Linux sempre responde "claro".
 */
interface SystemThemeDetector {
    val isDark: StateFlow<Boolean?>
}

/** Windows/macOS: o próprio Compose já detecta; não há o que fazer aqui. */
object ComposeSystemThemeDetector : SystemThemeDetector {
    override val isDark: StateFlow<Boolean?> = MutableStateFlow(null)
}

fun createSystemThemeDetector(scope: CoroutineScope): SystemThemeDetector =
    if ("linux" in System.getProperty("os.name").orEmpty().lowercase()) LinuxSystemThemeDetector(scope) else ComposeSystemThemeDetector
