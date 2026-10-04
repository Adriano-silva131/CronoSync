package com.adriano.cronosync.pomodoro.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adriano.cronosync.alarm.data.AlarmSilenceRepository
import com.adriano.cronosync.alarm.data.AlarmSource
import com.adriano.cronosync.core.Clock
import com.adriano.cronosync.core.clockTicks
import com.adriano.cronosync.pomodoro.data.PomodoroRepository
import com.adriano.cronosync.pomodoro.domain.Pomodoro
import com.adriano.cronosync.pomodoro.domain.PomodoroPhaseKind
import com.adriano.cronosync.pomodoro.domain.PomodoroSettings.Companion.MINUTE
import com.adriano.cronosync.pomodoro.domain.ringingTransitionAtMillis
import com.adriano.cronosync.timer.presentation.formatCountdown
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class PomodoroAlarmUiState(
    val phase: PomodoroPhaseKind,
    val phaseMinutes: Int,
    val remainingText: String,
    val isDismissed: Boolean,
)

sealed interface PomodoroAlarmAction {
    data object Stop : PomodoroAlarmAction
}

class PomodoroAlarmViewModel(
    private val repository: PomodoroRepository,
    private val clock: Clock,
    private val silence: AlarmSilenceRepository,
) : ViewModel() {

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<PomodoroAlarmUiState> =
        combine(repository.pomodoro, silence.silencedAtMillis(AlarmSource.Pomodoro), ::Pair)
            .flatMapLatest { (pomodoro, silenced) ->
                clockTicks(clock, TICK_MILLIS).map { now -> pomodoro.toAlarmUiState(now, silenced) }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = repository.pomodoro.value.toAlarmUiState(
                    clock.nowMillis(),
                    silence.silencedAtMillis(AlarmSource.Pomodoro).value,
                ),
            )

    fun onAction(action: PomodoroAlarmAction) {
        when (action) {
            PomodoroAlarmAction.Stop -> {
                val now = clock.nowMillis()
                repository.pomodoro.value.lastTransitionAtMillis(now)?.let { silence.silence(AlarmSource.Pomodoro, it) }
            }
        }
    }

    private companion object {
        const val TICK_MILLIS = 250L
    }
}

private fun Pomodoro.toAlarmUiState(nowMillis: Long, silencedAtMillis: Long?): PomodoroAlarmUiState {
    val elapsed = elapsedMillis(nowMillis)
    val phase = phaseForElapsed(elapsed)
    return PomodoroAlarmUiState(
        phase = phase.kind,
        phaseMinutes = (phase.durationMillis / MINUTE).toInt(),
        remainingText = formatCountdown(phase.endElapsedMillis - elapsed),
        isDismissed = ringingTransitionAtMillis(nowMillis, silencedAtMillis) == null,
    )
}
