package com.adriano.cronosync.pomodoro.data

import com.adriano.cronosync.core.Clock
import com.adriano.cronosync.pomodoro.domain.Pomodoro
import com.adriano.cronosync.pomodoro.domain.PomodoroCommand
import com.adriano.cronosync.pomodoro.domain.handle
import com.adriano.cronosync.sync.RoomCommand
import com.adriano.cronosync.sync.RoomConnection
import com.adriano.cronosync.sync.SyncJson
import com.russhwolf.settings.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import kotlinx.serialization.SerializationException

/**
 * Pomodoro com ou sem sala (mesma lógica do SyncedTimerRepository): fora de uma sala aplica aqui;
 * numa sala envia ao servidor e adota o estado oficial (com previsão local feita pela sessão).
 *
 * É persistido — local ou vindo do servidor — para os alarmes das trocas de fase continuarem
 * valendo se o Android matar o app. Guardado como JSON (o mesmo formato da rede).
 */
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
            null // formato antigo/corrompido: começa do zero em vez de quebrar o app
        }
    }

    private fun save(pomodoro: Pomodoro) = settings.putString(KEY, SyncJson.encodeToString(Pomodoro.serializer(), pomodoro))

    private companion object {
        const val KEY = "pomodoro.state"
    }
}
