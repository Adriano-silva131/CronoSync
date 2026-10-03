package com.adriano.cronosync.sync

import com.adriano.cronosync.core.AlignedClock
import com.russhwolf.settings.Settings
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.readText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.SerializationException
import kotlin.concurrent.Volatile
import kotlin.random.Random

/**
 * Conexão do app com uma sala no servidor.
 *
 * - [createRoom] pede ao servidor uma sala nova (é ele quem gera o código, sem colisões).
 * - [join] abre o WebSocket e mantém um laço de reconexão: se a rede cair, tenta de novo sozinho,
 *   esperando cada vez mais (1 s, 2 s, 4 s… até 15 s) para não martelar um servidor fora do ar.
 *   [reconnectNow] e [onNetworkChanged] pulam essa espera quando há motivo para tentar já.
 *   Se o servidor RECUSAR a sala (não existe), para de tentar e avisa em [lastError].
 * - [sendCommand] envia o comando com o instante do toque e a versão que o aparelho estava vendo,
 *   e aplica uma PREVISÃO local na hora (ver [PendingCommands]); o servidor confirma ou recusa.
 * - Os estados (oficial + previsões) saem em [roomStates]; os repositórios os adotam.
 * - Pings periódicos alinham o [AlignedClock] ao relógio do servidor. O primeiro estado só é
 *   publicado depois da primeira medição, para o tempo na tela não "pular" ao conectar.
 * - A sala escolhida fica salva: se o Android matar o app, ele volta para a mesma sala sozinho.
 *   O último estado oficial dela também: ao reabrir, a tela já mostra a sala (sem aceitar toques)
 *   enquanto reconecta — mesmo com o servidor fora do ar.
 */
class SyncSession(
    private val client: HttpClient,
    private val clock: AlignedClock,
    private val settings: Settings,
    private val scope: CoroutineScope,
    val config: SyncConfig,
) : RoomConnection {

    private val _status = MutableStateFlow<ConnectionStatus>(ConnectionStatus.Offline)
    val status: StateFlow<ConnectionStatus> = _status.asStateFlow()

    private val _commandsAvailable = MutableStateFlow(true)
    override val commandsAvailable: StateFlow<Boolean> = _commandsAvailable.asStateFlow()

    private val _lastError = MutableStateFlow<SyncError?>(null)
    val lastError: StateFlow<SyncError?> = _lastError.asStateFlow()

    private val _notices = MutableSharedFlow<SyncNotice>(extraBufferCapacity = 4)
    val notices: SharedFlow<SyncNotice> = _notices.asSharedFlow()

    // replay = 1: um repositório criado depois (ex.: ao abrir uma tela) recebe na hora o último estado.
    private val _roomStates = MutableSharedFlow<RoomState>(replay = 1)
    override val roomStates: Flow<RoomState> = _roomStates.asSharedFlow()

    private var connectionJob: Job? = null

    @Volatile
    private var socket: WebSocketSession? = null

    /** Protege o estado oficial e as previsões, mexidos pela conexão e pelos toques ao mesmo tempo. */
    private val stateLock = Mutex()
    private var serverRoom: RoomState? = null
    private val pending = PendingCommands()

    /** "Tente de novo agora": acorda o laço de reconexão no meio da espera. */
    private val retryNow = Channel<Unit>(Channel.CONFLATED)

    override val isInRoom: Boolean get() = connectionJob != null

    /**
     * Servidor a usar: nas versões de testes (local e homologação), sempre o fixo; na final, o
     * último usado (para preencher o formulário).
     */
    val lastServerAddress: String
        get() = if (config.isFixedServer) config.defaultServerAddress else settings.getString(KEY_SERVER, config.defaultServerAddress)

    init {
        // Um código salvo por uma versão antiga do app (ex.: "teste") não é mais válido: descarta.
        val savedRoom = settings.getStringOrNull(KEY_ROOM)?.let(RoomCode::parse)
        if (savedRoom != null) {
            // replay = 1 guarda o estado até os repositórios começarem a observar.
            loadCachedState()?.let { _roomStates.tryEmit(it) }
            join(lastServerAddress, savedRoom)
        } else {
            settings.remove(KEY_ROOM)
            settings.remove(KEY_CACHED_STATE)
        }
    }

    /** Pede ao servidor uma sala nova. null se não deu certo; o motivo fica em [lastError]. */
    suspend fun createRoom(serverAddress: String): RoomCode? {
        _lastError.value = null
        val code = try {
            val response = client.post(createRoomUrl(serverAddress))
            when {
                response.status == HttpStatusCode.TooManyRequests -> {
                    _lastError.value = SyncError.TooManyRoomsCreated
                    return null
                }
                !response.status.isSuccess() -> null
                else -> RoomCode.parse(SyncJson.decodeFromString(CreateRoomResponse.serializer(), response.bodyAsText()).roomId)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        if (code == null) _lastError.value = SyncError.ServerUnreachable
        return code
    }

    fun join(serverAddress: String, code: RoomCode) {
        connectionJob?.cancel()
        _lastError.value = null
        // O estado guardado é o da sala anterior: não serve para outra sala nem outro servidor.
        if (code.value != settings.getStringOrNull(KEY_ROOM) || serverAddress != settings.getStringOrNull(KEY_SERVER)) {
            settings.remove(KEY_CACHED_STATE)
        }
        settings.putString(KEY_SERVER, serverAddress)
        settings.putString(KEY_ROOM, code.value)
        // Começa com a diferença de relógio medida da última vez com este servidor: costuma mudar
        // pouco, então o tempo já aparece praticamente certo antes da primeira medição nova.
        settings.getLongOrNull(offsetKey(serverAddress))?.let { clock.offsetMillis = it }
        connectionJob = scope.launch { connectionLoop(serverAddress, code) }
    }

    /** Sai da sala. O último estado recebido continua na tela, agora só local. */
    fun leave() {
        connectionJob?.cancel()
        goOffline(error = null)
    }

    fun clearError() {
        _lastError.value = null
    }

    /**
     * Pula a espera e tenta reconectar já — ex.: o app voltou para a tela. Não faz nada se
     * estiver conectado ou fora de uma sala.
     */
    fun reconnectNow() {
        if (isInRoom && _status.value !is ConnectionStatus.Connected) retryNow.trySend(Unit)
    }

    /**
     * A rede do aparelho mudou (ex.: Wi-Fi → 4G). A conexão antiga provavelmente morreu, mas o
     * WebSocket só perceberia no próximo ping (até 15 s): derrubamos e reconectamos na hora.
     */
    fun onNetworkChanged() {
        if (!isInRoom) return
        socket?.cancel()
        retryNow.trySend(Unit)
    }

    override suspend fun sendCommand(command: RoomCommand): Boolean = stateLock.withLock {
        val current = socket ?: return false
        // Sem estado oficial ainda (conectando): não há versão "vista" para mandar.
        val server = serverRoom ?: return false
        val message = ClientMessage.Command(
            id = newCommandId(),
            command = command,
            atMillis = clock.nowMillis(), // instante do toque, no relógio do servidor
            expectedVersion = server.version,
        )
        try {
            current.send(Frame.Text(SyncJson.encodeToString(ClientMessage.serializer(), message)))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return false
        }
        pending.add(message, clock.localNowMillis())
        publishLocked()
        true
    }

    private suspend fun connectionLoop(serverAddress: String, code: RoomCode) {
        val url = roomWebSocketUrl(serverAddress, code)
        var failures = 0
        while (true) {
            setStatus(if (failures == 0) ConnectionStatus.Connecting(code) else ConnectionStatus.Reconnecting(code))
            var rejection: SyncError? = null
            try {
                client.webSocket(url) {
                    socket = this
                    val estimator = ClockOffsetEstimator()

                    // O primeiro estado espera a primeira medição do relógio (o pong do primeiro ping,
                    // alguns milissegundos). Sem resposta em CLOCK_SYNC_TIMEOUT, publica mesmo assim.
                    var clockSynced = false
                    var heldState: RoomState? = null
                    suspend fun adoptServerState(room: RoomState) {
                        failures = 0
                        retryNow.tryReceive() // um "tente agora" antigo não vale mais
                        serverRoom = room
                        // Só o estado OFICIAL vai para o cache, nunca as previsões.
                        settings.putString(KEY_CACHED_STATE, SyncJson.encodeToString(RoomState.serializer(), room))
                        pending.onState(room.version)
                        setStatus(ConnectionStatus.Connected(code))
                        publishLocked()
                    }
                    suspend fun releaseHeldState() = stateLock.withLock {
                        clockSynced = true
                        heldState?.let { adoptServerState(it) }
                        heldState = null
                    }

                    val pinger = launch {
                        while (true) {
                            sendMessage(this@webSocket, ClientMessage.Ping(clock.localNowMillis()))
                            delay(PING_INTERVAL_MILLIS)
                        }
                    }
                    val syncTimeout = launch {
                        delay(CLOCK_SYNC_TIMEOUT_MILLIS)
                        releaseHeldState()
                    }
                    // Previsões sem resposta (mensagem perdida) não podem ficar na tela para sempre.
                    val expirer = launch {
                        while (true) {
                            delay(1_000L)
                            stateLock.withLock { if (pending.expire(clock.localNowMillis())) publishLocked() }
                        }
                    }
                    try {
                        for (frame in incoming) {
                            if (frame !is Frame.Text) continue
                            when (val message = decodeOrNull(frame.readText())) {
                                // "Conectado" só quando o servidor aceitou a sala e mandou o estado.
                                is ServerMessage.State -> stateLock.withLock {
                                    if (clockSynced) adoptServerState(message.room) else heldState = message.room
                                }
                                is ServerMessage.CommandResult -> stateLock.withLock {
                                    val rejected = pending.onResult(message, latestServerVersion = serverRoom?.version ?: 0L)
                                    if (rejected) {
                                        _notices.tryEmit(
                                            if (message.reason == RejectionReason.TooManyCommands) SyncNotice.TooManyCommands
                                            else SyncNotice.CommandDiscardedByConflict,
                                        )
                                    }
                                    publishLocked()
                                }
                                is ServerMessage.Pong -> {
                                    clock.offsetMillis = estimator.onPong(message, clock.localNowMillis())
                                    settings.putLong(offsetKey(serverAddress), clock.offsetMillis)
                                    releaseHeldState()
                                }
                                null -> Unit
                            }
                        }
                    } finally {
                        pinger.cancel()
                        syncTimeout.cancel()
                        expirer.cancel()
                        socket = null
                    }
                    rejection = when (closeReason.await()?.code) {
                        SyncCloseCodes.ROOM_NOT_FOUND -> SyncError.RoomNotFound
                        SyncCloseCodes.INVALID_ROOM_CODE -> SyncError.InvalidRoomCode
                        else -> null
                    }
                }
            } catch (e: CancellationException) {
                // Saiu da sala (o laço foi cancelado): encerra. Se só a CONEXÃO foi derrubada
                // (onNetworkChanged), o laço continua ativo e tenta de novo.
                if (!currentCoroutineContext().isActive) throw e
            } catch (e: Exception) {
                // Servidor fora do ar, sem rede, endereço errado... tenta de novo depois.
            }
            // Conexão caiu: previsões não confirmadas não valem mais; fica o último estado oficial.
            stateLock.withLock {
                pending.clear()
                publishLocked()
            }
            // Recusa do servidor não se resolve tentando de novo: sai da sala e avisa o usuário.
            rejection?.let { error ->
                goOffline(error)
                return
            }
            failures++
            setStatus(ConnectionStatus.Reconnecting(code))
            // Espera o tempo da tentativa — ou menos, se alguém pedir "tente agora".
            withTimeoutOrNull(backoffMillis(failures)) { retryNow.receive() }
        }
    }

    /** Publica estado oficial + previsões. Chamar com [stateLock] já obtido. */
    private suspend fun publishLocked() {
        serverRoom?.let { _roomStates.emit(pending.predict(it)) }
    }

    private suspend fun sendMessage(session: WebSocketSession, message: ClientMessage) {
        session.send(Frame.Text(SyncJson.encodeToString(ClientMessage.serializer(), message)))
    }

    private fun setStatus(status: ConnectionStatus) {
        _status.value = status
        _commandsAvailable.value = status == ConnectionStatus.Offline || status is ConnectionStatus.Connected
    }

    private fun goOffline(error: SyncError?) {
        connectionJob = null
        socket = null
        serverRoom = null
        pending.clear()
        settings.remove(KEY_ROOM)
        settings.remove(KEY_CACHED_STATE)
        _roomStates.resetReplayCache()
        setStatus(ConnectionStatus.Offline)
        _lastError.value = error
    }

    /** null se não há cache ou se ele está num formato que esta versão do app não entende. */
    private fun loadCachedState(): RoomState? {
        val json = settings.getStringOrNull(KEY_CACHED_STATE) ?: return null
        return try {
            SyncJson.decodeFromString(RoomState.serializer(), json)
        } catch (e: SerializationException) {
            settings.remove(KEY_CACHED_STATE)
            null
        } catch (e: IllegalArgumentException) {
            settings.remove(KEY_CACHED_STATE)
            null
        }
    }

    private fun decodeOrNull(text: String): ServerMessage? =
        try {
            SyncJson.decodeFromString(ServerMessage.serializer(), text)
        } catch (e: SerializationException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        }

    companion object {
        const val PING_INTERVAL_MILLIS = 10_000L

        /** Quanto o primeiro estado espera pela medição do relógio antes de ser mostrado mesmo assim. */
        const val CLOCK_SYNC_TIMEOUT_MILLIS = 2_000L
        private const val KEY_SERVER = "sync.serverAddress"
        private const val KEY_ROOM = "sync.roomId"

        /** Último estado oficial da sala salva em [KEY_ROOM] (JSON). */
        private const val KEY_CACHED_STATE = "sync.roomState"

        /** A diferença de relógio é por servidor: cada um tem o seu relógio. */
        private fun offsetKey(serverAddress: String) = "sync.clockOffset.$serverAddress"

        /** 1 s, 2 s, 4 s, 8 s, depois sempre 15 s. */
        fun backoffMillis(failures: Int): Long =
            (1_000L shl (failures - 1).coerceIn(0, 4)).coerceAtMost(15_000L)

        /** Identificador aleatório o bastante para não repetir entre aparelhos da mesma sala. */
        private fun newCommandId(): String =
            Random.nextLong().toULong().toString(36) + Random.nextLong().toULong().toString(36)
    }
}
