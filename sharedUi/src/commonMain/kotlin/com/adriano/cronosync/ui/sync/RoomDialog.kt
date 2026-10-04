package com.adriano.cronosync.ui.sync

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.adriano.cronosync.sync.data.AppEnvironment
import com.adriano.cronosync.sync.domain.SyncError
import com.adriano.cronosync.sync.presentation.SyncAction
import com.adriano.cronosync.sync.presentation.SyncUiState
import com.adriano.cronosync.ui.resources.Res
import com.adriano.cronosync.ui.resources.action_cancel
import com.adriano.cronosync.ui.resources.action_join
import com.adriano.cronosync.ui.resources.room_create
import com.adriano.cronosync.ui.resources.room_dialog_code
import com.adriano.cronosync.ui.resources.room_dialog_hint
import com.adriano.cronosync.ui.resources.room_dialog_server
import com.adriano.cronosync.ui.resources.room_dialog_title
import com.adriano.cronosync.ui.resources.room_error_local_unreachable
import com.adriano.cronosync.ui.resources.room_error_staging_unreachable
import com.adriano.cronosync.ui.resources.room_local_testing
import com.adriano.cronosync.ui.resources.room_or
import com.adriano.cronosync.ui.resources.room_staging
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun RoomDialog(
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
