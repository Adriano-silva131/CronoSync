package com.adriano.cronosync.core

import kotlin.time.ExperimentalTime

/**
 * Abstração de "que horas são agora" em epoch millis.
 *
 * Existe por dois motivos:
 * - testes: um relógio falso deixa o teste controlar o tempo, sem esperas reais;
 * - sincronização: no futuro teremos um relógio alinhado ao do servidor (relógio local + offset).
 */
fun interface Clock {
    fun nowMillis(): Long
}

object SystemClock : Clock {
    @OptIn(ExperimentalTime::class)
    override fun nowMillis(): Long = kotlin.time.Clock.System.now().toEpochMilliseconds()
}
