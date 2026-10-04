package com.adriano.cronosync.server

import com.adriano.cronosync.core.AlignedClock
import com.adriano.cronosync.core.Clock
import com.adriano.cronosync.stopwatch.data.SyncedStopwatchRepository
import com.adriano.cronosync.stopwatch.domain.StopwatchCommand
import com.adriano.cronosync.stopwatch.domain.StopwatchStatus
import com.adriano.cronosync.sync.data.SyncConfig
import com.adriano.cronosync.sync.data.SyncSession
import com.adriano.cronosync.sync.domain.ConnectionStatus
import com.adriano.cronosync.sync.domain.RoomCode
import com.adriano.cronosync.sync.domain.RoomState
import com.adriano.cronosync.sync.domain.SyncError
import com.adriano.cronosync.timer.data.SettingsTimerStorage
import com.adriano.cronosync.timer.data.SyncedTimerRepository
import com.adriano.cronosync.timer.domain.TimerCommand
import com.russhwolf.settings.MapSettings
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SyncSessionIntegrationTest {

    private class FakeClock(var currentMillis: Long) : Clock {
        override fun nowMillis(): Long = currentMillis
    }

    private val serverClock = FakeClock(currentMillis = 1_000_000L)

    private suspend fun <T> Flow<T>.awaitFirst(predicate: (T) -> Boolean): T = withTimeout(5_000) { first(predicate) }

    @Test
    fun twoPhonesShareTheSameStopwatchAndTimer() = testApplication {
        application { module(clock = serverClock) }
        val scope = CoroutineScope(SupervisorJob())

        fun phone(): Triple<SyncSession, SyncedStopwatchRepository, SyncedTimerRepository> {
            val clock = AlignedClock(FakeClock(currentMillis = 0L))
            val session = SyncSession(createClient { install(WebSockets) }, clock, MapSettings(), scope, SyncConfig.Configurable)
            val settings = MapSettings()
            return Triple(
                session,
                SyncedStopwatchRepository(clock, session, scope),
                SyncedTimerRepository(clock, SettingsTimerStorage(settings), session, scope),
            )
        }

        try {
            val (sessionA, stopwatchA, timerA) = phone()
            val (sessionB, stopwatchB, timerB) = phone()
            val code = assertNotNull(sessionA.createRoom("localhost"))
            sessionA.join("localhost", code)
            sessionB.join("localhost", assertNotNull(RoomCode.parse(code.formatted)))
            sessionA.status.awaitFirst { it is ConnectionStatus.Connected }
            sessionB.status.awaitFirst { it is ConnectionStatus.Connected }

            stopwatchA.send(StopwatchCommand.Start)
            val onB = stopwatchB.stopwatch.awaitFirst { it.status == StopwatchStatus.Running }
            assertEquals(1_000_000L, onB.runningSinceMillis)

            timerB.send(TimerCommand.SetDuration(45_000L))
            timerA.timer.awaitFirst { it.durationMillis == 45_000L }
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun pingAlignsThePhoneClockWithTheServer() = testApplication {
        application { module(clock = serverClock) }
        val scope = CoroutineScope(SupervisorJob())
        try {
            val phoneClock = AlignedClock(FakeClock(currentMillis = 400_000L))
            val session = SyncSession(createClient { install(WebSockets) }, phoneClock, MapSettings(), scope, SyncConfig.Configurable)
            session.join("localhost", assertNotNull(session.createRoom("localhost")))
            session.status.awaitFirst { it is ConnectionStatus.Connected }

            withTimeout(5_000) { while (phoneClock.offsetMillis == 0L) kotlinx.coroutines.delay(10) }

            assertEquals(600_000L, phoneClock.offsetMillis)
            assertEquals(serverClock.currentMillis, phoneClock.nowMillis())
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun rememberedRoomIsRejoinedWhenTheAppRestarts() = testApplication {
        application { module(clock = serverClock) }
        val scope = CoroutineScope(SupervisorJob())
        try {
            val settings = MapSettings()
            val clock = AlignedClock(FakeClock(0L))
            val first = SyncSession(createClient { install(WebSockets) }, clock, settings, scope, SyncConfig.Configurable)
            val code = assertNotNull(first.createRoom("localhost"))
            first.join("localhost", code)

            val restarted = SyncSession(createClient { install(WebSockets) }, clock, settings, scope, SyncConfig.Configurable)

            assertIs<ConnectionStatus.Connected>(restarted.status.awaitFirst { it is ConnectionStatus.Connected })
            assertEquals(ConnectionStatus.Connected(code), restarted.status.value)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun lastRoomStateIsShownWhileTheServerIsUnreachable() = testApplication {
        application { module(clock = serverClock) }
        val scope = CoroutineScope(SupervisorJob())
        try {
            val clock = AlignedClock(FakeClock(0L))
            val computer = SyncSession(createClient { install(WebSockets) }, clock, MapSettings(), scope, SyncConfig.Configurable)
            val phoneSettings = MapSettings()
            val phone = SyncSession(createClient { install(WebSockets) }, clock, phoneSettings, scope, SyncConfig.Configurable)
            val code = assertNotNull(computer.createRoom("localhost"))
            computer.join("localhost", code)
            phone.join("localhost", code)
            computer.status.awaitFirst { it is ConnectionStatus.Connected }
            SyncedStopwatchRepository(clock, computer, scope).send(StopwatchCommand.Start)
            SyncedStopwatchRepository(clock, phone, scope).stopwatch.awaitFirst { it.status == StopwatchStatus.Running }

            val reopened = SyncSession(createClient { }, clock, phoneSettings, scope, SyncConfig.Configurable)
            val stopwatch = SyncedStopwatchRepository(clock, reopened, scope)

            val shown = stopwatch.stopwatch.awaitFirst { it.status == StopwatchStatus.Running }
            assertEquals(1_000_000L, shown.runningSinceMillis)
            assertIs<ConnectionStatus.Reconnecting>(reopened.status.awaitFirst { it is ConnectionStatus.Reconnecting })
            assertEquals(false, reopened.commandsAvailable.value)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun leavingTheRoomForgetsTheCachedState() = testApplication {
        application { module(clock = serverClock) }
        val scope = CoroutineScope(SupervisorJob())
        try {
            val settings = MapSettings()
            val clock = AlignedClock(FakeClock(0L))
            val session = SyncSession(createClient { install(WebSockets) }, clock, settings, scope, SyncConfig.Configurable)
            session.join("localhost", assertNotNull(session.createRoom("localhost")))
            session.status.awaitFirst { it is ConnectionStatus.Connected }

            session.leave()

            assertEquals(setOf("sync.serverAddress"), settings.keys.filterNot { it.startsWith("sync.clockOffset") }.toSet())
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun creatingTooManyRoomsIsReportedToThePerson() = testApplication {
        application { module(clock = serverClock) }
        val scope = CoroutineScope(SupervisorJob())
        try {
            val session = SyncSession(createClient { install(WebSockets) }, AlignedClock(FakeClock(0L)), MapSettings(), scope, SyncConfig.Configurable)
            repeat(10) { assertNotNull(session.createRoom("localhost")) }

            assertNull(session.createRoom("localhost"))
            assertEquals(SyncError.TooManyRoomsCreated, session.lastError.value)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun joiningARoomThatDoesNotExistStopsAndReportsTheError() = testApplication {
        application { module(clock = serverClock) }
        val scope = CoroutineScope(SupervisorJob())
        try {
            val settings = MapSettings()
            val session = SyncSession(createClient { install(WebSockets) }, AlignedClock(FakeClock(0L)), settings, scope, SyncConfig.Configurable)

            session.join("localhost", RoomCode.generate(Random(99)))

            assertEquals(SyncError.RoomNotFound, session.lastError.awaitFirst { it != null })
            assertEquals(ConnectionStatus.Offline, session.status.value)
            assertEquals(false, session.isInRoom)
            assertNull(settings.getStringOrNull("sync.roomId"))
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun localTestingVersionAlwaysUsesTheLocalServer() = testApplication {
        val scope = CoroutineScope(SupervisorJob())
        try {
            val settings = MapSettings().apply { putString("sync.serverAddress", "192.168.0.99:9000") }
            val session = SyncSession(createClient { install(WebSockets) }, AlignedClock(FakeClock(0L)), settings, scope, SyncConfig.LocalTesting)

            assertEquals("localhost:8080", session.lastServerAddress)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun roomStateIsShownOnlyAfterTheClockIsAligned() = testApplication {
        application { module(clock = serverClock) }
        val scope = CoroutineScope(SupervisorJob())
        try {
            val phoneClock = AlignedClock(FakeClock(currentMillis = 400_000L))
            val session = SyncSession(createClient { install(WebSockets) }, phoneClock, MapSettings(), scope, SyncConfig.Configurable)
            session.join("localhost", assertNotNull(session.createRoom("localhost")))

            session.status.awaitFirst { it is ConnectionStatus.Connected }

            assertEquals(600_000L, phoneClock.offsetMillis)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun clockDifferenceIsRememberedForTheNextConnection() = testApplication {
        application { module(clock = serverClock) }
        val scope = CoroutineScope(SupervisorJob())
        try {
            val settings = MapSettings()
            val first = SyncSession(createClient { install(WebSockets) }, AlignedClock(FakeClock(400_000L)), settings, scope, SyncConfig.Configurable)
            val code = assertNotNull(first.createRoom("localhost"))
            first.join("localhost", code)
            first.status.awaitFirst { it is ConnectionStatus.Connected }
            first.leave()

            val reopenedClock = AlignedClock(FakeClock(400_000L))
            SyncSession(createClient { install(WebSockets) }, reopenedClock, settings, scope, SyncConfig.Configurable)
                .join("localhost", code)

            assertEquals(600_000L, reopenedClock.offsetMillis)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun networkChangeReconnectsRightAway() = testApplication {
        application { module(clock = serverClock) }
        val scope = CoroutineScope(SupervisorJob())
        try {
            val session = SyncSession(createClient { install(WebSockets) }, AlignedClock(FakeClock(0L)), MapSettings(), scope, SyncConfig.Configurable)
            session.join("localhost", assertNotNull(session.createRoom("localhost")))
            session.status.awaitFirst { it is ConnectionStatus.Connected }

            // Grava todas as transições em vez de esperar uma com first {}: o "Reconectando" pode durar
            // microssegundos, e um observador atrasado esperaria para sempre por um estado que já passou.
            val statuses = CopyOnWriteArrayList<ConnectionStatus>()
            val commandsAvailable = CopyOnWriteArrayList<Boolean>()
            scope.launch(Dispatchers.Unconfined) { session.status.collect { statuses += it } }
            scope.launch(Dispatchers.Unconfined) { session.commandsAvailable.collect { commandsAvailable += it } }

            session.onNetworkChanged()

            withTimeout(SyncSession.backoffMillis(1) - 200) {
                while (
                    statuses.none { it is ConnectionStatus.Reconnecting } ||
                    statuses.last() !is ConnectionStatus.Connected ||
                    commandsAvailable.lastOrNull() != true
                ) delay(10)
            }
            // dropWhile: a gravação pode começar com um false da conexão anterior.
            assertTrue(false in commandsAvailable.toList().dropWhile { !it }, "botões deveriam desabilitar: $commandsAvailable")
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun tapTakesEffectImmediatelyAndIsThenConfirmedByTheServer() = testApplication {
        application { module(clock = serverClock) }
        val scope = CoroutineScope(SupervisorJob())
        try {
            val clock = AlignedClock(FakeClock(0L))
            val session = SyncSession(createClient { install(WebSockets) }, clock, MapSettings(), scope, SyncConfig.Configurable)
            val stopwatch = SyncedStopwatchRepository(clock, session, scope)
            session.join("localhost", assertNotNull(session.createRoom("localhost")))
            session.status.awaitFirst { it is ConnectionStatus.Connected }
            stopwatch.stopwatch.awaitFirst { it.status == StopwatchStatus.Idle }
            val published = mutableListOf<RoomState>()
            scope.launch(Dispatchers.Unconfined) { session.roomStates.collect { published += it } }

            stopwatch.send(StopwatchCommand.Start)
            session.roomStates.awaitFirst { it.version == 1L }

            val prediction = published.first { it.stopwatch.status == StopwatchStatus.Running }
            assertEquals(0L, prediction.version)
            val confirmed = published.last()
            assertEquals(1L, confirmed.version)
            assertEquals(serverClock.currentMillis, confirmed.stopwatch.runningSinceMillis)
            assertTrue(published.indexOf(prediction) < published.indexOf(confirmed))
            assertEquals(StopwatchStatus.Running, stopwatch.stopwatch.awaitFirst { it.status == StopwatchStatus.Running }.status)
        } finally {
            scope.cancel()
        }
    }
}
