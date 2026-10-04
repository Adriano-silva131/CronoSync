package com.adriano.cronosync.pomodoro.presentation

import com.adriano.cronosync.alarm.data.AlarmSilenceRepository
import com.adriano.cronosync.alarm.data.AlarmSource
import com.adriano.cronosync.core.SchedulerClock
import com.adriano.cronosync.pomodoro.data.FakePomodoroRepository
import com.adriano.cronosync.pomodoro.domain.Pomodoro
import com.adriano.cronosync.pomodoro.domain.PomodoroPhaseKind
import com.adriano.cronosync.pomodoro.domain.PomodoroSettings.Companion.MINUTE
import com.adriano.cronosync.pomodoro.domain.PomodoroStatus
import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PomodoroAlarmViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val clock = SchedulerClock(dispatcher.scheduler)
    private val repository = FakePomodoroRepository(Pomodoro(status = PomodoroStatus.Running, runningSinceMillis = 0L))
    private val silence = AlarmSilenceRepository(MapSettings())

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun stopOnlySilencesAndTheCycleKeepsGoing() = runTest {
        val viewModel = PomodoroAlarmViewModel(repository, clock, silence)
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceTimeBy(26 * MINUTE)
        runCurrent()

        val ringing = viewModel.uiState.value
        assertFalse(ringing.isDismissed)
        assertEquals(PomodoroPhaseKind.ShortBreak, ringing.phase)
        assertEquals("04:00", ringing.remainingText)

        viewModel.onAction(PomodoroAlarmAction.Stop)
        runCurrent()

        assertTrue(viewModel.uiState.value.isDismissed)
        assertEquals(25 * MINUTE, silence.silencedAtMillis(AlarmSource.Pomodoro).value)
        assertEquals(emptyList(), repository.sentCommands)
    }
}
