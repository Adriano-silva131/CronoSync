package com.adriano.cronosync.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import com.adriano.cronosync.core.Clock
import com.adriano.cronosync.pomodoro.data.PomodoroRepository
import com.adriano.cronosync.pomodoro.domain.PomodoroCommand
import com.adriano.cronosync.timer.data.TimerRepository
import com.adriano.cronosync.timer.domain.TimerCommand
import com.adriano.cronosync.timer.domain.finishesAtMillis
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/*
 * BroadcastReceivers são pontos de entrada que o sistema chama mesmo com o app fechado.
 * Detalhe importante: se o processo estava morto, o Android cria o Application primeiro —
 * então o Koin e o TimerAlarmController já estão de pé quando onReceive roda.
 */

/** Chamado pelo AlarmManager no horário agendado: fim do timer ou troca de fase do Pomodoro. */
class AlarmFiredReceiver : BroadcastReceiver(), KoinComponent {
    private val repository: TimerRepository by inject()
    private val clock: Clock by inject()
    private val notifications: TimerNotifications by inject()
    private val silence: AlarmSilenceRepository by inject()
    private val pomodoroAlarm: PomodoroAlarmController by inject()

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.alarmSource()) {
            AlarmSource.Timer -> onTimerFinished(context)
            AlarmSource.Pomodoro -> pomodoroAlarm.onTransitionDue()
        }
    }

    private fun onTimerFinished(context: Context) {
        // Confere o estado atual: um alarme "velho" (ex.: timer pausado em outro aparelho) não deve tocar.
        // A tolerância cobre o caso do sistema disparar alguns milissegundos antes da hora.
        val finishesAt = repository.timer.value.finishesAtMillis() ?: return
        if (finishesAt - clock.nowMillis() > EARLY_FIRE_TOLERANCE_MILLIS) return
        // Já parado neste aparelho (ex.: "Zerar" sem conexão, que não chegou ao servidor).
        if (finishesAt == silence.silencedAtMillis(AlarmSource.Timer).value) return

        // Toca de verdade (som + vibração) num foreground service; se o sistema recusar, sobra a notificação.
        if (!AlarmRingingService.start(context, AlarmSource.Timer)) notifications.showFinished()
        // Com o app aberto na frente, o full-screen intent viraria só um aviso no topo. Como o app
        // está visível, o Android permite abrir uma Activity diretamente — então abrimos o alarme.
        if (ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            context.startActivity(alarmActivityIntent(context, AlarmSource.Timer))
        }
    }

    private companion object {
        const val EARLY_FIRE_TOLERANCE_MILLIS = 2_000L
    }
}

/** Botões das notificações (timer e Pomodoro). Só enviam comandos/silenciam — os controllers cuidam do resto. */
class TimerActionReceiver : BroadcastReceiver(), KoinComponent {
    private val repository: TimerRepository by inject()
    private val pomodoroRepository: PomodoroRepository by inject()
    private val clock: Clock by inject()
    private val silence: AlarmSilenceRepository by inject()
    private val appScope: CoroutineScope by inject()

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_RESET ->
                // Parar/Zerar silencia ESTE aparelho na hora, mesmo que o comando não chegue ao
                // servidor (sem conexão). Sem isso, o alarme continuaria tocando sem jeito de parar.
                repository.timer.value.finishesAtMillis()?.let { silence.silence(AlarmSource.Timer, it) }
            ACTION_POMODORO_SILENCE -> {
                // Só silencia: o ciclo segue sozinho, nada vai ao servidor.
                pomodoroRepository.pomodoro.value.lastTransitionAtMillis(clock.nowMillis())
                    ?.let { silence.silence(AlarmSource.Pomodoro, it) }
                return
            }
        }
        val send: suspend () -> Boolean = when (intent.action) {
            ACTION_PAUSE -> suspend { repository.send(TimerCommand.Pause) }
            ACTION_RESET -> suspend { repository.send(TimerCommand.Reset) }
            ACTION_POMODORO_PAUSE -> suspend { pomodoroRepository.send(PomodoroCommand.Pause) }
            ACTION_POMODORO_SKIP -> suspend { pomodoroRepository.send(PomodoroCommand.Skip) }
            else -> return
        }
        // goAsync: avisa o sistema para não matar o processo até terminarmos o trabalho assíncrono.
        val pending = goAsync()
        appScope.launch {
            try {
                send()
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_PAUSE = "com.adriano.cronosync.timer.PAUSE"
        const val ACTION_RESET = "com.adriano.cronosync.timer.RESET"
        const val ACTION_POMODORO_PAUSE = "com.adriano.cronosync.pomodoro.PAUSE"
        const val ACTION_POMODORO_SKIP = "com.adriano.cronosync.pomodoro.SKIP"
        const val ACTION_POMODORO_SILENCE = "com.adriano.cronosync.pomodoro.SILENCE"
    }
}

/**
 * Reiniciar o aparelho (ou atualizar o app) apaga os alarmes agendados. Este receiver não precisa
 * fazer nada: só de o sistema acordar o app, o Application inicia o TimerAlarmController, que lê o
 * timer salvo e reagenda o alarme se ele ainda estiver rodando.
 */
class RescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Boa prática de segurança: só aceitar as ações declaradas no manifesto.
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        // Nada mais a fazer — o reagendamento aconteceu no Application.onCreate.
    }
}
