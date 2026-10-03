package com.adriano.cronosync.pomodoro.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Durações do ciclo. Valem para a sala inteira (todos os aparelhos seguem o mesmo ciclo). */
@Serializable
data class PomodoroSettings(
    val focusMillis: Long = 25 * MINUTE,
    val shortBreakMillis: Long = 5 * MINUTE,
    val longBreakMillis: Long = 15 * MINUTE,
    /** A cada quantos focos vem a pausa longa. */
    val focusesBeforeLongBreak: Int = 4,
) {
    /** Garante valores que fazem sentido (o servidor aplica o mesmo, então ninguém burla pela rede). */
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

/**
 * Pomodoro: focos e pausas que se alternam SOZINHOS (foco → pausa curta → foco … → pausa longa).
 *
 * Mesmo princípio do timer: o estado guarda só quanto o ciclo já andou ([accumulatedMillis]) e
 * desde quando está rodando ([runningSinceMillis]). Em que fase está, quanto falta e quantos focos
 * foram concluídos são CALCULADOS a partir de "agora" ([phaseAt]). Por isso as trocas de fase não
 * são comandos: todos os aparelhos trocam de fase no mesmo instante, sem ninguém avisar ninguém.
 */
@Serializable
data class Pomodoro(
    val status: PomodoroStatus = PomodoroStatus.Idle,
    val settings: PomodoroSettings = PomodoroSettings(),
    val accumulatedMillis: Long = 0L,
    val runningSinceMillis: Long? = null,
) {
    /** Tempo total andado no ciclo (soma de todas as fases já percorridas). */
    fun elapsedMillis(nowMillis: Long): Long {
        val currentSegment = runningSinceMillis?.let { (nowMillis - it).coerceAtLeast(0L) } ?: 0L
        return accumulatedMillis + currentSegment
    }

    fun phaseAt(nowMillis: Long): PomodoroPhase = phaseForElapsed(elapsedMillis(nowMillis))

    /** Fase em que o ciclo está depois de [elapsedMillis] andados. */
    fun phaseForElapsed(elapsedMillis: Long): PomodoroPhase {
        val n = settings.focusesBeforeLongBreak
        // Uma "rodada" completa: n focos, n-1 pausas curtas e 1 pausa longa.
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

    /** Instante (relógio do servidor) em que a fase atual termina, ou null se não está rodando. */
    fun nextTransitionAtMillis(nowMillis: Long): Long? {
        val since = runningSinceMillis ?: return null
        if (status != PomodoroStatus.Running) return null
        return since + (phaseAt(nowMillis).endElapsedMillis - accumulatedMillis)
    }

    /**
     * Instante da última troca de fase que aconteceu SOZINHA enquanto rodava — é ela que toca o
     * alarme. null se não houve (ainda na primeira fase deste trecho, pausado, ou a fase atual
     * começou por um "Pular", que foi uma ação da própria pessoa e não deve tocar).
     */
    fun lastTransitionAtMillis(nowMillis: Long): Long? {
        val since = runningSinceMillis ?: return null
        if (status != PomodoroStatus.Running) return null
        val phaseStart = phaseAt(nowMillis).startElapsedMillis
        return if (phaseStart > accumulatedMillis) since + (phaseStart - accumulatedMillis) else null
    }
}

/** Uma fase do ciclo. Os tempos são "andados no ciclo" (não horários). */
data class PomodoroPhase(
    val kind: PomodoroPhaseKind,
    /** 0 = primeiro foco, 1 = primeira pausa, 2 = segundo foco… */
    val number: Long,
    val startElapsedMillis: Long,
    val endElapsedMillis: Long,
    /** Focos concluídos até o início desta fase. */
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

    /** Encerra a fase atual e já começa a próxima (ex.: voltar da pausa mais cedo). */
    @Serializable @SerialName("skip")
    data object Skip : PomodoroCommand

    /** Volta ao primeiro foco, mantendo os ajustes. */
    @Serializable @SerialName("reset")
    data object Reset : PomodoroCommand

    /** Só vale parado (Idle): mudar durações no meio de um ciclo embaralharia as fases. */
    @Serializable @SerialName("settings")
    data class UpdateSettings(val settings: PomodoroSettings) : PomodoroCommand
}

/** Função pura, como no timer: comandos sem sentido no estado atual são ignorados. */
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
        // Pula para o fim da fase atual; se estiver rodando, a próxima fase começa agora.
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
