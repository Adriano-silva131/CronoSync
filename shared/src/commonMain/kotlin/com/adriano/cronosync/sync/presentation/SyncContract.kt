package com.adriano.cronosync.sync.presentation

import com.adriano.cronosync.sync.data.AppEnvironment
import com.adriano.cronosync.sync.domain.ConnectionStatus
import com.adriano.cronosync.sync.domain.SyncError
import com.adriano.cronosync.sync.domain.SyncNotice

data class SyncUiState(
    val status: ConnectionStatus,
    val serverAddress: String,
    val error: SyncError? = null,
    val isCreatingRoom: Boolean = false,
    val environment: AppEnvironment = AppEnvironment.Production,
    val notice: SyncNotice? = null,
)

sealed interface SyncAction {
    data class Create(val serverAddress: String) : SyncAction
    data class Join(val serverAddress: String, val roomCode: String) : SyncAction
    data object Leave : SyncAction
    data object DismissError : SyncAction
}
