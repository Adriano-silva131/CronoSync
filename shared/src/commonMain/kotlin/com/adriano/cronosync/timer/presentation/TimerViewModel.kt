package com.adriano.cronosync.timer.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adriano.cronosync.core.Clock
import com.adriano.cronosync.core.clockTicks
import com.adriano.cronosync.timer.data.TimerRepository
import com.adriano.cronosync.timer.domain.Timer
import com.adriano.cronosync.timer.domain.TimerCommand
import com.adriano.cronosync.timer.domain.TimerStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.launch

class TimerViewModel(
    private val repository: TimerRepository,
    private val clock: Clock,
) : ViewModel() {

    /** Mesma estrutura do StopwatchViewModel: estado do repositório + "agora", recalculado a cada tick. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<TimerUiState> = repository.timer
        .flatMapLatest { timer -> ticks(timer).map { now -> timer.toUiState(now) } }
        // Sem conexão numa sala, a tela desabilita os botões (ver controlsEnabled).
        .combine(repository.acceptsCommands) { state, enabled -> state.copy(controlsEnabled = enabled) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = repository.timer.value.toUiState(clock.nowMillis())
                .copy(controlsEnabled = repository.acceptsCommands.value),
        )

    fun onAction(action: TimerAction) {
        // Defesa extra além dos botões desabilitados: sem conexão, nenhum comando sai daqui.
        if (!repository.acceptsCommands.value) return
        val command = when (action) {
            is TimerAction.StepDuration -> {
                val current = repository.timer.value.durationMillis.toDurationFields()
                TimerCommand.SetDuration(current.step(action.field, action.delta).toMillis())
            }
            TimerAction.Start -> TimerCommand.Start
            TimerAction.Pause -> TimerCommand.Pause
            TimerAction.Reset -> TimerCommand.Reset
        }
        viewModelScope.launch { repository.send(command) }
    }

    /**
     * Diferença para o cronômetro: o timer tem fim. transformWhile emite o tick e para o Flow logo
     * depois do primeiro instante em que o timer está Finished — sem gastar bateria com um "00:00" parado.
     */
    private fun ticks(timer: Timer): Flow<Long> =
        if (timer.status == TimerStatus.Running) {
            clockTicks(clock).transformWhile { now ->
                emit(now)
                timer.statusAt(now) != TimerStatus.Finished
            }
        } else {
            flowOf(clock.nowMillis())
        }
}

private fun Timer.toUiState(nowMillis: Long): TimerUiState {
    val remaining = remainingMillis(nowMillis)
    return TimerUiState(
        status = statusAt(nowMillis),
        remainingText = formatCountdown(remaining),
        remainingFraction = if (durationMillis > 0L) remaining.toFloat() / durationMillis else 0f,
        duration = durationMillis.toDurationFields(),
        canStart = when (statusAt(nowMillis)) {
            TimerStatus.Idle -> durationMillis > 0L
            TimerStatus.Paused -> true
            TimerStatus.Running, TimerStatus.Finished -> false
        },
    )
}
