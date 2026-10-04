package com.adriano.cronosync.timer.presentation

import com.adriano.cronosync.timer.domain.TimerStatus

data class TimerUiState(
    val status: TimerStatus,
    val remainingText: String,
    val remainingFraction: Float,
    val duration: DurationFields,
    val canStart: Boolean,
    val controlsEnabled: Boolean = true,
)

data class DurationFields(
    val hours: Int = 0,
    val minutes: Int = 0,
    val seconds: Int = 0,
)

enum class DurationField { Hours, Minutes, Seconds }

sealed interface TimerAction {
    data class StepDuration(val field: DurationField, val delta: Int) : TimerAction
    data object Start : TimerAction
    data object Pause : TimerAction
    data object Reset : TimerAction
}
