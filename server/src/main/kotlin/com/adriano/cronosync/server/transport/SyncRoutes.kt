package com.adriano.cronosync.server.transport

import com.adriano.cronosync.core.Clock
import com.adriano.cronosync.server.room.RoomRegistry
import com.adriano.cronosync.sync.data.ClientMessage
import com.adriano.cronosync.sync.data.CreateRoomResponse
import com.adriano.cronosync.sync.data.RejectionReason
import com.adriano.cronosync.sync.data.ServerMessage
import com.adriano.cronosync.sync.data.SyncCloseCodes
import com.adriano.cronosync.sync.data.SyncJson
import com.adriano.cronosync.sync.domain.RoomCode
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.plugins.origin
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import org.slf4j.LoggerFactory
import java.util.UUID

private val log = LoggerFactory.getLogger("SyncRoutes")

fun Route.syncRoutes(registry: RoomRegistry, clock: Clock, connections: ConnectionLimiter) {
    rateLimit(CREATE_ROOM_RATE_LIMIT) {
        createRoomRoute(registry)
    }

    rateLimit(ROOM_CONNECT_RATE_LIMIT) {
        webSocketRoomRoute(registry, clock, connections)
    }
}

private fun Route.createRoomRoute(registry: RoomRegistry) {
    post("/rooms") {
        val code = registry.create()
        log.info("Sala criada: {}", code)
        call.respondText(
            text = SyncJson.encodeToString(CreateRoomResponse.serializer(), CreateRoomResponse(code.formatted)),
            contentType = ContentType.Application.Json,
            status = HttpStatusCode.Created,
        )
    }

}

private fun Route.webSocketRoomRoute(registry: RoomRegistry, clock: Clock, connections: ConnectionLimiter) {
    webSocket("/rooms/{roomId}") {
        val code = RoomCode.parse(call.parameters["roomId"].orEmpty())
        if (code == null) {
            close(CloseReason(SyncCloseCodes.INVALID_ROOM_CODE, "Código de sala inválido"))
            return@webSocket
        }
        val address = call.request.origin.remoteAddress
        if (!connections.tryAcquire(address)) {
            close(CloseReason(SyncCloseCodes.TOO_MANY_CONNECTIONS, "Conexões demais deste endereço"))
            return@webSocket
        }
        try {
            serveRoom(code, registry, clock)
        } finally {
            connections.release(address)
        }
    }
}

private suspend fun DefaultWebSocketServerSession.serveRoom(code: RoomCode, registry: RoomRegistry, clock: Clock) {
    val room = registry.connect(code)
    if (room == null) {
        close(CloseReason(SyncCloseCodes.ROOM_NOT_FOUND, "Sala não encontrada"))
        return
    }
    log.info("Aparelho conectou na sala {}", code)
    val connectionId = UUID.randomUUID().toString()
    val rateLimiter = CommandRateLimiter(clock)

    val sender = launch {
        room.state.collect { state -> sendMessage(ServerMessage.State(state)) }
    }

    try {
        for (frame in incoming) {
            if (frame !is Frame.Text) continue
            val message = decodeOrNull(frame.readText())
            if (message != null && !rateLimiter.tryAcquire()) {
                if (message is ClientMessage.Command) {
                    sendMessage(
                        ServerMessage.CommandResult(message.id, accepted = false, room.state.value.version, RejectionReason.TooManyCommands),
                    )
                }
                continue
            }
            when (message) {
                is ClientMessage.Command -> {
                    val outcome = room.apply(message.command, message.atMillis, message.expectedVersion, connectionId)
                    // Grava antes de confirmar: comando confirmado precisa sobreviver a um reinício.
                    if (outcome.changed) registry.save(code, room)
                    sendMessage(ServerMessage.CommandResult(message.id, outcome.accepted, outcome.version, outcome.reason))
                }
                is ClientMessage.Ping -> sendMessage(ServerMessage.Pong(message.clientTimeMillis, clock.nowMillis()))
                null -> Unit
            }
        }
    } finally {
        sender.cancel()
        // NonCancellable: a conexão já está sendo encerrada, mas a sala precisa saber que ele saiu.
        withContext(NonCancellable) { registry.disconnect(code, room) }
        log.info("Aparelho saiu da sala {}", code)
    }
}

private suspend fun DefaultWebSocketServerSession.sendMessage(message: ServerMessage) {
    send(Frame.Text(SyncJson.encodeToString(ServerMessage.serializer(), message)))
}

private fun decodeOrNull(text: String): ClientMessage? =
    try {
        SyncJson.decodeFromString(ClientMessage.serializer(), text)
    } catch (e: SerializationException) {
        log.warn("Mensagem inválida ignorada: {}", e.message)
        null
    } catch (e: IllegalArgumentException) {
        log.warn("Mensagem inválida ignorada: {}", e.message)
        null
    }
