package com.adriano.cronosync.sync.data

import com.adriano.cronosync.core.AlignedClock
import com.adriano.cronosync.sync.domain.ClockOffsetEstimator
import com.adriano.cronosync.sync.domain.ConnectionStatus
import com.adriano.cronosync.sync.domain.RoomCode
import com.adriano.cronosync.sync.domain.RoomCommand
import com.adriano.cronosync.sync.domain.RoomState
import com.adriano.cronosync.sync.domain.SyncError
import com.adriano.cronosync.sync.domain.SyncNotice
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

    private val _roomStates = MutableSharedFlow<RoomState>(replay = 1)
    override val roomStates: Flow<RoomState> = _roomStates.asSharedFlow()

    private var connectionJob: Job? = null

    @Volatile
    private var socket: WebSocketSession? = null

    private val stateLock = Mutex()
    private var serverRoom: RoomState? = null
    private val pending = PendingCommands()

    private val retryNow = Channel<Unit>(Channel.CONFLATED)

    override val isInRoom: Boolean get() = connectionJob != null

    val lastServerAddress: String
        get() = if (config.isFixedServer) config.defaultServerAddress else settings.getString(KEY_SERVER, config.defaultServerAddress)

    init {
        val savedRoom = settings.getStringOrNull(KEY_ROOM)?.let(RoomCode::parse)
        if (savedRoom != null) {
            loadCachedState()?.let { _roomStates.tryEmit(it) }
            join(lastServerAddress, savedRoom)
        } else {
            settings.remove(KEY_ROOM)
            settings.remove(KEY_CACHED_STATE)
        }
    }

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
        if (code.value != settings.getStringOrNull(KEY_ROOM) || serverAddress != settings.getStringOrNull(KEY_SERVER)) {
            settings.remove(KEY_CACHED_STATE)
        }
        settings.putString(KEY_SERVER, serverAddress)
        settings.putString(KEY_ROOM, code.value)
        settings.getLongOrNull(offsetKey(serverAddress))?.let { clock.offsetMillis = it }
        connectionJob = scope.launch { connectionLoop(serverAddress, code) }
    }

    fun leave() {
        connectionJob?.cancel()
        goOffline(error = null)
    }

    fun clearError() {
        _lastError.value = null
    }

    fun reconnectNow() {
        if (isInRoom && _status.value !is ConnectionStatus.Connected) retryNow.trySend(Unit)
    }

    fun onNetworkChanged() {
        if (!isInRoom) return
        socket?.cancel()
        retryNow.trySend(Unit)
    }

    override suspend fun sendCommand(command: RoomCommand): Boolean = stateLock.withLock {
        val current = socket ?: return false
        val server = serverRoom ?: return false
        val message = ClientMessage.Command(
            id = newCommandId(),
            command = command,
            atMillis = clock.nowMillis(),
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

                    var clockSynced = false
                    var heldState: RoomState? = null
                    suspend fun adoptServerState(room: RoomState) {
                        failures = 0
                        retryNow.tryReceive()
                        serverRoom = room
                        // Só o estado oficial vai para o cache, nunca as previsões.
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
                if (!currentCoroutineContext().isActive) throw e
            } catch (e: Exception) {
            }
            stateLock.withLock {
                pending.clear()
                publishLocked()
            }
            rejection?.let { error ->
                goOffline(error)
                return
            }
            failures++
            setStatus(ConnectionStatus.Reconnecting(code))
            withTimeoutOrNull(backoffMillis(failures)) { retryNow.receive() }
        }
    }

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

        const val CLOCK_SYNC_TIMEOUT_MILLIS = 2_000L
        private const val KEY_SERVER = "sync.serverAddress"
        private const val KEY_ROOM = "sync.roomId"

        private const val KEY_CACHED_STATE = "sync.roomState"

        private fun offsetKey(serverAddress: String) = "sync.clockOffset.$serverAddress"

        fun backoffMillis(failures: Int): Long =
            (1_000L shl (failures - 1).coerceIn(0, 4)).coerceAtMost(15_000L)

        private fun newCommandId(): String =
            Random.nextLong().toULong().toString(36) + Random.nextLong().toULong().toString(36)
    }
}
