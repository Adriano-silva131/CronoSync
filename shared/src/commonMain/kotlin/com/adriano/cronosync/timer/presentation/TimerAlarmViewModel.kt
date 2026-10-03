package com.adriano.cronosync.timer.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adriano.cronosync.alarm.AlarmSilenceRepository
import com.adriano.cronosync.alarm.AlarmSource
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
    /** Há quanto tempo o alarme está tocando, ex.: "-00:12". */
    val overtimeText: String,
    /** O timer foi parado/zerado (aqui ou em outro lugar): a tela de alarme deve fechar. */
    val isDismissed: Boolean,
)

sealed interface TimerAlarmAction {
    data object Stop : TimerAlarmAction
}

/**
 * ViewModel da tela de alarme. Fica no shared porque o desktop também terá uma janela de alarme.
 *
 * A tela só existe enquanto o timer está rodando/terminado. "Fechar" não é uma ação da tela: é
 * consequência do estado — se alguém zerar o timer (botão Parar, notificação, outro aparelho),
 * [TimerAlarmUiState.isDismissed] vira true e a tela se fecha sozinha.
 */
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
                // Silencia AQUI na hora (com ou sem conexão) e avisa o servidor para zerar para todos.
                repository.timer.value.finishesAtMillis()?.let { silence.silence(AlarmSource.Timer, it) }
                viewModelScope.launch { repository.send(TimerCommand.Reset) }
            }
        }
    }

    companion object {
        /** A tela mostra só segundos: não precisa de 60 atualizações por segundo. */
        const val TICK_MILLIS = 200L
    }
}

private fun Timer.toAlarmUiState(nowMillis: Long, silencedFinishAtMillis: Long?): TimerAlarmUiState {
    val finishesAt = finishesAtMillis()
    return TimerAlarmUiState(
        overtimeText = formatOvertime(if (finishesAt != null) nowMillis - finishesAt else 0L),
        // Fecha se o timer parou/zerou OU se este alarme já foi parado aqui (mesmo sem conexão).
        isDismissed = finishesAt == null || finishesAt == silencedFinishAtMillis,
    )
}
