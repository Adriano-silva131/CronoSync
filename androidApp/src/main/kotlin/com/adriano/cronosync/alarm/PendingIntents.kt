package com.adriano.cronosync.alarm

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.adriano.cronosync.MainActivity
import com.adriano.cronosync.alarm.data.AlarmSource

private const val FLAGS = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT

internal fun openAppPendingIntent(context: Context): PendingIntent {
    val intent = Intent(context, MainActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    return PendingIntent.getActivity(context, 0, intent, FLAGS)
}

internal const val EXTRA_ALARM_SOURCE = "com.adriano.cronosync.alarm.SOURCE"

// requestCode distinto por fonte/ação: senão o Android trata os PendingIntents como o mesmo.
private fun AlarmSource.requestCode(base: Int) = base + ordinal * 10

internal fun Intent.alarmSource(): AlarmSource =
    getStringExtra(EXTRA_ALARM_SOURCE)?.let { name -> AlarmSource.entries.firstOrNull { it.name == name } } ?: AlarmSource.Timer

internal fun alarmActivityIntent(context: Context, source: AlarmSource): Intent =
    Intent(context, AlarmActivity::class.java)
        .putExtra(EXTRA_ALARM_SOURCE, source.name)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION)

internal fun alarmActivityPendingIntent(context: Context, source: AlarmSource): PendingIntent =
    PendingIntent.getActivity(context, source.requestCode(1), alarmActivityIntent(context, source), FLAGS)

internal fun alarmFiredPendingIntent(context: Context, source: AlarmSource): PendingIntent {
    val intent = Intent(context, AlarmFiredReceiver::class.java).putExtra(EXTRA_ALARM_SOURCE, source.name)
    return PendingIntent.getBroadcast(context, source.requestCode(0), intent, FLAGS)
}

internal fun timerActionPendingIntent(context: Context, action: String): PendingIntent {
    val intent = Intent(context, TimerActionReceiver::class.java).setAction(action)
    return PendingIntent.getBroadcast(context, action.hashCode(), intent, FLAGS)
}
