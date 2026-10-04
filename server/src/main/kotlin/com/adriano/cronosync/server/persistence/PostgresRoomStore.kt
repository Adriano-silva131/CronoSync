package com.adriano.cronosync.server.persistence

import com.adriano.cronosync.sync.data.SyncJson
import com.adriano.cronosync.sync.domain.RoomCode
import com.adriano.cronosync.sync.domain.RoomState
import java.sql.Connection
import java.sql.Timestamp
import javax.sql.DataSource

class PostgresRoomStore(private val dataSource: DataSource) : RoomStore {

    override fun insert(code: RoomCode, state: RoomState, nowMillis: Long): Boolean = connection {
        prepareStatement(
            """
            INSERT INTO rooms (code, state, version, created_at, last_active_at)
            VALUES (?, ?::jsonb, ?, ?, ?)
            ON CONFLICT (code) DO NOTHING
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, code.value)
            statement.setString(2, encode(state))
            statement.setLong(3, state.version)
            statement.setTimestamp(4, Timestamp(nowMillis))
            statement.setTimestamp(5, Timestamp(nowMillis))
            statement.executeUpdate() == 1
        }
    }

    override fun load(code: RoomCode): RoomState? = connection {
        prepareStatement("SELECT state FROM rooms WHERE code = ?").use { statement ->
            statement.setString(1, code.value)
            statement.executeQuery().use { result ->
                if (result.next()) SyncJson.decodeFromString(RoomState.serializer(), result.getString(1)) else null
            }
        }
    }

    override fun save(code: RoomCode, state: RoomState, nowMillis: Long) {
        connection {
            prepareStatement(
                "UPDATE rooms SET state = ?::jsonb, version = ?, last_active_at = ? WHERE code = ? AND version < ?",
            ).use { statement ->
                statement.setString(1, encode(state))
                statement.setLong(2, state.version)
                statement.setTimestamp(3, Timestamp(nowMillis))
                statement.setString(4, code.value)
                statement.setLong(5, state.version)
                statement.executeUpdate()
            }
        }
    }

    override fun touch(codes: Collection<RoomCode>, nowMillis: Long) {
        if (codes.isEmpty()) return
        connection {
            prepareStatement("UPDATE rooms SET last_active_at = ? WHERE code = ANY(?)").use { statement ->
                statement.setTimestamp(1, Timestamp(nowMillis))
                statement.setArray(2, createArrayOf("text", codes.map { it.value }.toTypedArray()))
                statement.executeUpdate()
            }
        }
    }

    override fun deleteInactiveSince(cutoffMillis: Long): List<RoomCode> = connection {
        prepareStatement("DELETE FROM rooms WHERE last_active_at < ? RETURNING code").use { statement ->
            statement.setTimestamp(1, Timestamp(cutoffMillis))
            statement.executeQuery().use { result ->
                buildList { while (result.next()) RoomCode.parse(result.getString(1))?.let(::add) }
            }
        }
    }

    private fun <T> connection(block: Connection.() -> T): T = dataSource.connection.use(block)

    private fun encode(state: RoomState): String = SyncJson.encodeToString(RoomState.serializer(), state)
}
