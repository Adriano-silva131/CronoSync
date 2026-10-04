package com.adriano.cronosync.stopwatch.presentation

import com.adriano.cronosync.stopwatch.domain.StopwatchStatus

data class StopwatchUiState(
    val status: StopwatchStatus = StopwatchStatus.Idle,
    val elapsedText: String = formatElapsed(0L),
    val laps: List<LapUiModel> = emptyList(),
    val controlsEnabled: Boolean = true,
    val lapLimitReached: Boolean = false,
)

data class LapUiModel(
    val number: Int,
    val lapText: String,
    val totalText: String,
)

sealed interface StopwatchAction {
    data object Start : StopwatchAction
    data object Pause : StopwatchAction
    data object Reset : StopwatchAction
    data object Lap : StopwatchAction
}
