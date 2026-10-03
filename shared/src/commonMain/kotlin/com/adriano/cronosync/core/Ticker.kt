package com.adriano.cronosync.core

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** ~60 atualizações por segundo: suficiente para centésimos e barras de progresso parecerem fluidos. */
const val UI_TICK_MILLIS = 16L

/**
 * Flow infinito que emite "agora" a cada [intervalMillis]. Quem coleta decide quando parar
 * (cancelando a coleta ou com operadores como `transformWhile`).
 */
fun clockTicks(clock: Clock, intervalMillis: Long = UI_TICK_MILLIS): Flow<Long> = flow {
    while (true) {
        emit(clock.nowMillis())
        delay(intervalMillis)
    }
}
