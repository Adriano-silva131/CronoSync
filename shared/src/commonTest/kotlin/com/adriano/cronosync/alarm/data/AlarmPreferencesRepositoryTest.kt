package com.adriano.cronosync.alarm.data

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

        assertEquals(AlarmPreferences(sound = false, vibration = false), AlarmPreferencesRepository(settings).preferences.value)
    }
}
