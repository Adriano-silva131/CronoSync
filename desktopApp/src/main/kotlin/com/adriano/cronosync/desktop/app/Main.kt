package com.adriano.cronosync.desktop.app

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.adriano.cronosync.desktop.alarm.DesktopPomodoroAlarm
import com.adriano.cronosync.desktop.alarm.DesktopTimerAlarm
import com.adriano.cronosync.desktop.integration.NotificationSender
import com.adriano.cronosync.desktop.integration.SystemThemeDetector
import com.adriano.cronosync.desktop.integration.TrayController
import com.adriano.cronosync.desktop.integration.applyLinuxWindowClass
import com.adriano.cronosync.desktop.window.ExitConfirmationDialog
import com.adriano.cronosync.desktop.window.MainWindow
import com.adriano.cronosync.desktop.window.rememberAppIcon
import com.adriano.cronosync.di.initKoin
import com.adriano.cronosync.pomodoro.data.PomodoroRepository
import com.adriano.cronosync.pomodoro.domain.PomodoroStatus
import com.adriano.cronosync.sync.AppEnvironment
import com.adriano.cronosync.sync.SyncSession
import com.adriano.cronosync.timer.data.TimerRepository
import com.adriano.cronosync.timer.domain.TimerStatus
import com.russhwolf.settings.Settings
import org.koin.core.Koin

fun main() {
    // Antes de qualquer janela existir: liga as janelas ao atalho do menu no Linux (ícone na dock).
    applyLinuxWindowClass()
    val koin = initKoin { modules(desktopModule) }.koin

    // Sincronização, timer, Pomodoro e seus avisos pertencem ao ciclo de vida do APLICATIVO, não da
    // janela: criados já na partida, continuam valendo com a janela oculta na bandeja.
    koin.get<SyncSession>()
    koin.get<TimerRepository>()
    koin.get<PomodoroRepository>()
    koin.get<DesktopTimerAlarm>().start(scope = koin.get())
    koin.get<DesktopPomodoroAlarm>().start(scope = koin.get())

    application {
        val icon = rememberAppIcon()
        // Larga o bastante para os três modos lado a lado; estreitando, vira abas.
        val windowState = rememberWindowState(size = DpSize(1280.dp, 720.dp))
        var windowVisible by remember { mutableStateOf(true) }
        var askingToExit by remember { mutableStateOf(false) }
        var trayInstalled by remember { mutableStateOf(false) }
        val showWindow = {
            windowVisible = true
            windowState.isMinimized = false
        }

        // Ícone na bandeja enquanto o app existir. Se o sistema não tiver bandeja, trayInstalled
        // fica false e fechar a janela não a esconde (não haveria como recuperá-la).
        val tray = koin.get<TrayController>()
        DisposableEffect(Unit) {
            trayInstalled = tray.install(onOpen = showWindow, onExit = ::exitApplication)
            onDispose { tray.remove() }
        }

        MainWindow(
            // O título deixa claro quando é uma versão de testes.
            title = when (appEnvironment()) {
                AppEnvironment.LocalTesting -> "CronoSync — testes locais"
                AppEnvironment.Staging -> "CronoSync — homologação"
                AppEnvironment.Production -> "CronoSync"
            },
            icon = icon,
            state = windowState,
            visible = windowVisible,
            systemIsDark = koin.get<SystemThemeDetector>().isDark,
            onFocused = { koin.get<SyncSession>().reconnectNow() },
            onCloseRequest = {
                when (closeAction(trayInstalled, hasActiveCountdown(koin))) {
                    CloseAction.HideToTray -> {
                        windowVisible = false
                        explainTrayOnce(koin)
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
}

/** Algo contando agora — fechar o app sem bandeja faria o aviso do fim não acontecer. */
private fun hasActiveCountdown(koin: Koin): Boolean =
    koin.get<TimerRepository>().timer.value.status == TimerStatus.Running ||
        koin.get<PomodoroRepository>().pomodoro.value.status == PomodoroStatus.Running

/** Na primeira vez que a janela vai para a bandeja, explica onde o app foi parar. */
private fun explainTrayOnce(koin: Koin) {
    val settings = koin.get<Settings>()
    if (settings.getBoolean(KEY_TRAY_EXPLAINED, false)) return
    settings.putBoolean(KEY_TRAY_EXPLAINED, true)
    koin.get<NotificationSender>().send(
        "O CronoSync continua aberto",
        "Ele está na bandeja do sistema. Use o ícone para abrir de novo ou para sair.",
    )
}

private const val KEY_TRAY_EXPLAINED = "desktop.trayExplained"
