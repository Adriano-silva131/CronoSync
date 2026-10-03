package com.adriano.cronosync.desktop.alarm

import com.adriano.cronosync.core.AlignedClock
import com.adriano.cronosync.timer.data.AlarmPreferencesRepository
import com.adriano.cronosync.timer.data.TimerRepository
import com.adriano.cronosync.timer.domain.Timer
import com.adriano.cronosync.timer.domain.TimerCommand
import com.adriano.cronosync.timer.domain.TimerFinished
import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DesktopTimerAlarmTest {

    private val notifications = mutableListOf<Pair<String, String>>()
    private var chimes = 0
    private val preferences = AlarmPreferencesRepository(MapSettings())

    private val repository = object : TimerRepository {
        override val timer = MutableStateFlow(Timer(durationMillis = 5 * 60_000L))
        override val acceptsCommands = MutableStateFlow(true)
        override suspend fun send(command: TimerCommand) = true
    }

    private val alarm = DesktopTimerAlarm(
        repository = repository,
        clock = AlignedClock { 0L },
        preferences = preferences,
        notifications = { title, message -> notifications += title to message },
        player = { chimes++ },
        zone = ZoneOffset.UTC,
    )

    @Test
    fun onTimeFinishNotifiesAndPlaysTheChime() {
        alarm.alert(TimerFinished(finishedAtMillis = 0L, lateByMillis = 200L))

        assertEquals(listOf("Tempo esgotado!" to "O timer de 05:00 chegou ao fim."), notifications)
        assertEquals(1, chimes)
    }

    @Test
    fun soundCanBeTurnedOff() {
        preferences.setSound(false)

        alarm.alert(TimerFinished(finishedAtMillis = 0L, lateByMillis = 200L))

        assertEquals(1, notifications.size)
        assertEquals(0, chimes)
    }

    @Test
    fun finishNoticedAfterSuspensionOnlyNotifiesWithTheTime() {
        // Terminou às 14:30 UTC, percebido 20 min depois.
        val finishedAt = java.time.Instant.parse("2026-09-30T14:30:00Z").toEpochMilli()

        alarm.alert(TimerFinished(finishedAtMillis = finishedAt, lateByMillis = 20 * 60_000L))

        val (title, message) = notifications.single()
        assertEquals("O timer terminou", title)
        assertTrue("14:30" in message, message)
        assertEquals(0, chimes) // sem som de repente
    }
}
