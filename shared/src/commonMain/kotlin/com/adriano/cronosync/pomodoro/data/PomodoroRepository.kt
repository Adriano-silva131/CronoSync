package com.adriano.cronosync.pomodoro.data

import com.adriano.cronosync.pomodoro.domain.Pomodoro
import com.adriano.cronosync.pomodoro.domain.PomodoroCommand
import kotlinx.coroutines.flow.StateFlow

interface PomodoroRepository {
    val pomodoro: StateFlow<Pomodoro>

    val acceptsCommands: StateFlow<Boolean>

    suspend fun send(command: PomodoroCommand): Boolean
}
