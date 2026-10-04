package com.adriano.cronosync.desktop.app

import com.adriano.cronosync.pomodoro.domain.Pomodoro
import com.adriano.cronosync.pomodoro.domain.PomodoroStatus
import com.adriano.cronosync.timer.domain.Timer
import com.adriano.cronosync.timer.domain.TimerStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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

    @Test
    fun onlyRunningTimerOrPomodoroCountsAsActive() {
        assertFalse(hasActiveCountdown(Timer(), Pomodoro()))
        assertTrue(hasActiveCountdown(Timer(status = TimerStatus.Running), Pomodoro()))
        assertTrue(hasActiveCountdown(Timer(), Pomodoro(status = PomodoroStatus.Running)))
        assertFalse(hasActiveCountdown(Timer(status = TimerStatus.Paused), Pomodoro(status = PomodoroStatus.Paused)))
        assertFalse(hasActiveCountdown(Timer(status = TimerStatus.Finished), Pomodoro()))
    }
}
