package com.adriano.cronosync.server.transport

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.forwardedheaders.XForwardedHeaders
import io.ktor.server.plugins.origin
import io.ktor.server.plugins.ratelimit.RateLimit
import io.ktor.server.plugins.ratelimit.RateLimitName
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.pingPeriod
import io.ktor.server.websocket.timeout
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

fun Application.installTransportPlugins(behindProxy: Boolean) {
    if (behindProxy) {
        install(XForwardedHeaders) {
            // Só o último endereço foi visto pelo nosso proxy; os anteriores podem ser forjados pelo cliente.
            useLastProxy()
        }
    }

    install(WebSockets) {
        pingPeriod = 15.seconds
        timeout = 30.seconds
        maxFrameSize = MAX_FRAME_BYTES
    }
    install(RateLimit) {
        register(CREATE_ROOM_RATE_LIMIT) {
            rateLimiter(limit = 10, refillPeriod = 1.minutes)
            requestKey { call -> call.request.origin.remoteAddress }
        }
        register(ROOM_CONNECT_RATE_LIMIT) {
            rateLimiter(limit = 60, refillPeriod = 1.minutes)
            requestKey { call -> call.request.origin.remoteAddress }
        }
    }
}

val CREATE_ROOM_RATE_LIMIT = RateLimitName("create-room")
val ROOM_CONNECT_RATE_LIMIT = RateLimitName("room-connect")

// Folgado de propósito: na rede móvel, muitos clientes saem pelo mesmo IP (CGNAT).
const val MAX_CONNECTIONS_PER_ADDRESS = 50

const val MAX_FRAME_BYTES = 32L * 1024
