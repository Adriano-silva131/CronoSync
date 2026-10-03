package com.adriano.cronosync.pomodoro.data

import com.adriano.cronosync.pomodoro.domain.Pomodoro
import com.adriano.cronosync.pomodoro.domain.PomodoroCommand
import kotlinx.coroutines.flow.MutableStateFlow

class FakePomodoroRepository(initial: Pomodoro = Pomodoro()) : PomodoroRepository {
    override val pomodoro = MutableStateFlow(initial)
    override val acceptsCommands = MutableStateFlow(true)
    val sentCommands = mutableListOf<PomodoroCommand>()

    override suspend fun send(command: PomodoroCommand): Boolean {
        sentCommands += command
        return acceptsCommands.value
    }
}
