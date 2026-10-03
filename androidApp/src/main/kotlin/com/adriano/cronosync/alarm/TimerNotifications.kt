package com.adriano.cronosync.alarm

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.adriano.cronosync.R
import com.adriano.cronosync.alarm.AlarmSource

/**
 * As duas notificações do timer:
 * - "rodando": silenciosa e fixa, com contagem regressiva que o próprio sistema atualiza
 *   (setUsesChronometer) — funciona mesmo com o app fechado, sem gastar nada.
 * - "terminou": alta prioridade com full-screen intent (abre a tela de alarme sobre a tela de
 *   bloqueio). Ela é SILENCIOSA: o som e a vibração são tocados pelo [AlarmRingingService], porque
 *   o som de notificação é filtrado pelo sistema (modo vibrar, "Não perturbe", regras do fabricante).
 */
class TimerNotifications(private val context: Context) {

    private val manager = NotificationManagerCompat.from(context)

    /**
     * Canais (Android 8+) definem som/vibração/importância e o usuário pode ajustá-los nas
     * configurações. Depois de criado, o app não consegue mais mudar o som de um canal — por isso
     * o canal de alarme ganhou um ID novo e o antigo (que tinha som) é apagado.
     */
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
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(openAppPendingIntent(context))
            .addAction(0, context.getString(R.string.action_pause), timerActionPendingIntent(context, TimerActionReceiver.ACTION_PAUSE))
            .addAction(0, context.getString(R.string.action_reset), timerActionPendingIntent(context, TimerActionReceiver.ACTION_RESET))
            .build()
        post(ID_RUNNING, notification)
    }

    /** Notificação de "tempo esgotado". Usada pelo [AlarmRingingService] como sua notificação de foreground. */
    fun buildFinished(): Notification {
        val stop = timerActionPendingIntent(context, TimerActionReceiver.ACTION_RESET)
        return NotificationCompat.Builder(context, CHANNEL_ALARM)
            .setSmallIcon(R.drawable.ic_timer)
            .setContentTitle(context.getString(R.string.timer_notification_finished))
            .setContentText(context.getString(R.string.timer_notification_finished_text))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            // NÃO usar setSilent(true) aqui: ele coloca a notificação num grupo "silencioso" e o sistema
            // deixa de abrir o full-screen intent. O silêncio vem do canal (sem som) — quem toca é o serviço.
            .setOngoing(true)
            .setContentIntent(alarmActivityPendingIntent(context, AlarmSource.Timer))
            // Full-screen intent: com a tela bloqueada/apagada, o sistema abre a AlarmActivity direto.
            // Com o aparelho em uso, vira uma notificação "fixa" no topo, que não some sozinha.
            .setFullScreenIntent(alarmActivityPendingIntent(context, AlarmSource.Timer), true)
            .addAction(0, context.getString(R.string.action_stop), stop)
            .setDeleteIntent(stop)
            .build()
    }

    /** Plano B, caso o sistema não deixe iniciar o serviço: pelo menos a notificação aparece. */
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

    /** No Android 13+ notificação exige permissão em tempo de execução (pedida ao iniciar um timer). */
    private fun post(id: Int, notification: Notification) = postIfAllowed(context, manager, id, notification)

    companion object {
        const val ID_FINISHED = 2
        private const val ID_RUNNING = 1
        // Canais compartilhados com o Pomodoro (ver PomodoroNotifications).
        const val CHANNEL_RUNNING = "timer_running"
        const val CHANNEL_ALARM = "timer_alarm_v2"
        private const val LEGACY_CHANNEL_ALARM = "timer_alarm"
    }
}

/** No Android 13+ notificação exige permissão em tempo de execução (pedida ao iniciar um timer/Pomodoro). */
internal fun postIfAllowed(context: Context, manager: NotificationManagerCompat, id: Int, notification: Notification) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    ) {
        return
    }
    manager.notify(id, notification)
}
