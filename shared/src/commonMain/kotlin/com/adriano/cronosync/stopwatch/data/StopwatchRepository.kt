package com.adriano.cronosync.stopwatch.data

import com.adriano.cronosync.stopwatch.domain.Stopwatch
import com.adriano.cronosync.stopwatch.domain.StopwatchCommand
import kotlinx.coroutines.flow.StateFlow

interface StopwatchRepository {
    val stopwatch: StateFlow<Stopwatch>

    val acceptsCommands: StateFlow<Boolean>

    suspend fun send(command: StopwatchCommand): Boolean
}
