package com.adriano.cronosync.timer.data

import com.adriano.cronosync.timer.domain.Timer
import com.adriano.cronosync.timer.domain.TimerCommand
import kotlinx.coroutines.flow.MutableStateFlow

class FakeTimerRepository(initial: Timer = Timer()) : TimerRepository {
    override val timer = MutableStateFlow(initial)
    override val acceptsCommands = MutableStateFlow(true)
    val sentCommands = mutableListOf<TimerCommand>()

    override suspend fun send(command: TimerCommand): Boolean {
        sentCommands += command
        return acceptsCommands.value
    }
}
