package com.adriano.cronosync.server

import com.adriano.cronosync.core.Clock
import com.adriano.cronosync.stopwatch.domain.StopwatchCommand
import com.adriano.cronosync.sync.RejectionReason
import com.adriano.cronosync.sync.RoomCommand
import kotlin.test.Test
import kotlin.test.assertEquals

class RoomTest {

    private var now = 10_000L
    private val room = Room(Clock { now })
    private val start = RoomCommand.StopwatchCmd(StopwatchCommand.Start)
    private val pause = RoomCommand.StopwatchCmd(StopwatchCommand.Pause)
    private val lap = RoomCommand.StopwatchCmd(StopwatchCommand.RecordLap)

    @Test
    fun eachRealChangeBumpsTheVersion() {
        assertEquals(CommandOutcome(accepted = true, version = 1L, changed = true), room.apply(start, now, expectedVersion = 0L, authorId = "a"))
        // Iniciar de novo não muda nada: aceito, mas a versão não sobe.
        assertEquals(CommandOutcome(accepted = true, version = 1L), room.apply(start, now, expectedVersion = 1L, authorId = "a"))
        assertEquals(1L, room.state.value.version)
    }

    @Test
    fun sameDeviceCanSendSeveralCommandsInARow() {
        // O celular toca "Iniciar" e "Volta" rápido, antes de receber a versão 1: os dois com versão 0.
        room.apply(start, now, expectedVersion = 0L, authorId = "celular")
        now += 500

        val outcome = room.apply(lap, now, expectedVersion = 0L, authorId = "celular")

        assertEquals(true, outcome.accepted) // ele mesmo fez a mudança que "pulou"
        assertEquals(1, room.state.value.stopwatch.laps.size)
    }

    @Test
    fun changeByAnotherDeviceMakesTheCommandStale() {
        room.apply(start, now, expectedVersion = 0L, authorId = "computador")

        val outcome = room.apply(pause, now, expectedVersion = 0L, authorId = "celular")

        assertEquals(CommandOutcome(accepted = false, version = 1L, reason = RejectionReason.Stale), outcome)
        assertEquals(1L, room.state.value.version) // nada mudou
    }

    @Test
    fun recordsWhenTheLastChangeHappened() {
        room.apply(start, requestedAtMillis = 9_800L, expectedVersion = 0L, authorId = "a")

        assertEquals(9_800L, room.state.value.updatedAtMillis)
        assertEquals(9_800L, room.state.value.stopwatch.runningSinceMillis)
    }
}
