package com.adriano.cronosync.desktop.alarm

import com.adriano.cronosync.alarm.data.AlarmPreferencesRepository
import com.adriano.cronosync.core.Clock
import com.adriano.cronosync.desktop.integration.audio.AlarmPlayer
import com.adriano.cronosync.desktop.integration.notification.NotificationSender
import com.adriano.cronosync.pomodoro.data.PomodoroRepository
import com.adriano.cronosync.pomodoro.domain.PomodoroPhaseKind
import com.adriano.cronosync.pomodoro.domain.PomodoroSettings.Companion.MINUTE
import com.adriano.cronosync.pomodoro.domain.PomodoroTransition
import com.adriano.cronosync.pomodoro.domain.pomodoroTransitionEvents
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class DesktopPomodoroAlarm(
    private val repository: PomodoroRepository,
    private val clock: Clock,
    private val preferences: AlarmPreferencesRepository,
    private val notifications: NotificationSender,
    private val player: AlarmPlayer,
) {
    fun start(scope: CoroutineScope) {
        pomodoroTransitionEvents(repository.pomodoro, clock).onEach(::alert).launchIn(scope)
    }

    internal fun alert(event: PomodoroTransition) {
        val minutes = event.newPhaseMillis / MINUTE
        val phase = when (event.newPhase) {
            PomodoroPhaseKind.Focus -> "foco"
            PomodoroPhaseKind.ShortBreak -> "pausa curta"
            PomodoroPhaseKind.LongBreak -> "pausa longa"
        }
        if (event.isLate) {
            notifications.send("Pomodoro", "O ciclo seguiu enquanto o computador estava suspenso: agora é $phase.")
            return
        }
        val title = if (event.newPhase == PomodoroPhaseKind.Focus) "Hora de focar!" else "Hora da pausa!"
        notifications.send(title, "${phase.replaceFirstChar { it.uppercase() }} de $minutes min já começou.")
        if (preferences.preferences.value.sound) player.play()
    }
}
