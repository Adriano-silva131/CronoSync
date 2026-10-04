package com.adriano.cronosync.alarm.data

import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AlarmSource { Timer, Pomodoro }

class AlarmSilenceRepository(private val settings: Settings) {

    private val silenced = AlarmSource.entries.associateWith { MutableStateFlow(settings.getLongOrNull(key(it))) }

    fun silencedAtMillis(source: AlarmSource): StateFlow<Long?> = silenced.getValue(source).asStateFlow()

    fun silence(source: AlarmSource, atMillis: Long) {
        settings.putLong(key(source), atMillis)
        silenced.getValue(source).value = atMillis
    }

    private fun key(source: AlarmSource) = when (source) {
        // Nome antigo mantido: versões anteriores do app já gravaram com ele.
        AlarmSource.Timer -> "alarm.silencedFinishAt"
        AlarmSource.Pomodoro -> "alarm.silenced.pomodoro"
    }
}
