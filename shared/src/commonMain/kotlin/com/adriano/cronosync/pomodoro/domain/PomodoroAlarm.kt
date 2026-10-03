package com.adriano.cronosync.pomodoro.domain

import com.adriano.cronosync.core.Clock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.transformLatest

/**
 * A troca de fase que deve estar TOCANDO agora neste aparelho, ou null.
 *
 * Toca a última troca automática (ver [Pomodoro.lastTransitionAtMillis]) enquanto ninguém a
 * silenciar aqui. Pausar, zerar ou pular encerram o toque — o ciclo deixou de "virar sozinho".
 * Se a próxima troca chegar com o alarme ainda tocando, passa a valer a nova.
 */
fun Pomodoro.ringingTransitionAtMillis(nowMillis: Long, silencedAtMillis: Long?): Long? =
    lastTransitionAtMillis(nowMillis)?.takeIf { it != silencedAtMillis }

/** Uma troca de fase percebida: qual fase começou e quando. */
data class PomodoroTransition(
    val newPhase: PomodoroPhaseKind,
    val newPhaseMillis: Long,
    val atMillis: Long,
    val lateByMillis: Long,
) {
    /** Percebida bem depois (ex.: computador suspenso): avisar sem som, como no timer. */
    val isLate: Boolean get() = lateByMillis > LATE_THRESHOLD_MILLIS

    companion object {
        const val LATE_THRESHOLD_MILLIS = 60_000L
    }
}

/**
 * Emite cada troca de fase enquanto o Pomodoro roda — para plataformas sem AlarmManager (desktop,
 * web). Mesma técnica do timerFinishEvents: confere o relógio de parede a cada [pollMillis] (aguenta
 * suspensão) e recomeça se o estado mudar (pausa, pular, outro aparelho mexeu).
 * Se várias fases passaram durante uma suspensão, avisa uma vez só, com a fase atual.
 */
@OptIn(ExperimentalCoroutinesApi::class)
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
