package com.adriano.cronosync.sync.data

import com.adriano.cronosync.sync.domain.RoomCommand
import com.adriano.cronosync.sync.domain.RoomState
import com.adriano.cronosync.sync.domain.handle

// Não é thread-safe: a SyncSession acessa sob um Mutex.
internal class PendingCommands(private val timeoutMillis: Long = 5_000L) {

    private class Pending(
        val id: String,
        val command: RoomCommand,
        val atMillis: Long,
        val sentAtLocalMillis: Long,
        var confirmedAtVersion: Long? = null,
    )

    private val items = mutableListOf<Pending>()

    val isEmpty: Boolean get() = items.isEmpty()

    fun add(message: ClientMessage.Command, sentAtLocalMillis: Long) {
        items += Pending(message.id, message.command, message.atMillis, sentAtLocalMillis)
    }

    fun onResult(result: ServerMessage.CommandResult, latestServerVersion: Long): Boolean {
        if (!result.accepted) {
            items.clear()
            return true
        }
        items.firstOrNull { it.id == result.id }?.confirmedAtVersion = result.version
        onState(latestServerVersion)
        return false
    }

    fun onState(version: Long) {
        items.removeAll { pending -> pending.confirmedAtVersion?.let { it <= version } ?: false }
    }

    fun expire(nowLocalMillis: Long): Boolean =
        items.removeAll { nowLocalMillis - it.sentAtLocalMillis > timeoutMillis }

    fun clear() = items.clear()

    fun predict(server: RoomState): RoomState =
        items.fold(server) { room, pending -> room.handle(pending.command, pending.atMillis) }
}
