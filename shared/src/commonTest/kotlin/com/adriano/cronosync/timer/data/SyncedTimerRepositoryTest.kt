package com.adriano.cronosync.timer.data

import com.adriano.cronosync.core.FakeClock
import com.adriano.cronosync.sync.RoomCommand
import com.adriano.cronosync.sync.FakeRoomConnection
import com.adriano.cronosync.sync.RoomState
import com.adriano.cronosync.timer.domain.Timer
import com.adriano.cronosync.timer.domain.TimerCommand
import com.adriano.cronosync.timer.domain.TimerStatus
import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SyncedTimerRepositoryTest {

    // MapSettings: implementação em memória do Settings, feita para testes.
    private val storage = SettingsTimerStorage(MapSettings())
    private val clock = FakeClock()
    private val connection = FakeRoomConnection()

    @Test
    fun emptyStorageLoadsNothing() {
        assertNull(storage.load())
    }

    @Test
    fun storageRoundTripsEveryField() {
        val running = Timer(status = TimerStatus.Running, durationMillis = 60_000L, accumulatedMillis = 5_000L, runningSinceMillis = 123L)
        storage.save(running)
        assertEquals(running, storage.load())

        // runningSinceMillis volta a null ao pausar — não pode sobrar o valor antigo salvo.
        val paused = running.copy(status = TimerStatus.Paused, runningSinceMillis = null)
        storage.save(paused)
        assertEquals(paused, storage.load())
    }

    @Test
    fun localStateSurvivesRecreatingTheRepository() = runTest {
        val first = SyncedTimerRepository(clock, storage, connection, backgroundScope)
        clock.currentMillis = 1_000L
        first.send(TimerCommand.SetDuration(30_000L))
        first.send(TimerCommand.Start)

        // Simula o processo do app morrendo e renascendo: novo repositório, mesmo armazenamento.
        val second = SyncedTimerRepository(clock, storage, connection, backgroundScope)

        assertEquals(TimerStatus.Running, second.timer.value.status)
        assertEquals(30_000L, second.timer.value.durationMillis)
        assertEquals(1_000L, second.timer.value.runningSinceMillis)
    }

    @Test
    fun insideARoomCommandsGoToTheServer() = runTest {
        connection.isInRoom = true
        val repository = SyncedTimerRepository(clock, storage, connection, backgroundScope)

        repository.send(TimerCommand.Start)

        assertEquals(listOf<RoomCommand>(RoomCommand.TimerCmd(TimerCommand.Start)), connection.sent)
        assertEquals(TimerStatus.Idle, repository.timer.value.status)
    }

    @Test
    fun serverStateIsAdoptedAndPersisted() = runTest {
        val repository = SyncedTimerRepository(clock, storage, connection, backgroundScope)
        val fromServer = Timer(status = TimerStatus.Running, durationMillis = 10_000L, runningSinceMillis = 99L)

        connection.roomStates.emit(RoomState(timer = fromServer))
        runCurrent()

        assertEquals(fromServer, repository.timer.value)
        // Persistido: se o app morrer agora, o alarme continua valendo ao renascer.
        assertEquals(fromServer, storage.load())
    }
}
