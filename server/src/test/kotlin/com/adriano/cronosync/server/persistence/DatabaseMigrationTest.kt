package com.adriano.cronosync.server.persistence

import org.postgresql.ds.PGSimpleDataSource
import org.testcontainers.postgresql.PostgreSQLContainer
import javax.sql.DataSource
import kotlin.test.Test
import kotlin.test.assertEquals

class DatabaseMigrationTest {

    @Test
    fun emptyDatabaseGetsAllTables() {
        val dataSource = newDatabase("fresh")

        migrateDatabase(dataSource)

        assertEquals(listOf("rooms"), dataSource.appTables())
    }

    @Test
    fun databaseFromBeforeMigrationsKeepsItsRooms() {
        val dataSource = newDatabase("legacy")
        dataSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.execute(
                    """
                    CREATE TABLE rooms (
                        code TEXT PRIMARY KEY, state JSONB NOT NULL, version BIGINT NOT NULL,
                        created_at TIMESTAMPTZ NOT NULL, last_active_at TIMESTAMPTZ NOT NULL
                    )
                    """.trimIndent(),
                )
                statement.execute("INSERT INTO rooms VALUES ('ABCDEFGH', '{}', 3, now(), now())")
            }
        }

        migrateDatabase(dataSource)

        assertEquals(listOf("rooms"), dataSource.appTables())
        val roomCount = dataSource.connection.use { connection ->
            connection.createStatement().use { it.executeQuery("SELECT count(*) FROM rooms").use { r -> r.next(); r.getInt(1) } }
        }
        assertEquals(1, roomCount)
    }

    private fun newDatabase(name: String): DataSource {
        postgres.createConnection("").use { it.createStatement().execute("CREATE DATABASE $name") }
        return PGSimpleDataSource().apply {
            setUrl(postgres.jdbcUrl.replace("/${postgres.databaseName}", "/$name"))
            user = postgres.username
            password = postgres.password
        }
    }

    private fun DataSource.appTables(): List<String> = connection.use { connection ->
        connection.createStatement().use { statement ->
            statement.executeQuery(
                "SELECT tablename FROM pg_tables WHERE schemaname = 'public' AND tablename <> 'flyway_schema_history' ORDER BY tablename",
            ).use { result -> buildList { while (result.next()) add(result.getString(1)) } }
        }
    }

    private companion object {
        val postgres = PostgreSQLContainer("postgres:16").apply { start() }
    }
}
