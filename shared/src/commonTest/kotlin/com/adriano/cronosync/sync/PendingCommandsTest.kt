package com.adriano.cronosync.sync

import com.adriano.cronosync.stopwatch.domain.StopwatchCommand
import com.adriano.cronosync.stopwatch.domain.StopwatchStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PendingCommandsTest {

    private val pending = PendingCommands(timeoutMillis = 5_000L)
    private val start = ClientMessage.Command("a", RoomCommand.StopwatchCmd(StopwatchCommand.Start), atMillis = 1_000L, expectedVersion = 0L)
    private val lap = ClientMessage.Command("b", RoomCommand.StopwatchCmd(StopwatchCommand.RecordLap), atMillis = 2_000L, expectedVersion = 0L)

    @Test
    fun predictionAppearsBeforeTheServerAnswers() {
        pending.add(start, sentAtLocalMillis = 0L)

        val shown = pending.predict(RoomState())

        assertEquals(StopwatchStatus.Running, shown.stopwatch.status)
        assertEquals(1_000L, shown.stopwatch.runningSinceMillis) // no instante do toque
    }

    @Test
    fun confirmedPredictionLeavesOnlyWhenTheOfficialStateContainsIt() {
        pending.add(start, sentAtLocalMillis = 0L)

        // A resposta pode chegar ANTES do estado novo (são mensagens separadas)...
        pending.onResult(ServerMessage.CommandResult("a", accepted = true, version = 1L), latestServerVersion = 0L)
        // ...e a previsão continua, para a tela não "voltar" ao estado antigo por um instante.
        assertEquals(StopwatchStatus.Running, pending.predict(RoomState(version = 0L)).stopwatch.status)

        pending.onState(version = 1L)
        assertTrue(pending.isEmpty)
    }

    @Test
    fun rejectionDiscardsEveryPrediction() {
        pending.add(start, sentAtLocalMillis = 0L)
        pending.add(lap, sentAtLocalMillis = 0L) // feita em cima da previsão de "start"

        val rejected = pending.onResult(ServerMessage.CommandResult("a", accepted = false, version = 4L, reason = RejectionReason.Stale), 4L)

        assertTrue(rejected)
        assertTrue(pending.isEmpty)
        assertEquals(RoomState(version = 4L), pending.predict(RoomState(version = 4L)))
    }

    @Test
    fun unansweredPredictionExpires() {
        pending.add(start, sentAtLocalMillis = 0L)

        assertEquals(false, pending.expire(nowLocalMillis = 5_000L))
        assertEquals(true, pending.expire(nowLocalMillis = 5_001L))
        assertTrue(pending.isEmpty)
    }
}
