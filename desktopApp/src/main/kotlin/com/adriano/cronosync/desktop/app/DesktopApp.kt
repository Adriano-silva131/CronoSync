package com.adriano.cronosync.desktop.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.rememberWindowState
import com.adriano.cronosync.desktop.integration.tray.TrayController
import com.adriano.cronosync.desktop.window.ExitConfirmationDialog
import com.adriano.cronosync.desktop.window.MainWindow
import com.adriano.cronosync.desktop.window.rememberAppIcon
import kotlinx.coroutines.flow.StateFlow

@Composable
fun ApplicationScope.DesktopApp(
    title: String,
    tray: TrayController,
    systemIsDark: StateFlow<Boolean?>,
    onFocused: () -> Unit,
    hasActiveCountdown: () -> Boolean,
    onHiddenToTray: () -> Unit,
) {
    val icon = rememberAppIcon()
    val windowState = rememberWindowState(size = DpSize(1280.dp, 720.dp))
    var windowVisible by remember { mutableStateOf(true) }
    var askingToExit by remember { mutableStateOf(false) }
    var trayInstalled by remember { mutableStateOf(false) }
    val showWindow = {
        windowVisible = true
        windowState.isMinimized = false
    }

    DisposableEffect(Unit) {
        trayInstalled = tray.install(onOpen = showWindow, onExit = ::exitApplication)
        onDispose { tray.remove() }
    }

    MainWindow(
        title = title,
        icon = icon,
        state = windowState,
        visible = windowVisible,
        systemIsDark = systemIsDark,
        onFocused = onFocused,
        onCloseRequest = {
            when (closeAction(trayInstalled, hasActiveCountdown())) {
                CloseAction.HideToTray -> {
                    windowVisible = false
                    onHiddenToTray()
                }
                CloseAction.AskBeforeExit -> askingToExit = true
                CloseAction.Exit -> exitApplication()
            }
        },
    )

    if (askingToExit) {
        ExitConfirmationDialog(
            onMinimize = {
                askingToExit = false
                windowState.isMinimized = true
            },
            onExit = ::exitApplication,
            onCancel = { askingToExit = false },
        )
    }
}
