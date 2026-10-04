package com.adriano.cronosync.sync.domain

sealed interface ConnectionStatus {
    data object Offline : ConnectionStatus

    data class Connecting(val room: RoomCode) : ConnectionStatus

    data class Connected(val room: RoomCode) : ConnectionStatus

    data class Reconnecting(val room: RoomCode) : ConnectionStatus
}

enum class SyncError {
    InvalidRoomCode,
    MissingServer,
    ServerUnreachable,
    RoomNotFound,
    TooManyRoomsCreated,
}

enum class SyncNotice {
    CommandDiscardedByConflict,

    TooManyCommands,
}
