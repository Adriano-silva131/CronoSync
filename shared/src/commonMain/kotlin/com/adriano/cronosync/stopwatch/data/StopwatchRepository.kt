package com.adriano.cronosync.stopwatch.data

import com.adriano.cronosync.stopwatch.domain.Stopwatch
import com.adriano.cronosync.stopwatch.domain.StopwatchCommand
import kotlinx.coroutines.flow.StateFlow

/**
 * Fonte única da verdade do cronômetro para o app.
 *
 * O ViewModel só conhece esta interface: observa [stopwatch] e envia comandos com [send].
 * A implementação decide se o comando é aplicado aqui (modo local) ou vai ao servidor (numa sala).
 */
interface StopwatchRepository {
    val stopwatch: StateFlow<Stopwatch>

    /** Se comandos podem ser enviados agora (false numa sala sem conexão). */
    val acceptsCommands: StateFlow<Boolean>

    /** @return false se o comando não pôde ser aplicado nem enviado (ex.: sem conexão). */
    suspend fun send(command: StopwatchCommand): Boolean
}
