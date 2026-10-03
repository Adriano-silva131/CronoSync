package com.adriano.cronosync.sync

import com.adriano.cronosync.core.AlignedClock
import com.adriano.cronosync.core.FakeClock
import kotlin.test.Test
import kotlin.test.assertEquals

class SyncClockTest {

    @Test
    fun offsetIsServerTimeMinusMidpointOfTheRoundTrip() {
        // Ping às 1.000 (relógio do celular), servidor responde "10.050", chega às 1.100.
        // Meio do caminho = 1.050 no celular ↔ 10.050 no servidor → servidor está 9.000 ms à frente.
        val offset = ClockOffsetEstimator().onPong(ServerMessage.Pong(clientTimeMillis = 1_000L, serverTimeMillis = 10_050L), receivedAtMillis = 1_100L)

        assertEquals(9_000L, offset)
    }

    @Test
    fun keepsTheFastestSampleBecauseItIsTheMostAccurate() {
        val estimator = ClockOffsetEstimator()
        estimator.onPong(ServerMessage.Pong(clientTimeMillis = 0L, serverTimeMillis = 5_010L), receivedAtMillis = 20L) // ida e volta 20 ms

        // Pico de latência (2 s): amostra pior, deve ser descartada.
        val offset = estimator.onPong(ServerMessage.Pong(clientTimeMillis = 100L, serverTimeMillis = 7_000L), receivedAtMillis = 2_100L)

        assertEquals(5_000L, offset)
    }

    @Test
    fun alignedClockAddsOffsetAndConvertsBack() {
        val clock = AlignedClock(FakeClock(currentMillis = 1_000L)).apply { offsetMillis = 250L }

        assertEquals(1_250L, clock.nowMillis())
        assertEquals(1_000L, clock.localNowMillis())
        assertEquals(1_000L, clock.toLocalMillis(1_250L))
    }

    @Test
    fun reconnectionWaitsLongerAfterEachFailure() {
        assertEquals(listOf(1_000L, 2_000L, 4_000L, 8_000L, 15_000L, 15_000L), (1..6).map(SyncSession::backoffMillis))
    }
}
