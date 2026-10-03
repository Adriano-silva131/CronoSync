package com.adriano.cronosync.sync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SyncUiState(
    val status: ConnectionStatus,
    /** Para preencher o formulário com o último servidor usado. */
    val serverAddress: String,
    val error: SyncError? = null,
    /** Pedido de "criar sala" em andamento (mostra carregando e evita toque duplo). */
    val isCreatingRoom: Boolean = false,
    /** Versão do app. Fora da final ([AppEnvironment.Production]) o servidor é fixo, sem campo para editar. */
    val environment: AppEnvironment = AppEnvironment.Production,
    /** Aviso de passagem (ex.: toque descartado por conflito), mostrado por alguns segundos. */
    val notice: SyncNotice? = null,
)

sealed interface SyncAction {
    data class Create(val serverAddress: String) : SyncAction
    data class Join(val serverAddress: String, val roomCode: String) : SyncAction
    data object Leave : SyncAction
    data object DismissError : SyncAction
}

/** Barra/diálogo de sala: criar, entrar, sair e mostrar o status da conexão. */
class SyncViewModel(private val session: SyncSession) : ViewModel() {

    /** Erros de validação do formulário (os de conexão vêm de [SyncSession.lastError]). */
    private val formError = MutableStateFlow<SyncError?>(null)
    private val isCreatingRoom = MutableStateFlow(false)
    private val notice = MutableStateFlow<SyncNotice?>(null)

    init {
        // Cada aviso fica NOTICE_MILLIS na tela; um aviso novo reinicia a contagem (collectLatest).
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

    /** Nas versões de testes o endereço digitado é ignorado: vale sempre o servidor fixo. */
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
            // Se falhar, o motivo (servidor fora do ar, salas demais...) aparece por session.lastError.
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
