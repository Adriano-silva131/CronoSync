package com.adriano.cronosync.stopwatch.presentation

import app.cash.turbine.test
import com.adriano.cronosync.core.SchedulerClock
import com.adriano.cronosync.core.UI_TICK_MILLIS
import com.adriano.cronosync.stopwatch.data.FakeStopwatchRepository
import com.adriano.cronosync.stopwatch.domain.Lap
import com.adriano.cronosync.stopwatch.domain.Stopwatch
import com.adriano.cronosync.stopwatch.domain.StopwatchCommand
import com.adriano.cronosync.stopwatch.domain.StopwatchStatus
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

@OptIn(ExperimentalCoroutinesApi::class)
class StopwatchViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val clock = SchedulerClock(dispatcher.scheduler)
    private val repository = FakeStopwatchRepository()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun createViewModel() = StopwatchViewModel(repository, clock)

    @Test
    fun initialStateIsIdleAtZero() {
        val viewModel = createViewModel()

        assertEquals(StopwatchUiState(), viewModel.uiState.value)
    }

    @Test
    fun actionsAreSentToRepositoryAsCommands() = runTest {
        val viewModel = createViewModel()

        viewModel.onAction(StopwatchAction.Start)
        viewModel.onAction(StopwatchAction.Lap)
        viewModel.onAction(StopwatchAction.Pause)
        viewModel.onAction(StopwatchAction.Reset)
        runCurrent()

        assertEquals(
            listOf(StopwatchCommand.Start, StopwatchCommand.RecordLap, StopwatchCommand.Pause, StopwatchCommand.Reset),
            repository.sentCommands,
        )
    }

    @Test
    fun elapsedTimeTicksWhileRunning() = runTest {
        repository.stopwatch.value = Stopwatch(status = StopwatchStatus.Running, runningSinceMillis = 0L)
        val viewModel = createViewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }

        advanceTimeBy(UI_TICK_MILLIS * 100)
        runCurrent()

        assertEquals("00:01.60", viewModel.uiState.value.elapsedText)
        assertEquals(StopwatchStatus.Running, viewModel.uiState.value.status)
    }

    @Test
    fun pausedStopwatchDoesNotTick() = runTest {
        repository.stopwatch.value = Stopwatch(status = StopwatchStatus.Paused, accumulatedMillis = 5_000L)
        val viewModel = createViewModel()

        viewModel.uiState.test {
            assertEquals("00:05.00", awaitItem().elapsedText)

            advanceTimeBy(10_000L)
            expectNoEvents()
        }
    }

    @Test
    fun reflectsChangesMadeByAnotherDevice() = runTest {
        repository.stopwatch.value = Stopwatch(status = StopwatchStatus.Running, runningSinceMillis = 0L)
        val viewModel = createViewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceTimeBy(1_000L)

        repository.stopwatch.value = Stopwatch(status = StopwatchStatus.Paused, accumulatedMillis = 1_000L)
        runCurrent()

        assertEquals(StopwatchStatus.Paused, viewModel.uiState.value.status)
        assertEquals("00:01.00", viewModel.uiState.value.elapsedText)
    }

    @Test
    fun lapsAreFormattedNewestFirst() = runTest {
        repository.stopwatch.value = Stopwatch(
            status = StopwatchStatus.Paused,
            accumulatedMillis = 4_000L,
            laps = listOf(
                Lap(number = 1, lapMillis = 1_000L, totalMillis = 1_000L),
                Lap(number = 2, lapMillis = 2_500L, totalMillis = 3_500L),
            ),
        )
        val viewModel = createViewModel()

        viewModel.uiState.test {
            assertEquals(
                listOf(
                    LapUiModel(number = 2, lapText = "00:02.50", totalText = "00:03.50"),
                    LapUiModel(number = 1, lapText = "00:01.00", totalText = "00:01.00"),
                ),
                awaitItem().laps,
            )
        }
    }

    @Test
    fun withoutConnectionControlsAreDisabledAndActionsIgnored() = runTest {
        repository.acceptsCommands.value = false
        val viewModel = createViewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }
        runCurrent()

        assertEquals(false, viewModel.uiState.value.controlsEnabled)
        viewModel.onAction(StopwatchAction.Start)
        runCurrent()
        assertEquals(emptyList(), repository.sentCommands)

        repository.acceptsCommands.value = true
        runCurrent()
        assertEquals(true, viewModel.uiState.value.controlsEnabled)
    }
}
