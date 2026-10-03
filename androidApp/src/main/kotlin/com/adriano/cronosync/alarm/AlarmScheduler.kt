package com.adriano.cronosync.alarm

import android.app.AlarmManager
import android.content.Context
import android.os.Build
import androidx.core.content.getSystemService
import com.adriano.cronosync.alarm.AlarmSource

/**
 * Agenda os "despertares" (fim do timer, trocas de fase do Pomodoro) no AlarmManager do sistema.
 *
 * Por que não um simples `delay()` numa corrotina? Porque o Android congela ou mata apps em
 * segundo plano. O AlarmManager vive no sistema: dispara na hora mesmo com o app morto e o
 * aparelho em Doze (modo de economia com a tela desligada).
 */
class AlarmScheduler(private val context: Context) {

    private val alarmManager: AlarmManager = requireNotNull(context.getSystemService())

    /** Um alarme por fonte: agendar de novo a mesma fonte substitui o anterior. */
    fun schedule(source: AlarmSource, atMillis: Long) {
        val operation = alarmFiredPendingIntent(context, source)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            // Sem permissão de alarme exato: o sistema pode atrasar alguns minutos. Melhor que nada.
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, operation)
        } else {
            // setAlarmClock é o tipo mais confiável: o sistema trata como despertador e sai do Doze.
            val info = AlarmManager.AlarmClockInfo(atMillis, openAppPendingIntent(context))
            alarmManager.setAlarmClock(info, operation)
        }
    }

    fun cancel(source: AlarmSource) {
        alarmManager.cancel(alarmFiredPendingIntent(context, source))
    }
}
