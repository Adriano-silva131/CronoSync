package com.adriano.cronosync.stopwatch.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Stopwatch(
    val status: StopwatchStatus = StopwatchStatus.Idle,
    val accumulatedMillis: Long = 0L,
    val runningSinceMillis: Long? = null,
    val laps: List<Lap> = emptyList(),
) {
    fun elapsedMillis(nowMillis: Long): Long {
        val currentSegment = runningSinceMillis?.let { (nowMillis - it).coerceAtLeast(0L) } ?: 0L
        return accumulatedMillis + currentSegment
    }

    val lapLimitReached: Boolean get() = laps.size >= MAX_LAPS

    companion object {
        // Limita o tamanho das mensagens: todo estado enviado leva a lista inteira de voltas.
        const val MAX_LAPS = 200
    }
}

@Serializable
enum class StopwatchStatus { Idle, Running, Paused }

@Serializable
data class Lap(
    val number: Int,
    val lapMillis: Long,
    val totalMillis: Long,
)

@Serializable
sealed interface StopwatchCommand {
    @Serializable @SerialName("start")
    data object Start : StopwatchCommand

    @Serializable @SerialName("pause")
    data object Pause : StopwatchCommand

    @Serializable @SerialName("reset")
    data object Reset : StopwatchCommand

    @Serializable @SerialName("lap")
    data object RecordLap : StopwatchCommand
}

fun Stopwatch.handle(command: StopwatchCommand, nowMillis: Long): Stopwatch = when (command) {
    StopwatchCommand.Start -> when (status) {
        StopwatchStatus.Running -> this
        StopwatchStatus.Idle, StopwatchStatus.Paused -> copy(
            status = StopwatchStatus.Running,
            runningSinceMillis = nowMillis,
        )
    }

    StopwatchCommand.Pause -> when (status) {
        StopwatchStatus.Running -> copy(
            status = StopwatchStatus.Paused,
            accumulatedMillis = elapsedMillis(nowMillis),
            runningSinceMillis = null,
        )
        StopwatchStatus.Idle, StopwatchStatus.Paused -> this
    }

    StopwatchCommand.Reset -> Stopwatch()

    StopwatchCommand.RecordLap -> when {
        lapLimitReached -> this
        status == StopwatchStatus.Running -> {
            val total = elapsedMillis(nowMillis)
            val previousTotal = laps.lastOrNull()?.totalMillis ?: 0L
            copy(laps = laps + Lap(number = laps.size + 1, lapMillis = total - previousTotal, totalMillis = total))
        }
        else -> this
    }
}
