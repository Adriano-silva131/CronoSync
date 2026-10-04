package com.adriano.cronosync.sync.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adriano.cronosync.sync.data.SyncSession
import com.adriano.cronosync.sync.domain.RoomCode
import com.adriano.cronosync.sync.domain.SyncError
import com.adriano.cronosync.sync.domain.SyncNotice
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SyncViewModel(private val session: SyncSession) : ViewModel() {

    private val formError = MutableStateFlow<SyncError?>(null)
    private val isCreatingRoom = MutableStateFlow(false)
    private val notice = MutableStateFlow<SyncNotice?>(null)

    init {
        viewModelScope.launch {
            session.notices.collectLatest { newNotice ->
                notice.value = newNotice
                delay(NOTICE_MILLIS)
                notice.value = null
            }
        }
    }

    val uiState: StateFlow<SyncUiState> =
        combine(session.status, session.lastError, formError, isCreatingRoom, notice) { status, sessionError, formError, creating, notice ->
            SyncUiState(
                status = status,
                serverAddress = session.lastServerAddress,
                error = formError ?: sessionError,
                isCreatingRoom = creating,
                environment = session.config.environment,
                notice = notice,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = SyncUiState(
                status = session.status.value,
                serverAddress = session.lastServerAddress,
                environment = session.config.environment,
            ),
        )

    fun onAction(action: SyncAction) {
        when (action) {
            is SyncAction.Create -> create(serverFor(action.serverAddress))
            is SyncAction.Join -> join(serverFor(action.serverAddress), action.roomCode)
            SyncAction.Leave -> session.leave()
            SyncAction.DismissError -> {
                formError.value = null
                session.clearError()
            }
        }
    }

    private fun serverFor(typed: String): String =
        if (session.config.isFixedServer) session.config.defaultServerAddress else typed.trim()

    private fun create(server: String) {
        if (server.isEmpty()) {
            formError.value = SyncError.MissingServer
            return
        }
        if (isCreatingRoom.value) return
        formError.value = null
        isCreatingRoom.value = true
        viewModelScope.launch {
            session.createRoom(server)?.let { code -> session.join(server, code) }
            isCreatingRoom.value = false
        }
    }

    private fun join(server: String, typedCode: String) {
        val code = RoomCode.parse(typedCode)
        formError.value = when {
            server.isEmpty() -> SyncError.MissingServer
            code == null -> SyncError.InvalidRoomCode
            else -> null
        }
        if (code != null && formError.value == null) session.join(server, code)
    }

    private companion object {
        const val NOTICE_MILLIS = 4_000L
    }
}
