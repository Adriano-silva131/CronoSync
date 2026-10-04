package com.adriano.cronosync.server.transport

import com.adriano.cronosync.core.Clock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CommandRateLimiterTest {

    private var now = 0L
    private val limiter = CommandRateLimiter(Clock { now }, capacity = 20.0, refillPerSecond = 10.0)

    private fun acceptedOutOf(attempts: Int) = (1..attempts).count { limiter.tryAcquire() }

    @Test
    fun shortBurstPassesUpToTheCapacity() {
        assertEquals(20, acceptedOutOf(25))
    }

    @Test
    fun refillsOverTime() {
        acceptedOutOf(20)
        assertFalse(limiter.tryAcquire())

        now += 100
        assertTrue(limiter.tryAcquire())
        assertFalse(limiter.tryAcquire())
    }

    @Test
    fun neverHoldsMoreThanTheCapacity() {
        now += 60_000
        assertEquals(20, acceptedOutOf(100))
    }

    @Test
    fun scriptSpammingIsHeldToTheRefillRate() {
        acceptedOutOf(20)
        val accepted = (1..1_000).count { now++; limiter.tryAcquire() }
        assertEquals(10, accepted)
    }
}
