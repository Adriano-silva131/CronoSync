package com.adriano.cronosync.alarm

import android.content.Context
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import com.adriano.cronosync.alarm.data.AlarmSilenceRepository
import com.adriano.cronosync.alarm.data.AlarmSource
import com.adriano.cronosync.alarm.notification.PomodoroNotifications
import com.adriano.cronosync.core.AlignedClock
import com.adriano.cronosync.pomodoro.data.PomodoroRepository
import com.adriano.cronosync.pomodoro.domain.Pomodoro
import com.adriano.cronosync.pomodoro.domain.ringingTransitionAtMillis
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class PomodoroAlarmController(
    private val context: Context,
    private val repository: PomodoroRepository,
    private val clock: AlignedClock,
    private val scheduler: AlarmScheduler,
    private val notifications: PomodoroNotifications,
    private val silence: AlarmSilenceRepository,
) {
    fun start(scope: CoroutineScope) {
        combine(repository.pomodoro, silence.silencedAtMillis(AlarmSource.Pomodoro), ::Pair)
            .onEach { (pomodoro, silenced) -> sync(pomodoro, silenced) }
            .launchIn(scope)
    }

    fun onTransitionDue() {
        val pomodoro = repository.pomodoro.value
        val silenced = silence.silencedAtMillis(AlarmSource.Pomodoro).value
        val transition = pomodoro.ringingTransitionAtMillis(clock.nowMillis() + EARLY_FIRE_TOLERANCE_MILLIS, silenced)
        if (transition != null && clock.nowMillis() - transition < STALE_AFTER_MILLIS) {
            if (!AlarmRingingService.start(context, AlarmSource.Pomodoro)) notifications.showRingingFallback()
            if (ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                context.startActivity(alarmActivityIntent(context, AlarmSource.Pomodoro))
            }
        }
        sync(pomodoro, silenced)
    }

    private fun sync(pomodoro: Pomodoro, silencedAtMillis: Long?) {
        val now = clock.nowMillis()
        val next = pomodoro.nextTransitionAtMillis(now)
        if (next != null) {
            // A troca está no relógio do servidor; AlarmManager e notificação usam o do aparelho.
            val localAt = clock.toLocalMillis(next)
            scheduler.schedule(AlarmSource.Pomodoro, localAt)
            notifications.showRunning(pomodoro.phaseAt(now).kind, localAt)
        } else {
            scheduler.cancel(AlarmSource.Pomodoro)
            notifications.cancelRunning()
        }
        if (pomodoro.ringingTransitionAtMillis(now, silencedAtMillis) == null) {
            AlarmRingingService.stop(context, AlarmSource.Pomodoro)
            notifications.cancelRingingFallback()
        }
    }

    private companion object {
        const val EARLY_FIRE_TOLERANCE_MILLIS = 2_000L
        const val STALE_AFTER_MILLIS = 60_000L
    }
}
