package com.adriano.cronosync.timer.data

import com.adriano.cronosync.timer.domain.Timer
import com.adriano.cronosync.timer.domain.TimerCommand
import kotlinx.coroutines.flow.StateFlow

interface TimerRepository {
    val timer: StateFlow<Timer>

    val acceptsCommands: StateFlow<Boolean>

    suspend fun send(command: TimerCommand): Boolean
}
