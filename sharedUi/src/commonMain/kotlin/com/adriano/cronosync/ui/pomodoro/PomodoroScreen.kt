package com.adriano.cronosync.ui.pomodoro

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adriano.cronosync.pomodoro.domain.PomodoroPhaseKind
import com.adriano.cronosync.pomodoro.domain.PomodoroStatus
import com.adriano.cronosync.pomodoro.presentation.PomodoroAction
import com.adriano.cronosync.pomodoro.presentation.PomodoroSettingField
import com.adriano.cronosync.pomodoro.presentation.PomodoroSettingsFields
import com.adriano.cronosync.pomodoro.presentation.PomodoroUiState
import com.adriano.cronosync.pomodoro.presentation.PomodoroViewModel
import com.adriano.cronosync.ui.alarm.AlarmOptionsRoute
import com.adriano.cronosync.ui.resources.Res
import com.adriano.cronosync.ui.resources.action_pause
import com.adriano.cronosync.ui.resources.action_reset
import com.adriano.cronosync.ui.resources.action_resume
import com.adriano.cronosync.ui.resources.action_start
import com.adriano.cronosync.ui.resources.pomodoro_completed
import com.adriano.cronosync.ui.resources.pomodoro_focus
import com.adriano.cronosync.ui.resources.pomodoro_long_break
import com.adriano.cronosync.ui.resources.pomodoro_next
import com.adriano.cronosync.ui.resources.pomodoro_setting_cycles
import com.adriano.cronosync.ui.resources.pomodoro_setting_focus
import com.adriano.cronosync.ui.resources.pomodoro_setting_long
import com.adriano.cronosync.ui.resources.pomodoro_setting_short
import com.adriano.cronosync.ui.resources.pomodoro_settings_title
import com.adriano.cronosync.ui.resources.pomodoro_short_break
import com.adriano.cronosync.ui.resources.pomodoro_skip
import com.adriano.cronosync.ui.resources.timer_decrease
import com.adriano.cronosync.ui.resources.timer_increase
import com.adriano.cronosync.ui.sync.OfflineControlsHint
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

private val TabularNumbers = TextStyle(fontFeatureSettings = "tnum")

@Composable
fun PomodoroRoute(
    modifier: Modifier = Modifier,
    viewModel: PomodoroViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    PomodoroScreen(
        state = state,
        onAction = viewModel::onAction,
        modifier = modifier,
        footer = { AlarmOptionsRoute(showVibration = false) },
    )
}

@Composable
fun PomodoroScreen(
    state: PomodoroUiState,
    onAction: (PomodoroAction) -> Unit,
    modifier: Modifier = Modifier,
    footer: @Composable () -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Cor fixa por fase (acessibilidade): troca uma vez na mudança de fase, nunca pisca.
        val phaseColor = phaseColor(state.phase)
        Text(
            text = stringResource(state.phase.label),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = phaseColor,
        )
        Spacer(Modifier.height(16.dp))
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(240.dp)) {
            CircularProgressIndicator(
                progress = { state.remainingFraction },
                modifier = Modifier.fillMaxSize(),
                color = phaseColor,
                strokeWidth = 8.dp,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
            Text(state.remainingText, style = MaterialTheme.typography.displayMedium.merge(TabularNumbers))
        }
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(Res.string.pomodoro_next, stringResource(state.nextPhase.label), state.nextPhaseMinutes),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            stringResource(Res.string.pomodoro_completed, state.completedFocuses),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        PomodoroControls(state = state, onAction = onAction)
        if (!state.controlsEnabled) OfflineControlsHint(modifier = Modifier.padding(top = 16.dp))
        if (state.status == PomodoroStatus.Idle) {
            Spacer(Modifier.height(24.dp))
            PomodoroSettingsEditor(settings = state.settings, enabled = state.controlsEnabled, onAction = onAction)
        }
        Spacer(Modifier.height(24.dp))
        footer()
    }
}

private val PomodoroPhaseKind.label: StringResource
    get() = when (this) {
        PomodoroPhaseKind.Focus -> Res.string.pomodoro_focus
        PomodoroPhaseKind.ShortBreak -> Res.string.pomodoro_short_break
        PomodoroPhaseKind.LongBreak -> Res.string.pomodoro_long_break
    }

@Composable
private fun phaseColor(phase: PomodoroPhaseKind): Color = when (phase) {
    PomodoroPhaseKind.Focus -> MaterialTheme.colorScheme.primary
    PomodoroPhaseKind.ShortBreak, PomodoroPhaseKind.LongBreak -> MaterialTheme.colorScheme.tertiary
}

@Composable
private fun PomodoroControls(state: PomodoroUiState, onAction: (PomodoroAction) -> Unit) {
    val enabled = state.controlsEnabled
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        when (state.status) {
            PomodoroStatus.Idle -> Button(onClick = { onAction(PomodoroAction.Start) }, enabled = enabled) {
                Text(stringResource(Res.string.action_start))
            }
            PomodoroStatus.Running -> {
                OutlinedButton(onClick = { onAction(PomodoroAction.Skip) }, enabled = enabled) {
                    Text(stringResource(Res.string.pomodoro_skip))
                }
                Button(onClick = { onAction(PomodoroAction.Pause) }, enabled = enabled) {
                    Text(stringResource(Res.string.action_pause))
                }
            }
            PomodoroStatus.Paused -> {
                OutlinedButton(onClick = { onAction(PomodoroAction.Reset) }, enabled = enabled) {
                    Text(stringResource(Res.string.action_reset))
                }
                OutlinedButton(onClick = { onAction(PomodoroAction.Skip) }, enabled = enabled) {
                    Text(stringResource(Res.string.pomodoro_skip))
                }
                Button(onClick = { onAction(PomodoroAction.Start) }, enabled = enabled) {
                    Text(stringResource(Res.string.action_resume))
                }
            }
        }
    }
}

@Composable
private fun PomodoroSettingsEditor(
    settings: PomodoroSettingsFields,
    enabled: Boolean,
    onAction: (PomodoroAction) -> Unit,
) {
    Column(modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth()) {
        Text(stringResource(Res.string.pomodoro_settings_title), style = MaterialTheme.typography.titleSmall)
        SettingRow(Res.string.pomodoro_setting_focus, settings.focusMinutes, PomodoroSettingField.Focus, enabled, onAction)
        SettingRow(Res.string.pomodoro_setting_short, settings.shortBreakMinutes, PomodoroSettingField.ShortBreak, enabled, onAction)
        SettingRow(Res.string.pomodoro_setting_long, settings.longBreakMinutes, PomodoroSettingField.LongBreak, enabled, onAction)
        SettingRow(Res.string.pomodoro_setting_cycles, settings.focusesBeforeLongBreak, PomodoroSettingField.FocusesBeforeLongBreak, enabled, onAction)
    }
}

@Composable
private fun SettingRow(
    label: StringResource,
    value: Int,
    field: PomodoroSettingField,
    enabled: Boolean,
    onAction: (PomodoroAction) -> Unit,
) {
    val labelText = stringResource(label)
    val increase = stringResource(Res.string.timer_increase, labelText)
    val decrease = stringResource(Res.string.timer_decrease, labelText)
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(labelText, modifier = Modifier.weight(1f))
        TextButton(
            onClick = { onAction(PomodoroAction.StepSetting(field, -1)) },
            enabled = enabled,
            modifier = Modifier.semantics { contentDescription = decrease },
        ) { Text("−", style = MaterialTheme.typography.titleLarge) }
        Text(
            value.toString(),
            style = MaterialTheme.typography.titleMedium.merge(TabularNumbers),
            textAlign = TextAlign.Center,
            modifier = Modifier.width(40.dp),
        )
        TextButton(
            onClick = { onAction(PomodoroAction.StepSetting(field, +1)) },
            enabled = enabled,
            modifier = Modifier.semantics { contentDescription = increase },
        ) { Text("+", style = MaterialTheme.typography.titleLarge) }
    }
}

@Preview
@Composable
private fun PomodoroScreenPreview() {
    MaterialTheme {
        PomodoroScreen(
            state = PomodoroUiState(
                status = PomodoroStatus.Running,
                phase = PomodoroPhaseKind.ShortBreak,
                remainingText = "03:12",
                remainingFraction = 0.64f,
                completedFocuses = 2,
                nextPhase = PomodoroPhaseKind.Focus,
                nextPhaseMinutes = 25,
                settings = PomodoroSettingsFields(25, 5, 15, 4),
            ),
            onAction = {},
        )
    }
}
