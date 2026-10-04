package com.adriano.cronosync.sync.domain

import com.adriano.cronosync.sync.data.ServerMessage

class ClockOffsetEstimator {
    private var bestRoundTripMillis = Long.MAX_VALUE
    private var offsetMillis: Long? = null

    fun onPong(pong: ServerMessage.Pong, receivedAtMillis: Long): Long {
        val roundTrip = receivedAtMillis - pong.clientTimeMillis
        if (roundTrip >= 0 && roundTrip <= bestRoundTripMillis) {
            bestRoundTripMillis = roundTrip
            offsetMillis = pong.serverTimeMillis - (pong.clientTimeMillis + receivedAtMillis) / 2
        }
        return offsetMillis ?: 0L
    }
}
