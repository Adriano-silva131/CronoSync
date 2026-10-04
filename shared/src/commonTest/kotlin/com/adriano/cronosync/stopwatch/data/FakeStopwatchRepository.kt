package com.adriano.cronosync.stopwatch.data

import com.adriano.cronosync.stopwatch.domain.Stopwatch
import com.adriano.cronosync.stopwatch.domain.StopwatchCommand
import kotlinx.coroutines.flow.MutableStateFlow

class FakeStopwatchRepository(initial: Stopwatch = Stopwatch()) : StopwatchRepository {
    override val stopwatch = MutableStateFlow(initial)
    override val acceptsCommands = MutableStateFlow(true)
    val sentCommands = mutableListOf<StopwatchCommand>()

    override suspend fun send(command: StopwatchCommand): Boolean {
        sentCommands += command
        return acceptsCommands.value
    }
}
