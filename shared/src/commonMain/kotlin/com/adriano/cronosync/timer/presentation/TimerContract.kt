package com.adriano.cronosync.timer.presentation

import com.adriano.cronosync.timer.domain.TimerStatus

data class TimerUiState(
    val status: TimerStatus,
    val remainingText: String,
    /** Fração que ainda falta, de 1 (cheio) a 0 (acabou). Alimenta o indicador circular. */
    val remainingFraction: Float,
    /** Duração configurada, separada em campos para o seletor (só editável com o timer parado). */
    val duration: DurationFields,
    val canStart: Boolean,
    /** false numa sala sem conexão: os botões ficam desabilitados em vez de engolir toques. */
    val controlsEnabled: Boolean = true,
)

data class DurationFields(
    val hours: Int = 0,
    val minutes: Int = 0,
    val seconds: Int = 0,
)

enum class DurationField { Hours, Minutes, Seconds }

sealed interface TimerAction {
    /** Botões +/− do seletor. O ViewModel calcula a nova duração (com "volta" 59 → 00). */
    data class StepDuration(val field: DurationField, val delta: Int) : TimerAction
    data object Start : TimerAction
    data object Pause : TimerAction
    data object Reset : TimerAction
}
