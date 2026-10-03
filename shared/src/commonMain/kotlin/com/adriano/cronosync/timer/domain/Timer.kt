package com.adriano.cronosync.timer.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Timer (contagem regressiva). Usa a mesma ideia do cronômetro: guarda quanto já correu
 * ([accumulatedMillis] + trecho atual desde [runningSinceMillis]) e calcula o restante a partir de "agora".
 *
 * Detalhe importante: "terminou" NÃO é armazenado. Um timer rodando cujo tempo acabou está
 * terminado por consequência do relógio — veja [statusAt]. Assim nenhum dispositivo precisa avisar
 * os outros que o timer acabou: todos chegam à mesma conclusão sozinhos, no mesmo instante.
 */
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

    /** Status "efetivo" num instante: igual a [status], exceto que um timer rodando e zerado está [TimerStatus.Finished]. */
    fun statusAt(nowMillis: Long): TimerStatus =
        if (status == TimerStatus.Running && remainingMillis(nowMillis) == 0L) TimerStatus.Finished else status

    companion object {
        const val DEFAULT_DURATION_MILLIS = 5 * 60 * 1_000L
        const val MAX_DURATION_MILLIS = (99 * 3_600 + 59 * 60 + 59) * 1_000L
    }
}

/** [Finished] só aparece via [Timer.statusAt]; o campo [Timer.status] guarda apenas os outros três. */
@Serializable
enum class TimerStatus { Idle, Running, Paused, Finished }

@Serializable
sealed interface TimerCommand {
    /** Só vale com o timer parado ([TimerStatus.Idle]); o valor é limitado a 0..[Timer.MAX_DURATION_MILLIS]. */
    @Serializable @SerialName("set_duration")
    data class SetDuration(val durationMillis: Long) : TimerCommand

    @Serializable @SerialName("start")
    data object Start : TimerCommand

    @Serializable @SerialName("pause")
    data object Pause : TimerCommand

    /** Volta ao início mantendo a duração configurada, pronto para rodar de novo. */
    @Serializable @SerialName("reset")
    data object Reset : TimerCommand
}

/** Função pura, como no cronômetro: comandos que não fazem sentido no estado atual são ignorados. */
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
        else -> this // inclui Finished: não faz sentido pausar um timer que já acabou
    }

    TimerCommand.Reset -> Timer(durationMillis = durationMillis)
}
