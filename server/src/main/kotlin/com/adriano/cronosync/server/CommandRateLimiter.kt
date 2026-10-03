package com.adriano.cronosync.server

import com.adriano.cronosync.core.Clock

/**
 * Limite de mensagens por conexão, no modelo "balde de fichas" (token bucket):
 * - o balde começa cheio, com [capacity] fichas; cada mensagem gasta uma;
 * - ele se reenche sozinho, [refillPerSecond] fichas por segundo, até ficar cheio de novo;
 * - balde vazio = mensagem recusada.
 *
 * Assim uma rajada curta passa (tocar "Volta" várias vezes seguidas), mas um script mandando
 * mensagens sem parar fica preso ao ritmo de reenchimento. Os padrões (rajada de 20, 10 por
 * segundo) estão muito acima do que um dedo consegue e muito abaixo do que pesaria no servidor.
 *
 * Uma instância por conexão, usada só pela tarefa que lê as mensagens dela: não precisa de trava.
 */
class CommandRateLimiter(
    private val clock: Clock,
    private val capacity: Double = 20.0,
    private val refillPerSecond: Double = 10.0,
) {
    private var tokens = capacity
    private var lastRefillMillis = clock.nowMillis()

    /** true se a mensagem pode ser processada (e gasta uma ficha). */
    fun tryAcquire(): Boolean {
        val now = clock.nowMillis()
        // coerceAtLeast: se o relógio do sistema voltar no tempo, só não reenche.
        val elapsedSeconds = (now - lastRefillMillis).coerceAtLeast(0L) / 1_000.0
        tokens = (tokens + elapsedSeconds * refillPerSecond).coerceAtMost(capacity)
        lastRefillMillis = now
        if (tokens < 1.0) return false
        tokens -= 1.0
        return true
    }
}
