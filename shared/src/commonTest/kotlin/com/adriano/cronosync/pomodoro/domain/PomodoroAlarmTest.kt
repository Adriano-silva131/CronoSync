package com.adriano.cronosync.pomodoro.domain

import com.adriano.cronosync.core.Clock
import com.adriano.cronosync.core.SchedulerClock
import com.adriano.cronosync.pomodoro.domain.PomodoroSettings.Companion.MINUTE
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PomodoroAlarmTest {

    private val running = Pomodoro().handle(PomodoroCommand.Start, nowMillis = 0L)

    @Test
    fun ringsAfterAnAutomaticTransitionUntilSilencedHere() {
        assertNull(running.ringingTransitionAtMillis(nowMillis = 10 * MINUTE, silencedAtMillis = null)) // ainda no foco
        assertEquals(25 * MINUTE, running.ringingTransitionAtMillis(nowMillis = 26 * MINUTE, silencedAtMillis = null))
        assertNull(running.ringingTransitionAtMillis(nowMillis = 26 * MINUTE, silencedAtMillis = 25 * MINUTE)) // parado aqui
        // A troca seguinte (pausa → foco) toca de novo, mesmo com a anterior silenciada.
        assertEquals(30 * MINUTE, running.ringingTransitionAtMillis(nowMillis = 31 * MINUTE, silencedAtMillis = 25 * MINUTE))
    }

    @Test
    fun pausingStopsTheRinging() {
        val paused = running.handle(PomodoroCommand.Pause, nowMillis = 26 * MINUTE)

        assertNull(paused.ringingTransitionAtMillis(nowMillis = 26 * MINUTE, silencedAtMillis = null))
    }

    @Test
    fun emitsEachPhaseChangeWhileRunning() = runTest {
        val events = mutableListOf<PomodoroTransition>()
        backgroundScope.launch { pomodoroTransitionEvents(MutableStateFlow(running), SchedulerClock(testScheduler)).toList(events) }
        runCurrent()

        advanceTimeBy(31 * MINUTE)
        runCurrent()

        assertEquals(
            listOf(
                PomodoroTransition(PomodoroPhaseKind.ShortBreak, 5 * MINUTE, atMillis = 25 * MINUTE, lateByMillis = 0L),
                PomodoroTransition(PomodoroPhaseKind.Focus, 25 * MINUTE, atMillis = 30 * MINUTE, lateByMillis = 0L),
            ),
            events,
        )
    }

    @Test
    fun phasesMissedDuringSuspensionAreReportedOnceAsLate() = runTest {
        var wallClock = 0L
        val events = mutableListOf<PomodoroTransition>()
        backgroundScope.launch { pomodoroTransitionEvents(MutableStateFlow(running), Clock { wallClock }).toList(events) }
        runCurrent()

        wallClock = 70 * MINUTE // dormiu: passaram foco, pausa, foco, pausa e começou o 3º foco
        advanceTimeBy(1_000L)
        runCurrent()

        val event = events.single()
        assertEquals(PomodoroPhaseKind.Focus, event.newPhase)
        assertTrue(event.isLate)
    }
}
