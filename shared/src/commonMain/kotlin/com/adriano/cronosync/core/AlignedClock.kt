package com.adriano.cronosync.core

import kotlin.concurrent.Volatile

// Os instantes do estado sincronizado estão no relógio do servidor: use este relógio, nunca o local.
class AlignedClock(private val local: Clock) : Clock {

    @Volatile
    var offsetMillis: Long = 0L

    override fun nowMillis(): Long = local.nowMillis() + offsetMillis

    fun localNowMillis(): Long = local.nowMillis()

    fun toLocalMillis(alignedMillis: Long): Long = alignedMillis - offsetMillis
}
