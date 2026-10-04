package com.adriano.cronosync.server.transport

import com.adriano.cronosync.core.Clock

// Sem trava: uma instância por conexão, usada só pela tarefa que lê as mensagens dela.
class CommandRateLimiter(
    private val clock: Clock,
    private val capacity: Double = 20.0,
    private val refillPerSecond: Double = 10.0,
) {
    private var tokens = capacity
    private var lastRefillMillis = clock.nowMillis()

    fun tryAcquire(): Boolean {
        val now = clock.nowMillis()
        val elapsedSeconds = (now - lastRefillMillis).coerceAtLeast(0L) / 1_000.0
        tokens = (tokens + elapsedSeconds * refillPerSecond).coerceAtMost(capacity)
        lastRefillMillis = now
        if (tokens < 1.0) return false
        tokens -= 1.0
        return true
    }
}
