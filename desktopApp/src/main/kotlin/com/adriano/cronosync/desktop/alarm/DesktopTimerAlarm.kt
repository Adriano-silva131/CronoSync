package com.adriano.cronosync.desktop.alarm

import com.adriano.cronosync.alarm.data.AlarmPreferencesRepository
import com.adriano.cronosync.core.AlignedClock
import com.adriano.cronosync.desktop.integration.audio.AlarmPlayer
import com.adriano.cronosync.desktop.integration.notification.NotificationSender
import com.adriano.cronosync.timer.data.TimerRepository
import com.adriano.cronosync.timer.domain.TimerFinished
import com.adriano.cronosync.timer.domain.timerFinishEvents
import com.adriano.cronosync.timer.presentation.formatCountdown
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class DesktopTimerAlarm(
    private val repository: TimerRepository,
    private val clock: AlignedClock,
    private val preferences: AlarmPreferencesRepository,
    private val notifications: NotificationSender,
    private val player: AlarmPlayer,
    private val zone: ZoneId = ZoneId.systemDefault(),
) {
    fun start(scope: CoroutineScope) {
        timerFinishEvents(repository.timer, clock).onEach(::alert).launchIn(scope)
    }

    internal fun alert(event: TimerFinished) {
        if (event.isLate) {
            val finishedAt = Instant.ofEpochMilli(clock.toLocalMillis(event.finishedAtMillis)).atZone(zone)
            notifications.send(TITLE_LATE, "Terminou às ${TIME.format(finishedAt)}, enquanto o computador estava suspenso.")
        } else {
            val duration = formatCountdown(repository.timer.value.durationMillis)
            notifications.send(TITLE, "O timer de $duration chegou ao fim.")
            if (preferences.preferences.value.sound) player.play()
        }
    }

    private companion object {
        const val TITLE = "Tempo esgotado!"
        const val TITLE_LATE = "O timer terminou"
        val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    }
}
