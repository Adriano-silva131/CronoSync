package com.adriano.cronosync.server.room

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.slf4j.Logger
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

fun CoroutineScope.launchExpiredRoomCleanup(
    registry: RoomRegistry,
    log: Logger,
    interval: Duration = 1.hours,
): Job = launch {
    while (true) {
        val removed = registry.removeInactive()
        if (removed > 0) log.info("Salas expiradas removidas: {}", removed)
        delay(interval)
    }
}
