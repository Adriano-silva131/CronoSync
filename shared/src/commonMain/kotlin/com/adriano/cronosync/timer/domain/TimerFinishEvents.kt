package com.adriano.cronosync.timer.domain

import com.adriano.cronosync.core.Clock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.transformLatest

data class TimerFinished(val finishedAtMillis: Long, val lateByMillis: Long) {
    val isLate: Boolean get() = lateByMillis > LATE_THRESHOLD_MILLIS

    companion object {
        const val LATE_THRESHOLD_MILLIS = 60_000L
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
// Confere o relógio a cada pollMillis em vez de um delay longo: com o computador suspenso o delay
// para de contar, e o fim seria percebido atrasado pelo tempo inteiro da suspensão.
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
