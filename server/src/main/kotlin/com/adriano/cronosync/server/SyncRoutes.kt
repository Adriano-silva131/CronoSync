package com.adriano.cronosync.server

import com.adriano.cronosync.core.Clock
import com.adriano.cronosync.sync.ClientMessage
import com.adriano.cronosync.sync.CreateRoomResponse
import com.adriano.cronosync.sync.RejectionReason
import com.adriano.cronosync.sync.RoomCode
import com.adriano.cronosync.sync.ServerMessage
import com.adriano.cronosync.sync.SyncCloseCodes
import com.adriano.cronosync.sync.SyncJson
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

/**
 * Rotas de sincronização:
 * - `POST /rooms` cria uma sala e devolve o código (`{"roomId":"ABCD-EFGH"}`);
 * - `ws://servidor:8080/rooms/{codigo}` conecta um aparelho a uma sala EXISTENTE.
 *
 * Por conexão, duas tarefas rodam em paralelo:
 * 1. "envio": observa o estado da sala e manda cada versão nova para este aparelho;
 * 2. "recebimento": lê as mensagens do aparelho e aplica os comandos na sala.
 * Como todas as conexões da sala observam o mesmo estado, um comando de um aparelho chega a todos.
 * Cada mudança também é gravada no banco (ver RoomRegistry), para a sala sobreviver a reinícios.
 */
fun Route.syncRoutes(registry: RoomRegistry, clock: Clock, connections: ConnectionLimiter) {
    // Limite de criação de salas por endereço IP (ver CREATE_ROOM_RATE_LIMIT em Application.kt).
    rateLimit(CREATE_ROOM_RATE_LIMIT) {
        createRoomRoute(registry)
    }

    // Limite de TENTATIVAS de conexão por IP (ROOM_CONNECT_RATE_LIMIT): barra quem fica testando
    // códigos de sala ao acaso. Acima do limite, a conexão é recusada (429) antes de virar WebSocket.
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
        // Limite de conexões ABERTAS ao mesmo tempo por IP (ver ConnectionLimiter).
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

/** Liga este aparelho à sala [code] e fica trocando mensagens até a conexão fechar. */
private suspend fun DefaultWebSocketServerSession.serveRoom(code: RoomCode, registry: RoomRegistry, clock: Clock) {
    // Não cria sala sob demanda: só entra em sala que o servidor criou. Sem isso, qualquer
    // palavra viraria uma sala e dois grupos poderiam cair na mesma sem saber.
    val room = registry.connect(code)
    if (room == null) {
        close(CloseReason(SyncCloseCodes.ROOM_NOT_FOUND, "Sala não encontrada"))
        return
    }
    log.info("Aparelho conectou na sala {}", code)
    // Identifica ESTA conexão como autora dos comandos (ver "versão esperada" em Room).
    val connectionId = UUID.randomUUID().toString()
    // Limite de mensagens desta conexão: barra scripts que mandam comandos sem parar.
    val rateLimiter = CommandRateLimiter(clock)

    // StateFlow entrega o valor atual logo de cara: quem entra já recebe o estado da sala.
    val sender = launch {
        room.state.collect { state -> sendMessage(ServerMessage.State(state)) }
    }

    try {
        for (frame in incoming) {
            if (frame !is Frame.Text) continue
            val message = decodeOrNull(frame.readText())
            if (message != null && !rateLimiter.tryAcquire()) {
                // Comando recusado avisa o autor (para ele desfazer a previsão); ping é só ignorado.
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
                    // Grava ANTES de confirmar: comando confirmado é comando que sobrevive a um reinício.
                    if (outcome.changed) registry.save(code, room)
                    // Resposta só para quem enviou; o estado novo vai para todos pelo "sender".
                    sendMessage(ServerMessage.CommandResult(message.id, outcome.accepted, outcome.version, outcome.reason))
                }
                is ClientMessage.Ping -> sendMessage(ServerMessage.Pong(message.clientTimeMillis, clock.nowMillis()))
                // Mensagem inválida: registra e ignora, sem derrubar a conexão.
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
