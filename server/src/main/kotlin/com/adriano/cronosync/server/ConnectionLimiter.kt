package com.adriano.cronosync.server

import java.util.concurrent.ConcurrentHashMap

/**
 * Conta as conexões WebSocket abertas por endereço IP e recusa as que passarem de [maxPerAddress].
 * Sem isso, um script poderia abrir milhares de conexões e ocupar a memória do servidor.
 *
 * O limite é folgado de propósito: na rede móvel, muitos clientes de uma operadora saem pelo
 * MESMO IP (CGNAT), e eles não podem se bloquear entre si.
 */
class ConnectionLimiter(private val maxPerAddress: Int) {

    private val open = ConcurrentHashMap<String, Int>()

    /** Reserva uma vaga para [address]. Se devolver true, chame [release] quando a conexão fechar. */
    fun tryAcquire(address: String): Boolean {
        var acquired = false
        // compute é atômico por chave: duas conexões simultâneas do mesmo IP não "furam" o limite.
        open.compute(address) { _, count ->
            val current = count ?: 0
            if (current < maxPerAddress) {
                acquired = true
                current + 1
            } else {
                current
            }
        }
        return acquired
    }

    fun release(address: String) {
        // Devolver null remove a chave: IPs sem conexão não ficam ocupando o mapa.
        open.computeIfPresent(address) { _, count -> (count - 1).takeIf { it > 0 } }
    }

    fun openConnections(address: String): Int = open[address] ?: 0
}
