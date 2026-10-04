package com.adriano.cronosync.pomodoro.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PomodoroSettings(
    val focusMillis: Long = 25 * MINUTE,
    val shortBreakMillis: Long = 5 * MINUTE,
    val longBreakMillis: Long = 15 * MINUTE,
    val focusesBeforeLongBreak: Int = 4,
) {
    fun normalized(): PomodoroSettings = PomodoroSettings(
        focusMillis = focusMillis.coerceIn(1 * MINUTE, 180 * MINUTE),
        shortBreakMillis = shortBreakMillis.coerceIn(1 * MINUTE, 60 * MINUTE),
        longBreakMillis = longBreakMillis.coerceIn(1 * MINUTE, 60 * MINUTE),
        focusesBeforeLongBreak = focusesBeforeLongBreak.coerceIn(1, 12),
    )

    companion object {
        const val MINUTE = 60_000L
    }
}

@Serializable
enum class PomodoroStatus { Idle, Running, Paused }

enum class PomodoroPhaseKind { Focus, ShortBreak, LongBreak }

@Serializable
data class Pomodoro(
    val status: PomodoroStatus = PomodoroStatus.Idle,
    val settings: PomodoroSettings = PomodoroSettings(),
    val accumulatedMillis: Long = 0L,
    val runningSinceMillis: Long? = null,
) {
    fun elapsedMillis(nowMillis: Long): Long {
        val currentSegment = runningSinceMillis?.let { (nowMillis - it).coerceAtLeast(0L) } ?: 0L
        return accumulatedMillis + currentSegment
    }

    fun phaseAt(nowMillis: Long): PomodoroPhase = phaseForElapsed(elapsedMillis(nowMillis))

    fun phaseForElapsed(elapsedMillis: Long): PomodoroPhase {
        val n = settings.focusesBeforeLongBreak
        val roundMillis = n * settings.focusMillis + (n - 1) * settings.shortBreakMillis + settings.longBreakMillis
        val rounds = elapsedMillis / roundMillis
        var phaseStart = rounds * roundMillis
        var phaseNumber = rounds * 2 * n
        var focusesDone = rounds * n
        for (position in 0 until 2 * n) {
            val kind = when {
                position % 2 == 0 -> PomodoroPhaseKind.Focus
                position == 2 * n - 1 -> PomodoroPhaseKind.LongBreak
                else -> PomodoroPhaseKind.ShortBreak
            }
            val length = durationOf(kind)
            if (elapsedMillis < phaseStart + length) {
                return PomodoroPhase(kind, phaseNumber, phaseStart, phaseStart + length, focusesDone.toInt())
            }
            if (kind == PomodoroPhaseKind.Focus) focusesDone++
            phaseStart += length
            phaseNumber++
        }
        error("inalcançável: a rodada cobre todo o tempo")
    }

    fun durationOf(kind: PomodoroPhaseKind): Long = when (kind) {
        PomodoroPhaseKind.Focus -> settings.focusMillis
        PomodoroPhaseKind.ShortBreak -> settings.shortBreakMillis
        PomodoroPhaseKind.LongBreak -> settings.longBreakMillis
    }

    fun nextTransitionAtMillis(nowMillis: Long): Long? {
        val since = runningSinceMillis ?: return null
        if (status != PomodoroStatus.Running) return null
        return since + (phaseAt(nowMillis).endElapsedMillis - accumulatedMillis)
    }

    fun lastTransitionAtMillis(nowMillis: Long): Long? {
        val since = runningSinceMillis ?: return null
        if (status != PomodoroStatus.Running) return null
        val phaseStart = phaseAt(nowMillis).startElapsedMillis
        return if (phaseStart > accumulatedMillis) since + (phaseStart - accumulatedMillis) else null
    }
}

data class PomodoroPhase(
    val kind: PomodoroPhaseKind,
    val number: Long,
    val startElapsedMillis: Long,
    val endElapsedMillis: Long,
    val completedFocuses: Int,
) {
    val durationMillis: Long get() = endElapsedMillis - startElapsedMillis
}

@Serializable
sealed interface PomodoroCommand {
    @Serializable @SerialName("start")
    data object Start : PomodoroCommand

    @Serializable @SerialName("pause")
    data object Pause : PomodoroCommand

    @Serializable @SerialName("skip")
    data object Skip : PomodoroCommand

    @Serializable @SerialName("reset")
    data object Reset : PomodoroCommand

    @Serializable @SerialName("settings")
    data class UpdateSettings(val settings: PomodoroSettings) : PomodoroCommand
}

fun Pomodoro.handle(command: PomodoroCommand, nowMillis: Long): Pomodoro = when (command) {
    PomodoroCommand.Start -> when (status) {
        PomodoroStatus.Idle, PomodoroStatus.Paused -> copy(status = PomodoroStatus.Running, runningSinceMillis = nowMillis)
        PomodoroStatus.Running -> this
    }

    PomodoroCommand.Pause -> when (status) {
        PomodoroStatus.Running -> copy(status = PomodoroStatus.Paused, accumulatedMillis = elapsedMillis(nowMillis), runningSinceMillis = null)
        PomodoroStatus.Idle, PomodoroStatus.Paused -> this
    }

    PomodoroCommand.Skip -> when (status) {
        PomodoroStatus.Running -> copy(accumulatedMillis = phaseAt(nowMillis).endElapsedMillis, runningSinceMillis = nowMillis)
        PomodoroStatus.Paused -> copy(accumulatedMillis = phaseAt(nowMillis).endElapsedMillis)
        PomodoroStatus.Idle -> this
    }

    PomodoroCommand.Reset -> Pomodoro(settings = settings)

    is PomodoroCommand.UpdateSettings -> when (status) {
        PomodoroStatus.Idle -> copy(settings = command.settings.normalized())
        PomodoroStatus.Running, PomodoroStatus.Paused -> this
    }
}
