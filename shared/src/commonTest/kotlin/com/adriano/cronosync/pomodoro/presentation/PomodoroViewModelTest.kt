package com.adriano.cronosync.pomodoro.presentation

import com.adriano.cronosync.core.SchedulerClock
import com.adriano.cronosync.pomodoro.data.FakePomodoroRepository
import com.adriano.cronosync.pomodoro.domain.Pomodoro
import com.adriano.cronosync.pomodoro.domain.PomodoroCommand
import com.adriano.cronosync.pomodoro.domain.PomodoroPhaseKind
import com.adriano.cronosync.pomodoro.domain.PomodoroSettings
import com.adriano.cronosync.pomodoro.domain.PomodoroSettings.Companion.MINUTE
import com.adriano.cronosync.pomodoro.domain.PomodoroStatus
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

@OptIn(ExperimentalCoroutinesApi::class)
class PomodoroViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val clock = SchedulerClock(dispatcher.scheduler)
    private val repository = FakePomodoroRepository()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun createViewModel() = PomodoroViewModel(repository, clock)

    @Test
    fun idleShowsTheFirstFocusAndTheSettings() {
        val state = createViewModel().uiState.value

        assertEquals(PomodoroStatus.Idle, state.status)
        assertEquals(PomodoroPhaseKind.Focus, state.phase)
        assertEquals("25:00", state.remainingText)
        assertEquals(PomodoroPhaseKind.ShortBreak, state.nextPhase)
        assertEquals(5, state.nextPhaseMinutes)
        assertEquals(PomodoroSettingsFields(25, 5, 15, 4), state.settings)
    }

    @Test
    fun breakStartsByItselfWhenTheFocusEnds() = runTest {
        repository.pomodoro.value = Pomodoro(status = PomodoroStatus.Running, runningSinceMillis = 0L)
        val viewModel = createViewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }

        advanceTimeBy(26 * MINUTE)
        runCurrent()

        val state = viewModel.uiState.value
        assertEquals(PomodoroPhaseKind.ShortBreak, state.phase)
        assertEquals("04:00", state.remainingText)
        assertEquals(1, state.completedFocuses)
        assertEquals(PomodoroPhaseKind.Focus, state.nextPhase)
    }

    @Test
    fun settingsStepWithinLimits() = runTest {
        repository.pomodoro.value = Pomodoro(settings = PomodoroSettings(focusMillis = 1 * MINUTE))
        val viewModel = createViewModel()

        viewModel.onAction(PomodoroAction.StepSetting(PomodoroSettingField.Focus, -1)) // já no mínimo
        viewModel.onAction(PomodoroAction.StepSetting(PomodoroSettingField.ShortBreak, +1))
        runCurrent()

        assertEquals(
            listOf<PomodoroCommand>(
                PomodoroCommand.UpdateSettings(PomodoroSettings(focusMillis = 1 * MINUTE)),
                PomodoroCommand.UpdateSettings(PomodoroSettings(focusMillis = 1 * MINUTE, shortBreakMillis = 6 * MINUTE)),
            ),
            repository.sentCommands,
        )
    }

    @Test
    fun withoutConnectionControlsAreDisabledAndActionsIgnored() = runTest {
        repository.acceptsCommands.value = false
        val viewModel = createViewModel()

        assertFalse(viewModel.uiState.value.controlsEnabled)
        viewModel.onAction(PomodoroAction.Start)
        runCurrent()

        assertEquals(emptyList(), repository.sentCommands)
    }
}
