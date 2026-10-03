package com.adriano.cronosync.server

import com.adriano.cronosync.sync.RoomCode
import com.adriano.cronosync.sync.RoomState
import com.adriano.cronosync.sync.SyncJson
import java.sql.Connection
import java.sql.Timestamp
import javax.sql.DataSource

/**
 * Salas no PostgreSQL. Uma tabela só (criada pela migração V1; ver [migrateDatabase]):
 *
 * | code (PK) | state (jsonb)  | version | created_at | last_active_at |
 *
 * - `state` é o [RoomState] em JSON, o mesmo formato que vai pelo WebSocket. A sala é sempre lida e
 *   gravada inteira, então não vale a pena quebrá-la em colunas — e um campo novo no RoomState não
 *   exige mudar a tabela. Por ser `jsonb`, ainda dá para consultar por dentro no psql se preciso.
 * - `version` repete a versão do estado numa coluna própria para a gravação condicional de [save].
 * - `last_active_at` decide a expiração; o índice deixa a limpeza rápida mesmo com muitas salas.
 *
 * [dataSource] é um pool de conexões (HikariCP): abrir uma conexão nova com o Postgres custa
 * dezenas de milissegundos; o pool mantém algumas abertas e as empresta a cada operação.
 */
class PostgresRoomStore(private val dataSource: DataSource) : RoomStore {

    override fun insert(code: RoomCode, state: RoomState, nowMillis: Long): Boolean = connection {
        // ON CONFLICT DO NOTHING: se o código já existe, não grava nada (0 linhas) em vez de dar erro.
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

    /** Pega uma conexão do pool, usa e devolve (o `use` devolve mesmo se der erro). */
    private fun <T> connection(block: Connection.() -> T): T = dataSource.connection.use(block)

    private fun encode(state: RoomState): String = SyncJson.encodeToString(RoomState.serializer(), state)
}
