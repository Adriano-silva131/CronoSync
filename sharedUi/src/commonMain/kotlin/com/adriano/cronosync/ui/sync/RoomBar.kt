package com.adriano.cronosync.ui.sync

import org.jetbrains.compose.resources.StringResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import com.adriano.cronosync.ui.resources.Res
import com.adriano.cronosync.ui.resources.action_cancel
import com.adriano.cronosync.ui.resources.action_join
import com.adriano.cronosync.ui.resources.room_connected
import com.adriano.cronosync.ui.resources.room_connecting
import com.adriano.cronosync.ui.resources.room_create
import com.adriano.cronosync.ui.resources.room_dialog_code
import com.adriano.cronosync.ui.resources.room_dialog_hint
import com.adriano.cronosync.ui.resources.room_dialog_server
import com.adriano.cronosync.ui.resources.room_dialog_title
import com.adriano.cronosync.ui.resources.room_error_code
import com.adriano.cronosync.ui.resources.room_error_local_unreachable
import com.adriano.cronosync.ui.resources.room_error_not_found
import com.adriano.cronosync.ui.resources.room_error_server
import com.adriano.cronosync.ui.resources.room_error_unreachable
import com.adriano.cronosync.ui.resources.room_join
import com.adriano.cronosync.ui.resources.room_leave
import com.adriano.cronosync.ui.resources.room_local_testing
import com.adriano.cronosync.ui.resources.room_none
import com.adriano.cronosync.ui.resources.room_error_too_many_rooms
import com.adriano.cronosync.ui.resources.room_notice_conflict
import com.adriano.cronosync.ui.resources.room_notice_too_many_commands
import com.adriano.cronosync.ui.resources.room_or
import com.adriano.cronosync.ui.resources.room_reconnecting
import com.adriano.cronosync.ui.resources.room_error_staging_unreachable
import com.adriano.cronosync.ui.resources.room_staging
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adriano.cronosync.sync.AppEnvironment
import com.adriano.cronosync.sync.ConnectionStatus
import com.adriano.cronosync.sync.RoomCode
import com.adriano.cronosync.sync.SyncAction
import com.adriano.cronosync.sync.SyncError
import com.adriano.cronosync.sync.SyncNotice
import com.adriano.cronosync.sync.SyncUiState
import com.adriano.cronosync.sync.SyncViewModel
import kotlin.random.Random
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun RoomBarRoute(
    modifier: Modifier = Modifier,
    viewModel: SyncViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    RoomBar(state = state, onAction = viewModel::onAction, modifier = modifier)
}

/** Faixa abaixo das abas: mostra em que sala o app está e o estado da conexão. */
@Composable
fun RoomBar(
    state: SyncUiState,
    onAction: (SyncAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Abrir/fechar o diálogo é estado de UI puro: fica aqui, não no ViewModel.
    var dialogOpen by rememberSaveable { mutableStateOf(false) }

    // Entrou numa sala (saiu do Offline): fecha o diálogo.
    LaunchedEffect(state.status) {
        if (state.status != ConnectionStatus.Offline) dialogOpen = false
    }

    Surface(color = MaterialTheme.colorScheme.surfaceContainer, modifier = modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
        ) {
            // Com o diálogo fechado, um erro de conexão (ex.: a sala sumiu) aparece aqui na barra.
            val barError = state.error.takeIf { !dialogOpen && state.status == ConnectionStatus.Offline }
            Text(
                text = when {
                    barError != null -> stringResource(barError.message)
                    // Aviso de passagem tem prioridade sobre o status por alguns segundos.
                    state.notice == SyncNotice.CommandDiscardedByConflict -> stringResource(Res.string.room_notice_conflict)
                    state.notice == SyncNotice.TooManyCommands -> stringResource(Res.string.room_notice_too_many_commands)
                    else -> statusText(state.status)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = when {
                    barError != null || state.status is ConnectionStatus.Reconnecting -> MaterialTheme.colorScheme.error
                    state.notice != null -> MaterialTheme.colorScheme.tertiary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.weight(1f),
            )
            if (state.status == ConnectionStatus.Offline) {
                TextButton(onClick = { dialogOpen = true }) { Text(stringResource(Res.string.room_join)) }
            } else {
                TextButton(onClick = { onAction(SyncAction.Leave) }) { Text(stringResource(Res.string.room_leave)) }
            }
        }
    }

    if (dialogOpen) {
        RoomDialog(
            state = state,
            onAction = onAction,
            onDismiss = {
                dialogOpen = false
                onAction(SyncAction.DismissError)
            },
        )
    }
}

@Composable
private fun statusText(status: ConnectionStatus): String = when (status) {
    ConnectionStatus.Offline -> stringResource(Res.string.room_none)
    is ConnectionStatus.Connecting -> stringResource(Res.string.room_connecting, status.room.formatted)
    is ConnectionStatus.Connected -> stringResource(Res.string.room_connected, status.room.formatted)
    is ConnectionStatus.Reconnecting -> stringResource(Res.string.room_reconnecting, status.room.formatted)
}

private val SyncError.message: StringResource
    get() = when (this) {
        SyncError.InvalidRoomCode -> Res.string.room_error_code
        SyncError.MissingServer -> Res.string.room_error_server
        SyncError.ServerUnreachable -> Res.string.room_error_unreachable
        SyncError.RoomNotFound -> Res.string.room_error_not_found
        SyncError.TooManyRoomsCreated -> Res.string.room_error_too_many_rooms
    }

/**
 * Criar sala (o servidor gera o código) OU entrar numa existente digitando o código.
 * O que está sendo digitado é estado de UI: só vira ação ao tocar num botão.
 */
@Composable
private fun RoomDialog(
    state: SyncUiState,
    onAction: (SyncAction) -> Unit,
    onDismiss: () -> Unit,
) {
    var server by rememberSaveable { mutableStateOf(state.serverAddress) }
    var code by rememberSaveable { mutableStateOf("") }
    val join = { onAction(SyncAction.Join(serverAddress = server, roomCode = code)) }
    val codeError = state.error == SyncError.InvalidRoomCode || state.error == SyncError.RoomNotFound

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.room_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(Res.string.room_dialog_hint), style = MaterialTheme.typography.bodyMedium)
                if (state.environment != AppEnvironment.Production) {
                    // Versão de testes: servidor fixo. Mostramos para onde vai, em vez de um campo editável.
                    val local = state.environment == AppEnvironment.LocalTesting
                    Text(
                        stringResource(if (local) Res.string.room_local_testing else Res.string.room_staging, state.serverAddress),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                    if (state.error == SyncError.ServerUnreachable) {
                        Text(
                            stringResource(if (local) Res.string.room_error_local_unreachable else Res.string.room_error_staging_unreachable),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                } else {
                    OutlinedTextField(
                        value = server,
                        onValueChange = { server = it },
                        label = { Text(stringResource(Res.string.room_dialog_server)) },
                        singleLine = true,
                        isError = state.error == SyncError.MissingServer || state.error == SyncError.ServerUnreachable,
                        supportingText = state.error
                            ?.takeIf { it == SyncError.MissingServer || it == SyncError.ServerUnreachable }
                            ?.let { error -> { Text(stringResource(error.message)) } },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                    )
                }
                FilledTonalButton(
                    onClick = { onAction(SyncAction.Create(serverAddress = server)) },
                    enabled = !state.isCreatingRoom,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (state.isCreatingRoom) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text(stringResource(Res.string.room_create))
                    }
                }
                if (state.error == SyncError.TooManyRoomsCreated) {
                    Text(
                        stringResource(SyncError.TooManyRoomsCreated.message),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                HorizontalDivider()
                Text(stringResource(Res.string.room_or), style = MaterialTheme.typography.labelLarge)
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it },
                    label = { Text(stringResource(Res.string.room_dialog_code)) },
                    singleLine = true,
                    isError = codeError,
                    supportingText = state.error?.takeIf { codeError }?.let { error -> { Text(stringResource(error.message)) } },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        keyboardType = KeyboardType.Ascii,
                        imeAction = ImeAction.Go,
                    ),
                    keyboardActions = KeyboardActions(onGo = { join() }),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = join, enabled = code.isNotBlank()) { Text(stringResource(Res.string.action_join)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) } },
    )
}

@Preview(showBackground = true)
@Composable
private fun RoomBarConnectedPreview() {
    MaterialTheme {
        RoomBar(
            state = SyncUiState(
                status = ConnectionStatus.Connected(RoomCode.generate(Random(1))),
                serverAddress = "localhost:8080",
            ),
            onAction = {},
        )
    }
}
