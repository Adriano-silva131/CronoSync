package com.adriano.cronosync.timer.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TimerAlarmTest {

    private val tenSeconds = Timer(durationMillis = 10_000L)

    @Test
    fun finishTimeAccountsForTimeAlreadyElapsed() {
        val resumed = tenSeconds
            .handle(TimerCommand.Start, nowMillis = 0L)
            .handle(TimerCommand.Pause, nowMillis = 4_000L)
            .handle(TimerCommand.Start, nowMillis = 100_000L)

        assertEquals(106_000L, resumed.finishesAtMillis())
    }

    @Test
    fun onlyRunningTimersHaveFinishTime() {
        assertNull(tenSeconds.finishesAtMillis())
        assertNull(tenSeconds.handle(TimerCommand.Start, 0L).handle(TimerCommand.Pause, 1_000L).finishesAtMillis())
    }

    @Test
    fun runningTimerSchedulesAlarmAtFinishTime() {
        val running = tenSeconds.handle(TimerCommand.Start, nowMillis = 1_000L)

        assertEquals(AlarmPlan.Schedule(atMillis = 11_000L), running.alarmPlan(nowMillis = 5_000L))
    }

    @Test
    fun finishedTimerKeepsRinging() {
        val running = tenSeconds.handle(TimerCommand.Start, nowMillis = 0L)

        assertEquals(AlarmPlan.Ringing, running.alarmPlan(nowMillis = 10_000L))
        assertEquals(AlarmPlan.Ringing, running.alarmPlan(nowMillis = 60_000L))
    }

    @Test
    fun stoppedTimerCancelsAlarm() {
        val paused = tenSeconds.handle(TimerCommand.Start, 0L).handle(TimerCommand.Pause, 1_000L)

        assertEquals(AlarmPlan.Cancel, tenSeconds.alarmPlan(nowMillis = 0L))
        assertEquals(AlarmPlan.Cancel, paused.alarmPlan(nowMillis = 2_000L))
    }

    @Test
    fun alarmStoppedOnThisDeviceDoesNotRingAgain() {
        val running = tenSeconds.handle(TimerCommand.Start, nowMillis = 0L)

        assertEquals(AlarmPlan.Cancel, running.alarmPlan(nowMillis = 12_000L, silencedFinishAtMillis = 10_000L))
        val restarted = tenSeconds.handle(TimerCommand.Start, nowMillis = 20_000L)
        assertEquals(AlarmPlan.Schedule(atMillis = 30_000L), restarted.alarmPlan(nowMillis = 21_000L, silencedFinishAtMillis = 10_000L))
    }
}
