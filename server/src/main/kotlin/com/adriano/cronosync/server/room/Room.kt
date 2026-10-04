package com.adriano.cronosync.server.room

import com.adriano.cronosync.core.Clock
import com.adriano.cronosync.sync.data.RejectionReason
import com.adriano.cronosync.sync.domain.RoomCommand
import com.adriano.cronosync.sync.domain.RoomState
import com.adriano.cronosync.sync.domain.effectiveCommandTime
import com.adriano.cronosync.sync.domain.handle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicInteger

data class CommandOutcome(
    val accepted: Boolean,
    val version: Long,
    val reason: RejectionReason? = null,
    val changed: Boolean = false,
)

class Room(private val clock: Clock, initialState: RoomState = RoomState()) {

    private val _state = MutableStateFlow(initialState)
    val state: StateFlow<RoomState> = _state.asStateFlow()

    private val lock = Any()

    private val authors = ArrayDeque<Pair<Long, String>>()

    fun apply(command: RoomCommand, requestedAtMillis: Long, expectedVersion: Long, authorId: String): CommandOutcome =
        synchronized(lock) {
            val current = _state.value
            if (changedByOthersSince(expectedVersion, current.version, authorId)) {
                return CommandOutcome(accepted = false, version = current.version, reason = RejectionReason.Stale)
            }
            val at = effectiveCommandTime(requestedAtMillis, clock.nowMillis(), current.updatedAtMillis)
            val next = current.handle(command, at)
            if (next == current) return CommandOutcome(accepted = true, version = current.version)

            val version = current.version + 1
            authors.addLast(version to authorId)
            if (authors.size > HISTORY_SIZE) authors.removeFirst()
            _state.value = next.copy(version = version, updatedAtMillis = at)
            CommandOutcome(accepted = true, version = version, changed = true)
        }

    private val connections = AtomicInteger(0)
    val connectionCount: Int get() = connections.get()

    fun onConnected() {
        connections.incrementAndGet()
    }

    fun onDisconnected() {
        connections.decrementAndGet()
    }

    private fun changedByOthersSince(expectedVersion: Long, currentVersion: Long, authorId: String): Boolean {
        if (expectedVersion >= currentVersion) return false
        val oldestKnown = authors.firstOrNull()?.first ?: return true
        if (expectedVersion + 1 < oldestKnown) return true
        return authors.any { (version, author) -> version > expectedVersion && author != authorId }
    }

    private companion object {
        const val HISTORY_SIZE = 64
    }
}
