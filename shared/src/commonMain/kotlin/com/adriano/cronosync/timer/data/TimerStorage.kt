package com.adriano.cronosync.timer.data

import com.adriano.cronosync.timer.domain.Timer
import com.adriano.cronosync.timer.domain.TimerStatus
import com.russhwolf.settings.Settings

/**
 * Persiste o timer para ele sobreviver ao Android matar o processo do app.
 *
 * Sem isso: o timer está rodando, o app vai para segundo plano, o sistema mata o processo para
 * liberar memória, o alarme dispara, o app renasce… com o timer zerado em memória.
 *
 * A API é síncrona de propósito: o repositório precisa do estado salvo já na criação, antes de
 * qualquer um observar — senão alguém veria um "timer zerado" falso por alguns milissegundos.
 */
interface TimerStorage {
    fun load(): Timer?
    fun save(timer: Timer)
}

/**
 * Implementação com multiplatform-settings: uma interface chave-valor comum que, por baixo, usa
 * SharedPreferences no Android, java.util.prefs no desktop e localStorage na web.
 */
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
