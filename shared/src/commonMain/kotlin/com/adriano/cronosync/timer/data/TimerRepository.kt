package com.adriano.cronosync.timer.data

import com.adriano.cronosync.timer.domain.Timer
import com.adriano.cronosync.timer.domain.TimerCommand
import kotlinx.coroutines.flow.StateFlow

/** Mesmo contrato do cronômetro: observa o estado, envia comandos. */
interface TimerRepository {
    val timer: StateFlow<Timer>

    /** Se comandos podem ser enviados agora (false numa sala sem conexão). */
    val acceptsCommands: StateFlow<Boolean>

    /** @return false se o comando não pôde ser aplicado nem enviado (ex.: sem conexão). */
    suspend fun send(command: TimerCommand): Boolean
}
