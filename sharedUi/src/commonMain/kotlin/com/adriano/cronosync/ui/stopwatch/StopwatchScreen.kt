package com.adriano.cronosync.ui.stopwatch

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adriano.cronosync.stopwatch.domain.Stopwatch
import com.adriano.cronosync.stopwatch.domain.StopwatchStatus
import com.adriano.cronosync.stopwatch.presentation.LapUiModel
import com.adriano.cronosync.stopwatch.presentation.StopwatchAction
import com.adriano.cronosync.stopwatch.presentation.StopwatchUiState
import com.adriano.cronosync.stopwatch.presentation.StopwatchViewModel
import com.adriano.cronosync.ui.resources.Res
import com.adriano.cronosync.ui.resources.action_pause
import com.adriano.cronosync.ui.resources.action_reset
import com.adriano.cronosync.ui.resources.action_resume
import com.adriano.cronosync.ui.resources.action_start
import com.adriano.cronosync.ui.resources.stopwatch_lap
import com.adriano.cronosync.ui.resources.stopwatch_lap_limit
import com.adriano.cronosync.ui.resources.stopwatch_lap_number
import com.adriano.cronosync.ui.sync.OfflineControlsHint
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun StopwatchRoute(
    modifier: Modifier = Modifier,
    viewModel: StopwatchViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    StopwatchScreen(state = state, onAction = viewModel::onAction, modifier = modifier)
}

@Composable
fun StopwatchScreen(
    state: StopwatchUiState,
    onAction: (StopwatchAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(64.dp))
        Text(
            text = state.elapsedText,
            style = MaterialTheme.typography.displayLarge.merge(TextStyle(fontFeatureSettings = "tnum")),
        )
        Spacer(Modifier.height(48.dp))
        StopwatchControls(
            status = state.status,
            enabled = state.controlsEnabled,
            lapEnabled = !state.lapLimitReached,
            onAction = onAction,
        )
        if (!state.controlsEnabled) {
            OfflineControlsHint(modifier = Modifier.padding(top = 16.dp))
        } else if (state.lapLimitReached && state.status == StopwatchStatus.Running) {
            Text(
                text = stringResource(Res.string.stopwatch_lap_limit, Stopwatch.MAX_LAPS),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
        Spacer(Modifier.height(24.dp))
        LapList(laps = state.laps)
    }
}

@Composable
private fun StopwatchControls(
    status: StopwatchStatus,
    enabled: Boolean,
    lapEnabled: Boolean,
    onAction: (StopwatchAction) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        when (status) {
            StopwatchStatus.Paused -> OutlinedButton(onClick = { onAction(StopwatchAction.Reset) }, enabled = enabled) {
                Text(stringResource(Res.string.action_reset))
            }
            StopwatchStatus.Idle, StopwatchStatus.Running -> OutlinedButton(
                onClick = { onAction(StopwatchAction.Lap) },
                enabled = enabled && lapEnabled && status == StopwatchStatus.Running,
            ) {
                Text(stringResource(Res.string.stopwatch_lap))
            }
        }

        when (status) {
            StopwatchStatus.Running -> Button(onClick = { onAction(StopwatchAction.Pause) }, enabled = enabled) {
                Text(stringResource(Res.string.action_pause))
            }
            StopwatchStatus.Idle, StopwatchStatus.Paused -> Button(onClick = { onAction(StopwatchAction.Start) }, enabled = enabled) {
                val label = if (status == StopwatchStatus.Idle) Res.string.action_start else Res.string.action_resume
                Text(stringResource(label))
            }
        }
    }
}

@Composable
private fun LapList(laps: List<LapUiModel>) {
    LazyColumn(modifier = Modifier.fillMaxWidth()) {
        items(laps, key = { it.number }) { lap ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(stringResource(Res.string.stopwatch_lap_number, lap.number))
                Text(lap.lapText)
                Text(lap.totalText, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            HorizontalDivider()
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun StopwatchScreenIdlePreview() {
    MaterialTheme {
        StopwatchScreen(state = StopwatchUiState(), onAction = {})
    }
}

@Preview(showBackground = true)
@Composable
private fun StopwatchScreenRunningPreview() {
    MaterialTheme {
        StopwatchScreen(
            state = StopwatchUiState(
                status = StopwatchStatus.Running,
                elapsedText = "01:23.45",
                laps = listOf(
                    LapUiModel(number = 2, lapText = "00:41.20", totalText = "01:10.70"),
                    LapUiModel(number = 1, lapText = "00:29.50", totalText = "00:29.50"),
                ),
            ),
            onAction = {},
        )
    }
}
