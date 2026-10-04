package com.adriano.cronosync.ui.timer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adriano.cronosync.timer.domain.TimerStatus
import com.adriano.cronosync.timer.presentation.DurationField
import com.adriano.cronosync.timer.presentation.DurationFields
import com.adriano.cronosync.timer.presentation.TimerAction
import com.adriano.cronosync.timer.presentation.TimerUiState
import com.adriano.cronosync.timer.presentation.TimerViewModel
import com.adriano.cronosync.ui.alarm.AlarmOptionsRoute
import com.adriano.cronosync.ui.resources.Res
import com.adriano.cronosync.ui.resources.action_pause
import com.adriano.cronosync.ui.resources.action_reset
import com.adriano.cronosync.ui.resources.action_resume
import com.adriano.cronosync.ui.resources.action_start
import com.adriano.cronosync.ui.resources.timer_decrease
import com.adriano.cronosync.ui.resources.timer_hours
import com.adriano.cronosync.ui.resources.timer_increase
import com.adriano.cronosync.ui.resources.timer_minutes
import com.adriano.cronosync.ui.resources.timer_seconds
import com.adriano.cronosync.ui.sync.OfflineControlsHint
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

private val TabularNumbers = TextStyle(fontFeatureSettings = "tnum")

@Composable
fun TimerRoute(
    modifier: Modifier = Modifier,
    viewModel: TimerViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    TimerScreen(
        state = state,
        onAction = viewModel::onAction,
        modifier = modifier,
        footer = { AlarmOptionsRoute(showVibration = false) },
    )
}

@Composable
fun TimerScreen(
    state: TimerUiState,
    onAction: (TimerAction) -> Unit,
    modifier: Modifier = Modifier,
    footer: @Composable () -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(48.dp))
        if (state.status == TimerStatus.Idle) {
            DurationPicker(duration = state.duration, enabled = state.controlsEnabled, onAction = onAction)
        } else {
            Countdown(state = state)
        }
        Spacer(Modifier.height(48.dp))
        TimerControls(state = state, onAction = onAction)
        if (!state.controlsEnabled) OfflineControlsHint(modifier = Modifier.padding(top = 16.dp))
        Spacer(Modifier.height(32.dp))
        footer()
    }
}

@Composable
private fun DurationPicker(
    duration: DurationFields,
    enabled: Boolean,
    onAction: (TimerAction) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        DurationFieldStepper(duration.hours, Res.string.timer_hours, DurationField.Hours, enabled, onAction)
        DurationFieldStepper(duration.minutes, Res.string.timer_minutes, DurationField.Minutes, enabled, onAction)
        DurationFieldStepper(duration.seconds, Res.string.timer_seconds, DurationField.Seconds, enabled, onAction)
    }
}

@Composable
private fun DurationFieldStepper(
    value: Int,
    label: StringResource,
    field: DurationField,
    enabled: Boolean,
    onAction: (TimerAction) -> Unit,
) {
    val labelText = stringResource(label)
    val increase = stringResource(Res.string.timer_increase, labelText)
    val decrease = stringResource(Res.string.timer_decrease, labelText)

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        TextButton(
            onClick = { onAction(TimerAction.StepDuration(field, +1)) },
            enabled = enabled,
            modifier = Modifier.semantics { contentDescription = increase },
        ) {
            Text("+", style = MaterialTheme.typography.headlineMedium)
        }
        Text(
            text = value.toString().padStart(2, '0'),
            style = MaterialTheme.typography.displayMedium.merge(TabularNumbers),
        )
        Text(labelText, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(
            onClick = { onAction(TimerAction.StepDuration(field, -1)) },
            enabled = enabled,
            modifier = Modifier.semantics { contentDescription = decrease },
        ) {
            Text("−", style = MaterialTheme.typography.headlineMedium)
        }
    }
}

@Composable
private fun Countdown(state: TimerUiState) {
    val finished = state.status == TimerStatus.Finished
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(280.dp)) {
        CircularProgressIndicator(
            progress = { state.remainingFraction },
            modifier = Modifier.fillMaxSize(),
            strokeWidth = 8.dp,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
        Text(
            text = state.remainingText,
            style = MaterialTheme.typography.displayMedium.merge(TabularNumbers),
            color = if (finished) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun TimerControls(
    state: TimerUiState,
    onAction: (TimerAction) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        when (state.status) {
            TimerStatus.Idle -> Button(onClick = { onAction(TimerAction.Start) }, enabled = state.canStart && state.controlsEnabled) {
                Text(stringResource(Res.string.action_start))
            }

            TimerStatus.Running, TimerStatus.Paused -> {
                OutlinedButton(onClick = { onAction(TimerAction.Reset) }, enabled = state.controlsEnabled) {
                    Text(stringResource(Res.string.action_reset))
                }
                if (state.status == TimerStatus.Running) {
                    Button(onClick = { onAction(TimerAction.Pause) }, enabled = state.controlsEnabled) { Text(stringResource(Res.string.action_pause)) }
                } else {
                    Button(onClick = { onAction(TimerAction.Start) }, enabled = state.controlsEnabled) { Text(stringResource(Res.string.action_resume)) }
                }
            }

            TimerStatus.Finished -> Button(onClick = { onAction(TimerAction.Reset) }, enabled = state.controlsEnabled) {
                Text(stringResource(Res.string.action_reset))
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun TimerScreenIdlePreview() {
    MaterialTheme {
        TimerScreen(
            state = TimerUiState(
                status = TimerStatus.Idle,
                remainingText = "05:00",
                remainingFraction = 1f,
                duration = DurationFields(minutes = 5),
                canStart = true,
            ),
            onAction = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun TimerScreenRunningPreview() {
    MaterialTheme {
        TimerScreen(
            state = TimerUiState(
                status = TimerStatus.Running,
                remainingText = "03:12",
                remainingFraction = 0.64f,
                duration = DurationFields(minutes = 5),
                canStart = false,
            ),
            onAction = {},
        )
    }
}
