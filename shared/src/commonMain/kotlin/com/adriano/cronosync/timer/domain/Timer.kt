package com.adriano.cronosync.timer.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Timer(
    val status: TimerStatus = TimerStatus.Idle,
    val durationMillis: Long = DEFAULT_DURATION_MILLIS,
    val accumulatedMillis: Long = 0L,
    val runningSinceMillis: Long? = null,
) {
    fun elapsedMillis(nowMillis: Long): Long {
        val currentSegment = runningSinceMillis?.let { (nowMillis - it).coerceAtLeast(0L) } ?: 0L
        return (accumulatedMillis + currentSegment).coerceAtMost(durationMillis)
    }

    fun remainingMillis(nowMillis: Long): Long = durationMillis - elapsedMillis(nowMillis)

    fun statusAt(nowMillis: Long): TimerStatus =
        if (status == TimerStatus.Running && remainingMillis(nowMillis) == 0L) TimerStatus.Finished else status

    companion object {
        const val DEFAULT_DURATION_MILLIS = 5 * 60 * 1_000L
        const val MAX_DURATION_MILLIS = (99 * 3_600 + 59 * 60 + 59) * 1_000L
    }
}

@Serializable
// Finished nunca é gravado em Timer.status: só aparece calculado por Timer.statusAt.
enum class TimerStatus { Idle, Running, Paused, Finished }

@Serializable
sealed interface TimerCommand {
    @Serializable @SerialName("set_duration")
    data class SetDuration(val durationMillis: Long) : TimerCommand

    @Serializable @SerialName("start")
    data object Start : TimerCommand

    @Serializable @SerialName("pause")
    data object Pause : TimerCommand

    @Serializable @SerialName("reset")
    data object Reset : TimerCommand
}

fun Timer.handle(command: TimerCommand, nowMillis: Long): Timer = when (command) {
    is TimerCommand.SetDuration -> when (status) {
        TimerStatus.Idle -> copy(durationMillis = command.durationMillis.coerceIn(0L, Timer.MAX_DURATION_MILLIS))
        else -> this
    }

    TimerCommand.Start -> when (status) {
        TimerStatus.Idle -> if (durationMillis > 0L) copy(status = TimerStatus.Running, runningSinceMillis = nowMillis) else this
        TimerStatus.Paused -> copy(status = TimerStatus.Running, runningSinceMillis = nowMillis)
        else -> this
    }

    TimerCommand.Pause -> when (statusAt(nowMillis)) {
        TimerStatus.Running -> copy(
            status = TimerStatus.Paused,
            accumulatedMillis = elapsedMillis(nowMillis),
            runningSinceMillis = null,
        )
        else -> this
    }

    TimerCommand.Reset -> Timer(durationMillis = durationMillis)
}
