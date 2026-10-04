package com.adriano.cronosync.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import com.adriano.cronosync.alarm.data.AlarmSilenceRepository
import com.adriano.cronosync.alarm.data.AlarmSource
import com.adriano.cronosync.alarm.notification.TimerNotifications
import com.adriano.cronosync.core.Clock
import com.adriano.cronosync.timer.data.TimerRepository
import com.adriano.cronosync.timer.domain.finishesAtMillis
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

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
        val finishesAt = repository.timer.value.finishesAtMillis() ?: return
        // Alarme velho (timer mudou em outro aparelho) não toca; a tolerância cobre o sistema disparar antes.
        if (finishesAt - clock.nowMillis() > EARLY_FIRE_TOLERANCE_MILLIS) return
        if (finishesAt == silence.silencedAtMillis(AlarmSource.Timer).value) return

        if (!AlarmRingingService.start(context, AlarmSource.Timer)) notifications.showFinished()
        if (ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            context.startActivity(alarmActivityIntent(context, AlarmSource.Timer))
        }
    }

    private companion object {
        const val EARLY_FIRE_TOLERANCE_MILLIS = 2_000L
    }
}
