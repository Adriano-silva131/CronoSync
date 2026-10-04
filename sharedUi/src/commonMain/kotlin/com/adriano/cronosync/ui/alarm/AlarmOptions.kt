package com.adriano.cronosync.ui.alarm

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adriano.cronosync.alarm.data.AlarmPreferences
import com.adriano.cronosync.alarm.presentation.AlarmOptionsAction
import com.adriano.cronosync.alarm.presentation.AlarmOptionsViewModel
import com.adriano.cronosync.ui.resources.Res
import com.adriano.cronosync.ui.resources.alarm_option_sound
import com.adriano.cronosync.ui.resources.alarm_option_vibration
import com.adriano.cronosync.ui.resources.alarm_options_label
import com.adriano.cronosync.ui.resources.alarm_options_screen_only
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun AlarmOptionsRoute(
    showVibration: Boolean,
    modifier: Modifier = Modifier,
    viewModel: AlarmOptionsViewModel = koinViewModel(),
) {
    val preferences by viewModel.uiState.collectAsStateWithLifecycle()
    AlarmOptions(preferences = preferences, showVibration = showVibration, onAction = viewModel::onAction, modifier = modifier)
}

@Composable
fun AlarmOptions(
    preferences: AlarmPreferences,
    showVibration: Boolean,
    onAction: (AlarmOptionsAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier,
    ) {
        Text(
            stringResource(Res.string.alarm_options_label),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = preferences.sound,
                onClick = { onAction(AlarmOptionsAction.SetSound(!preferences.sound)) },
                label = { Text(stringResource(Res.string.alarm_option_sound)) },
            )
            if (showVibration) {
                FilterChip(
                    selected = preferences.vibration,
                    onClick = { onAction(AlarmOptionsAction.SetVibration(!preferences.vibration)) },
                    label = { Text(stringResource(Res.string.alarm_option_vibration)) },
                )
            }
        }
        if (showVibration && !preferences.sound && !preferences.vibration) {
            Text(
                stringResource(Res.string.alarm_options_screen_only),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Preview
@Composable
private fun AlarmOptionsPreview() {
    MaterialTheme {
        AlarmOptions(preferences = AlarmPreferences(sound = false, vibration = false), showVibration = true, onAction = {})
    }
}
