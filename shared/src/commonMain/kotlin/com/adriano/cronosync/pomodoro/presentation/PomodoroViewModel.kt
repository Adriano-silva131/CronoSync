package com.adriano.cronosync.pomodoro.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adriano.cronosync.core.Clock
import com.adriano.cronosync.core.clockTicks
import com.adriano.cronosync.pomodoro.data.PomodoroRepository
import com.adriano.cronosync.pomodoro.domain.Pomodoro
import com.adriano.cronosync.pomodoro.domain.PomodoroCommand
import com.adriano.cronosync.pomodoro.domain.PomodoroSettings
import com.adriano.cronosync.pomodoro.domain.PomodoroSettings.Companion.MINUTE
import com.adriano.cronosync.pomodoro.domain.PomodoroStatus
import com.adriano.cronosync.timer.presentation.formatCountdown
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PomodoroViewModel(
    private val repository: PomodoroRepository,
    private val clock: Clock,
) : ViewModel() {

    /**
     * Mesma estrutura dos outros modos: estado do repositório + "agora", recalculado a cada tick.
     * As trocas de fase aparecem sozinhas, porque a fase é calculada a partir do relógio.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<PomodoroUiState> = repository.pomodoro
        .flatMapLatest { pomodoro -> ticks(pomodoro).map { now -> pomodoro.toUiState(now) } }
        .combine(repository.acceptsCommands) { state, enabled -> state.copy(controlsEnabled = enabled) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = repository.pomodoro.value.toUiState(clock.nowMillis())
                .copy(controlsEnabled = repository.acceptsCommands.value),
        )

    fun onAction(action: PomodoroAction) {
        if (!repository.acceptsCommands.value) return
        val command = when (action) {
            PomodoroAction.Start -> PomodoroCommand.Start
            PomodoroAction.Pause -> PomodoroCommand.Pause
            PomodoroAction.Skip -> PomodoroCommand.Skip
            PomodoroAction.Reset -> PomodoroCommand.Reset
            is PomodoroAction.StepSetting ->
                PomodoroCommand.UpdateSettings(repository.pomodoro.value.settings.step(action.field, action.delta))
        }
        viewModelScope.launch { repository.send(command) }
    }

    /** Rodando: atualiza 4x por segundo (a contagem é em segundos; um ciclo dura horas). */
    private fun ticks(pomodoro: Pomodoro): Flow<Long> =
        if (pomodoro.status == PomodoroStatus.Running) clockTicks(clock, TICK_MILLIS) else flowOf(clock.nowMillis())

    companion object {
        const val TICK_MILLIS = 250L
    }
}

/** Ajuste de um campo; os limites são aplicados pelo domínio (PomodoroSettings.normalized). */
internal fun PomodoroSettings.step(field: PomodoroSettingField, delta: Int): PomodoroSettings = when (field) {
    PomodoroSettingField.Focus -> copy(focusMillis = focusMillis + delta * MINUTE)
    PomodoroSettingField.ShortBreak -> copy(shortBreakMillis = shortBreakMillis + delta * MINUTE)
    PomodoroSettingField.LongBreak -> copy(longBreakMillis = longBreakMillis + delta * MINUTE)
    PomodoroSettingField.FocusesBeforeLongBreak -> copy(focusesBeforeLongBreak = focusesBeforeLongBreak + delta)
}.normalized()

private fun Pomodoro.toUiState(nowMillis: Long): PomodoroUiState {
    val elapsed = elapsedMillis(nowMillis)
    val phase = phaseForElapsed(elapsed)
    val next = phaseForElapsed(phase.endElapsedMillis)
    val remaining = phase.endElapsedMillis - elapsed
    return PomodoroUiState(
        status = status,
        phase = phase.kind,
        remainingText = formatCountdown(remaining),
        remainingFraction = remaining.toFloat() / phase.durationMillis,
        completedFocuses = phase.completedFocuses,
        nextPhase = next.kind,
        nextPhaseMinutes = (next.durationMillis / MINUTE).toInt(),
        settings = PomodoroSettingsFields(
            focusMinutes = (settings.focusMillis / MINUTE).toInt(),
            shortBreakMinutes = (settings.shortBreakMillis / MINUTE).toInt(),
            longBreakMinutes = (settings.longBreakMillis / MINUTE).toInt(),
            focusesBeforeLongBreak = settings.focusesBeforeLongBreak,
        ),
    )
}
