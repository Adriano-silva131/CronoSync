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
import com.adriano.cronosync.timer.presentation.TimerAlarmAction
import com.adriano.cronosync.timer.presentation.TimerAlarmUiState
import com.adriano.cronosync.timer.presentation.TimerAlarmViewModel
import com.adriano.cronosync.ui.theme.CronoSyncTheme
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun AlarmRoute(
    onDismissed: () -> Unit,
    viewModel: TimerAlarmViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.isDismissed) {
        if (state.isDismissed) onDismissed()
    }
    // Voltar não fecha: o alarme só para com um Parar explícito, para não tocar escondido.
    BackHandler {}

    AlarmScreen(state = state, onAction = viewModel::onAction)
}

@Composable
fun AlarmScreen(
    state: TimerAlarmUiState,
    onAction: (TimerAlarmAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Acessibilidade (epilepsia, WCAG 2.3.1): fundo fixo, nada pisca nem muda de cor/brilho; só o botão
    // "respira" no tamanho, e nem isso com "Remover animações" ligado.
    val buttonScale = if (rememberReduceMotion()) 1f else breathingScale()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.errorContainer),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_timer),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.size(72.dp),
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.alarm_title),
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = state.overtimeText,
            style = MaterialTheme.typography.displayMedium.merge(TextStyle(fontFeatureSettings = "tnum")),
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
        Spacer(Modifier.height(64.dp))
        Button(
            onClick = { onAction(TimerAlarmAction.Stop) },
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
            ),
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
private fun AlarmScreenPreview() {
    CronoSyncTheme {
        AlarmScreen(state = TimerAlarmUiState(overtimeText = "-00:12", isDismissed = false), onAction = {})
    }
}
