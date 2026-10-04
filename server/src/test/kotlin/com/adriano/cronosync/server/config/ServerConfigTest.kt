package com.adriano.cronosync.server.config

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ServerConfigTest {

    private val database = mapOf(
        "CRONOSYNC_DB_URL" to "jdbc:postgresql://db:5432/cronosync",
        "CRONOSYNC_DB_USER" to "app",
        "CRONOSYNC_DB_PASSWORD" to "segredo",
    )

    @Test
    fun developmentUsesTheLocalDatabaseByDefault() {
        val config = ServerConfig.fromEnvironment(environment = { null }, development = true)

        assertEquals(8080, config.port)
        assertEquals(DatabaseConfig("jdbc:postgresql://localhost:5434/cronosync", "cronosync", "cronosync"), config.database)
        assertFalse(config.behindProxy)
    }

    @Test
    fun outsideDevelopmentTheDatabaseIsRequired() {
        val error = assertFailsWith<IllegalStateException> {
            ServerConfig.fromEnvironment(environment = { null }, development = false)
        }

        assertContains(error.message.orEmpty(), "CRONOSYNC_DB_URL")
    }

    @Test
    fun readsEveryVariable() {
        val environment = database + mapOf("PORT" to "9000", "CRONOSYNC_BEHIND_PROXY" to "true")

        val config = ServerConfig.fromEnvironment(environment = environment::get, development = false)

        assertEquals(9000, config.port)
        assertEquals(DatabaseConfig("jdbc:postgresql://db:5432/cronosync", "app", "segredo"), config.database)
        assertTrue(config.behindProxy)
    }

    @Test
    fun invalidPortFallsBackToTheDefault() {
        val config = ServerConfig.fromEnvironment(environment = (database + ("PORT" to "abc"))::get, development = false)

        assertEquals(8080, config.port)
    }

    @Test
    fun passwordNeverAppearsInText() {
        val config = ServerConfig.fromEnvironment(environment = database::get, development = false)

        assertFalse("segredo" in config.toString())
    }
}
