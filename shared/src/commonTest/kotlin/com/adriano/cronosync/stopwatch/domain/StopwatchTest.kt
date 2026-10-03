package com.adriano.cronosync.stopwatch.domain

import com.adriano.cronosync.stopwatch.domain.StopwatchCommand.Pause
import com.adriano.cronosync.stopwatch.domain.StopwatchCommand.RecordLap
import com.adriano.cronosync.stopwatch.domain.StopwatchCommand.Reset
import com.adriano.cronosync.stopwatch.domain.StopwatchCommand.Start
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class StopwatchTest {

    @Test
    fun newStopwatchIsIdleAtZero() {
        val stopwatch = Stopwatch()

        assertEquals(StopwatchStatus.Idle, stopwatch.status)
        assertEquals(0L, stopwatch.elapsedMillis(nowMillis = 99_999L))
    }

    @Test
    fun startRecordsWhenItStartedRunning() {
        val stopwatch = Stopwatch().handle(Start, nowMillis = 1_000L)

        assertEquals(StopwatchStatus.Running, stopwatch.status)
        assertEquals(1_000L, stopwatch.runningSinceMillis)
        assertEquals(500L, stopwatch.elapsedMillis(nowMillis = 1_500L))
    }

    @Test
    fun pauseFreezesElapsedTime() {
        val paused = Stopwatch()
            .handle(Start, nowMillis = 1_000L)
            .handle(Pause, nowMillis = 3_000L)

        assertEquals(StopwatchStatus.Paused, paused.status)
        assertEquals(2_000L, paused.elapsedMillis(nowMillis = 3_000L))
        assertEquals(2_000L, paused.elapsedMillis(nowMillis = 60_000L))
    }

    @Test
    fun resumingContinuesFromAccumulatedTime() {
        val resumed = Stopwatch()
            .handle(Start, nowMillis = 0L)
            .handle(Pause, nowMillis = 2_000L)
            .handle(Start, nowMillis = 10_000L) // 8s parado não contam

        assertEquals(3_000L, resumed.elapsedMillis(nowMillis = 11_000L))
    }

    @Test
    fun startWhileRunningIsIgnored() {
        val running = Stopwatch().handle(Start, nowMillis = 1_000L)

        // Ex.: dois dispositivos apertam "iniciar" quase juntos — o segundo não reinicia a contagem.
        assertSame(running, running.handle(Start, nowMillis = 5_000L))
    }

    @Test
    fun pauseWhenNotRunningIsIgnored() {
        val idle = Stopwatch()
        val paused = Stopwatch().handle(Start, 0L).handle(Pause, 1_000L)

        assertSame(idle, idle.handle(Pause, nowMillis = 2_000L))
        assertSame(paused, paused.handle(Pause, nowMillis = 2_000L))
    }

    @Test
    fun resetReturnsToInitialState() {
        val reset = Stopwatch()
            .handle(Start, 0L)
            .handle(RecordLap, 1_000L)
            .handle(Pause, 2_000L)
            .handle(Reset, 3_000L)

        assertEquals(Stopwatch(), reset)
    }

    @Test
    fun lapsRecordLapDurationAndTotal() {
        val stopwatch = Stopwatch()
            .handle(Start, nowMillis = 0L)
            .handle(RecordLap, nowMillis = 1_000L)
            .handle(RecordLap, nowMillis = 3_500L)

        assertEquals(
            listOf(
                Lap(number = 1, lapMillis = 1_000L, totalMillis = 1_000L),
                Lap(number = 2, lapMillis = 2_500L, totalMillis = 3_500L),
            ),
            stopwatch.laps,
        )
    }

    @Test
    fun lapTimesIgnoreTimeSpentPaused() {
        val stopwatch = Stopwatch()
            .handle(Start, nowMillis = 0L)
            .handle(RecordLap, nowMillis = 1_000L)
            .handle(Pause, nowMillis = 2_000L)
            .handle(Start, nowMillis = 50_000L)
            .handle(RecordLap, nowMillis = 51_000L)

        assertEquals(Lap(number = 2, lapMillis = 2_000L, totalMillis = 3_000L), stopwatch.laps.last())
    }

    @Test
    fun lapWhenNotRunningIsIgnored() {
        val paused = Stopwatch().handle(Start, 0L).handle(Pause, 1_000L)

        assertSame(paused, paused.handle(RecordLap, nowMillis = 2_000L))
    }

    @Test
    fun lapsStopAtTheLimit() {
        var stopwatch = Stopwatch().handle(Start, 0L)
        repeat(Stopwatch.MAX_LAPS) { stopwatch = stopwatch.handle(RecordLap, nowMillis = (it + 1) * 1_000L) }
        assertTrue(stopwatch.lapLimitReached)

        // A volta 201 é ignorada: o estado nem muda (e o servidor nem grava).
        assertSame(stopwatch, stopwatch.handle(RecordLap, nowMillis = 999_000L))
        assertEquals(Stopwatch.MAX_LAPS, stopwatch.laps.size)
    }

    @Test
    fun elapsedIsNeverNegativeWhenClockIsBehindStart() {
        val running = Stopwatch().handle(Start, nowMillis = 10_000L)

        // Relógio deste dispositivo um pouco atrás de quem iniciou o cronômetro.
        assertEquals(0L, running.elapsedMillis(nowMillis = 9_900L))
    }
}
