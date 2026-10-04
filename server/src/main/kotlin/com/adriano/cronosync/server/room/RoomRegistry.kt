package com.adriano.cronosync.server.room

import com.adriano.cronosync.core.Clock
import com.adriano.cronosync.server.persistence.RoomStore
import com.adriano.cronosync.sync.domain.RoomCode
import com.adriano.cronosync.sync.domain.RoomState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.asKotlinRandom
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

// SecureRandom: os códigos não podem ser previsíveis a partir dos anteriores.
private val secureRandom = SecureRandom().asKotlinRandom()

private val log = LoggerFactory.getLogger(RoomRegistry::class.java)

class RoomRegistry(
    private val clock: Clock,
    private val store: RoomStore,
    private val inactivityLimit: Duration = 1.days,
    private val generateCode: () -> RoomCode = { RoomCode.generate(secureRandom) },
) {
    private val rooms = ConcurrentHashMap<String, Room>()

    // Protege carregar sala + conectar contra a limpeza apagar a sala nesse meio-tempo.
    private val lock = Mutex()

    suspend fun create(): RoomCode {
        repeat(MAX_ATTEMPTS) {
            val code = generateCode()
            val state = RoomState()
            if (io { store.insert(code, state, clock.nowMillis()) }) {
                rooms[code.value] = Room(clock, state)
                return code
            }
        }
        error("Não foi possível gerar um código de sala livre após $MAX_ATTEMPTS tentativas")
    }

    suspend fun connect(code: RoomCode): Room? = lock.withLock {
        val room = rooms[code.value]
            ?: io { store.load(code) }?.let { saved -> Room(clock, saved).also { rooms[code.value] = it } }
        room?.onConnected()
        room
    }

    suspend fun disconnect(code: RoomCode, room: Room) {
        room.onDisconnected()
        persist("marcar atividade da sala $code") { store.touch(listOf(code), clock.nowMillis()) }
    }

    suspend fun save(code: RoomCode, room: Room) {
        persist("gravar a sala $code") { store.save(code, room.state.value, clock.nowMillis()) }
    }

    suspend fun removeInactive(): Int = lock.withLock {
        val now = clock.nowMillis()
        val inUse = rooms.filterValues { it.connectionCount > 0 }.keys.mapNotNull(RoomCode::parse)
        val removed = io {
            store.touch(inUse, now)
            store.deleteInactiveSince(now - inactivityLimit.inWholeMilliseconds)
        }
        removed.forEach { rooms.remove(it.value) }
        removed.size
    }

    private suspend fun persist(what: String, block: () -> Unit) {
        try {
            io(block)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.error("Falha ao {} no banco", what, e)
        }
    }

    private suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) { block() }

    private companion object {
        const val MAX_ATTEMPTS = 10
    }
}
