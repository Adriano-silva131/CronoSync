package com.adriano.cronosync.desktop.app

import com.adriano.cronosync.desktop.alarm.DesktopPomodoroAlarm
import com.adriano.cronosync.desktop.alarm.DesktopTimerAlarm
import com.adriano.cronosync.desktop.integration.audio.AlarmPlayer
import com.adriano.cronosync.desktop.integration.audio.ChimeAlarmPlayer
import com.adriano.cronosync.desktop.integration.notification.NotificationSender
import com.adriano.cronosync.desktop.integration.notification.createNotificationSender
import com.adriano.cronosync.desktop.integration.theme.SystemThemeDetector
import com.adriano.cronosync.desktop.integration.theme.createSystemThemeDetector
import com.adriano.cronosync.desktop.integration.tray.createTrayController
import com.adriano.cronosync.sync.data.AppEnvironment
import com.adriano.cronosync.sync.data.SyncConfig
import com.russhwolf.settings.PreferencesSettings
import com.russhwolf.settings.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.dsl.module
import java.util.prefs.Preferences

val desktopModule = module {
    // java.util.prefs: no Windows grava no Registro; no Linux, em ~/.java/.userPrefs.
    // A homologação guarda à parte: a sala salva de um servidor não vaza para o outro.
    single<Settings> {
        val node = if (appEnvironment() == AppEnvironment.Staging) "com/adriano/cronosync-homologacao" else "com/adriano/cronosync"
        PreferencesSettings(Preferences.userRoot().node(node))
    }

    // Escopo do app inteiro: vive enquanto o processo existir, independente de haver janela aberta.
    single<CoroutineScope> { CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate) }

    single {
        when (appEnvironment()) {
            AppEnvironment.LocalTesting -> SyncConfig.LocalTesting
            AppEnvironment.Staging -> SyncConfig.Staging
            AppEnvironment.Production -> SyncConfig.Configurable
        }
    }


    single { createTrayController() }
    single<NotificationSender> { createNotificationSender(tray = get()) }
    single<AlarmPlayer> { ChimeAlarmPlayer() }
    single<SystemThemeDetector> { createSystemThemeDetector(scope = get()) }
    single { TrayExplanation(settings = get(), notifications = get()) }
    single {
        DesktopPomodoroAlarm(
            repository = get(),
            clock = get(),
            preferences = get(),
            notifications = get(),
            player = get(),
        )
    }
    single {
        DesktopTimerAlarm(
            repository = get(),
            clock = get(),
            preferences = get(),
            notifications = get(),
            player = get(),
        )
    }
}

fun appEnvironment(): AppEnvironment = when (System.getProperty("cronosync.environment")) {
    "local" -> AppEnvironment.LocalTesting
    "homologacao" -> AppEnvironment.Staging
    else -> AppEnvironment.Production
}
