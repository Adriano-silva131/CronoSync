package com.adriano.cronosync.server

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConnectionLimiterTest {

    private val limiter = ConnectionLimiter(maxPerAddress = 2)

    @Test
    fun refusesAboveTheLimit() {
        assertTrue(limiter.tryAcquire("1.1.1.1"))
        assertTrue(limiter.tryAcquire("1.1.1.1"))

        assertFalse(limiter.tryAcquire("1.1.1.1"))
        assertEquals(2, limiter.openConnections("1.1.1.1")) // a recusa não conta como conexão
    }

    @Test
    fun eachAddressHasItsOwnLimit() {
        repeat(2) { limiter.tryAcquire("1.1.1.1") }

        assertTrue(limiter.tryAcquire("2.2.2.2"))
    }

    @Test
    fun closingAConnectionFreesASlot() {
        repeat(2) { limiter.tryAcquire("1.1.1.1") }

        limiter.release("1.1.1.1")

        assertTrue(limiter.tryAcquire("1.1.1.1"))
    }

    @Test
    fun addressWithoutConnectionsIsForgotten() {
        limiter.tryAcquire("1.1.1.1")
        limiter.release("1.1.1.1")
        limiter.release("1.1.1.1") // liberar a mais não deixa o contador negativo

        assertEquals(0, limiter.openConnections("1.1.1.1"))
        repeat(2) { assertTrue(limiter.tryAcquire("1.1.1.1")) }
        assertFalse(limiter.tryAcquire("1.1.1.1"))
    }
}
