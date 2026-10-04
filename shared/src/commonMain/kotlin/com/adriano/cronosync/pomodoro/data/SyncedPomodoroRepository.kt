package com.adriano.cronosync.pomodoro.data

import com.adriano.cronosync.core.Clock
import com.adriano.cronosync.pomodoro.domain.Pomodoro
import com.adriano.cronosync.pomodoro.domain.PomodoroCommand
import com.adriano.cronosync.pomodoro.domain.handle
import com.adriano.cronosync.sync.data.RoomConnection
import com.adriano.cronosync.sync.data.SyncJson
import com.adriano.cronosync.sync.domain.RoomCommand
import com.russhwolf.settings.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import kotlinx.serialization.SerializationException

class SyncedPomodoroRepository(
    private val clock: Clock,
    private val settings: Settings,
    private val connection: RoomConnection,
    scope: CoroutineScope,
) : PomodoroRepository {

    private val _pomodoro = MutableStateFlow(load() ?: Pomodoro())
    override val pomodoro: StateFlow<Pomodoro> = _pomodoro.asStateFlow()

    override val acceptsCommands: StateFlow<Boolean> = connection.commandsAvailable

    init {
        scope.launch {
            connection.roomStates.collect { room ->
                _pomodoro.value = room.pomodoro
                save(room.pomodoro)
            }
        }
    }

    override suspend fun send(command: PomodoroCommand): Boolean =
        if (connection.isInRoom) {
            connection.sendCommand(RoomCommand.PomodoroCmd(command))
        } else {
            save(_pomodoro.updateAndGet { it.handle(command, clock.nowMillis()) })
            true
        }

    private fun load(): Pomodoro? = settings.getStringOrNull(KEY)?.let { json ->
        try {
            SyncJson.decodeFromString(Pomodoro.serializer(), json)
        } catch (e: SerializationException) {
            null
        }
    }

    private fun save(pomodoro: Pomodoro) = settings.putString(KEY, SyncJson.encodeToString(Pomodoro.serializer(), pomodoro))

    private companion object {
        const val KEY = "pomodoro.state"
    }
}
