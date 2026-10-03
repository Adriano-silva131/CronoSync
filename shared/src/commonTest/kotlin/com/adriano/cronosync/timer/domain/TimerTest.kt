package com.adriano.cronosync.timer.domain

import com.adriano.cronosync.timer.domain.TimerCommand.Pause
import com.adriano.cronosync.timer.domain.TimerCommand.Reset
import com.adriano.cronosync.timer.domain.TimerCommand.SetDuration
import com.adriano.cronosync.timer.domain.TimerCommand.Start
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class TimerTest {

    private val tenSeconds = Timer(durationMillis = 10_000L)

    @Test
    fun newTimerIsIdleWithDefaultDuration() {
        val timer = Timer()

        assertEquals(TimerStatus.Idle, timer.statusAt(nowMillis = 0L))
        assertEquals(Timer.DEFAULT_DURATION_MILLIS, timer.remainingMillis(nowMillis = 99_999L))
    }

    @Test
    fun setDurationChangesIdleTimer() {
        assertEquals(30_000L, Timer().handle(SetDuration(30_000L), 0L).durationMillis)
    }

    @Test
    fun setDurationIsClampedToValidRange() {
        assertEquals(0L, Timer().handle(SetDuration(-1L), 0L).durationMillis)
        assertEquals(Timer.MAX_DURATION_MILLIS, Timer().handle(SetDuration(Long.MAX_VALUE), 0L).durationMillis)
    }

    @Test
    fun setDurationIsIgnoredOnceStarted() {
        val running = tenSeconds.handle(Start, 0L)

        assertSame(running, running.handle(SetDuration(60_000L), 1_000L))
    }

    @Test
    fun runningTimerCountsDown() {
        val running = tenSeconds.handle(Start, nowMillis = 1_000L)

        assertEquals(TimerStatus.Running, running.statusAt(nowMillis = 4_000L))
        assertEquals(7_000L, running.remainingMillis(nowMillis = 4_000L))
    }

    @Test
    fun cannotStartWithZeroDuration() {
        val empty = Timer(durationMillis = 0L)

        assertSame(empty, empty.handle(Start, 0L))
    }

    @Test
    fun pauseFreezesRemainingTimeAndResumeContinues() {
        val paused = tenSeconds.handle(Start, 0L).handle(Pause, 3_000L)
        assertEquals(TimerStatus.Paused, paused.statusAt(nowMillis = 50_000L))
        assertEquals(7_000L, paused.remainingMillis(nowMillis = 50_000L))

        val resumed = paused.handle(Start, nowMillis = 50_000L)
        assertEquals(5_000L, resumed.remainingMillis(nowMillis = 52_000L))
    }

    @Test
    fun finishesWhenTimeRunsOutWithoutAnyCommand() {
        val running = tenSeconds.handle(Start, nowMillis = 0L)

        assertEquals(TimerStatus.Running, running.statusAt(nowMillis = 9_999L))
        assertEquals(TimerStatus.Finished, running.statusAt(nowMillis = 10_000L))
        assertEquals(0L, running.remainingMillis(nowMillis = 60_000L)) // nunca fica negativo
    }

    @Test
    fun pauseAndStartAreIgnoredAfterFinishing() {
        val running = tenSeconds.handle(Start, 0L)

        assertSame(running, running.handle(Pause, nowMillis = 12_000L))
        assertSame(running, running.handle(Start, nowMillis = 12_000L))
    }

    @Test
    fun resetKeepsConfiguredDuration() {
        val reset = tenSeconds.handle(Start, 0L).handle(Pause, 3_000L).handle(Reset, 4_000L)

        assertEquals(Timer(durationMillis = 10_000L), reset)
    }

    @Test
    fun resetWorksAfterFinishing() {
        val reset = tenSeconds.handle(Start, 0L).handle(Reset, 20_000L)

        assertEquals(TimerStatus.Idle, reset.statusAt(20_000L))
        assertEquals(10_000L, reset.remainingMillis(20_000L))
    }
}
