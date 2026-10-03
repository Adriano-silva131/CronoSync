package com.adriano.cronosync.alarm

import android.content.Context
import com.adriano.cronosync.core.AlignedClock
import com.adriano.cronosync.timer.data.TimerRepository
import com.adriano.cronosync.timer.domain.AlarmPlan
import com.adriano.cronosync.timer.domain.Timer
import com.adriano.cronosync.timer.domain.alarmPlan
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * Mantém alarme e notificações de acordo com o estado do timer.
 *
 * É o mesmo fluxo unidirecional da UI, só que o "consumidor" do estado é o sistema em vez de uma
 * tela: ninguém chama "agende o alarme" — o controller observa o repositório e reage. Por isso,
 * quando a sincronização chegar, um timer iniciado em OUTRO aparelho também tocará neste.
 */
class TimerAlarmController(
    private val context: Context,
    private val repository: TimerRepository,
    private val clock: AlignedClock,
    private val scheduler: AlarmScheduler,
    private val notifications: TimerNotifications,
    private val silence: AlarmSilenceRepository,
) {
    fun start(scope: CoroutineScope) {
        // Reage ao timer E ao "já parei aqui" (Parar sem conexão também precisa silenciar).
        combine(repository.timer, silence.silencedAtMillis(AlarmSource.Timer), ::Pair)
            .onEach { (timer, silenced) -> sync(timer, silenced) }
            .launchIn(scope)
    }

    private fun sync(timer: Timer, silencedFinishAtMillis: Long?) {
        when (val plan = timer.alarmPlan(clock.nowMillis(), silencedFinishAtMillis)) {
            is AlarmPlan.Schedule -> {
                // O instante do fim está no relógio do servidor; AlarmManager e notificação usam o do aparelho.
                val localAt = clock.toLocalMillis(plan.atMillis)
                scheduler.schedule(AlarmSource.Timer, localAt)
                notifications.showRunning(localAt)
            }
            // O alarme já disparou (ou está disparando agora): quem cuida é o AlarmFiredReceiver.
            AlarmPlan.Ringing -> Unit
            AlarmPlan.Cancel -> {
                scheduler.cancel(AlarmSource.Timer)
                AlarmRingingService.stop(context, AlarmSource.Timer) // para o toque do timer, se estiver tocando
                notifications.cancelAll()
            }
        }
    }
}
