package com.adriano.cronosync.desktop.app

import com.adriano.cronosync.pomodoro.domain.Pomodoro
import com.adriano.cronosync.pomodoro.domain.PomodoroStatus
import com.adriano.cronosync.timer.domain.Timer
import com.adriano.cronosync.timer.domain.TimerStatus

enum class CloseAction {
    HideToTray,

    AskBeforeExit,

    Exit,
}

fun closeAction(trayAvailable: Boolean, hasActiveCountdown: Boolean): CloseAction = when {
    trayAvailable -> CloseAction.HideToTray
    hasActiveCountdown -> CloseAction.AskBeforeExit
    else -> CloseAction.Exit
}

fun hasActiveCountdown(timer: Timer, pomodoro: Pomodoro): Boolean =
    timer.status == TimerStatus.Running || pomodoro.status == PomodoroStatus.Running
