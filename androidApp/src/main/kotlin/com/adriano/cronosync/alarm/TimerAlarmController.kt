package com.adriano.cronosync.alarm

import android.content.Context
import com.adriano.cronosync.alarm.data.AlarmSilenceRepository
import com.adriano.cronosync.alarm.data.AlarmSource
import com.adriano.cronosync.alarm.notification.TimerNotifications
import com.adriano.cronosync.core.AlignedClock
import com.adriano.cronosync.timer.data.TimerRepository
import com.adriano.cronosync.timer.domain.AlarmPlan
import com.adriano.cronosync.timer.domain.Timer
import com.adriano.cronosync.timer.domain.alarmPlan
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class TimerAlarmController(
    private val context: Context,
    private val repository: TimerRepository,
    private val clock: AlignedClock,
    private val scheduler: AlarmScheduler,
    private val notifications: TimerNotifications,
    private val silence: AlarmSilenceRepository,
) {
    fun start(scope: CoroutineScope) {
        combine(repository.timer, silence.silencedAtMillis(AlarmSource.Timer), ::Pair)
            .onEach { (timer, silenced) -> sync(timer, silenced) }
            .launchIn(scope)
    }

    private fun sync(timer: Timer, silencedFinishAtMillis: Long?) {
        when (val plan = timer.alarmPlan(clock.nowMillis(), silencedFinishAtMillis)) {
            is AlarmPlan.Schedule -> {
                // O fim está no relógio do servidor; AlarmManager e notificação usam o do aparelho.
                val localAt = clock.toLocalMillis(plan.atMillis)
                scheduler.schedule(AlarmSource.Timer, localAt)
                notifications.showRunning(localAt)
            }
            AlarmPlan.Ringing -> Unit
            AlarmPlan.Cancel -> {
                scheduler.cancel(AlarmSource.Timer)
                AlarmRingingService.stop(context, AlarmSource.Timer)
                notifications.cancelAll()
            }
        }
    }
}
