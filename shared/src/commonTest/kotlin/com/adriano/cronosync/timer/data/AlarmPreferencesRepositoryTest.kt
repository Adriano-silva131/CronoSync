package com.adriano.cronosync.timer.data

import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertEquals

class AlarmPreferencesRepositoryTest {

    @Test
    fun soundAndVibrationAreOnByDefault() {
        assertEquals(AlarmPreferences(sound = true, vibration = true), AlarmPreferencesRepository(MapSettings()).preferences.value)
    }

    @Test
    fun choicesAreSavedOnThisDevice() {
        val settings = MapSettings()
        AlarmPreferencesRepository(settings).apply {
            setSound(false)
            setVibration(false)
        }

        // "Reabriu o app": lê o que ficou salvo.
        assertEquals(AlarmPreferences(sound = false, vibration = false), AlarmPreferencesRepository(settings).preferences.value)
    }
}
