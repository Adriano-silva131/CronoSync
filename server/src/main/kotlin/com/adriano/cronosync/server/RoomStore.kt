package com.adriano.cronosync.server

import com.adriano.cronosync.sync.RoomCode
import com.adriano.cronosync.sync.RoomState
import java.util.concurrent.ConcurrentHashMap

/**
 * Onde as salas ficam guardadas para sobreviver a um reinício do servidor.
 *
 * Guarda o [RoomState] INTEIRO de cada sala: ele já é "começou em X + acumulado" no relógio do
 * servidor, então basta recarregá-lo para um cronômetro que estava rodando continuar certo — o
 * tempo em que o servidor ficou fora do ar conta normalmente.
 *
 * As funções bloqueiam (banco de dados): quem chama deve rodá-las fora das threads de rede
 * (o [RoomRegistry] usa Dispatchers.IO). Instantes em milissegundos, no relógio do servidor.
 */
interface RoomStore {
    /** Grava uma sala nova. false se o código já existe (colisão: quem chama sorteia outro). */
    fun insert(code: RoomCode, state: RoomState, nowMillis: Long): Boolean

    /** O estado salvo da sala, ou null se ela não existe (nunca existiu ou expirou). */
    fun load(code: RoomCode): RoomState?

    /**
     * Grava o estado novo e marca a sala como ativa agora. Só grava se [RoomState.version] for
     * MAIOR que a salva: duas gravações que chegam fora de ordem nunca trocam o novo pelo antigo.
     */
    fun save(code: RoomCode, state: RoomState, nowMillis: Long)

    /** Marca as salas como ativas agora (ex.: há aparelhos conectados, mesmo sem comandos). */
    fun touch(codes: Collection<RoomCode>, nowMillis: Long)

    /** Apaga as salas sem atividade desde [cutoffMillis] e devolve os códigos apagados. */
    fun deleteInactiveSince(cutoffMillis: Long): List<RoomCode>
}

/** Versão em memória, para os testes: se comporta igual ao banco, mas some com o processo. */
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
