package com.adriano.cronosync.alarm

import androidx.annotation.StringRes
import com.adriano.cronosync.R
import com.adriano.cronosync.pomodoro.domain.PomodoroPhaseKind

@get:StringRes
internal val PomodoroPhaseKind.label: Int
    get() = when (this) {
        PomodoroPhaseKind.Focus -> R.string.pomodoro_phase_focus
        PomodoroPhaseKind.ShortBreak -> R.string.pomodoro_phase_short_break
        PomodoroPhaseKind.LongBreak -> R.string.pomodoro_phase_long_break
    }
