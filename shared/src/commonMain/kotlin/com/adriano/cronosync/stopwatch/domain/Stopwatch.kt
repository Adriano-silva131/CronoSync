package com.adriano.cronosync.stopwatch.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Estado de um cronômetro — a regra de negócio central do CronoSync.
 *
 * O estado NÃO guarda "o tempo atual" que é incrementado a cada segundo. Ele guarda só:
 * - [accumulatedMillis]: quanto tempo já correu em trechos anteriores (antes da última pausa);
 * - [runningSinceMillis]: o instante (epoch millis) em que o trecho atual começou, ou `null` se parado.
 *
 * O tempo decorrido é sempre *calculado* a partir de "agora" ([elapsedMillis]). Assim, dois dispositivos
 * com o mesmo estado mostram o mesmo tempo sem precisar trocar mensagens a cada tick.
 */
@Serializable
data class Stopwatch(
    val status: StopwatchStatus = StopwatchStatus.Idle,
    val accumulatedMillis: Long = 0L,
    val runningSinceMillis: Long? = null,
    val laps: List<Lap> = emptyList(),
) {
    fun elapsedMillis(nowMillis: Long): Long {
        // coerceAtLeast protege contra relógios levemente dessincronizados ("agora" antes do início).
        val currentSegment = runningSinceMillis?.let { (nowMillis - it).coerceAtLeast(0L) } ?: 0L
        return accumulatedMillis + currentSegment
    }

    val lapLimitReached: Boolean get() = laps.size >= MAX_LAPS

    companion object {
        /**
         * Máximo de voltas. Cobre até uma maratona numa pista de 400 m (~106 voltas) com folga, e
         * impede que alguém infle a sala sem fim: TODA mensagem de estado leva a lista inteira.
         */
        const val MAX_LAPS = 200
    }
}

@Serializable
enum class StopwatchStatus { Idle, Running, Paused }

/** Uma volta registrada: [lapMillis] é a duração da volta e [totalMillis] o tempo total no momento. */
@Serializable
data class Lap(
    val number: Int,
    val lapMillis: Long,
    val totalMillis: Long,
)

/**
 * Comandos que alteram o cronômetro. São a "linguagem" que dispositivos e servidor vão trocar:
 * um dispositivo envia um comando, quem é dono do estado aplica com [handle] e todos recebem o resultado.
 */
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

/**
 * Função pura que aplica um comando: mesmo estado + mesmo comando + mesmo instante = mesmo resultado.
 * Comandos inválidos para o estado atual (ex.: pausar algo parado) são ignorados, o que torna
 * seguro receber comandos repetidos ou concorrentes de vários dispositivos.
 */
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
