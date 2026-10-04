package com.adriano.cronosync.alarm

import android.app.AlarmManager
import android.content.Context
import android.os.Build
import androidx.core.content.getSystemService
import com.adriano.cronosync.alarm.data.AlarmSource

class AlarmScheduler(private val context: Context) {

    private val alarmManager: AlarmManager = requireNotNull(context.getSystemService())

    fun schedule(source: AlarmSource, atMillis: Long) {
        val operation = alarmFiredPendingIntent(context, source)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, operation)
        } else {
            val info = AlarmManager.AlarmClockInfo(atMillis, openAppPendingIntent(context))
            // setAlarmClock: tratado como despertador, dispara mesmo em Doze.
            alarmManager.setAlarmClock(info, operation)
        }
    }

    fun cancel(source: AlarmSource) {
        alarmManager.cancel(alarmFiredPendingIntent(context, source))
    }
}
