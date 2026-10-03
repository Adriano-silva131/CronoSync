package com.adriano.cronosync.alarm

import android.app.Notification
import android.content.Context
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.adriano.cronosync.R
import com.adriano.cronosync.core.Clock
import com.adriano.cronosync.pomodoro.data.PomodoroRepository
import com.adriano.cronosync.pomodoro.domain.PomodoroPhaseKind
import com.adriano.cronosync.pomodoro.domain.PomodoroSettings.Companion.MINUTE

/**
 * Notificações do Pomodoro, nos mesmos canais do timer:
 * - "rodando": fixa e silenciosa, com a contagem da fase atual (atualizada pelo sistema) e
 *   os botões Pausar / Pular;
 * - "troca de fase": alta prioridade com full-screen intent, igual ao alarme do timer — mas o
 *   "Parar" só silencia: o ciclo já seguiu para a próxima fase sozinho.
 */
class PomodoroNotifications(
    private val context: Context,
    private val repository: PomodoroRepository,
    private val clock: Clock,
) {
    private val manager = NotificationManagerCompat.from(context)

    fun showRunning(phase: PomodoroPhaseKind, endsAtLocalMillis: Long) {
        val notification = NotificationCompat.Builder(context, TimerNotifications.CHANNEL_RUNNING)
            .setSmallIcon(R.drawable.ic_timer)
            .setContentTitle(context.getString(R.string.pomodoro_running_title, context.getString(phase.label)))
            .setWhen(endsAtLocalMillis)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(openAppPendingIntent(context))
            .addAction(0, context.getString(R.string.action_pause), timerActionPendingIntent(context, TimerActionReceiver.ACTION_POMODORO_PAUSE))
            .addAction(0, context.getString(R.string.action_skip), timerActionPendingIntent(context, TimerActionReceiver.ACTION_POMODORO_SKIP))
            .build()
        postIfAllowed(context, manager, ID_RUNNING, notification)
    }

    /** Notificação da troca de fase (usada pelo AlarmRingingService). Descreve a fase que começou. */
    fun buildRinging(): Notification {
        val now = clock.nowMillis()
        val phase = repository.pomodoro.value.phaseAt(now)
        val silence = timerActionPendingIntent(context, TimerActionReceiver.ACTION_POMODORO_SILENCE)
        val title = if (phase.kind == PomodoroPhaseKind.Focus) R.string.pomodoro_ringing_focus else R.string.pomodoro_ringing_break
        return NotificationCompat.Builder(context, TimerNotifications.CHANNEL_ALARM)
            .setSmallIcon(R.drawable.ic_timer)
            .setContentTitle(context.getString(title))
            .setContentText(context.getString(R.string.pomodoro_ringing_text, context.getString(phase.kind.label), (phase.durationMillis / MINUTE).toInt()))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setContentIntent(alarmActivityPendingIntent(context, AlarmSource.Pomodoro))
            .setFullScreenIntent(alarmActivityPendingIntent(context, AlarmSource.Pomodoro), true)
            .addAction(0, context.getString(R.string.action_stop), silence)
            .setDeleteIntent(silence)
            .build()
    }

    /** Plano B, caso o sistema não deixe iniciar o serviço de alarme. */
    fun showRingingFallback() = postIfAllowed(context, manager, ID_RINGING_FALLBACK, buildRinging())

    fun cancelRunning() = manager.cancel(ID_RUNNING)

    fun cancelRingingFallback() = manager.cancel(ID_RINGING_FALLBACK)

    private companion object {
        const val ID_RUNNING = 3
        const val ID_RINGING_FALLBACK = 4
    }
}

@get:StringRes
private val PomodoroPhaseKind.label: Int
    get() = when (this) {
        PomodoroPhaseKind.Focus -> R.string.pomodoro_phase_focus
        PomodoroPhaseKind.ShortBreak -> R.string.pomodoro_phase_short_break
        PomodoroPhaseKind.LongBreak -> R.string.pomodoro_phase_long_break
    }
