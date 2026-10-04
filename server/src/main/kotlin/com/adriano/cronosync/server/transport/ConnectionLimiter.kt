package com.adriano.cronosync.server.transport

import java.util.concurrent.ConcurrentHashMap

class ConnectionLimiter(private val maxPerAddress: Int) {

    private val open = ConcurrentHashMap<String, Int>()

    fun tryAcquire(address: String): Boolean {
        var acquired = false
        // compute é atômico por chave: conexões simultâneas do mesmo IP não furam o limite.
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
        open.computeIfPresent(address) { _, count -> (count - 1).takeIf { it > 0 } }
    }

    fun openConnections(address: String): Int = open[address] ?: 0
}
