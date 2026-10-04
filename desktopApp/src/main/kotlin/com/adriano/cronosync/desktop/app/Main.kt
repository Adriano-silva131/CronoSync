package com.adriano.cronosync.desktop.app

import androidx.compose.ui.window.application
import com.adriano.cronosync.desktop.alarm.DesktopPomodoroAlarm
import com.adriano.cronosync.desktop.alarm.DesktopTimerAlarm
import com.adriano.cronosync.desktop.integration.theme.SystemThemeDetector
import com.adriano.cronosync.desktop.integration.window.applyLinuxWindowClass
import com.adriano.cronosync.di.initKoin
import com.adriano.cronosync.pomodoro.data.PomodoroRepository
import com.adriano.cronosync.sync.data.AppEnvironment
import com.adriano.cronosync.sync.data.SyncSession
import com.adriano.cronosync.timer.data.TimerRepository

fun main() {
    applyLinuxWindowClass()
    val koin = initKoin { modules(desktopModule) }.koin

    val sync = koin.get<SyncSession>()
    val timer = koin.get<TimerRepository>()
    val pomodoro = koin.get<PomodoroRepository>()
    koin.get<DesktopTimerAlarm>().start(scope = koin.get())
    koin.get<DesktopPomodoroAlarm>().start(scope = koin.get())
    val trayExplanation = koin.get<TrayExplanation>()

    application {
        DesktopApp(
            title = windowTitle(appEnvironment()),
            tray = koin.get(),
            systemIsDark = koin.get<SystemThemeDetector>().isDark,
            onFocused = sync::reconnectNow,
            hasActiveCountdown = { hasActiveCountdown(timer.timer.value, pomodoro.pomodoro.value) },
            onHiddenToTray = trayExplanation::showOnce,
        )
    }
}

private fun windowTitle(environment: AppEnvironment): String = when (environment) {
    AppEnvironment.LocalTesting -> "CronoSync — testes locais"
    AppEnvironment.Staging -> "CronoSync — homologação"
    AppEnvironment.Production -> "CronoSync"
}
