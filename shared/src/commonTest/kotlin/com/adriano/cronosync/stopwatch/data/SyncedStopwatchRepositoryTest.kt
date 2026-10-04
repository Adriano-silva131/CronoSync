package com.adriano.cronosync.stopwatch.data

import com.adriano.cronosync.core.FakeClock
import com.adriano.cronosync.stopwatch.domain.Stopwatch
import com.adriano.cronosync.stopwatch.domain.StopwatchCommand
import com.adriano.cronosync.stopwatch.domain.StopwatchStatus
import com.adriano.cronosync.sync.data.FakeRoomConnection
import com.adriano.cronosync.sync.domain.RoomCommand
import com.adriano.cronosync.sync.domain.RoomState
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SyncedStopwatchRepositoryTest {

    private val clock = FakeClock()
    private val connection = FakeRoomConnection()

    @Test
    fun outsideARoomCommandsAreAppliedLocally() = runTest {
        val repository = SyncedStopwatchRepository(clock, connection, backgroundScope)
        clock.currentMillis = 1_000L
        repository.send(StopwatchCommand.Start)
        clock.currentMillis = 4_000L
        repository.send(StopwatchCommand.Pause)

        assertEquals(3_000L, repository.stopwatch.value.accumulatedMillis)
        assertEquals(emptyList(), connection.sent)
    }

    @Test
    fun insideARoomCommandsGoToTheServerInstead() = runTest {
        connection.isInRoom = true
        val repository = SyncedStopwatchRepository(clock, connection, backgroundScope)

        repository.send(StopwatchCommand.Start)

        assertEquals(listOf<RoomCommand>(RoomCommand.StopwatchCmd(StopwatchCommand.Start)), connection.sent)
        assertEquals(StopwatchStatus.Idle, repository.stopwatch.value.status)
    }

    @Test
    fun adoptsStateSentByTheServer() = runTest {
        val repository = SyncedStopwatchRepository(clock, connection, backgroundScope)
        val fromServer = Stopwatch(status = StopwatchStatus.Running, runningSinceMillis = 42L)

        connection.roomStates.emit(RoomState(stopwatch = fromServer))
        runCurrent()

        assertEquals(fromServer, repository.stopwatch.value)
    }

    @Test
    fun inARoomWithoutConnectionCommandsAreRefused() = runTest {
        connection.isInRoom = true
        connection.commandsAvailable.value = false
        val repository = SyncedStopwatchRepository(clock, connection, backgroundScope)

        assertEquals(false, repository.acceptsCommands.value)
        assertEquals(false, repository.send(StopwatchCommand.Start))
        assertEquals(StopwatchStatus.Idle, repository.stopwatch.value.status)
    }
}
