package com.adriano.cronosync.ui.pomodoro

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adriano.cronosync.pomodoro.domain.PomodoroStatus
import com.adriano.cronosync.pomodoro.presentation.PomodoroAction
import com.adriano.cronosync.pomodoro.presentation.PomodoroViewModel
import com.adriano.cronosync.ui.timer.AlarmOptionsRoute
import com.adriano.cronosync.ui.timer.AlarmPermissionBanner
import com.adriano.cronosync.ui.timer.rememberMissingAlarmPermission
import com.adriano.cronosync.ui.timer.rememberNotificationPermissionRequest
import org.koin.compose.viewmodel.koinViewModel

/**
 * Rota do Pomodoro no Android: a tela compartilhada ([PomodoroScreen]) mais o que só existe aqui,
 * igual ao timer — pedir permissão de notificação ao iniciar e avisar se falta permissão de alarme.
 */
@Composable
fun AndroidPomodoroRoute(
    modifier: Modifier = Modifier,
    viewModel: PomodoroViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val requestNotificationPermission = rememberNotificationPermissionRequest()
    val missingPermission = rememberMissingAlarmPermission()

    Column(modifier = modifier) {
        if (state.status != PomodoroStatus.Idle && missingPermission != null) {
            AlarmPermissionBanner(missing = missingPermission, modifier = Modifier.padding(16.dp))
        }
        PomodoroScreen(
            state = state,
            onAction = { action ->
                if (action == PomodoroAction.Start) requestNotificationPermission()
                viewModel.onAction(action)
            },
            modifier = Modifier.weight(1f),
            footer = { AlarmOptionsRoute(showVibration = true) },
        )
    }
}
