package com.adriano.cronosync.server

import com.adriano.cronosync.stopwatch.domain.Stopwatch
import com.adriano.cronosync.stopwatch.domain.StopwatchStatus
import com.adriano.cronosync.sync.RoomCode
import com.adriano.cronosync.sync.RoomState
import org.postgresql.ds.PGSimpleDataSource
import org.testcontainers.postgresql.PostgreSQLContainer
import kotlin.random.Random
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * O SQL de verdade, num PostgreSQL de verdade: o Testcontainers sobe um contêiner Docker
 * descartável (mesma imagem do docker-compose.yml) e o apaga no fim. Precisa do Docker rodando.
 */
class PostgresRoomStoreTest {

    private val dataSource = PGSimpleDataSource().apply {
        setUrl(postgres.jdbcUrl)
        user = postgres.username
        password = postgres.password
    }
    private val store = PostgresRoomStore(dataSource)

    init {
        // As tabelas vêm das migrações, como em produção (rodar de novo não faz nada).
        migrateDatabase(dataSource)
    }

    private val codeA = RoomCode.generate(Random(1))
    private val codeB = RoomCode.generate(Random(2))
    private val running = RoomState(
        stopwatch = Stopwatch(status = StopwatchStatus.Running, runningSinceMillis = 1_000L),
        version = 1L,
        updatedAtMillis = 1_000L,
    )

    @BeforeTest
    fun emptyTable() {
        dataSource.connection.use { it.createStatement().execute("TRUNCATE rooms") }
    }

    @Test
    fun insertedRoomCanBeLoaded() {
        assertTrue(store.insert(codeA, running, nowMillis = 0L))

        assertEquals(running, store.load(codeA))
        assertNull(store.load(codeB))
    }

    @Test
    fun duplicateCodeIsReportedInsteadOfFailing() {
        store.insert(codeA, RoomState(), nowMillis = 0L)

        assertFalse(store.insert(codeA, running, nowMillis = 0L))
        assertEquals(RoomState(), store.load(codeA)) // a sala original não foi tocada
    }

    @Test
    fun saveKeepsOnlyTheNewestVersion() {
        store.insert(codeA, RoomState(), nowMillis = 0L)
        store.save(codeA, running.copy(version = 2L), nowMillis = 0L)

        store.save(codeA, running.copy(version = 1L, updatedAtMillis = 9L), nowMillis = 0L)

        assertEquals(running.copy(version = 2L), store.load(codeA))
    }

    @Test
    fun deletesOnlyInactiveRooms() {
        store.insert(codeA, RoomState(), nowMillis = 1_000L)
        store.insert(codeB, RoomState(), nowMillis = 1_000L)
        store.save(codeB, running, nowMillis = 5_000L) // comando: B ficou ativa
        store.touch(listOf(codeA), nowMillis = 2_000L)

        assertEquals(listOf(codeA), store.deleteInactiveSince(cutoffMillis = 3_000L))

        assertNull(store.load(codeA))
        assertEquals(running, store.load(codeB))
    }

    @Test
    fun touchMarksActivity() {
        store.insert(codeA, RoomState(), nowMillis = 1_000L)

        store.touch(listOf(codeA), nowMillis = 10_000L)

        assertEquals(emptyList(), store.deleteInactiveSince(cutoffMillis = 5_000L))
    }

    @Test
    fun roomIsStillThereForANewServerInstance() {
        store.insert(codeA, running, nowMillis = 0L)

        migrateDatabase(dataSource) // segundo "boot"
        assertEquals(running, PostgresRoomStore(dataSource).load(codeA))
    }

    private companion object {
        /** Um contêiner para a classe toda: subir o Postgres leva alguns segundos. */
        val postgres = PostgreSQLContainer("postgres:16").apply { start() }
    }
}
