package com.adriano.cronosync.stopwatch.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adriano.cronosync.core.Clock
import com.adriano.cronosync.core.clockTicks
import com.adriano.cronosync.stopwatch.data.StopwatchRepository
import com.adriano.cronosync.stopwatch.domain.Stopwatch
import com.adriano.cronosync.stopwatch.domain.StopwatchCommand
import com.adriano.cronosync.stopwatch.domain.StopwatchStatus
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

class StopwatchViewModel(
    private val repository: StopwatchRepository,
    private val clock: Clock,
) : ViewModel() {

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<StopwatchUiState> = repository.stopwatch
        .flatMapLatest { stopwatch -> ticks(stopwatch).map { now -> stopwatch.toUiState(now) } }
        .combine(repository.acceptsCommands) { state, enabled -> state.copy(controlsEnabled = enabled) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = repository.stopwatch.value.toUiState(clock.nowMillis())
                .copy(controlsEnabled = repository.acceptsCommands.value),
        )

    fun onAction(action: StopwatchAction) {
        if (!repository.acceptsCommands.value) return
        viewModelScope.launch { repository.send(action.toCommand()) }
    }

    private fun ticks(stopwatch: Stopwatch): Flow<Long> =
        if (stopwatch.status == StopwatchStatus.Running) clockTicks(clock) else flowOf(clock.nowMillis())
}

private fun StopwatchAction.toCommand(): StopwatchCommand = when (this) {
    StopwatchAction.Start -> StopwatchCommand.Start
    StopwatchAction.Pause -> StopwatchCommand.Pause
    StopwatchAction.Reset -> StopwatchCommand.Reset
    StopwatchAction.Lap -> StopwatchCommand.RecordLap
}

private fun Stopwatch.toUiState(nowMillis: Long) = StopwatchUiState(
    status = status,
    elapsedText = formatElapsed(elapsedMillis(nowMillis)),
    laps = laps.asReversed().map { lap ->
        LapUiModel(
            number = lap.number,
            lapText = formatElapsed(lap.lapMillis),
            totalText = formatElapsed(lap.totalMillis),
        )
    },
    lapLimitReached = lapLimitReached,
)
