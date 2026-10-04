package com.adriano.cronosync.sync.data

import com.adriano.cronosync.pomodoro.domain.PomodoroCommand
import com.adriano.cronosync.stopwatch.domain.Stopwatch
import com.adriano.cronosync.stopwatch.domain.StopwatchCommand
import com.adriano.cronosync.stopwatch.domain.StopwatchStatus
import com.adriano.cronosync.sync.domain.MAX_BACKDATE_MILLIS
import com.adriano.cronosync.sync.domain.RoomCommand
import com.adriano.cronosync.sync.domain.RoomState
import com.adriano.cronosync.sync.domain.effectiveCommandTime
import com.adriano.cronosync.sync.domain.handle
import com.adriano.cronosync.timer.domain.Timer
import com.adriano.cronosync.timer.domain.TimerCommand
import com.adriano.cronosync.timer.domain.TimerStatus
import kotlin.test.Test
import kotlin.test.assertEquals

class SyncProtocolTest {

    // Estes testes congelam o formato do JSON: se um quebrar, a mudança afeta apps em versões antigas.
    @Test
    fun clientMessagesHaveStableWireFormat() {
        assertEquals(
            """{"type":"command","id":"c1","command":{"type":"stopwatch","command":{"type":"lap"}},"atMillis":5000,"expectedVersion":3}""",
            SyncJson.encodeToString<ClientMessage>(
                ClientMessage.Command("c1", RoomCommand.StopwatchCmd(StopwatchCommand.RecordLap), atMillis = 5_000L, expectedVersion = 3L),
            ),
        )
        assertEquals(
            """{"type":"command","id":"c2","command":{"type":"timer","command":{"type":"set_duration","durationMillis":30000}},"atMillis":1,"expectedVersion":0}""",
            SyncJson.encodeToString<ClientMessage>(
                ClientMessage.Command("c2", RoomCommand.TimerCmd(TimerCommand.SetDuration(30_000L)), atMillis = 1L, expectedVersion = 0L),
            ),
        )
        assertEquals(
            """{"type":"command","id":"c3","command":{"type":"pomodoro","command":{"type":"skip"}},"atMillis":1,"expectedVersion":2}""",
            SyncJson.encodeToString<ClientMessage>(
                ClientMessage.Command("c3", RoomCommand.PomodoroCmd(PomodoroCommand.Skip), atMillis = 1L, expectedVersion = 2L),
            ),
        )
        assertEquals(
            """{"type":"ping","clientTimeMillis":123}""",
            SyncJson.encodeToString<ClientMessage>(ClientMessage.Ping(123L)),
        )
    }

    @Test
    fun serverMessagesHaveStableWireFormat() {
        assertEquals(
            """{"type":"command_result","id":"c1","accepted":false,"version":7,"reason":"stale"}""",
            SyncJson.encodeToString<ServerMessage>(ServerMessage.CommandResult("c1", accepted = false, version = 7L, reason = RejectionReason.Stale)),
        )
    }

    @Test
    fun everyMessageSurvivesARoundTrip() {
        val commands = listOf(
            RoomCommand.StopwatchCmd(StopwatchCommand.Start),
            RoomCommand.StopwatchCmd(StopwatchCommand.Pause),
            RoomCommand.StopwatchCmd(StopwatchCommand.Reset),
            RoomCommand.TimerCmd(TimerCommand.Start),
            RoomCommand.TimerCmd(TimerCommand.Pause),
            RoomCommand.TimerCmd(TimerCommand.Reset),
        )
        commands.forEachIndexed { i, command ->
            val message: ClientMessage = ClientMessage.Command("id$i", command, atMillis = i.toLong(), expectedVersion = i.toLong())
            assertEquals(message, SyncJson.decodeFromString<ClientMessage>(SyncJson.encodeToString(message)))
        }

        val state: ServerMessage = ServerMessage.State(
            RoomState(
                stopwatch = Stopwatch(status = StopwatchStatus.Running, accumulatedMillis = 1_500L, runningSinceMillis = 42L),
                timer = Timer(status = TimerStatus.Paused, durationMillis = 60_000L, accumulatedMillis = 10_000L),
                version = 12L,
                updatedAtMillis = 42L,
            ),
        )
        assertEquals(state, SyncJson.decodeFromString<ServerMessage>(SyncJson.encodeToString(state)))
    }

    @Test
    fun unknownFieldsFromNewerServersAreIgnored() {
        val json = """{"type":"pong","clientTimeMillis":1,"serverTimeMillis":2,"campoNovo":true}"""

        assertEquals(ServerMessage.Pong(clientTimeMillis = 1L, serverTimeMillis = 2L), SyncJson.decodeFromString<ServerMessage>(json))
    }

    @Test
    fun roomStateAppliesCommandsToTheRightFeature() {
        val room = RoomState()
            .handle(RoomCommand.StopwatchCmd(StopwatchCommand.Start), atMillis = 1_000L)
            .handle(RoomCommand.TimerCmd(TimerCommand.SetDuration(10_000L)), atMillis = 1_000L)

        assertEquals(StopwatchStatus.Running, room.stopwatch.status)
        assertEquals(10_000L, room.timer.durationMillis)
        assertEquals(TimerStatus.Idle, room.timer.status)
        assertEquals(0L, room.version)
    }

    @Test
    fun commandTimeIsTheTapTimeWithinLimits() {
        val now = 100_000L
        assertEquals(99_700L, effectiveCommandTime(requestedAtMillis = 99_700L, nowMillis = now, lastChangeAtMillis = 50_000L))
        assertEquals(now, effectiveCommandTime(requestedAtMillis = 103_000L, nowMillis = now, lastChangeAtMillis = 50_000L))
        assertEquals(99_900L, effectiveCommandTime(requestedAtMillis = 99_000L, nowMillis = now, lastChangeAtMillis = 99_900L))
        assertEquals(now - MAX_BACKDATE_MILLIS, effectiveCommandTime(requestedAtMillis = 10_000L, nowMillis = now, lastChangeAtMillis = 0L))
    }
}
