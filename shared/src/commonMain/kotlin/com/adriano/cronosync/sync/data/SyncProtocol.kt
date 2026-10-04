package com.adriano.cronosync.sync.data

import com.adriano.cronosync.sync.domain.RoomCommand
import com.adriano.cronosync.sync.domain.RoomState
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// Os @SerialName são o contrato da rede: mudar um quebra a comunicação com apps em versões antigas.
@Serializable
sealed interface ClientMessage {
    @Serializable @SerialName("command")
    data class Command(
        val id: String,
        val command: RoomCommand,
        val atMillis: Long,
        val expectedVersion: Long,
    ) : ClientMessage

    @Serializable @SerialName("ping")
    data class Ping(val clientTimeMillis: Long) : ClientMessage
}

@Serializable
enum class RejectionReason {
    @SerialName("stale") Stale,

    @SerialName("too_many_commands") TooManyCommands,
}

@Serializable
sealed interface ServerMessage {
    @Serializable @SerialName("state")
    data class State(val room: RoomState) : ServerMessage

    @Serializable @SerialName("command_result")
    data class CommandResult(
        val id: String,
        val accepted: Boolean,
        val version: Long,
        val reason: RejectionReason? = null,
    ) : ServerMessage

    @Serializable @SerialName("pong")
    data class Pong(val clientTimeMillis: Long, val serverTimeMillis: Long) : ServerMessage
}

@Serializable
data class CreateRoomResponse(val roomId: String)

object SyncCloseCodes {
    const val INVALID_ROOM_CODE: Short = 4400
    const val ROOM_NOT_FOUND: Short = 4404

    const val TOO_MANY_CONNECTIONS: Short = 4429
}

// ignoreUnknownKeys: apps antigos precisam ignorar campos novos que o servidor passar a enviar.
val SyncJson: Json = Json {
    classDiscriminator = "type"
    ignoreUnknownKeys = true
    encodeDefaults = true
}
