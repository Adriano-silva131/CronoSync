package com.adriano.cronosync.sync

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

/** Conexão falsa: o teste decide se está numa sala, injeta estados "do servidor" e vê o que foi enviado. */
class FakeRoomConnection(override var isInRoom: Boolean = false) : RoomConnection {
    override val roomStates = MutableSharedFlow<RoomState>(replay = 1)
    override val commandsAvailable = MutableStateFlow(true)
    val sent = mutableListOf<RoomCommand>()

    override suspend fun sendCommand(command: RoomCommand): Boolean {
        sent += command
        return commandsAvailable.value
    }
}
