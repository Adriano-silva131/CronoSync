package com.adriano.cronosync.timer.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adriano.cronosync.alarm.data.AlarmSilenceRepository
import com.adriano.cronosync.alarm.data.AlarmSource
import com.adriano.cronosync.core.Clock
import com.adriano.cronosync.core.clockTicks
import com.adriano.cronosync.timer.data.TimerRepository
import com.adriano.cronosync.timer.domain.Timer
import com.adriano.cronosync.timer.domain.TimerCommand
import com.adriano.cronosync.timer.domain.finishesAtMillis
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TimerAlarmUiState(
    val overtimeText: String,
    val isDismissed: Boolean,
)

sealed interface TimerAlarmAction {
    data object Stop : TimerAlarmAction
}

class TimerAlarmViewModel(
    private val repository: TimerRepository,
    private val clock: Clock,
    private val silence: AlarmSilenceRepository,
) : ViewModel() {

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<TimerAlarmUiState> = combine(repository.timer, silence.silencedAtMillis(AlarmSource.Timer), ::Pair)
        .flatMapLatest { (timer, silenced) ->
            if (timer.finishesAtMillis() != null) {
                clockTicks(clock, TICK_MILLIS).map { now -> timer.toAlarmUiState(now, silenced) }
            } else {
                flowOf(timer.toAlarmUiState(clock.nowMillis(), silenced))
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = repository.timer.value.toAlarmUiState(clock.nowMillis(), silence.silencedAtMillis(AlarmSource.Timer).value),
        )

    fun onAction(action: TimerAlarmAction) {
        when (action) {
            TimerAlarmAction.Stop -> {
                repository.timer.value.finishesAtMillis()?.let { silence.silence(AlarmSource.Timer, it) }
                viewModelScope.launch { repository.send(TimerCommand.Reset) }
            }
        }
    }

    companion object {
        const val TICK_MILLIS = 200L
    }
}

private fun Timer.toAlarmUiState(nowMillis: Long, silencedFinishAtMillis: Long?): TimerAlarmUiState {
    val finishesAt = finishesAtMillis()
    return TimerAlarmUiState(
        overtimeText = formatOvertime(if (finishesAt != null) nowMillis - finishesAt else 0L),
        isDismissed = finishesAt == null || finishesAt == silencedFinishAtMillis,
    )
}
