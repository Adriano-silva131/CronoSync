package com.adriano.cronosync.pomodoro.domain

import com.adriano.cronosync.core.Clock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.transformLatest

fun Pomodoro.ringingTransitionAtMillis(nowMillis: Long, silencedAtMillis: Long?): Long? =
    lastTransitionAtMillis(nowMillis)?.takeIf { it != silencedAtMillis }

data class PomodoroTransition(
    val newPhase: PomodoroPhaseKind,
    val newPhaseMillis: Long,
    val atMillis: Long,
    val lateByMillis: Long,
) {
    val isLate: Boolean get() = lateByMillis > LATE_THRESHOLD_MILLIS

    companion object {
        const val LATE_THRESHOLD_MILLIS = 60_000L
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
// Confere o relógio a cada pollMillis pelo mesmo motivo do timerFinishEvents (suspensão).
fun pomodoroTransitionEvents(
    pomodoro: Flow<Pomodoro>,
    clock: Clock,
    pollMillis: Long = 1_000L,
): Flow<PomodoroTransition> = pomodoro.transformLatest { current ->
    var next = current.nextTransitionAtMillis(clock.nowMillis()) ?: return@transformLatest
    while (true) {
        while (true) {
            val remaining = next - clock.nowMillis()
            if (remaining <= 0) break
            delay(minOf(remaining, pollMillis))
        }
        val now = clock.nowMillis()
        val phase = current.phaseAt(now)
        emit(PomodoroTransition(phase.kind, phase.durationMillis, atMillis = next, lateByMillis = now - next))
        next = current.nextTransitionAtMillis(now) ?: return@transformLatest
    }
}
