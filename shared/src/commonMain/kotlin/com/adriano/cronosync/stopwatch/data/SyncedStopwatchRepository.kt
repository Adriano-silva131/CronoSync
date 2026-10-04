package com.adriano.cronosync.stopwatch.data

import com.adriano.cronosync.core.Clock
import com.adriano.cronosync.stopwatch.domain.Stopwatch
import com.adriano.cronosync.stopwatch.domain.StopwatchCommand
import com.adriano.cronosync.stopwatch.domain.handle
import com.adriano.cronosync.sync.data.RoomConnection
import com.adriano.cronosync.sync.domain.RoomCommand
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
