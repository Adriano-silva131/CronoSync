package com.adriano.cronosync.ui.timer

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adriano.cronosync.timer.domain.TimerStatus
import com.adriano.cronosync.timer.presentation.TimerAction
import com.adriano.cronosync.timer.presentation.TimerViewModel
import org.koin.compose.viewmodel.koinViewModel

/**
 * Rota do timer no Android: a mesma tela compartilhada ([TimerScreen]), mais o que só existe
 * aqui — pedir permissão de notificação ao iniciar e avisar se falta alguma permissão de alarme.
 */
@Composable
fun AndroidTimerRoute(
    modifier: Modifier = Modifier,
    viewModel: TimerViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val requestNotificationPermission = rememberNotificationPermissionRequest()
    val missingPermission = rememberMissingAlarmPermission()

    Column(modifier = modifier) {
        // Só avisa depois que o usuário iniciou um timer — antes disso o aviso não faz sentido para ele.
        if (state.status != TimerStatus.Idle && missingPermission != null) {
            AlarmPermissionBanner(missing = missingPermission, modifier = Modifier.padding(16.dp))
        }
        TimerScreen(
            state = state,
            onAction = { action ->
                if (action == TimerAction.Start) requestNotificationPermission()
                viewModel.onAction(action)
            },
            modifier = Modifier.weight(1f),
            footer = { AlarmOptionsRoute(showVibration = true) },
        )
    }
}
