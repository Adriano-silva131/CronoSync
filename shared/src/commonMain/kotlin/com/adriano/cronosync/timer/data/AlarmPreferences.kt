package com.adriano.cronosync.timer.data

import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Como ESTE aparelho avisa que o timer acabou. É uma preferência local (não sincronizada): cada
 * pessoa decide se o próprio celular faz barulho — ex.: silencioso de madrugada, só a tela acende.
 */
data class AlarmPreferences(
    val sound: Boolean = true,
    val vibration: Boolean = true,
)

class AlarmPreferencesRepository(private val settings: Settings) {

    private val _preferences = MutableStateFlow(
        AlarmPreferences(
            sound = settings.getBoolean(KEY_SOUND, true),
            vibration = settings.getBoolean(KEY_VIBRATION, true),
        ),
    )
    val preferences: StateFlow<AlarmPreferences> = _preferences.asStateFlow()

    fun setSound(enabled: Boolean) {
        settings.putBoolean(KEY_SOUND, enabled)
        _preferences.update { it.copy(sound = enabled) }
    }

    fun setVibration(enabled: Boolean) {
        settings.putBoolean(KEY_VIBRATION, enabled)
        _preferences.update { it.copy(vibration = enabled) }
    }

    private companion object {
        const val KEY_SOUND = "alarm.sound"
        const val KEY_VIBRATION = "alarm.vibration"
    }
}
