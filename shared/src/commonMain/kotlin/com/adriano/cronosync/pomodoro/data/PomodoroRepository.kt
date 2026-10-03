package com.adriano.cronosync.pomodoro.data

import com.adriano.cronosync.pomodoro.domain.Pomodoro
import com.adriano.cronosync.pomodoro.domain.PomodoroCommand
import kotlinx.coroutines.flow.StateFlow

/** Mesmo contrato do cronômetro e do timer: observa o estado, envia comandos. */
interface PomodoroRepository {
    val pomodoro: StateFlow<Pomodoro>

    /** Se comandos podem ser enviados agora (false numa sala sem conexão). */
    val acceptsCommands: StateFlow<Boolean>

    /** @return false se o comando não pôde ser aplicado nem enviado (ex.: sem conexão). */
    suspend fun send(command: PomodoroCommand): Boolean
}
