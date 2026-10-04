package com.adriano.cronosync.timer.data

import com.adriano.cronosync.timer.domain.Timer
import com.adriano.cronosync.timer.domain.TimerStatus
import com.russhwolf.settings.Settings

// Síncrona de propósito: o repositório precisa do estado salvo já na criação, antes de alguém observar.
interface TimerStorage {
    fun load(): Timer?
    fun save(timer: Timer)
}

class SettingsTimerStorage(private val settings: Settings) : TimerStorage {

    override fun load(): Timer? {
        val status = settings.getStringOrNull(KEY_STATUS)
            ?.let { name -> TimerStatus.entries.firstOrNull { it.name == name } }
            ?: return null
        return Timer(
            status = status,
            durationMillis = settings.getLong(KEY_DURATION, Timer.DEFAULT_DURATION_MILLIS),
            accumulatedMillis = settings.getLong(KEY_ACCUMULATED, 0L),
            runningSinceMillis = settings.getLongOrNull(KEY_RUNNING_SINCE),
        )
    }

    override fun save(timer: Timer) {
        settings.putString(KEY_STATUS, timer.status.name)
        settings.putLong(KEY_DURATION, timer.durationMillis)
        settings.putLong(KEY_ACCUMULATED, timer.accumulatedMillis)
        timer.runningSinceMillis
            ?.let { settings.putLong(KEY_RUNNING_SINCE, it) }
            ?: settings.remove(KEY_RUNNING_SINCE)
    }

    private companion object {
        const val KEY_STATUS = "timer.status"
        const val KEY_DURATION = "timer.durationMillis"
        const val KEY_ACCUMULATED = "timer.accumulatedMillis"
        const val KEY_RUNNING_SINCE = "timer.runningSinceMillis"
    }
}
