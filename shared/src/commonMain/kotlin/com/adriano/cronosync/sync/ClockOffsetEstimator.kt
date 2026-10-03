package com.adriano.cronosync.sync

/**
 * Estima a diferença entre o relógio do servidor e o do aparelho a partir dos pongs (estilo NTP).
 *
 * Aparelho envia ping às t0; servidor responde com sua hora S; resposta chega às t1 (tempos do
 * aparelho). Supondo que ida e volta demoram o mesmo, o servidor marcava S no instante (t0+t1)/2,
 * logo: diferença = S − (t0 + t1) / 2.
 *
 * O erro máximo é metade do tempo de ida e volta, então guardamos a amostra MAIS RÁPIDA: redes
 * móveis têm picos de latência que estragariam a estimativa.
 */
class ClockOffsetEstimator {
    private var bestRoundTripMillis = Long.MAX_VALUE
    private var offsetMillis: Long? = null

    /** Registra um pong e devolve a melhor estimativa até agora. */
    fun onPong(pong: ServerMessage.Pong, receivedAtMillis: Long): Long {
        val roundTrip = receivedAtMillis - pong.clientTimeMillis
        if (roundTrip >= 0 && roundTrip <= bestRoundTripMillis) {
            bestRoundTripMillis = roundTrip
            offsetMillis = pong.serverTimeMillis - (pong.clientTimeMillis + receivedAtMillis) / 2
        }
        return offsetMillis ?: 0L
    }
}
