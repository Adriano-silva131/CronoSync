package com.adriano.cronosync.pomodoro.domain

import com.adriano.cronosync.pomodoro.domain.PomodoroPhaseKind.Focus
import com.adriano.cronosync.pomodoro.domain.PomodoroPhaseKind.LongBreak
import com.adriano.cronosync.pomodoro.domain.PomodoroPhaseKind.ShortBreak
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class PomodoroTest {

    private fun min(minutes: Long) = minutes * PomodoroSettings.MINUTE
    private fun runningSinceZero(settings: PomodoroSettings = PomodoroSettings()) =
        Pomodoro(settings = settings).handle(PomodoroCommand.Start, nowMillis = 0L)

    @Test
    fun classicCycleAlternatesFocusAndBreaksWithALongBreakAfterFour() {
        val pomodoro = runningSinceZero()

        // F25 S5 F25 S5 F25 S5 F25 L15 → rodada de 130 min (4×25 + 3×5 + 15), depois recomeça.
        val expected = listOf(
            0L to Focus, 25L to ShortBreak, 30L to Focus, 55L to ShortBreak, 60L to Focus,
            85L to ShortBreak, 90L to Focus, 115L to LongBreak, 129L to LongBreak, 130L to Focus,
        )
        expected.forEach { (minute, kind) -> assertEquals(kind, pomodoro.phaseAt(min(minute)).kind, "minuto $minute") }
    }

    @Test
    fun countsCompletedFocuses() {
        val pomodoro = runningSinceZero()

        assertEquals(0, pomodoro.phaseAt(min(10)).completedFocuses)
        assertEquals(1, pomodoro.phaseAt(min(26)).completedFocuses) // na 1ª pausa
        assertEquals(4, pomodoro.phaseAt(min(120)).completedFocuses) // na pausa longa
        assertEquals(4, pomodoro.phaseAt(min(140)).completedFocuses) // 5º foco em andamento
    }

    @Test
    fun customSettingsAreRespected() {
        val pomodoro = runningSinceZero(PomodoroSettings(focusMillis = min(50), shortBreakMillis = min(10), longBreakMillis = min(30), focusesBeforeLongBreak = 2))

        assertEquals(ShortBreak, pomodoro.phaseAt(min(55)).kind)
        assertEquals(LongBreak, pomodoro.phaseAt(min(115)).kind) // 50+10+50 = 110 → pausa longa
        assertEquals(Focus, pomodoro.phaseAt(min(140)).kind) // 110+30 = 140 → nova rodada
    }

    @Test
    fun pauseFreezesThePhaseAndResumeContinues() {
        val paused = runningSinceZero().handle(PomodoroCommand.Pause, nowMillis = min(20))

        assertEquals(Focus, paused.phaseAt(min(500)).kind)
        assertEquals(min(5), paused.phaseAt(min(500)).endElapsedMillis - paused.elapsedMillis(min(500)))

        val resumed = paused.handle(PomodoroCommand.Start, nowMillis = min(100))
        assertEquals(ShortBreak, resumed.phaseAt(min(106)).kind) // 20 + 6 = 26 min andados
    }

    @Test
    fun nextTransitionIsTheEndOfTheCurrentPhase() {
        val resumed = runningSinceZero()
            .handle(PomodoroCommand.Pause, nowMillis = min(20))
            .handle(PomodoroCommand.Start, nowMillis = min(100))

        assertEquals(min(105), resumed.nextTransitionAtMillis(min(101))) // faltavam 5 min de foco
        assertNull(resumed.handle(PomodoroCommand.Pause, min(102)).nextTransitionAtMillis(min(102)))
    }

    @Test
    fun automaticTransitionIsWhatRingsTheAlarm() {
        val pomodoro = runningSinceZero()

        assertNull(pomodoro.lastTransitionAtMillis(min(10))) // ainda no 1º foco: nada tocou
        assertEquals(min(25), pomodoro.lastTransitionAtMillis(min(27))) // foco → pausa às 25 min
        assertEquals(min(30), pomodoro.lastTransitionAtMillis(min(31))) // pausa → foco às 30 min
    }

    @Test
    fun skipStartsTheNextPhaseNowWithoutRinging() {
        val skipped = runningSinceZero().handle(PomodoroCommand.Skip, nowMillis = min(10))

        assertEquals(ShortBreak, skipped.phaseAt(min(10)).kind)
        assertEquals(min(15), skipped.nextTransitionAtMillis(min(10))) // pausa de 5 min a partir de agora
        assertNull(skipped.lastTransitionAtMillis(min(11))) // pular foi ação da pessoa: não toca
        assertEquals(min(15), skipped.lastTransitionAtMillis(min(16))) // a troca seguinte, automática, toca
    }

    @Test
    fun resetGoesBackToTheFirstFocusKeepingSettings() {
        val settings = PomodoroSettings(focusMillis = min(50))
        val reset = runningSinceZero(settings).handle(PomodoroCommand.Reset, nowMillis = min(70))

        assertEquals(Pomodoro(settings = settings), reset)
    }

    @Test
    fun settingsChangeOnlyWhenStoppedAndAreKeptWithinLimits() {
        val updated = Pomodoro().handle(PomodoroCommand.UpdateSettings(PomodoroSettings(focusMillis = 0L, focusesBeforeLongBreak = 50)), 0L)
        assertEquals(min(1), updated.settings.focusMillis)
        assertEquals(12, updated.settings.focusesBeforeLongBreak)

        val running = runningSinceZero()
        assertSame(running, running.handle(PomodoroCommand.UpdateSettings(PomodoroSettings(focusMillis = min(50))), min(1)))
    }
}
