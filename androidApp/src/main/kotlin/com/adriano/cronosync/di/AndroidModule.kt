package com.adriano.cronosync.di

import android.content.Context
import com.adriano.cronosync.BuildConfig
import com.adriano.cronosync.alarm.AlarmScheduler
import com.adriano.cronosync.alarm.PomodoroAlarmController
import com.adriano.cronosync.alarm.PomodoroNotifications
import com.adriano.cronosync.alarm.TimerAlarmController
import com.adriano.cronosync.alarm.TimerNotifications
import com.adriano.cronosync.sync.ConnectionWatcher
import com.adriano.cronosync.sync.AppEnvironment
import com.adriano.cronosync.sync.SyncConfig
import com.russhwolf.settings.Settings
import com.russhwolf.settings.SharedPreferencesSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/** Dependências que só existem no Android. Completa o sharedModule (ex.: fornece o Settings). */
val androidModule = module {
    single<Settings> {
        SharedPreferencesSettings(androidContext().getSharedPreferences("cronosync", Context.MODE_PRIVATE))
    }

    // Escopo do app inteiro (vive enquanto o processo existir), para trabalho que não pertence a
    // nenhuma tela. Main.immediate: começa a rodar na hora, sem esperar a próxima volta do main thread.
    single<CoroutineScope> { CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate) }

    // Debug = versão de testes presa ao servidor deste PC (IP da rede local, detectado no build);
    // homologação = presa ao servidor de testes publicado; release = endereço configurável.
    single {
        when (BuildConfig.ENVIRONMENT) {
            "local" -> SyncConfig(defaultServerAddress = BuildConfig.LOCAL_SERVER, environment = AppEnvironment.LocalTesting)
            "homologacao" -> SyncConfig.Staging
            else -> SyncConfig.Configurable
        }
    }

    single { TimerNotifications(androidContext()) }
    single { AlarmScheduler(androidContext()) }
    single { PomodoroNotifications(androidContext(), repository = get(), clock = get()) }
    single { TimerAlarmController(androidContext(), get(), get(), get(), get(), get()) }
    single { PomodoroAlarmController(androidContext(), get(), get(), get(), get(), get()) }
    single { ConnectionWatcher(androidContext(), session = get()) }
}
