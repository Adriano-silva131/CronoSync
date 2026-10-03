package com.adriano.cronosync.sync

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

sealed interface ConnectionStatus {
    /** Fora de qualquer sala: cronômetro e timer funcionam só neste aparelho. */
    data object Offline : ConnectionStatus

    data class Connecting(val room: RoomCode) : ConnectionStatus

    data class Connected(val room: RoomCode) : ConnectionStatus

    /** Na sala, mas a conexão caiu (ou o servidor não responde): tentando de novo sozinho. */
    data class Reconnecting(val room: RoomCode) : ConnectionStatus
}

/** Problemas que a tela de sala mostra ao usuário. */
enum class SyncError {
    /** Código com formato errado ou dígito verificador que não bate (provável erro de digitação). */
    InvalidRoomCode,
    MissingServer,
    /** Não foi possível criar a sala: servidor fora do ar ou endereço errado. */
    ServerUnreachable,
    /** O servidor não conhece a sala (código errado, ou ela expirou por ficar 1 dia sem uso). */
    RoomNotFound,
    /** O servidor recusou criar mais salas por enquanto: muitas criadas em pouco tempo. */
    TooManyRoomsCreated,
}

/** Avisos de passagem para a pessoa (mostrados por alguns segundos). */
enum class SyncNotice {
    /** O toque foi descartado porque outro aparelho mudou a sala antes (ver "versão esperada"). */
    CommandDiscardedByConflict,

    /** Toques demais em pouco tempo: o servidor ignorou alguns. Uma pessoa dificilmente chega aqui. */
    TooManyCommands,
}

/** O que os repositórios precisam saber da sincronização. Interface para poder ser trocada por um fake nos testes. */
interface RoomConnection {
    /** Cada estado novo da sala vindo do servidor. Só emite enquanto o app está numa sala. */
    val roomStates: Flow<RoomState>

    /** true se o app entrou numa sala: comandos devem ir para o servidor, não ser aplicados localmente. */
    val isInRoom: Boolean

    /**
     * Se dá para enviar comandos AGORA: fora de uma sala (modo local) ou numa sala conectada.
     * Numa sala sem conexão é false — a tela desabilita os botões em vez de engolir os toques.
     */
    val commandsAvailable: StateFlow<Boolean>

    /**
     * Envia um comando ao servidor e já aplica uma previsão local (a tela reage na hora).
     * @return false se não há conexão agora (ex.: reconectando).
     */
    suspend fun sendCommand(command: RoomCommand): Boolean
}
