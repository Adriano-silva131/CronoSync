package com.adriano.cronosync.timer.domain

import com.adriano.cronosync.core.Clock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.transformLatest

/** O timer chegou ao fim. [lateByMillis]: quanto depois do fim isso foi percebido. */
data class TimerFinished(val finishedAtMillis: Long, val lateByMillis: Long) {
    /**
     * Percebido bem depois do fim — ex.: o computador estava suspenso. Nesse caso não faz sentido
     * tocar som de repente; basta informar que terminou.
     */
    val isLate: Boolean get() = lateByMillis > LATE_THRESHOLD_MILLIS

    companion object {
        const val LATE_THRESHOLD_MILLIS = 60_000L
    }
}

/**
 * Emite um evento quando um timer que ESTAVA RODANDO chega ao fim — para plataformas sem
 * AlarmManager (desktop, web), cujo app precisa estar aberto para avisar.
 *
 * - transformLatest: se o timer mudar (pausa, zera, outro aparelho mexeu), a espera anterior é
 *   cancelada e recomeça com o estado novo.
 * - A espera confere o relógio de parede a cada [pollMillis] em vez de um único delay longo: com o
 *   computador suspenso o delay "para de contar", e ao acordar o fim seria percebido atrasado
 *   pelo tempo inteiro da suspensão. Conferindo o relógio, percebemos logo que o fim já passou.
 * - Um timer que já tinha terminado quando começamos a observar (ex.: app aberto depois) não gera
 *   evento: o usuário vê o estado na tela, e avisar de novo a cada abertura seria chato.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun timerFinishEvents(
    timer: Flow<Timer>,
    clock: Clock,
    pollMillis: Long = 1_000L,
): Flow<TimerFinished> = timer.transformLatest { current ->
    val finishesAt = current.finishesAtMillis() ?: return@transformLatest
    if (clock.nowMillis() >= finishesAt) return@transformLatest
    while (true) {
        val remaining = finishesAt - clock.nowMillis()
        if (remaining <= 0) break
        delay(minOf(remaining, pollMillis))
    }
    emit(TimerFinished(finishedAtMillis = finishesAt, lateByMillis = clock.nowMillis() - finishesAt))
}
