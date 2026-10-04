package com.adriano.cronosync.ui.sync

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.adriano.cronosync.ui.resources.Res
import com.adriano.cronosync.ui.resources.controls_offline
import org.jetbrains.compose.resources.stringResource

@Composable
fun OfflineControlsHint(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(Res.string.controls_offline),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
        textAlign = TextAlign.Center,
        modifier = modifier,
    )
}
