package com.adriano.cronosync.ui.alarm

import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
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

    // Efeito colateral dirigido pelo estado: quando o timer deixa de estar ativo, a tela se fecha.
    LaunchedEffect(state.isDismissed) {
        if (state.isDismissed) onDismissed()
    }
    // Voltar não fecha: o alarme só para com um "Parar" explícito, para não ficar tocando escondido.
    BackHandler {}

    AlarmScreen(state = state, onAction = viewModel::onAction)
}

@Composable
fun AlarmScreen(
    state: TimerAlarmUiState,
    onAction: (TimerAlarmAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    /*
     * Acessibilidade (fotossensibilidade / epilepsia — WCAG 2.3.1):
     * - o fundo é FIXO: nada de piscar ou alternar cores/brilho, principalmente vermelho saturado,
     *   que é o gatilho mais perigoso em áreas grandes da tela;
     * - a atenção vem do som, da vibração e de um movimento lento de "respiração" só no botão,
     *   que muda o tamanho, mas não a cor nem o brilho;
     * - com "Remover animações" ativado no Android, nem esse movimento acontece.
     */
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
                // graphicsLayer: muda só a escala na hora de desenhar, sem refazer o layout a cada frame.
                .graphicsLayer {
                    scaleX = buttonScale
                    scaleY = buttonScale
                },
        ) {
            Text(stringResource(R.string.action_stop), style = MaterialTheme.typography.headlineSmall)
        }
    }
}

/** Escala que vai de 100% a 106% e volta, num ciclo lento (~2,4 s), com início e fim suaves. */
@Composable
internal fun breathingScale(): Float {
    val transition = rememberInfiniteTransition(label = "alarm-breathing")
    val scale by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1_200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "alarm-button-scale",
    )
    return scale
}

/**
 * true quando o usuário ativou "Remover animações" (Acessibilidade), que zera a escala de
 * duração de animações do sistema.
 */
@Composable
internal fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    return remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

@Preview
@Composable
private fun AlarmScreenPreview() {
    CronoSyncTheme {
        AlarmScreen(state = TimerAlarmUiState(overtimeText = "-00:12", isDismissed = false), onAction = {})
    }
}
