package com.adriano.cronosync.desktop.app

import kotlin.test.Test
import kotlin.test.assertEquals

class WindowClosingTest {

    @Test
    fun withTrayTheWindowJustHides() {
        assertEquals(CloseAction.HideToTray, closeAction(trayAvailable = true, hasActiveCountdown = true))
        assertEquals(CloseAction.HideToTray, closeAction(trayAvailable = true, hasActiveCountdown = false))
    }

    @Test
    fun withoutTrayAsksOnlyIfSomethingIsCounting() {
        assertEquals(CloseAction.AskBeforeExit, closeAction(trayAvailable = false, hasActiveCountdown = true))
        assertEquals(CloseAction.Exit, closeAction(trayAvailable = false, hasActiveCountdown = false))
    }
}
