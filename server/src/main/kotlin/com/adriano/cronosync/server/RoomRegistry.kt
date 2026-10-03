package com.adriano.cronosync.server

import com.adriano.cronosync.core.Clock
import com.adriano.cronosync.sync.RoomCode
import com.adriano.cronosync.sync.RoomState
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

/** SecureRandom: códigos imprevisíveis — ninguém consegue adivinhar o próximo a partir dos anteriores. */
private val secureRandom = SecureRandom().asKotlinRandom()

private val log = LoggerFactory.getLogger(RoomRegistry::class.java)

/**
 * As salas do servidor. Só o servidor cria salas, então é aqui que se garante que dois grupos
 * nunca recebem o mesmo código.
 *
 * O [store] (banco) é a memória de longo prazo: as salas sobrevivem a um reinício. O mapa
 * [rooms] é um cache das salas em uso — os comandos mexem nele, sem esperar o banco, e cada
 * mudança é gravada logo em seguida ([save]).
 *
 * Expiração: sala sem atividade (comando ou aparelho conectado) há [inactivityLimit] é apagada
 * por [removeInactive], que o servidor chama periodicamente.
 */
class RoomRegistry(
    private val clock: Clock,
    private val store: RoomStore,
    private val inactivityLimit: Duration = 1.days,
    private val generateCode: () -> RoomCode = { RoomCode.generate(secureRandom) },
) {
    private val rooms = ConcurrentHashMap<String, Room>()

    /**
     * Protege "achar/carregar sala + conectar" contra a limpeza. Sem ele: a limpeza decide apagar
     * uma sala vazia, um aparelho entra nela nesse meio-tempo, e ela some com ele dentro. Também
     * impede que duas conexões simultâneas carreguem do banco duas cópias da mesma sala.
     */
    private val lock = Mutex()

    /**
     * Cria uma sala com um código livre.
     *
     * Quem decide se o código está livre é o BANCO (chave primária), não o mapa em memória: depois
     * de um reinício o mapa começa vazio, mas as salas antigas continuam valendo. Com 34 bilhões de
     * códigos possíveis a colisão é raríssima; o limite de tentativas só evita um laço infinito.
     */
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

    /**
     * Entra na sala: a da memória ou, se não estiver lá (ex.: o servidor reiniciou), a do banco.
     * null se ela não existe (nunca foi criada ou expirou). Chame [disconnect] ao sair.
     */
    suspend fun connect(code: RoomCode): Room? = lock.withLock {
        val room = rooms[code.value]
            ?: io { store.load(code) }?.let { saved -> Room(clock, saved).also { rooms[code.value] = it } }
        room?.onConnected()
        room
    }

    suspend fun disconnect(code: RoomCode, room: Room) {
        room.onDisconnected()
        // A contagem de inatividade começa quando o último aparelho sai.
        persist("marcar atividade da sala $code") { store.touch(listOf(code), clock.nowMillis()) }
    }

    /**
     * Grava o estado atual da sala. Se o banco falhar, a sala continua funcionando na memória e o
     * erro vai para o log; como cada gravação leva o estado INTEIRO, a próxima que der certo já
     * deixa o banco em dia.
     */
    suspend fun save(code: RoomCode, room: Room) {
        persist("gravar a sala $code") { store.save(code, room.state.value, clock.nowMillis()) }
    }

    /** Apaga as salas inativas (do banco e da memória). Devolve quantas foram apagadas. */
    suspend fun removeInactive(): Int = lock.withLock {
        val now = clock.nowMillis()
        // Sala com aparelho conectado conta como ativa, mesmo sem comandos (ex.: um Pomodoro que
        // avança sozinho o dia todo, só sendo olhado).
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

    /** O banco bloqueia a thread: roda num pool de threads próprio para isso, longe das de rede. */
    private suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) { block() }

    private companion object {
        const val MAX_ATTEMPTS = 10
    }
}
