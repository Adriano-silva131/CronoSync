package com.adriano.cronosync.di

import android.content.Context
import com.adriano.cronosync.BuildConfig
import com.adriano.cronosync.alarm.AlarmScheduler
import com.adriano.cronosync.alarm.PomodoroAlarmController
import com.adriano.cronosync.alarm.TimerAlarmController
import com.adriano.cronosync.alarm.notification.PomodoroNotifications
import com.adriano.cronosync.alarm.notification.TimerNotifications
import com.adriano.cronosync.sync.ConnectionWatcher
import com.adriano.cronosync.sync.data.AppEnvironment
import com.adriano.cronosync.sync.data.SyncConfig
import com.russhwolf.settings.Settings
import com.russhwolf.settings.SharedPreferencesSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val androidModule = module {
    single<Settings> {
        SharedPreferencesSettings(androidContext().getSharedPreferences("cronosync", Context.MODE_PRIVATE))
    }

    single<CoroutineScope> { CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate) }

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
