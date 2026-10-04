package com.adriano.cronosync.server

import com.adriano.cronosync.core.Clock
import com.adriano.cronosync.core.SystemClock
import com.adriano.cronosync.server.config.ServerConfig
import com.adriano.cronosync.server.persistence.InMemoryRoomStore
import com.adriano.cronosync.server.persistence.PostgresRoomStore
import com.adriano.cronosync.server.persistence.RoomStore
import com.adriano.cronosync.server.persistence.createDataSource
import com.adriano.cronosync.server.persistence.migrateDatabase
import com.adriano.cronosync.server.room.RoomRegistry
import com.adriano.cronosync.server.room.launchExpiredRoomCleanup
import com.adriano.cronosync.server.transport.ConnectionLimiter
import com.adriano.cronosync.server.transport.MAX_CONNECTIONS_PER_ADDRESS
import com.adriano.cronosync.server.transport.installTransportPlugins
import com.adriano.cronosync.server.transport.syncRoutes
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.log
import io.ktor.server.engine.embeddedServer
import io.ktor.server.http.content.staticResources
import io.ktor.server.netty.Netty
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing

fun main() {
    val config = ServerConfig.fromEnvironment()
    val dataSource = createDataSource(config.database)
    migrateDatabase(dataSource)
    embeddedServer(Netty, port = config.port, host = "0.0.0.0") {
        module(
            store = PostgresRoomStore(dataSource),
            behindProxy = config.behindProxy,
        )
        monitor.subscribe(ApplicationStopped) { dataSource.close() }
        if (developmentMode) log.info("Página de teste: http://localhost:{}/dev/", config.port)
    }.start(wait = true)
}

fun Application.module(
    clock: Clock = SystemClock,
    store: RoomStore = InMemoryRoomStore(),
    behindProxy: Boolean = false,
) {
    val registry = RoomRegistry(clock, store)
    launchExpiredRoomCleanup(registry, log)
    installTransportPlugins(behindProxy)
    routing {
        get("/") { call.respondText("CronoSync server") }
        syncRoutes(registry, clock, ConnectionLimiter(MAX_CONNECTIONS_PER_ADDRESS))
        if (developmentMode) {
            staticResources("/dev", "dev", index = "sync-tester.html")
        }
    }
}
