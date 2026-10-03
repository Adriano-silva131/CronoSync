package com.adriano.cronosync.desktop.window

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState

/**
 * Sem bandeja, o app não pode se esconder (não haveria como recuperá-lo). Se algo está contando,
 * avisamos que fechar desliga os avisos e oferecemos minimizar no lugar — a alternativa clara.
 */
@Composable
fun ExitConfirmationDialog(
    onMinimize: () -> Unit,
    onExit: () -> Unit,
    onCancel: () -> Unit,
) {
    DialogWindow(
        onCloseRequest = onCancel,
        title = "Fechar o CronoSync?",
        state = rememberDialogState(size = DpSize(460.dp, 220.dp)),
        resizable = false,
    ) {
        MaterialTheme {
            Surface(modifier = Modifier.fillMaxSize()) {
                Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        "Há um timer ou Pomodoro rodando. Com o app fechado, você não será avisado " +
                            "quando ele acabar. Minimizar mantém tudo funcionando.",
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = onCancel) { Text("Cancelar") }
                        OutlinedButton(onClick = onExit) { Text("Sair") }
                        Button(onClick = onMinimize) { Text("Minimizar") }
                    }
                }
            }
        }
    }
}
