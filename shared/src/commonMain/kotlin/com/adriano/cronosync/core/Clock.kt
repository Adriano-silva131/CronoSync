package com.adriano.cronosync.core

import kotlin.time.ExperimentalTime

fun interface Clock {
    fun nowMillis(): Long
}

object SystemClock : Clock {
    @OptIn(ExperimentalTime::class)
    override fun nowMillis(): Long = kotlin.time.Clock.System.now().toEpochMilliseconds()
}
