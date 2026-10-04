package com.adriano.cronosync.sync.data

import com.adriano.cronosync.sync.domain.RoomCommand
import com.adriano.cronosync.sync.domain.RoomState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface RoomConnection {
    val roomStates: Flow<RoomState>

    val isInRoom: Boolean

    val commandsAvailable: StateFlow<Boolean>

    suspend fun sendCommand(command: RoomCommand): Boolean
}
