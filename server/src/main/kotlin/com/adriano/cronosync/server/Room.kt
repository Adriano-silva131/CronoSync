package com.adriano.cronosync.server

import com.adriano.cronosync.core.Clock
import com.adriano.cronosync.sync.RejectionReason
import com.adriano.cronosync.sync.RoomCommand
import com.adriano.cronosync.sync.RoomState
import com.adriano.cronosync.sync.effectiveCommandTime
import com.adriano.cronosync.sync.handle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicInteger

/**
 * O que aconteceu com um comando. [version]: versão da sala logo depois de processá-lo.
 * [changed]: o estado mudou (e precisa ser gravado).
 */
data class CommandOutcome(
    val accepted: Boolean,
    val version: Long,
    val reason: RejectionReason? = null,
    val changed: Boolean = false,
)

/**
 * Uma sala: o cronômetro e o timer que todos os aparelhos conectados a ela compartilham.
 *
 * O servidor é a fonte da verdade. Os comandos são processados um de cada vez, numa ordem única,
 * e as regras em si (handle) são as do shared, idênticas às do app. Além disso:
 * - o comando vale no instante do TOQUE (ver effectiveCommandTime), não no da chegada;
 * - "versão esperada": se OUTRO aparelho mudou a sala depois da versão que o autor do comando
 *   estava vendo, o comando é recusado — ele decidiu com informação desatualizada.
 */
class Room(private val clock: Clock, initialState: RoomState = RoomState()) {

    private val _state = MutableStateFlow(initialState)
    val state: StateFlow<RoomState> = _state.asStateFlow()

    private val lock = Any()

    /**
     * Quem fez cada uma das últimas mudanças: (versão → conexão). Basta um histórico curto.
     * Fica só na memória: depois de um reinício ele começa vazio, e tudo bem — os aparelhos
     * reconectam, recebem o estado atual e passam a mandar comandos já com a versão certa.
     */
    private val authors = ArrayDeque<Pair<Long, String>>()

    /**
     * @param authorId identifica a conexão que enviou (um aparelho). Comandos seguidos do MESMO
     *   aparelho não se recusam entre si — ele já previu os próprios efeitos.
     */
    fun apply(command: RoomCommand, requestedAtMillis: Long, expectedVersion: Long, authorId: String): CommandOutcome =
        synchronized(lock) {
            val current = _state.value
            if (changedByOthersSince(expectedVersion, current.version, authorId)) {
                return CommandOutcome(accepted = false, version = current.version, reason = RejectionReason.Stale)
            }
            val at = effectiveCommandTime(requestedAtMillis, clock.nowMillis(), current.updatedAtMillis)
            val next = current.handle(command, at)
            // Comando sem efeito no estado atual (ex.: pausar algo já pausado): aceito, nada muda.
            if (next == current) return CommandOutcome(accepted = true, version = current.version)

            val version = current.version + 1
            authors.addLast(version to authorId)
            if (authors.size > HISTORY_SIZE) authors.removeFirst()
            _state.value = next.copy(version = version, updatedAtMillis = at)
            CommandOutcome(accepted = true, version = version, changed = true)
        }

    /** Aparelhos conectados agora. Sala com alguém conectado nunca expira (ver RoomRegistry). */
    private val connections = AtomicInteger(0)
    val connectionCount: Int get() = connections.get()

    fun onConnected() {
        connections.incrementAndGet()
    }

    fun onDisconnected() {
        connections.decrementAndGet()
    }

    private fun changedByOthersSince(expectedVersion: Long, currentVersion: Long, authorId: String): Boolean {
        if (expectedVersion >= currentVersion) return false
        // Versão tão antiga que já saiu do histórico: não dá para saber quem mudou — recusa por segurança.
        val oldestKnown = authors.firstOrNull()?.first ?: return true
        if (expectedVersion + 1 < oldestKnown) return true
        return authors.any { (version, author) -> version > expectedVersion && author != authorId }
    }

    private companion object {
        const val HISTORY_SIZE = 64
    }
}
