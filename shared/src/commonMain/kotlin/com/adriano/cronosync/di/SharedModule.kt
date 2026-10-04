package com.adriano.cronosync.di

import com.adriano.cronosync.alarm.data.AlarmPreferencesRepository
import com.adriano.cronosync.alarm.data.AlarmSilenceRepository
import com.adriano.cronosync.alarm.presentation.AlarmOptionsViewModel
import com.adriano.cronosync.core.AlignedClock
import com.adriano.cronosync.core.Clock
import com.adriano.cronosync.core.SystemClock
import com.adriano.cronosync.pomodoro.data.PomodoroRepository
import com.adriano.cronosync.pomodoro.data.SyncedPomodoroRepository
import com.adriano.cronosync.pomodoro.presentation.PomodoroAlarmViewModel
import com.adriano.cronosync.pomodoro.presentation.PomodoroViewModel
import com.adriano.cronosync.stopwatch.data.StopwatchRepository
import com.adriano.cronosync.stopwatch.data.SyncedStopwatchRepository
import com.adriano.cronosync.stopwatch.presentation.StopwatchViewModel
import com.adriano.cronosync.sync.data.RoomConnection
import com.adriano.cronosync.sync.data.SyncSession
import com.adriano.cronosync.sync.presentation.SyncViewModel
import com.adriano.cronosync.timer.data.SettingsTimerStorage
import com.adriano.cronosync.timer.data.SyncedTimerRepository
import com.adriano.cronosync.timer.data.TimerRepository
import com.adriano.cronosync.timer.data.TimerStorage
import com.adriano.cronosync.timer.presentation.TimerAlarmViewModel
import com.adriano.cronosync.timer.presentation.TimerViewModel
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.WebSockets
import org.koin.core.KoinApplication
import org.koin.core.context.startKoin
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.KoinAppDeclaration
import org.koin.dsl.module

val sharedModule = module {
    single { AlignedClock(SystemClock) }
    single<Clock> { get<AlignedClock>() }

    single {
        HttpClient {
            install(WebSockets) { pingIntervalMillis = 15_000L }
        }
    }
    single { SyncSession(client = get(), clock = get(), settings = get(), scope = get(), config = get()) }
    single<RoomConnection> { get<SyncSession>() }
    viewModelOf(::SyncViewModel)

    single<StopwatchRepository> { SyncedStopwatchRepository(clock = get(), connection = get(), scope = get()) }
    viewModelOf(::StopwatchViewModel)

    single<TimerStorage> { SettingsTimerStorage(settings = get()) }
    single<TimerRepository> { SyncedTimerRepository(clock = get(), storage = get(), connection = get(), scope = get()) }
    viewModelOf(::TimerViewModel)
    viewModelOf(::TimerAlarmViewModel)
    single<PomodoroRepository> { SyncedPomodoroRepository(clock = get(), settings = get(), connection = get(), scope = get()) }
    viewModelOf(::PomodoroViewModel)
    viewModelOf(::PomodoroAlarmViewModel)

    single { AlarmPreferencesRepository(settings = get()) }
    single { AlarmSilenceRepository(settings = get()) }
    viewModelOf(::AlarmOptionsViewModel)
}

fun initKoin(config: KoinAppDeclaration? = null): KoinApplication =
    startKoin {
        config?.invoke(this)
        modules(sharedModule)
    }
