package com.adriano.cronosync.core

import kotlin.concurrent.Volatile

/**
 * Relógio alinhado ao do servidor: relógio local + [offsetMillis].
 *
 * Os instantes do estado sincronizado (ex.: runningSinceMillis) foram gravados pelo relógio do
 * SERVIDOR. Se o celular estiver 3 s adiantado e usasse o próprio relógio, mostraria 3 s a mais
 * no cronômetro. Por isso todo o app usa este relógio; a diferença é medida pelo ping da sessão.
 */
class AlignedClock(private val local: Clock) : Clock {

    /** Servidor − local. Fica 0 enquanto nunca houve conexão (modo só local). */
    @Volatile
    var offsetMillis: Long = 0L

    override fun nowMillis(): Long = local.nowMillis() + offsetMillis

    /** Relógio do próprio aparelho, sem ajuste (usado para medir o próprio ping). */
    fun localNowMillis(): Long = local.nowMillis()

    /** Converte um instante do relógio alinhado para o relógio local (ex.: para o AlarmManager). */
    fun toLocalMillis(alignedMillis: Long): Long = alignedMillis - offsetMillis
}
