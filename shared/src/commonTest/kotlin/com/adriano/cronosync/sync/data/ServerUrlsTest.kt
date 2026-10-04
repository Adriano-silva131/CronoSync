package com.adriano.cronosync.sync.data

import com.adriano.cronosync.sync.domain.RoomCode
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

class ServerUrlsTest {

    private val random = Random(seed = 42)

    @Test
    fun urlsAreBuiltFromWhatTheUserTyped() {
        val code = RoomCode.generate(random)

        assertEquals("ws://192.168.0.10:8080/rooms/${code.value}", roomWebSocketUrl("192.168.0.10:8080", code))
        assertEquals("ws://localhost:8080/rooms/${code.value}", roomWebSocketUrl(" localhost:8080/ ", code))
        assertEquals("wss://crono.exemplo.com/rooms/${code.value}", roomWebSocketUrl("https://crono.exemplo.com", code))
        assertEquals("http://localhost:8080/rooms", createRoomUrl("localhost:8080"))
        assertEquals("https://crono.exemplo.com/rooms", createRoomUrl("wss://crono.exemplo.com/"))
    }
}
