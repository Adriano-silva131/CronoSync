package com.adriano.cronosync.timer.presentation

import com.adriano.cronosync.alarm.data.AlarmSilenceRepository
import com.adriano.cronosync.alarm.data.AlarmSource
import com.adriano.cronosync.core.SchedulerClock
import com.adriano.cronosync.timer.data.FakeTimerRepository
import com.adriano.cronosync.timer.domain.Timer
import com.adriano.cronosync.timer.domain.TimerCommand
import com.adriano.cronosync.timer.domain.TimerStatus
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
class TimerAlarmViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val clock = SchedulerClock(dispatcher.scheduler)
    private val repository = FakeTimerRepository()
    private val silence = AlarmSilenceRepository(MapSettings())

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private val finishedAtOneSecond =
        Timer(status = TimerStatus.Running, durationMillis = 1_000L, runningSinceMillis = 0L)

    @Test
    fun countsTimeSinceTheTimerFinished() = runTest {
        repository.timer.value = finishedAtOneSecond
        val viewModel = TimerAlarmViewModel(repository, clock, silence)
        backgroundScope.launch { viewModel.uiState.collect {} }

        advanceTimeBy(13_000L)
        runCurrent()

        assertEquals("-00:12", viewModel.uiState.value.overtimeText)
        assertFalse(viewModel.uiState.value.isDismissed)
    }

    @Test
    fun stopResetsTheTimer() = runTest {
        repository.timer.value = finishedAtOneSecond
        val viewModel = TimerAlarmViewModel(repository, clock, silence)

        viewModel.onAction(TimerAlarmAction.Stop)
        runCurrent()

        assertEquals(listOf<TimerCommand>(TimerCommand.Reset), repository.sentCommands)
    }

    @Test
    fun dismissesWhenTimerIsStoppedElsewhere() = runTest {
        repository.timer.value = finishedAtOneSecond
        val viewModel = TimerAlarmViewModel(repository, clock, silence)
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceTimeBy(5_000L)

        repository.timer.value = Timer(durationMillis = 1_000L)
        runCurrent()

        assertTrue(viewModel.uiState.value.isDismissed)
    }

    @Test
    fun openingWithoutRunningTimerIsDismissedImmediately() {
        assertTrue(TimerAlarmViewModel(repository, clock, silence).uiState.value.isDismissed)
    }

    @Test
    fun stopSilencesRightAwayEvenWithoutConnection() = runTest {
        repository.timer.value = finishedAtOneSecond
        repository.acceptsCommands.value = false
        val viewModel = TimerAlarmViewModel(repository, clock, silence)
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceTimeBy(5_000L)

        viewModel.onAction(TimerAlarmAction.Stop)
        runCurrent()

        assertTrue(viewModel.uiState.value.isDismissed)
        assertEquals(1_000L, silence.silencedAtMillis(AlarmSource.Timer).value)
    }
}
