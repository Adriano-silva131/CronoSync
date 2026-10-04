package com.adriano.cronosync.ui.alarm

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adriano.cronosync.R
import com.adriano.cronosync.alarm.label
import com.adriano.cronosync.pomodoro.domain.PomodoroPhaseKind
import com.adriano.cronosync.pomodoro.presentation.PomodoroAlarmAction
import com.adriano.cronosync.pomodoro.presentation.PomodoroAlarmUiState
import com.adriano.cronosync.pomodoro.presentation.PomodoroAlarmViewModel
import com.adriano.cronosync.ui.theme.CronoSyncTheme
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun PomodoroAlarmRoute(
    onDismissed: () -> Unit,
    viewModel: PomodoroAlarmViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.isDismissed) {
        if (state.isDismissed) onDismissed()
    }
    // Voltar não fecha: o alarme só para com um Parar explícito, para não tocar escondido.
    BackHandler {}
    PomodoroAlarmScreen(state = state, onAction = viewModel::onAction)
}

@Composable
fun PomodoroAlarmScreen(
    state: PomodoroAlarmUiState,
    onAction: (PomodoroAlarmAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isFocus = state.phase == PomodoroPhaseKind.Focus
    val container = if (isFocus) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.tertiaryContainer
    val onContainer = if (isFocus) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onTertiaryContainer
    val accent = if (isFocus) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
    val onAccent = if (isFocus) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onTertiary
    val buttonScale = if (rememberReduceMotion()) 1f else breathingScale()

    Column(
        modifier = modifier.fillMaxSize().background(container),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(painterResource(R.drawable.ic_timer), contentDescription = null, tint = onContainer, modifier = Modifier.size(72.dp))
        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(if (isFocus) R.string.pomodoro_ringing_focus else R.string.pomodoro_ringing_break),
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = onContainer,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.pomodoro_ringing_text, stringResource(state.phase.label), state.phaseMinutes),
            color = onContainer,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = state.remainingText,
            style = MaterialTheme.typography.displayMedium.merge(TextStyle(fontFeatureSettings = "tnum")),
            color = onContainer,
        )
        Spacer(Modifier.height(64.dp))
        Button(
            onClick = { onAction(PomodoroAlarmAction.Stop) },
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = onAccent),
            modifier = Modifier
                .size(160.dp)
                .graphicsLayer {
                    scaleX = buttonScale
                    scaleY = buttonScale
                },
        ) {
            Text(stringResource(R.string.action_stop), style = MaterialTheme.typography.headlineSmall)
        }
    }
}

@Preview
@Composable
private fun PomodoroAlarmScreenPreview() {
    CronoSyncTheme {
        PomodoroAlarmScreen(
            state = PomodoroAlarmUiState(phase = PomodoroPhaseKind.ShortBreak, phaseMinutes = 5, remainingText = "04:52", isDismissed = false),
            onAction = {},
        )
    }
}
