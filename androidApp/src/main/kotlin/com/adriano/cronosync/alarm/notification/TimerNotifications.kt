package com.adriano.cronosync.alarm.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.adriano.cronosync.R
import com.adriano.cronosync.alarm.TimerActionReceiver
import com.adriano.cronosync.alarm.alarmActivityPendingIntent
import com.adriano.cronosync.alarm.data.AlarmSource
import com.adriano.cronosync.alarm.openAppPendingIntent
import com.adriano.cronosync.alarm.timerActionPendingIntent

class TimerNotifications(private val context: Context) {

    private val manager = NotificationManagerCompat.from(context)

    fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val system = context.getSystemService(NotificationManager::class.java)
        system.deleteNotificationChannel(LEGACY_CHANNEL_ALARM)

        val running = NotificationChannel(
            CHANNEL_RUNNING,
            context.getString(R.string.channel_timer_running),
            NotificationManager.IMPORTANCE_LOW,
        ).apply { setShowBadge(false) }

        val alarm = NotificationChannel(
            CHANNEL_ALARM,
            context.getString(R.string.channel_timer_alarm),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            setSound(null, null)
            enableVibration(false)
        }

        system.createNotificationChannels(listOf(running, alarm))
    }

    fun showRunning(finishesAtMillis: Long) {
        val notification = NotificationCompat.Builder(context, CHANNEL_RUNNING)
            .setSmallIcon(R.drawable.ic_timer)
            .setContentTitle(context.getString(R.string.timer_notification_running))
            .setWhen(finishesAtMillis)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            // Não usar setSilent(true): o sistema deixa de abrir o full-screen intent. O silêncio vem do canal.
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(openAppPendingIntent(context))
            .addAction(0, context.getString(R.string.action_pause), timerActionPendingIntent(context, TimerActionReceiver.ACTION_PAUSE))
            .addAction(0, context.getString(R.string.action_reset), timerActionPendingIntent(context, TimerActionReceiver.ACTION_RESET))
            .build()
        post(ID_RUNNING, notification)
    }

    fun buildFinished(): Notification {
        val stop = timerActionPendingIntent(context, TimerActionReceiver.ACTION_RESET)
        return NotificationCompat.Builder(context, CHANNEL_ALARM)
            .setSmallIcon(R.drawable.ic_timer)
            .setContentTitle(context.getString(R.string.timer_notification_finished))
            .setContentText(context.getString(R.string.timer_notification_finished_text))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setContentIntent(alarmActivityPendingIntent(context, AlarmSource.Timer))
            .setFullScreenIntent(alarmActivityPendingIntent(context, AlarmSource.Timer), true)
            .addAction(0, context.getString(R.string.action_stop), stop)
            .setDeleteIntent(stop)
            .build()
    }

    fun showFinished() {
        manager.cancel(ID_RUNNING)
        post(ID_FINISHED, buildFinished())
    }

    fun cancelRunning() {
        manager.cancel(ID_RUNNING)
    }

    fun cancelAll() {
        manager.cancel(ID_RUNNING)
        manager.cancel(ID_FINISHED)
    }

    private fun post(id: Int, notification: Notification) = postIfAllowed(context, manager, id, notification)

    companion object {
        const val ID_FINISHED = 2
        private const val ID_RUNNING = 1
        const val CHANNEL_RUNNING = "timer_running"
        // Um canal não muda de som depois de criado: alterar o canal de alarme exige um ID novo.
        const val CHANNEL_ALARM = "timer_alarm_v2"
        private const val LEGACY_CHANNEL_ALARM = "timer_alarm"
    }
}
