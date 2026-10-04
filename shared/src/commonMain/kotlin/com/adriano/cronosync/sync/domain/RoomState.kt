package com.adriano.cronosync.sync.domain

import com.adriano.cronosync.pomodoro.domain.Pomodoro
import com.adriano.cronosync.pomodoro.domain.PomodoroCommand
import com.adriano.cronosync.pomodoro.domain.handle
import com.adriano.cronosync.stopwatch.domain.Stopwatch
import com.adriano.cronosync.stopwatch.domain.StopwatchCommand
import com.adriano.cronosync.stopwatch.domain.handle
import com.adriano.cronosync.timer.domain.Timer
import com.adriano.cronosync.timer.domain.TimerCommand
import com.adriano.cronosync.timer.domain.handle
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RoomState(
    val stopwatch: Stopwatch = Stopwatch(),
    val timer: Timer = Timer(),
    val pomodoro: Pomodoro = Pomodoro(),
    val version: Long = 0L,
    val updatedAtMillis: Long = 0L,
)

@Serializable
sealed interface RoomCommand {
    @Serializable @SerialName("stopwatch")
    data class StopwatchCmd(val command: StopwatchCommand) : RoomCommand

    @Serializable @SerialName("timer")
    data class TimerCmd(val command: TimerCommand) : RoomCommand

    @Serializable @SerialName("pomodoro")
    data class PomodoroCmd(val command: PomodoroCommand) : RoomCommand
}

// Não altera version: só o servidor numera as mudanças.
fun RoomState.handle(command: RoomCommand, atMillis: Long): RoomState = when (command) {
    is RoomCommand.StopwatchCmd -> copy(stopwatch = stopwatch.handle(command.command, atMillis))
    is RoomCommand.TimerCmd -> copy(timer = timer.handle(command.command, atMillis))
    is RoomCommand.PomodoroCmd -> copy(pomodoro = pomodoro.handle(command.command, atMillis))
}

fun effectiveCommandTime(requestedAtMillis: Long, nowMillis: Long, lastChangeAtMillis: Long): Long {
    val earliest = maxOf(lastChangeAtMillis, nowMillis - MAX_BACKDATE_MILLIS)
    return requestedAtMillis.coerceIn(minOf(earliest, nowMillis), nowMillis)
}

const val MAX_BACKDATE_MILLIS = 5_000L
