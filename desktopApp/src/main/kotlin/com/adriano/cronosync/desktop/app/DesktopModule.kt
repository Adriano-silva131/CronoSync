package com.adriano.cronosync.desktop.app

import com.adriano.cronosync.desktop.alarm.DesktopPomodoroAlarm
import com.adriano.cronosync.desktop.alarm.DesktopTimerAlarm
import com.adriano.cronosync.desktop.integration.AlarmPlayer
import com.adriano.cronosync.desktop.integration.ChimeAlarmPlayer
import com.adriano.cronosync.desktop.integration.NotificationSender
import com.adriano.cronosync.desktop.integration.SystemThemeDetector
import com.adriano.cronosync.desktop.integration.createNotificationSender
import com.adriano.cronosync.desktop.integration.createSystemThemeDetector
import com.adriano.cronosync.desktop.integration.createTrayController
import com.adriano.cronosync.sync.AppEnvironment
import com.adriano.cronosync.sync.SyncConfig
import com.russhwolf.settings.PreferencesSettings
import com.russhwolf.settings.Settings
import java.util.prefs.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.dsl.module

/** Dependências que só existem no desktop. Completa o sharedModule, como o androidModule faz no Android. */
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

    // Integrações com o sistema: cada uma escolhe a implementação para o SO atual.
    // Um só ícone de bandeja: ele e as notificações do Windows usam o mesmo.
    single { createTrayController() }
    single<NotificationSender> { createNotificationSender(tray = get()) }
    single<AlarmPlayer> { ChimeAlarmPlayer() }
    single<SystemThemeDetector> { createSystemThemeDetector(scope = get()) }
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

/** Versão do app, definida no build por `-Pcronosync.environment` (ver desktopApp/build.gradle.kts). */
fun appEnvironment(): AppEnvironment = when (System.getProperty("cronosync.environment")) {
    "local" -> AppEnvironment.LocalTesting
    "homologacao" -> AppEnvironment.Staging
    else -> AppEnvironment.Production
}
