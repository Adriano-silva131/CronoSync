package com.adriano.cronosync.timer.domain

import com.adriano.cronosync.core.Clock
import com.adriano.cronosync.core.SchedulerClock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class TimerFinishEventsTest {

    private fun running(durationMillis: Long, since: Long = 0L) =
        Timer(status = TimerStatus.Running, durationMillis = durationMillis, runningSinceMillis = since)

    private fun TestScope.collect(timer: MutableStateFlow<Timer>, clock: Clock): MutableList<TimerFinished> {
        val events = mutableListOf<TimerFinished>()
        backgroundScope.launch { timerFinishEvents(timer, clock).toList(events) }
        runCurrent()
        return events
    }

    @Test
    fun emitsOnceWhenARunningTimerReachesTheEnd() = runTest {
        val timer = MutableStateFlow(running(durationMillis = 10_000L))
        val events = collect(timer, SchedulerClock(testScheduler))

        advanceTimeBy(9_999L)
        assertEquals(emptyList(), events)

        advanceTimeBy(2L)
        assertEquals(listOf(TimerFinished(finishedAtMillis = 10_000L, lateByMillis = 0L)), events)
        assertFalse(events.single().isLate)

        advanceTimeBy(60_000L)
        assertEquals(1, events.size) // não repete
    }

    @Test
    fun pausingCancelsTheWait() = runTest {
        val timer = MutableStateFlow(running(durationMillis = 10_000L))
        val events = collect(timer, SchedulerClock(testScheduler))

        advanceTimeBy(5_000L)
        timer.value = Timer(status = TimerStatus.Paused, durationMillis = 10_000L, accumulatedMillis = 5_000L)
        advanceTimeBy(60_000L)

        assertEquals(emptyList(), events)
    }

    @Test
    fun timerThatAlreadyFinishedBeforeObservingIsIgnored() = runTest {
        advanceTimeBy(20_000L) // app abriu 10 s depois de o timer acabar
        val timer = MutableStateFlow(running(durationMillis = 10_000L))
        val events = collect(timer, SchedulerClock(testScheduler))

        advanceTimeBy(60_000L)
        assertEquals(emptyList(), events)
    }

    @Test
    fun endPassedDuringSuspensionIsReportedAsLate() = runTest {
        // Relógio de parede independente do tempo das corrotinas: simula o computador dormindo,
        // em que o relógio anda mas as esperas (delay) ficam congeladas.
        var wallClock = 0L
        val clock = Clock { wallClock }
        val timer = MutableStateFlow(running(durationMillis = 60_000L))
        val events = collect(timer, clock)

        wallClock = 10 * 60_000L // acordou 10 min depois
        advanceTimeBy(1_000L) // em até 1 s (uma checagem) o fim é percebido
        runCurrent()

        val event = events.single()
        assertEquals(9 * 60_000L, event.lateByMillis)
        assertTrue(event.isLate)
    }
}
