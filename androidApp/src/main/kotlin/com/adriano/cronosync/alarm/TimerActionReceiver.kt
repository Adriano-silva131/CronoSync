package com.adriano.cronosync.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.adriano.cronosync.alarm.data.AlarmSilenceRepository
import com.adriano.cronosync.alarm.data.AlarmSource
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

class TimerActionReceiver : BroadcastReceiver(), KoinComponent {
    private val repository: TimerRepository by inject()
    private val pomodoroRepository: PomodoroRepository by inject()
    private val clock: Clock by inject()
    private val silence: AlarmSilenceRepository by inject()
    private val appScope: CoroutineScope by inject()

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_RESET ->
                // Silencia este aparelho antes do comando: sem conexão, o comando não chega e o alarme não pararia.
                repository.timer.value.finishesAtMillis()?.let { silence.silence(AlarmSource.Timer, it) }
            ACTION_POMODORO_SILENCE -> {
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
