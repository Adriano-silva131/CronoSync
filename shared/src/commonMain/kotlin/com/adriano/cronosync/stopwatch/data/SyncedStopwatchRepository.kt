package com.adriano.cronosync.stopwatch.data

import com.adriano.cronosync.core.Clock
import com.adriano.cronosync.stopwatch.domain.Stopwatch
import com.adriano.cronosync.stopwatch.domain.StopwatchCommand
import com.adriano.cronosync.stopwatch.domain.handle
import com.adriano.cronosync.sync.RoomCommand
import com.adriano.cronosync.sync.RoomConnection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Cronômetro que funciona com ou sem sala:
 * - fora de uma sala: aplica os comandos aqui mesmo (como antes);
 * - numa sala: envia o comando ao servidor. A sessão já aplica uma PREVISÃO local (a tela reage na
 *   hora) e depois a substitui pelo estado oficial que o servidor devolve por
 *   [RoomConnection.roomStates]. O servidor continua decidindo a ordem e o resultado final.
 *
 * Para o ViewModel nada muda: ele continua observando [stopwatch] e chamando [send].
 */
class SyncedStopwatchRepository(
    private val clock: Clock,
    private val connection: RoomConnection,
    scope: CoroutineScope,
) : StopwatchRepository {

    private val _stopwatch = MutableStateFlow(Stopwatch())
    override val stopwatch: StateFlow<Stopwatch> = _stopwatch.asStateFlow()

    override val acceptsCommands: StateFlow<Boolean> = connection.commandsAvailable

    init {
        scope.launch {
            connection.roomStates.collect { room -> _stopwatch.value = room.stopwatch }
        }
    }

    override suspend fun send(command: StopwatchCommand): Boolean =
        if (connection.isInRoom) {
            connection.sendCommand(RoomCommand.StopwatchCmd(command))
        } else {
            _stopwatch.update { it.handle(command, clock.nowMillis()) }
            true
        }
}
