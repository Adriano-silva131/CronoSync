package com.adriano.cronosync.alarm

import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** O que pode tocar um alarme. Cada fonte é silenciada separadamente. */
enum class AlarmSource { Timer, Pomodoro }

/**
 * "Neste aparelho, o alarme do instante X (de tal fonte) já foi parado."
 *
 * - Timer: o Parar também envia "Zerar" ao servidor, mas não pode depender dele — sem conexão o
 *   comando não chega e o alarme não pode ficar tocando por isso.
 * - Pomodoro: o Parar SÓ silencia (o ciclo segue sozinho para a próxima fase), então este
 *   registro é a única coisa que ele faz.
 * Se surgir um instante novo (timer reiniciado, próxima troca de fase), ele toca normalmente.
 * Fica salvo para sobreviver ao Android matar o app.
 */
class AlarmSilenceRepository(private val settings: Settings) {

    private val silenced = AlarmSource.entries.associateWith { MutableStateFlow(settings.getLongOrNull(key(it))) }

    fun silencedAtMillis(source: AlarmSource): StateFlow<Long?> = silenced.getValue(source).asStateFlow()

    fun silence(source: AlarmSource, atMillis: Long) {
        settings.putLong(key(source), atMillis)
        silenced.getValue(source).value = atMillis
    }

    private fun key(source: AlarmSource) = when (source) {
        AlarmSource.Timer -> "alarm.silencedFinishAt" // nome antigo mantido: versões anteriores já gravaram
        AlarmSource.Pomodoro -> "alarm.silenced.pomodoro"
    }
}
