package com.adriano.cronosync.core

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

const val UI_TICK_MILLIS = 16L

fun clockTicks(clock: Clock, intervalMillis: Long = UI_TICK_MILLIS): Flow<Long> = flow {
    while (true) {
        emit(clock.nowMillis())
        delay(intervalMillis)
    }
}
