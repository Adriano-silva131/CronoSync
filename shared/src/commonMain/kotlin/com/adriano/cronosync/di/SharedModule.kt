package com.adriano.cronosync.di

import com.adriano.cronosync.alarm.AlarmSilenceRepository
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
import com.adriano.cronosync.sync.RoomConnection
import com.adriano.cronosync.sync.SyncSession
import com.adriano.cronosync.sync.SyncViewModel
import com.adriano.cronosync.timer.data.AlarmPreferencesRepository
import com.adriano.cronosync.timer.data.SettingsTimerStorage
import com.adriano.cronosync.timer.data.SyncedTimerRepository
import com.adriano.cronosync.timer.data.TimerRepository
import com.adriano.cronosync.timer.data.TimerStorage
import com.adriano.cronosync.timer.presentation.AlarmOptionsViewModel
import com.adriano.cronosync.timer.presentation.TimerAlarmViewModel
import com.adriano.cronosync.timer.presentation.TimerViewModel
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.WebSockets
import org.koin.core.KoinApplication
import org.koin.core.context.startKoin
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.KoinAppDeclaration
import org.koin.dsl.module

/**
 * Como montar cada dependência.
 *
 * Dependências específicas de plataforma NÃO são declaradas aqui — cada app as fornece no seu
 * próprio módulo Koin via [initKoin]:
 * - `Settings` (no Android precisa de um Context);
 * - `CoroutineScope` do app inteiro (para trabalho que não pertence a nenhuma tela, como a conexão);
 * - `SyncConfig`: para onde conectar, conforme a versão (testes locais ou final).
 */
val sharedModule = module {
    // Todo o app usa o relógio alinhado ao servidor; fora de uma sala a diferença é 0.
    single { AlignedClock(SystemClock) }
    single<Clock> { get<AlignedClock>() }

    single {
        HttpClient {
            // Pings de controle do WebSocket: detectam conexões "mortas" (ex.: trocou de rede).
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

/**
 * Ponto de entrada único do Koin para todas as plataformas. Cada app chama no seu início,
 * adicionando configurações próprias via [config] (ex.: no Android, `androidContext(...)`).
 */
fun initKoin(config: KoinAppDeclaration? = null): KoinApplication =
    startKoin {
        config?.invoke(this)
        modules(sharedModule)
    }
