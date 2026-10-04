package com.adriano.cronosync.desktop.app

import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertEquals

class TrayExplanationTest {

    private val settings = MapSettings()
    private val notifications = mutableListOf<String>()

    private fun explanation() = TrayExplanation(settings) { title, _ -> notifications += title }

    @Test
    fun explainsOnlyTheFirstTime() {
        val explanation = explanation()

        explanation.showOnce()
        explanation.showOnce()

        assertEquals(listOf("O CronoSync continua aberto"), notifications)
    }

    @Test
    fun rememberedAcrossAppRestarts() {
        explanation().showOnce()
        explanation().showOnce()

        assertEquals(1, notifications.size)
    }
}
