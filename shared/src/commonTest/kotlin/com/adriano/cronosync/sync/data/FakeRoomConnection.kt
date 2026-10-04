package com.adriano.cronosync.sync.data

import com.adriano.cronosync.sync.domain.RoomCommand
import com.adriano.cronosync.sync.domain.RoomState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeRoomConnection(override var isInRoom: Boolean = false) : RoomConnection {
    override val roomStates = MutableSharedFlow<RoomState>(replay = 1)
    override val commandsAvailable = MutableStateFlow(true)
    val sent = mutableListOf<RoomCommand>()

    override suspend fun sendCommand(command: RoomCommand): Boolean {
        sent += command
        return commandsAvailable.value
    }
}
