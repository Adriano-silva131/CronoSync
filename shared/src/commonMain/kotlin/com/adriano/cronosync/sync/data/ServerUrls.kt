package com.adriano.cronosync.sync.data

import com.adriano.cronosync.sync.domain.RoomCode

private data class ServerAddress(val secure: Boolean, val host: String)

private fun parseServerAddress(serverAddress: String): ServerAddress {
    val address = serverAddress.trim().trimEnd('/')
    val secureSchemes = listOf("wss://", "https://")
    val plainSchemes = listOf("ws://", "http://")
    secureSchemes.firstOrNull { address.startsWith(it) }?.let { return ServerAddress(true, address.removePrefix(it)) }
    plainSchemes.firstOrNull { address.startsWith(it) }?.let { return ServerAddress(false, address.removePrefix(it)) }
    return ServerAddress(false, address)
}

fun roomWebSocketUrl(serverAddress: String, code: RoomCode): String {
    val server = parseServerAddress(serverAddress)
    return "${if (server.secure) "wss" else "ws"}://${server.host}/rooms/${code.value}"
}

fun createRoomUrl(serverAddress: String): String {
    val server = parseServerAddress(serverAddress)
    return "${if (server.secure) "https" else "http"}://${server.host}/rooms"
}
