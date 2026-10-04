package com.adriano.cronosync.server.transport

import com.adriano.cronosync.core.Clock
import com.adriano.cronosync.pomodoro.domain.PomodoroCommand
import com.adriano.cronosync.pomodoro.domain.PomodoroStatus
import com.adriano.cronosync.server.module
import com.adriano.cronosync.server.persistence.InMemoryRoomStore
import com.adriano.cronosync.server.persistence.RoomStore
import com.adriano.cronosync.stopwatch.domain.Stopwatch
import com.adriano.cronosync.stopwatch.domain.StopwatchCommand
import com.adriano.cronosync.stopwatch.domain.StopwatchStatus
import com.adriano.cronosync.sync.data.ClientMessage
import com.adriano.cronosync.sync.data.CreateRoomResponse
import com.adriano.cronosync.sync.data.RejectionReason
import com.adriano.cronosync.sync.data.ServerMessage
import com.adriano.cronosync.sync.data.SyncCloseCodes
import com.adriano.cronosync.sync.data.SyncJson
import com.adriano.cronosync.sync.domain.RoomCode
import com.adriano.cronosync.sync.domain.RoomCommand
import com.adriano.cronosync.sync.domain.RoomState
import com.adriano.cronosync.timer.domain.TimerCommand
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull

class SyncRoutesTest {

    private class FakeClock(var currentMillis: Long = 1_000L) : Clock {
        override fun nowMillis(): Long = currentMillis
    }

    private val clock = FakeClock()

    private fun serverTest(
        store: RoomStore = InMemoryRoomStore(),
        behindProxy: Boolean = false,
        block: suspend ApplicationTestBuilder.(HttpClient) -> Unit,
    ) = testApplication {
        application { module(clock = clock, store = store, behindProxy = behindProxy) }
        block(createClient { install(WebSockets) })
    }

    private suspend fun HttpClient.createRoom(): RoomCode {
        val response = post("/rooms")
        assertEquals(HttpStatusCode.Created, response.status)
        val body = SyncJson.decodeFromString(CreateRoomResponse.serializer(), response.bodyAsText())
        return assertNotNull(RoomCode.parse(body.roomId), "servidor devolveu código inválido: ${body.roomId}")
    }

    private suspend fun DefaultClientWebSocketSession.sendMessage(message: ClientMessage) =
        send(Frame.Text(SyncJson.encodeToString(ClientMessage.serializer(), message)))

    private suspend fun DefaultClientWebSocketSession.receiveMessage(): ServerMessage {
        val frame = incoming.receive()
        return SyncJson.decodeFromString(ServerMessage.serializer(), (frame as Frame.Text).readText())
    }

    private suspend fun DefaultClientWebSocketSession.receiveState(): RoomState {
        while (true) {
            val message = receiveMessage()
            if (message is ServerMessage.State) return message.room
            assertIs<ServerMessage.CommandResult>(message)
        }
    }

    private var nextId = 0

    private fun command(command: RoomCommand, expectedVersion: Long = 0L, atMillis: Long = clock.currentMillis) =
        ClientMessage.Command(id = "cmd${nextId++}", command = command, atMillis = atMillis, expectedVersion = expectedVersion)

    @Test
    fun healthCheckResponds() = serverTest { client ->
        assertEquals("CronoSync server", client.get("/").bodyAsText())
    }

    @Test
    fun eachCreatedRoomGetsADifferentValidCode() = serverTest { client ->
        val codes = List(10) { client.createRoom() }

        assertEquals(codes.size, codes.toSet().size)
    }

    @Test
    fun newDeviceReceivesCurrentRoomState() = serverTest { client ->
        val code = client.createRoom()
        client.webSocket("/rooms/${code.value}") {
            assertEquals(RoomState(), receiveState())
        }
    }

    @Test
    fun formattedCodeWithHyphenAlsoWorksInTheUrl() = serverTest { client ->
        val code = client.createRoom()
        client.webSocket("/rooms/${code.formatted.lowercase()}") {
            assertEquals(RoomState(), receiveState())
        }
    }

    @Test
    fun commandsAreAppliedWithTheServerClock() = serverTest { client ->
        val code = client.createRoom()
        client.webSocket("/rooms/${code.value}") {
            receiveState()

            clock.currentMillis = 5_000L
            sendMessage(command(RoomCommand.StopwatchCmd(StopwatchCommand.Start)))

            val stopwatch = receiveState().stopwatch
            assertEquals(StopwatchStatus.Running, stopwatch.status)
            assertEquals(5_000L, stopwatch.runningSinceMillis)
        }
    }

    @Test
    fun commandFromOneDeviceReachesTheOthers() = serverTest { client ->
        val code = client.createRoom()
        client.webSocket("/rooms/${code.value}") {
            val phoneA = this
            phoneA.receiveState()

            client.webSocket("/rooms/${code.value}") {
                val phoneB = this
                phoneB.receiveState()

                phoneA.sendMessage(command(RoomCommand.TimerCmd(TimerCommand.SetDuration(90_000L))))

                assertEquals(90_000L, phoneB.receiveState().timer.durationMillis)
                assertEquals(90_000L, phoneA.receiveState().timer.durationMillis)
            }
        }
    }

    @Test
    fun roomsAreIsolated() = serverTest { client ->
        val first = client.createRoom()
        val second = client.createRoom()
        client.webSocket("/rooms/${first.value}") {
            receiveState()
            sendMessage(command(RoomCommand.StopwatchCmd(StopwatchCommand.Start)))
            receiveState()
        }
        client.webSocket("/rooms/${second.value}") {
            assertEquals(StopwatchStatus.Idle, receiveState().stopwatch.status)
        }
    }

    @Test
    fun stateOutlivesDisconnections() = serverTest { client ->
        val code = client.createRoom()
        client.webSocket("/rooms/${code.value}") {
            receiveState()
            sendMessage(command(RoomCommand.StopwatchCmd(StopwatchCommand.Start)))
            receiveState()
        }
        client.webSocket("/rooms/${code.value}") {
            assertEquals(StopwatchStatus.Running, receiveState().stopwatch.status)
        }
    }

    @Test
    fun roomSurvivesAServerRestart() {
        val store = InMemoryRoomStore()
        var created: RoomCode? = null
        serverTest(store) { client ->
            val code = client.createRoom().also { created = it }
            client.webSocket("/rooms/${code.value}") {
                receiveState()
                sendMessage(command(RoomCommand.StopwatchCmd(StopwatchCommand.Start)))
                while (receiveMessage() !is ServerMessage.CommandResult) Unit
            }
        }

        clock.currentMillis += 60_000L
        val code = assertNotNull(created)
        serverTest(store) { client ->
            client.webSocket("/rooms/${code.value}") {
                val state = receiveState()
                assertEquals(StopwatchStatus.Running, state.stopwatch.status)
                assertEquals(1_000L, state.stopwatch.runningSinceMillis)
                assertEquals(1L, state.version)
            }
        }
    }

    @Test
    fun pingIsAnsweredWithServerTime() = serverTest { client ->
        val code = client.createRoom()
        client.webSocket("/rooms/${code.value}") {
            receiveState()
            clock.currentMillis = 7_777L

            sendMessage(ClientMessage.Ping(clientTimeMillis = 123L))

            assertEquals(ServerMessage.Pong(clientTimeMillis = 123L, serverTimeMillis = 7_777L), receiveMessage())
        }
    }

    @Test
    fun invalidMessagesAreIgnoredWithoutDroppingTheConnection() = serverTest { client ->
        val code = client.createRoom()
        client.webSocket("/rooms/${code.value}") {
            receiveState()
            send(Frame.Text("isto não é JSON"))
            send(Frame.Text("""{"type":"comando_que_nao_existe"}"""))

            sendMessage(command(RoomCommand.StopwatchCmd(StopwatchCommand.Start)))
            assertEquals(StopwatchStatus.Running, receiveState().stopwatch.status)
        }
    }

    @Test
    fun codeThatWasNeverCreatedIsRejected() = serverTest { client ->
        val created = client.createRoom()
        val neverCreated = generateSequence { RoomCode.generate(Random(7)) }.first { it != created }

        client.webSocket("/rooms/${neverCreated.value}") {
            assertEquals(SyncCloseCodes.ROOM_NOT_FOUND, closeReason.await()?.code)
        }
    }

    @Test
    fun malformedCodeIsRejected() = serverTest { client ->
        client.webSocket("/rooms/teste") {
            assertEquals(SyncCloseCodes.INVALID_ROOM_CODE, closeReason.await()?.code)
        }
    }

    @Test
    fun codeWithATypoIsRejectedAsInvalid() = serverTest { client ->
        val code = client.createRoom().value
        val typo = code.replaceRange(0, 1, if (code[0] == 'A') "B" else "A")
        assertNotEquals(code, typo)

        client.webSocket("/rooms/$typo") {
            assertEquals(SyncCloseCodes.INVALID_ROOM_CODE, closeReason.await()?.code)
        }
    }

    @Test
    fun commandUsesTheTapTimeNotTheArrivalTime() = serverTest { client ->
        val code = client.createRoom()
        client.webSocket("/rooms/${code.value}") {
            receiveState()
            clock.currentMillis = 10_000L

            sendMessage(command(RoomCommand.StopwatchCmd(StopwatchCommand.Start), atMillis = 9_700L))

            assertEquals(9_700L, receiveState().stopwatch.runningSinceMillis)
        }
    }

    @Test
    fun authorReceivesTheResultWithTheNewVersion() = serverTest { client ->
        val code = client.createRoom()
        client.webSocket("/rooms/${code.value}") {
            receiveState()
            sendMessage(ClientMessage.Command("meu-id", RoomCommand.StopwatchCmd(StopwatchCommand.Start), clock.currentMillis, expectedVersion = 0L))

            val messages = List(2) { receiveMessage() }
            assertEquals(ServerMessage.CommandResult("meu-id", accepted = true, version = 1L), messages.filterIsInstance<ServerMessage.CommandResult>().single())
            assertEquals(1L, messages.filterIsInstance<ServerMessage.State>().single().room.version)
        }
    }

    @Test
    fun commandBasedOnAnOutdatedVersionIsRejected() = serverTest { client ->
        val code = client.createRoom()
        client.webSocket("/rooms/${code.value}") {
            val computer = this
            computer.receiveState()
            client.webSocket("/rooms/${code.value}") {
                val phone = this
                phone.receiveState()

                computer.sendMessage(command(RoomCommand.StopwatchCmd(StopwatchCommand.Start), expectedVersion = 0L))
                assertEquals(1L, computer.receiveState().version)

                phone.sendMessage(ClientMessage.Command("velho", RoomCommand.StopwatchCmd(StopwatchCommand.RecordLap), clock.currentMillis, expectedVersion = 0L))

                var result: ServerMessage.CommandResult? = null
                while (result == null) result = phone.receiveMessage() as? ServerMessage.CommandResult
                assertEquals(ServerMessage.CommandResult("velho", accepted = false, version = 1L, reason = RejectionReason.Stale), result)
            }
        }
    }

    private suspend fun DefaultClientWebSocketSession.sendAndAwaitResult(message: ClientMessage.Command): ServerMessage.CommandResult {
        sendMessage(message)
        while (true) {
            val reply = receiveMessage()
            if (reply is ServerMessage.CommandResult && reply.id == message.id) return reply
        }
    }

    @Test
    fun roomCreationIsLimitedPerAddress() = serverTest { client ->
        repeat(10) { client.createRoom() }

        assertEquals(HttpStatusCode.TooManyRequests, client.post("/rooms").status)
    }

    @Test
    fun connectionSpammingCommandsGetsRejected() = serverTest { client ->
        val code = client.createRoom()
        client.webSocket("/rooms/${code.value}") {
            receiveState()
            val results = List(25) { sendAndAwaitResult(command(RoomCommand.StopwatchCmd(StopwatchCommand.Start), expectedVersion = 1L)) }

            assertEquals(20, results.count { it.accepted })
            assertEquals(List(5) { RejectionReason.TooManyCommands }, results.filterNot { it.accepted }.map { it.reason })
        }
    }

    @Test
    fun lapsStopAtTheLimitAndTheFullStateStillFitsInAMessage() = serverTest { client ->
        val code = client.createRoom()
        client.webSocket("/rooms/${code.value}") {
            receiveState()
            var version = sendAndAwaitResult(command(RoomCommand.StopwatchCmd(StopwatchCommand.Start))).version
            repeat(Stopwatch.MAX_LAPS + 1) {
                clock.currentMillis += 100
                version = sendAndAwaitResult(command(RoomCommand.StopwatchCmd(StopwatchCommand.RecordLap), expectedVersion = version)).version
            }
        }
        client.webSocket("/rooms/${code.value}") {
            val state = receiveState()
            assertEquals(Stopwatch.MAX_LAPS, state.stopwatch.laps.size)
            assertEquals(Stopwatch.MAX_LAPS + 1L, state.version)
        }
    }

    @Test
    fun oversizedMessageClosesTheConnection() = serverTest { client ->
        val code = client.createRoom()
        client.webSocket("/rooms/${code.value}") {
            receiveState()

            send(Frame.Text("x".repeat(MAX_FRAME_BYTES.toInt() + 1)))

            assertEquals(CloseReason.Codes.TOO_BIG.code, closeReason.await()?.code)
        }
    }

    @Test
    fun pomodoroIsSharedByTheRoomToo() = serverTest { client ->
        val code = client.createRoom()
        client.webSocket("/rooms/${code.value}") {
            receiveState()
            clock.currentMillis = 2_000L

            sendMessage(command(RoomCommand.PomodoroCmd(PomodoroCommand.Start)))

            val pomodoro = receiveState().pomodoro
            assertEquals(PomodoroStatus.Running, pomodoro.status)
            assertEquals(2_000L, pomodoro.runningSinceMillis)
        }
    }

    @Test
    fun connectionAttemptsAreLimitedPerAddress() = serverTest { client ->
        repeat(60) {
            client.webSocket("/rooms/${RoomCode.generate(Random(it)).value}") {
                assertEquals(SyncCloseCodes.ROOM_NOT_FOUND, closeReason.await()?.code)
            }
        }

        assertFails { client.webSocket("/rooms/${RoomCode.generate(Random(99)).value}") {} }
    }

    @Test
    fun simultaneousConnectionsAreLimitedPerAddress() = serverTest { client ->
        val code = client.createRoom()
        val open = List(MAX_CONNECTIONS_PER_ADDRESS) {
            client.webSocketSession("/rooms/${code.value}").also { it.receiveState() }
        }

        client.webSocket("/rooms/${code.value}") {
            assertEquals(SyncCloseCodes.TOO_MANY_CONNECTIONS, closeReason.await()?.code)
        }
        open.forEach { it.close() }
    }

    @Test
    fun behindAProxyEachRealAddressHasItsOwnLimit() = serverTest(behindProxy = true) { client ->
        repeat(10) { assertEquals(HttpStatusCode.Created, client.createRoom(forwardedFor = "inventado-$it, 1.1.1.1")) }

        assertEquals(HttpStatusCode.TooManyRequests, client.createRoom(forwardedFor = "outro, 1.1.1.1"))
        assertEquals(HttpStatusCode.Created, client.createRoom(forwardedFor = "2.2.2.2"))
    }

    @Test
    fun withoutAProxyAnInventedForwardedHeaderDoesNotEscapeTheLimit() = serverTest { client ->
        repeat(10) { client.createRoom(forwardedFor = "10.0.0.$it") }

        assertEquals(HttpStatusCode.TooManyRequests, client.createRoom(forwardedFor = "10.0.0.99"))
    }

    private suspend fun HttpClient.createRoom(forwardedFor: String): HttpStatusCode =
        post("/rooms") { header("X-Forwarded-For", forwardedFor) }.status
}
