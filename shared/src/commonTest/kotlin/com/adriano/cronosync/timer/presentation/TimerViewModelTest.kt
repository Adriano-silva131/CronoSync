package com.adriano.cronosync.timer.presentation

import app.cash.turbine.test
import com.adriano.cronosync.core.SchedulerClock
import com.adriano.cronosync.core.UI_TICK_MILLIS
import com.adriano.cronosync.timer.data.FakeTimerRepository
import com.adriano.cronosync.timer.domain.Timer
import com.adriano.cronosync.timer.domain.TimerCommand
import com.adriano.cronosync.timer.domain.TimerStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
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
class TimerViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val clock = SchedulerClock(dispatcher.scheduler)
    private val repository = FakeTimerRepository()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun createViewModel() = TimerViewModel(repository, clock)

    private fun runningTimer(durationMillis: Long) =
        Timer(status = TimerStatus.Running, durationMillis = durationMillis, runningSinceMillis = 0L)

    @Test
    fun idleTimerShowsConfiguredDuration() {
        repository.timer.value = Timer(durationMillis = 90_000L)

        val state = createViewModel().uiState.value

        assertEquals(TimerStatus.Idle, state.status)
        assertEquals("01:30", state.remainingText)
        assertEquals(DurationFields(minutes = 1, seconds = 30), state.duration)
        assertEquals(1f, state.remainingFraction)
        assertTrue(state.canStart)
    }

    @Test
    fun cannotStartWithZeroDuration() {
        repository.timer.value = Timer(durationMillis = 0L)

        assertFalse(createViewModel().uiState.value.canStart)
    }

    @Test
    fun stepDurationSendsNewDurationToRepository() = runTest {
        repository.timer.value = Timer(durationMillis = 90_000L)
        val viewModel = createViewModel()

        viewModel.onAction(TimerAction.StepDuration(DurationField.Minutes, +1))
        runCurrent()

        assertEquals(listOf<TimerCommand>(TimerCommand.SetDuration(150_000L)), repository.sentCommands)
    }

    @Test
    fun runningTimerCountsDown() = runTest {
        repository.timer.value = runningTimer(durationMillis = 10_000L)
        val viewModel = createViewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }

        advanceTimeBy(UI_TICK_MILLIS * 125)
        runCurrent()

        val state = viewModel.uiState.value
        assertEquals(TimerStatus.Running, state.status)
        assertEquals("00:08", state.remainingText)
        assertEquals(0.8f, state.remainingFraction)
        assertFalse(state.canStart)
    }

    @Test
    fun finishesAndStopsTicking() = runTest {
        repository.timer.value = runningTimer(durationMillis = 1_000L)
        val viewModel = createViewModel()
        val states = mutableListOf<TimerUiState>()
        backgroundScope.launch { viewModel.uiState.toList(states) }

        advanceTimeBy(2_000L)
        runCurrent()
        val finished = states.last()
        assertEquals(TimerStatus.Finished, finished.status)
        assertEquals("00:00", finished.remainingText)
        assertEquals(0f, finished.remainingFraction)

        val emittedSoFar = states.size
        advanceTimeBy(60_000L)
        assertEquals(emittedSoFar, states.size)
    }

    @Test
    fun reflectsChangesMadeByAnotherDevice() = runTest {
        repository.timer.value = runningTimer(durationMillis = 10_000L)
        val viewModel = createViewModel()

        viewModel.uiState.test {
            assertEquals(TimerStatus.Running, awaitItem().status)

            repository.timer.value = Timer(status = TimerStatus.Paused, durationMillis = 10_000L, accumulatedMillis = 4_000L)
            runCurrent()

            val paused = expectMostRecentItem()
            assertEquals(TimerStatus.Paused, paused.status)
            assertEquals("00:06", paused.remainingText)
        }
    }

    @Test
    fun withoutConnectionControlsAreDisabledAndActionsIgnored() = runTest {
        repository.acceptsCommands.value = false
        val viewModel = createViewModel()

        assertFalse(viewModel.uiState.value.controlsEnabled)
        viewModel.onAction(TimerAction.StepDuration(DurationField.Minutes, +1))
        viewModel.onAction(TimerAction.Start)
        runCurrent()

        assertEquals(emptyList(), repository.sentCommands)
    }
}
