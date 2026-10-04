package com.adriano.cronosync.desktop.integration.theme

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

interface SystemThemeDetector {
    val isDark: StateFlow<Boolean?>
}

object ComposeSystemThemeDetector : SystemThemeDetector {
    override val isDark: StateFlow<Boolean?> = MutableStateFlow(null)
}

fun createSystemThemeDetector(scope: CoroutineScope): SystemThemeDetector =
    if ("linux" in System.getProperty("os.name").orEmpty().lowercase()) LinuxSystemThemeDetector(scope) else ComposeSystemThemeDetector
