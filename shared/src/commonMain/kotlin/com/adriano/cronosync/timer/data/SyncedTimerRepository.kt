package com.adriano.cronosync.timer.data

import com.adriano.cronosync.core.Clock
import com.adriano.cronosync.sync.data.RoomConnection
import com.adriano.cronosync.sync.domain.RoomCommand
import com.adriano.cronosync.timer.domain.Timer
import com.adriano.cronosync.timer.domain.TimerCommand
import com.adriano.cronosync.timer.domain.handle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch

class SyncedTimerRepository(
    private val clock: Clock,
    private val storage: TimerStorage,
    private val connection: RoomConnection,
    scope: CoroutineScope,
) : TimerRepository {

    private val _timer = MutableStateFlow(storage.load() ?: Timer())
    override val timer: StateFlow<Timer> = _timer.asStateFlow()

    override val acceptsCommands: StateFlow<Boolean> = connection.commandsAvailable

    init {
        scope.launch {
            connection.roomStates.collect { room ->
                _timer.value = room.timer
                storage.save(room.timer)
            }
        }
    }

    override suspend fun send(command: TimerCommand): Boolean =
        if (connection.isInRoom) {
            connection.sendCommand(RoomCommand.TimerCmd(command))
        } else {
            storage.save(_timer.updateAndGet { it.handle(command, clock.nowMillis()) })
            true
        }
}
