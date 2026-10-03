package com.adriano.cronosync.server

import com.adriano.cronosync.core.Clock
import com.adriano.cronosync.stopwatch.domain.StopwatchCommand
import com.adriano.cronosync.stopwatch.domain.StopwatchStatus
import com.adriano.cronosync.sync.RoomCode
import com.adriano.cronosync.sync.RoomCommand
import com.adriano.cronosync.sync.RoomState
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

class RoomRegistryTest {

    private var now = 0L
    private val clock = Clock { now }
    private val store = InMemoryRoomStore()
    private val codeA = RoomCode.generate(Random(1))
    private val codeB = RoomCode.generate(Random(2))

    // value class não pode ser vararg: os códigos vão como texto para registry(...).
    private val a = codeA.value
    private val b = codeB.value

    /** Gerador "viciado": devolve os códigos da lista, em ordem, para forçar colisões. */
    private fun scriptedGenerator(codes: List<RoomCode>): () -> RoomCode {
        val queue = ArrayDeque(codes)
        return { queue.removeFirst() }
    }

    private fun registry(vararg codes: String) =
        RoomRegistry(clock, store, generateCode = scriptedGenerator(codes.map { requireNotNull(RoomCode.parse(it)) }))

    private val start = RoomCommand.StopwatchCmd(StopwatchCommand.Start)

    @Test
    fun collisionIsDetectedAndANewCodeIsDrawn() = runTest {
        val registry = registry(a, a, b)

        assertEquals(codeA, registry.create())
        // O sorteio repetiu codeA: o registry percebe que já existe e sorteia de novo.
        assertEquals(codeB, registry.create())
    }

    @Test
    fun givesUpAfterTooManyCollisionsInsteadOfLoopingForever() = runTest {
        val registry = RoomRegistry(clock, store) { codeA }
        registry.create()

        assertFailsWith<IllegalStateException> { registry.create() }
    }

    @Test
    fun findsOnlyRoomsThatWereCreated() = runTest {
        val registry = registry(a)
        registry.create()

        assertNotNull(registry.connect(codeA))
        assertNull(registry.connect(codeB))
    }

    @Test
    fun allDevicesShareTheSameRoomObject() = runTest {
        val registry = registry(a)
        registry.create()

        assertSame(registry.connect(codeA), registry.connect(codeA))
    }

    @Test
    fun roomIsReloadedFromTheStoreAfterARestart() = runTest {
        val before = registry(a)
        before.create()
        val room = assertNotNull(before.connect(codeA))
        room.apply(start, requestedAtMillis = 0L, expectedVersion = 0L, authorId = "a")
        before.save(codeA, room)

        // Servidor novo: memória vazia, mesmo banco.
        val after = registry()

        val reloaded = assertNotNull(after.connect(codeA)).state.value
        assertEquals(StopwatchStatus.Running, reloaded.stopwatch.status)
        assertEquals(1L, reloaded.version)
    }

    @Test
    fun codeOfARoomThatOnlyExistsInTheStoreIsNotReused() = runTest {
        // A sala codeA foi criada antes do reinício: não está na memória, só no banco.
        store.insert(codeA, RoomState(), now)
        val registry = registry(a, b)

        assertEquals(codeB, registry.create())
    }

    @Test
    fun roomInactiveForADayIsRemoved() = runTest {
        val registry = registry(a, b)
        registry.create()
        now += 12.hours.inWholeMilliseconds
        registry.create()
        now += 13.hours.inWholeMilliseconds // A: 25 h parada; B: 13 h

        assertEquals(1, registry.removeInactive())

        assertNull(registry.connect(codeA))
        assertNull(store.load(codeA))
        assertNotNull(registry.connect(codeB))
    }

    @Test
    fun commandsCountAsActivity() = runTest {
        val registry = registry(a)
        registry.create()
        val room = assertNotNull(registry.connect(codeA))
        registry.disconnect(codeA, room)
        now += 20.hours.inWholeMilliseconds
        room.apply(start, requestedAtMillis = now, expectedVersion = 0L, authorId = "a")
        registry.save(codeA, room)

        now += 20.hours.inWholeMilliseconds // 40 h desde a criação, 20 h desde o comando

        assertEquals(0, registry.removeInactive())
    }

    @Test
    fun roomWithAConnectedDeviceNeverExpires() = runTest {
        val registry = registry(a)
        registry.create()
        val room = assertNotNull(registry.connect(codeA))
        now += 3.days.inWholeMilliseconds // aparelho olhando a sala há 3 dias, sem tocar em nada

        assertEquals(0, registry.removeInactive())

        // Saiu: a partir de agora a sala começa a contar o dia de inatividade.
        registry.disconnect(codeA, room)
        now += 23.hours.inWholeMilliseconds
        assertEquals(0, registry.removeInactive())
        now += 2.hours.inWholeMilliseconds
        assertEquals(1, registry.removeInactive())
    }

    @Test
    fun olderStateNeverOverwritesANewerOne() {
        store.insert(codeA, RoomState(), now)
        store.save(codeA, RoomState(version = 2L), now)

        store.save(codeA, RoomState(version = 1L), now) // gravação atrasada chegando fora de ordem

        assertEquals(2L, store.load(codeA)?.version)
    }
}
