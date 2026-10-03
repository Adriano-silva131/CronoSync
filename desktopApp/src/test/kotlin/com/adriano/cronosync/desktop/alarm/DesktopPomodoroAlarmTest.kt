package com.adriano.cronosync.desktop.alarm

import com.adriano.cronosync.pomodoro.domain.Pomodoro
import com.adriano.cronosync.pomodoro.domain.PomodoroCommand
import com.adriano.cronosync.pomodoro.data.PomodoroRepository
import com.adriano.cronosync.pomodoro.domain.PomodoroPhaseKind
import com.adriano.cronosync.pomodoro.domain.PomodoroSettings.Companion.MINUTE
import com.adriano.cronosync.pomodoro.domain.PomodoroTransition
import com.adriano.cronosync.timer.data.AlarmPreferencesRepository
import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.Test
import kotlin.test.assertEquals

class DesktopPomodoroAlarmTest {

    private val notifications = mutableListOf<Pair<String, String>>()
    private var chimes = 0
    private val repository = object : PomodoroRepository {
        override val pomodoro = MutableStateFlow(Pomodoro())
        override val acceptsCommands = MutableStateFlow(true)
        override suspend fun send(command: PomodoroCommand) = true
    }
    private val alarm = DesktopPomodoroAlarm(
        repository = repository,
        clock = { 0L },
        preferences = AlarmPreferencesRepository(MapSettings()),
        notifications = { title, message -> notifications += title to message },
        player = { chimes++ },
    )

    @Test
    fun breakStartingNotifiesAndChimes() {
        alarm.alert(PomodoroTransition(PomodoroPhaseKind.ShortBreak, 5 * MINUTE, atMillis = 0L, lateByMillis = 100L))

        assertEquals(listOf("Hora da pausa!" to "Pausa curta de 5 min já começou."), notifications)
        assertEquals(1, chimes)
    }

    @Test
    fun lateTransitionOnlyTellsTheCurrentPhase() {
        alarm.alert(PomodoroTransition(PomodoroPhaseKind.Focus, 25 * MINUTE, atMillis = 0L, lateByMillis = 30 * MINUTE))

        assertEquals("Pomodoro", notifications.single().first)
        assertEquals(0, chimes)
    }
}
