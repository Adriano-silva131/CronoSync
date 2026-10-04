package com.adriano.cronosync.server.persistence

import com.adriano.cronosync.sync.domain.RoomCode
import com.adriano.cronosync.sync.domain.RoomState
import java.util.concurrent.ConcurrentHashMap

// Funções bloqueantes: chamar fora das threads de rede.
interface RoomStore {
    fun insert(code: RoomCode, state: RoomState, nowMillis: Long): Boolean

    fun load(code: RoomCode): RoomState?

    // Só grava se a versão for maior que a salva: gravações fora de ordem não trocam o novo pelo antigo.
    fun save(code: RoomCode, state: RoomState, nowMillis: Long)

    fun touch(codes: Collection<RoomCode>, nowMillis: Long)

    fun deleteInactiveSince(cutoffMillis: Long): List<RoomCode>
}

class InMemoryRoomStore : RoomStore {

    private data class Row(val state: RoomState, val lastActiveAtMillis: Long)

    private val rows = ConcurrentHashMap<String, Row>()

    override fun insert(code: RoomCode, state: RoomState, nowMillis: Long): Boolean =
        rows.putIfAbsent(code.value, Row(state, nowMillis)) == null

    override fun load(code: RoomCode): RoomState? = rows[code.value]?.state

    override fun save(code: RoomCode, state: RoomState, nowMillis: Long) {
        rows.computeIfPresent(code.value) { _, row ->
            if (state.version > row.state.version) Row(state, nowMillis) else row
        }
    }

    override fun touch(codes: Collection<RoomCode>, nowMillis: Long) {
        codes.forEach { code -> rows.computeIfPresent(code.value) { _, row -> row.copy(lastActiveAtMillis = nowMillis) } }
    }

    override fun deleteInactiveSince(cutoffMillis: Long): List<RoomCode> {
        val expired = rows.filterValues { it.lastActiveAtMillis < cutoffMillis }.keys
        expired.forEach(rows::remove)
        return expired.mapNotNull(RoomCode::parse)
    }
}
