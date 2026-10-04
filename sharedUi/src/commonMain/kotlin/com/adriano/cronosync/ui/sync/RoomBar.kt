package com.adriano.cronosync.ui.sync

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adriano.cronosync.sync.domain.ConnectionStatus
import com.adriano.cronosync.sync.domain.RoomCode
import com.adriano.cronosync.sync.domain.SyncError
import com.adriano.cronosync.sync.domain.SyncNotice
import com.adriano.cronosync.sync.presentation.SyncAction
import com.adriano.cronosync.sync.presentation.SyncUiState
import com.adriano.cronosync.sync.presentation.SyncViewModel
import com.adriano.cronosync.ui.resources.Res
import com.adriano.cronosync.ui.resources.room_connected
import com.adriano.cronosync.ui.resources.room_connecting
import com.adriano.cronosync.ui.resources.room_error_code
import com.adriano.cronosync.ui.resources.room_error_not_found
import com.adriano.cronosync.ui.resources.room_error_server
import com.adriano.cronosync.ui.resources.room_error_too_many_rooms
import com.adriano.cronosync.ui.resources.room_error_unreachable
import com.adriano.cronosync.ui.resources.room_join
import com.adriano.cronosync.ui.resources.room_leave
import com.adriano.cronosync.ui.resources.room_none
import com.adriano.cronosync.ui.resources.room_notice_conflict
import com.adriano.cronosync.ui.resources.room_notice_too_many_commands
import com.adriano.cronosync.ui.resources.room_reconnecting
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import kotlin.random.Random

@Composable
fun RoomBarRoute(
    modifier: Modifier = Modifier,
    viewModel: SyncViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    RoomBar(state = state, onAction = viewModel::onAction, modifier = modifier)
}

@Composable
fun RoomBar(
    state: SyncUiState,
    onAction: (SyncAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    var dialogOpen by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(state.status) {
        if (state.status != ConnectionStatus.Offline) dialogOpen = false
    }

    Surface(color = MaterialTheme.colorScheme.surfaceContainer, modifier = modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
        ) {
            val barError = state.error.takeIf { !dialogOpen && state.status == ConnectionStatus.Offline }
            Text(
                text = when {
                    barError != null -> stringResource(barError.message)
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

internal val SyncError.message: StringResource
    get() = when (this) {
        SyncError.InvalidRoomCode -> Res.string.room_error_code
        SyncError.MissingServer -> Res.string.room_error_server
        SyncError.ServerUnreachable -> Res.string.room_error_unreachable
        SyncError.RoomNotFound -> Res.string.room_error_not_found
        SyncError.TooManyRoomsCreated -> Res.string.room_error_too_many_rooms
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
