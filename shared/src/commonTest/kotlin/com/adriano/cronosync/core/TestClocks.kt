package com.adriano.cronosync.core

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineScheduler

class FakeClock(var currentMillis: Long = 0L) : Clock {
    override fun nowMillis(): Long = currentMillis
}

@OptIn(ExperimentalCoroutinesApi::class)
class SchedulerClock(private val scheduler: TestCoroutineScheduler) : Clock {
    override fun nowMillis(): Long = scheduler.currentTime
}
