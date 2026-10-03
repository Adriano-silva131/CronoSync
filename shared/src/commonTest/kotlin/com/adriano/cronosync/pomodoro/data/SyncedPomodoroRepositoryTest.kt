package com.adriano.cronosync.pomodoro.data

import com.adriano.cronosync.core.FakeClock
import com.adriano.cronosync.pomodoro.domain.Pomodoro
import com.adriano.cronosync.pomodoro.domain.PomodoroCommand
import com.adriano.cronosync.pomodoro.domain.PomodoroStatus
import com.adriano.cronosync.sync.FakeRoomConnection
import com.adriano.cronosync.sync.RoomCommand
import com.adriano.cronosync.sync.RoomState
import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SyncedPomodoroRepositoryTest {

    private val clock = FakeClock()
    private val settings = MapSettings()
    private val connection = FakeRoomConnection()

    @Test
    fun outsideARoomCommandsApplyLocallyAndSurviveARestart() = runTest {
        clock.currentMillis = 1_000L
        SyncedPomodoroRepository(clock, settings, connection, backgroundScope).send(PomodoroCommand.Start)

        // "App reiniciou": lê o que ficou salvo.
        val reopened = SyncedPomodoroRepository(clock, settings, connection, backgroundScope)
        assertEquals(PomodoroStatus.Running, reopened.pomodoro.value.status)
        assertEquals(1_000L, reopened.pomodoro.value.runningSinceMillis)
    }

    @Test
    fun insideARoomCommandsGoToTheServerAndItsStateIsAdopted() = runTest {
        connection.isInRoom = true
        val repository = SyncedPomodoroRepository(clock, settings, connection, backgroundScope)

        repository.send(PomodoroCommand.Skip)
        assertEquals(listOf<RoomCommand>(RoomCommand.PomodoroCmd(PomodoroCommand.Skip)), connection.sent)

        val fromServer = Pomodoro(status = PomodoroStatus.Paused, accumulatedMillis = 42L)
        connection.roomStates.emit(RoomState(pomodoro = fromServer))
        runCurrent()
        assertEquals(fromServer, repository.pomodoro.value)
    }
}
