package com.adriano.cronosync.pomodoro.presentation

import com.adriano.cronosync.pomodoro.domain.PomodoroPhaseKind
import com.adriano.cronosync.pomodoro.domain.PomodoroStatus

data class PomodoroUiState(
    val status: PomodoroStatus,
    val phase: PomodoroPhaseKind,
    /** Quanto falta para a fase atual acabar, ex.: "24:13". */
    val remainingText: String,
    /** De 1 (fase começando) a 0 (fase acabando). Alimenta o anel de progresso. */
    val remainingFraction: Float,
    val completedFocuses: Int,
    val nextPhase: PomodoroPhaseKind,
    val nextPhaseMinutes: Int,
    /** Ajustes do ciclo em minutos (só editáveis com o Pomodoro parado). */
    val settings: PomodoroSettingsFields,
    /** false numa sala sem conexão: os botões ficam desabilitados em vez de engolir toques. */
    val controlsEnabled: Boolean = true,
)

data class PomodoroSettingsFields(
    val focusMinutes: Int,
    val shortBreakMinutes: Int,
    val longBreakMinutes: Int,
    val focusesBeforeLongBreak: Int,
)

enum class PomodoroSettingField { Focus, ShortBreak, LongBreak, FocusesBeforeLongBreak }

sealed interface PomodoroAction {
    data object Start : PomodoroAction
    data object Pause : PomodoroAction
    data object Skip : PomodoroAction
    data object Reset : PomodoroAction

    /** Botões +/− dos ajustes (minutos, ou quantidade de focos antes da pausa longa). */
    data class StepSetting(val field: PomodoroSettingField, val delta: Int) : PomodoroAction
}
