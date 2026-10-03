package com.adriano.cronosync.core

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineScheduler

/** Relógio controlado manualmente: o teste decide "que horas são". */
class FakeClock(var currentMillis: Long = 0L) : Clock {
    override fun nowMillis(): Long = currentMillis
}

/**
 * Relógio atrelado ao tempo virtual das corrotinas de teste: `advanceTimeBy(1_000)` avança os
 * `delay`s e este relógio juntos, então os tickers dos ViewModels se comportam como na vida real.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SchedulerClock(private val scheduler: TestCoroutineScheduler) : Clock {
    override fun nowMillis(): Long = scheduler.currentTime
}
